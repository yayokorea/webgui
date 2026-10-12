package land.webgui.paper;

import land.webgui.WebGUIVersion;
import land.webgui.common.ClientCheck;
import land.webgui.common.ClientKind;
import land.webgui.common.WebGUILog;
import land.webgui.server.WebviewRateLimiter;
import land.webgui.server.WebviewServerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * What happens when a player joins: find out whether their client has WebGUI, tell them if
 * not, and otherwise hand over the server's settings and the automatic HUD.
 *
 * Mirrors the mod's {@code WebviewClientCheck} and {@code WebviewJoinHud}. The difference is
 * when: a mod server knows the client's channels during login, Bukkit only learns them from
 * the client afterwards, so {@link JoinHandshake} waits for them first.
 */
public final class PaperJoinFlow implements Listener {
    private final JoinHandshake handshake;

    public PaperJoinFlow(Plugin plugin) {
        this.handshake = new JoinHandshake(
                (ticks, task) -> {
                    var scheduled = Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
                    return scheduled::cancel;
                },
                id -> {
                    Player player = Bukkit.getPlayer(id);
                    return player == null ? ClientKind.ABSENT : PaperNetworking.clientKind(player);
                },
                this::proceed);
    }

    /** For players already online when the plugin starts, e.g. after a plugin manager reload. */
    public void adoptOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            handshake.onJoin(player.getUniqueId());
        }
    }

    public void shutdown() {
        handshake.shutdown();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        handshake.onJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent event) {
        handshake.onChannelRegistered(event.getPlayer().getUniqueId(), event.getChannel());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        handshake.onQuit(id);
        WebviewRateLimiter.forget(id);
    }

    private void proceed(UUID id, ClientKind kind) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            return;
        }
        // First: everything below is pointless for a client that cannot receive it, and
        // this is also where such a client is told so.
        if (!checkClient(player, kind)) {
            return;
        }

        PaperNetworking.sendConfigSnapshot(player);

        if (!WebviewServerConfig.autoHudOnJoin()) {
            return;
        }
        String url = WebviewServerConfig.autoHudUrl();
        if (url.isEmpty()) {
            WebGUILog.LOGGER.warn("webgui: autoHudOnJoin is true but autoHudUrl is empty — set autoHudUrl in {}/server.json",
                    WebviewServerConfig.displayDir());
            return;
        }
        PaperNetworking.openHud(player, url);
        WebGUILog.LOGGER.info("webgui: auto HUD for {} → {}", player.getName(), url);
    }

    /** Returns false when the player was refused. */
    private boolean checkClient(Player player, ClientKind kind) {
        if (kind == ClientKind.CURRENT) {
            PaperNetworking.sendHello(player);
            return true;
        }

        String name = player.getName();
        if (WebviewServerConfig.requireClientMod()) {
            WebGUILog.LOGGER.info("webgui: refused {} — client is {}", name, kind);
            player.kick(Component.text(ClientCheck.kickText(kind)));
            return false;
        }

        if (kind == ClientKind.OUTDATED) {
            WebGUILog.LOGGER.warn("webgui: {} has an outdated WebGUI; server runs {}", name, WebGUIVersion.current());
            player.sendMessage(Component.text("[WebGUI] " + ClientCheck.outdatedText(), NamedTextColor.YELLOW));
        } else if (ClientCheck.serverUsesPages()) {
            WebGUILog.LOGGER.info("webgui: {} joined without WebGUI; server runs {}", name, WebGUIVersion.current());
            player.sendMessage(Component.text("[WebGUI] " + ClientCheck.absentText(), NamedTextColor.GRAY));
        }
        return true;
    }
}
