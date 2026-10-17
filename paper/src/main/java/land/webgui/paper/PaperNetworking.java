package land.webgui.paper;

import land.webgui.WebGUIVersion;
import land.webgui.common.ClientKind;
import land.webgui.common.WebGUILog;
import land.webgui.common.WebviewProtocol;
import land.webgui.common.WebviewWire;
import land.webgui.common.WireException;
import land.webgui.common.WireText;
import land.webgui.paper.event.WebGUIPageEvent;
import land.webgui.server.WebviewAssetStore;
import land.webgui.server.WebviewAssets;
import land.webgui.server.WebviewRateLimiter;
import land.webgui.server.WebviewServerConfig;
import land.webgui.server.WebviewSignedToken;
import land.webgui.server.WebviewUrlBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.List;

import static land.webgui.common.WebviewProtocol.MODE_GUI;
import static land.webgui.common.WebviewProtocol.MODE_HUD;
import static land.webgui.common.WebviewProtocol.PROTOCOL_VERSION;

/**
 * Everything the server says to a WebGUI client, and the two things a client says back.
 *
 * The Paper counterpart of the mod's {@code WebviewNetworking}, with the same method names
 * and the same behaviour; it differs only in how bytes reach the wire. A mod loader has a
 * typed payload per message. Bukkit has "plugin channels": a name and raw bytes, which
 * {@link WebviewWire} produces in exactly the format the client's codecs read.
 *
 * A message to a player whose client has not registered that channel is dropped by Bukkit
 * without a word. A Fabric client registers its channels itself, shortly after it joins
 * (see {@link PaperJoinFlow}), so nothing here may be sent before then.
 */
public final class PaperNetworking {
    private static final List<String> OUTGOING = List.of(
            WebviewProtocol.CHANNEL_OPEN_WEB,
            WebviewProtocol.CHANNEL_SET_MAIN_MENU,
            WebviewProtocol.CHANNEL_EMIT_TO_PAGE,
            WebviewProtocol.CHANNEL_ENTITY_CONTEXT,
            WebviewProtocol.CHANNEL_TRUSTED_ORIGINS,
            WebviewProtocol.CHANNEL_DEATH_SCREEN,
            WebviewProtocol.CHANNEL_HELLO,
            WebviewProtocol.CHANNEL_ASSET_MANIFEST,
            WebviewProtocol.CHANNEL_ASSET_CHUNK);

    private static volatile Plugin plugin;

    private PaperNetworking() {}

    // --- Channels ---

    /**
     * Declares the channels to Bukkit.
     *
     * Incoming ones matter beyond receiving: Bukkit announces them to every joining client
     * in its {@code minecraft:register}, and a Fabric client refuses to send a channel the
     * server never announced. So this has to run before players join, which onEnable does.
     */
    public static void registerChannels(Plugin owner) {
        plugin = owner;
        Messenger messenger = Bukkit.getMessenger();
        for (String channel : OUTGOING) {
            messenger.registerOutgoingPluginChannel(owner, channel);
        }
        Incoming incoming = new Incoming();
        messenger.registerIncomingPluginChannel(owner, WebviewProtocol.CHANNEL_PAGE_EVENT, incoming);
        messenger.registerIncomingPluginChannel(owner, WebviewProtocol.CHANNEL_ASSET_REQUEST, incoming);
    }

    public static void unregisterChannels(Plugin owner) {
        Messenger messenger = Bukkit.getMessenger();
        messenger.unregisterOutgoingPluginChannel(owner);
        messenger.unregisterIncomingPluginChannel(owner);
        plugin = null;
    }

    /** Whether the client told the server it can receive this channel. */
    static boolean canSend(Player player, String channel) {
        return player.getListeningPluginChannels().contains(channel);
    }

    private static void send(Player player, String channel, byte[] bytes) {
        player.sendPluginMessage(plugin, channel, bytes);
    }

    // --- Sending ---

    public static void openGui(Player player, String url) {
        clearEntityContext(player);
        send(player, WebviewProtocol.CHANNEL_OPEN_WEB, WebviewWire.encodeOpenWeb(
                new WebviewWire.OpenWeb(PROTOCOL_VERSION, MODE_GUI, withPlayerToken(player, url))));
    }

    public static void openHud(Player player, String url) {
        clearEntityContext(player);
        send(player, WebviewProtocol.CHANNEL_OPEN_WEB, WebviewWire.encodeOpenWeb(
                new WebviewWire.OpenWeb(PROTOCOL_VERSION, MODE_HUD, withPlayerToken(player, url))));
    }

    public static void openGuiForEntity(Player player, String url, String entityJson) {
        send(player, WebviewProtocol.CHANNEL_OPEN_WEB, WebviewWire.encodeOpenWeb(
                new WebviewWire.OpenWeb(PROTOCOL_VERSION, MODE_GUI, withPlayerToken(player, url))));
        // The context goes out after the open, not before it. Opening a gui builds a new
        // browser and clears the client's push cache, and the entity context lives in that
        // cache: sent first, it was wiped by the very screen it was meant for, and the page
        // received null. Packets on one connection keep their order, so this arrives after.
        sendEntityContext(player, entityJson);
    }

    public static void sendEntityContext(Player player, String entityJson) {
        send(player, WebviewProtocol.CHANNEL_ENTITY_CONTEXT,
                WebviewWire.encodeEntityContext(new WebviewWire.EntityContext(entityJson)));
    }

    public static void clearEntityContext(Player player) {
        sendEntityContext(player, "null");
    }

    public static void emitToPage(Player player, String eventName, String jsonPayload) {
        String name = WireText.sanitizeStr(eventName, WebviewProtocol.MAX_EVENT_NAME_LENGTH);
        String data = WireText.emitData(jsonPayload);
        send(player, WebviewProtocol.CHANNEL_EMIT_TO_PAGE,
                WebviewWire.encodeEmitToPage(new WebviewWire.EmitToPage(name, data)));
    }

    public static void sendMainMenuUrl(Player player, String url) {
        send(player, WebviewProtocol.CHANNEL_SET_MAIN_MENU,
                WebviewWire.encodeSetMainMenu(new WebviewWire.SetMainMenu(WireText.sanitizeUrl(url))));
    }

    /**
     * Tells the client which page replaces the vanilla death screen, and — when the
     * player has just died — what killed them.
     *
     * The URL carries a per-player token like any other page, so the death page can verify
     * who it is talking about.
     */
    public static void sendDeathScreen(Player player, String url, String infoJson) {
        String u = (url == null || url.isBlank()) ? "" : withPlayerToken(player, url);
        String info = (infoJson == null || infoJson.isBlank())
                ? ""
                : WireText.sanitizeStr(infoJson, WebviewProtocol.MAX_EVENT_DATA_LENGTH);
        send(player, WebviewProtocol.CHANNEL_DEATH_SCREEN,
                WebviewWire.encodeDeathScreen(new WebviewWire.DeathScreen(u, info)));
    }

    /**
     * Tells the client which WebGUI the server runs. Skipped for a client that has no such
     * channel — that case is reported to the player as text instead.
     */
    public static void sendHello(Player player) {
        if (!canSend(player, WebviewProtocol.CHANNEL_HELLO)) {
            return;
        }
        send(player, WebviewProtocol.CHANNEL_HELLO,
                WebviewWire.encodeHello(new WebviewWire.Hello(PROTOCOL_VERSION, WebGUIVersion.current())));
    }

    /** Whether the player's client has WebGUI, and whether it is new enough to talk to us. */
    public static ClientKind clientKind(Player player) {
        return clientKind(player.getListeningPluginChannels());
    }

    static ClientKind clientKind(java.util.Set<String> registered) {
        if (registered.contains(WebviewProtocol.CHANNEL_HELLO)) {
            return ClientKind.CURRENT;
        }
        // No handshake channel but the original one is there: WebGUI, just an older build.
        return registered.contains(WebviewProtocol.CHANNEL_OPEN_WEB)
                ? ClientKind.OUTDATED
                : ClientKind.ABSENT;
    }

    /** Tells the client which pages this server ships. Skipped when it ships none. */
    public static void sendAssetManifest(Player player) {
        if (!WebviewAssets.enabled() || !canSend(player, WebviewProtocol.CHANNEL_ASSET_MANIFEST)) {
            return;
        }
        WebviewAssetStore store = WebviewAssets.store();
        send(player, WebviewProtocol.CHANNEL_ASSET_MANIFEST, WebviewWire.encodeAssetManifest(
                new WebviewWire.AssetManifest(store.revision(), store.toManifest())));
    }

    /**
     * Sends a player every client-side copy of a server setting.
     *
     * One place, called both when a player joins and when {@code /webgui reload} runs.
     * These values live on the client once pushed, and each one the reload path forgot to
     * re-send was a setting that silently did not apply until the player reconnected —
     * which is how an edited page kept showing its old self.
     */
    public static void sendConfigSnapshot(Player player) {
        sendTrustedOrigins(player, WebviewServerConfig.trustedCommandOriginsJoined());
        // Before any page URL: a page pointed at webgui:/ cannot resolve anything until
        // the client knows what the server ships.
        sendAssetManifest(player);
        // Pushed ahead of the death itself: the client has to know whether to suppress
        // the vanilla screen the instant it is opened.
        sendDeathScreen(player, WebviewServerConfig.deathScreenUrl(), "");
        // Empty included, so clearing mainMenuUrl reaches a player who was given one.
        sendMainMenuUrl(player, WebviewServerConfig.mainMenuUrl());
    }

    public static void sendTrustedOrigins(Player player, String origins) {
        String o = origins == null ? "" : origins;
        send(player, WebviewProtocol.CHANNEL_TRUSTED_ORIGINS,
                WebviewWire.encodeTrustedOrigins(new WebviewWire.TrustedOrigins(o)));
    }

    private static String withPlayerToken(Player player, String url) {
        if (!WebviewServerConfig.enableTokens()) {
            return WireText.sanitizeUrl(url);
        }
        String token = WebviewSignedToken.create(player.getUniqueId());
        if (token.isEmpty()) {
            return WireText.sanitizeUrl(url);
        }
        String withParam = WebviewUrlBuilder.appendQueryParam(url == null ? "" : url, WebviewServerConfig.queryParamName(), token);
        return WireText.sanitizeUrl(withParam);
    }

    // --- Receiving ---

    /**
     * Both channels a client writes to.
     *
     * Bukkit calls this on the server's main thread (Paper's custom payload handler hops to
     * it before dispatching), so the work that does not belong on the tick loop has to leave
     * it explicitly.
     */
    private static final class Incoming implements PluginMessageListener {
        @Override
        public void onPluginMessageReceived(String channel, Player player, byte[] message) {
            try {
                switch (channel) {
                    case WebviewProtocol.CHANNEL_PAGE_EVENT -> onPageEvent(player, message);
                    case WebviewProtocol.CHANNEL_ASSET_REQUEST -> onAssetRequest(player, message);
                    default -> { }
                }
            } catch (WireException e) {
                // A client we cannot parse is either a different version or hostile, and
                // either way the answer is to ignore it — never to throw back into Bukkit.
                WebGUILog.LOGGER.debug("webgui: dropped a malformed {} message from {}: {}",
                        channel, player.getName(), e.getMessage());
            }
        }
    }

    private static void onPageEvent(Player player, byte[] message) {
        WebviewWire.PageEvent event = WebviewWire.decodePageEvent(message);
        // Before anything is scheduled or fired, so a flood is stopped at the cheapest point.
        if (!WebviewRateLimiter.allowPageEvent(player.getUniqueId(), player.getName())) {
            return;
        }
        Runnable fire = () -> {
            WebGUILog.LOGGER.debug("webgui: page event {} from {}", event.channel(), player.getName());
            Bukkit.getPluginManager().callEvent(new WebGUIPageEvent(player, event.channel(), event.jsonPayload()));
        };
        if (Bukkit.isPrimaryThread()) {
            fire.run();
        } else {
            runSync(fire);
        }
    }

    private static void onAssetRequest(Player player, byte[] message) {
        String requested = WebviewWire.decodeAssetRequest(message).path();
        Plugin owner = plugin;
        if (owner == null || !owner.isEnabled()) {
            return;
        }
        // Reading and slicing a file has no business on the tick loop, and the budget
        // check inside is what keeps this from being a way to stall it.
        Bukkit.getScheduler().runTaskAsynchronously(owner, () -> {
            String path = WebviewAssetStore.normalizeRequest(requested);
            List<byte[]> chunks = WebviewAssets.chunksFor(player.getUniqueId(), player.getName(), path);
            // Handed back to the main thread to send. Queuing a packet is cheap; what is
            // not guaranteed safe off-thread is Bukkit's own bookkeeping behind it (the
            // player's channel set is a plain HashSet the main thread mutates).
            runSync(() -> serveAsset(player, path, chunks));
        });
    }

    /**
     * Answers one asset request, in slices.
     *
     * Always answers, even with nothing: the client has a browser request parked on this,
     * and a page that hangs forever is far worse to diagnose than one that reports a
     * missing file.
     */
    private static void serveAsset(Player player, String path, List<byte[]> chunks) {
        if (!player.isOnline()) {
            return;
        }
        if (chunks.isEmpty()) {
            sendAssetChunk(player, path, 0, 0, new byte[0]);
            return;
        }
        for (int i = 0; i < chunks.size(); i++) {
            sendAssetChunk(player, path, i, chunks.size(), chunks.get(i));
        }
    }

    private static void sendAssetChunk(Player player, String path, int index, int count, byte[] bytes) {
        send(player, WebviewProtocol.CHANNEL_ASSET_CHUNK, WebviewWire.encodeAssetChunk(
                new WebviewWire.AssetChunk(path, index, count, bytes)));
    }

    private static void runSync(Runnable task) {
        Plugin owner = plugin;
        // The plugin can be disabled while a read is in flight; scheduling then throws.
        if (owner != null && owner.isEnabled()) {
            Bukkit.getScheduler().runTask(owner, task);
        }
    }
}
