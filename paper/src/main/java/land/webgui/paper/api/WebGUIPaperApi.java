package land.webgui.paper.api;

import land.webgui.EntityBindingStore;
import land.webgui.common.ClientKind;
import land.webgui.common.WebviewProtocol;
import land.webgui.paper.PaperJoinFlow;
import land.webgui.paper.PaperNetworking;
import land.webgui.server.EntityBinding;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * What other plugins can ask WebGUI to do. The Paper counterpart of the mod's {@code WebviewApi}.
 * Call from the server's main thread.
 *
 * <b>When a page can be sent.</b> A player's client announces what it can receive only after it
 * has joined, and Bukkit silently drops anything sent on a channel the client has not announced.
 * So {@code openGui}, {@code openHud}, {@code sendMainMenuUrl} and {@code emitToPage} do nothing
 * for a player in {@code PlayerJoinEvent}, and nothing for one whose client has no WebGUI.
 * Wait for {@link land.webgui.paper.event.WebGUIClientReadyEvent}, or check {@link #isReady}.
 *
 * Declare the dependency in your plugin.yml ({@code depend: [WebGUI]} or {@code softdepend}) so
 * WebGUI is enabled before you use this.
 */
public final class WebGUIPaperApi {
    private WebGUIPaperApi() {}

    /**
     * Opens a page as a screen. If signed tokens are enabled in server.json, a token is appended
     * to the URL automatically.
     *
     * Dropped silently unless the player {@linkplain #isReady is ready}.
     */
    public static void openGui(Player player, String url) {
        PaperNetworking.openGui(player, url);
    }

    /**
     * Shows a page as a HUD overlay. If signed tokens are enabled in server.json, a token is
     * appended to the URL automatically.
     *
     * Dropped silently unless the player {@linkplain #isReady is ready}.
     */
    public static void openHud(Player player, String url) {
        PaperNetworking.openHud(player, url);
    }

    /** Player opens this URL via the main menu keybind (default F6). */
    public static void sendMainMenuUrl(Player player, String url) {
        PaperNetworking.sendMainMenuUrl(player, url);
    }

    /**
     * Sends a named event to the player's active WebGUI page(s).
     *
     * @param eventName   name of the event; the page receives it as {@code webgui:<eventName>}
     *                    via {@code window.addEventListener} or {@code window.webgui.on}
     * @param jsonPayload valid JSON value (object, array, string, number, boolean, or {@code "null"});
     *                    passed as {@code event.detail} in the browser
     */
    public static void emitToPage(Player player, String eventName, String jsonPayload) {
        PaperNetworking.emitToPage(player, eventName, jsonPayload);
    }

    /**
     * Binds an entity to a WebGUI URL. When a player right-clicks the entity, the GUI opens.
     * Bindings are saved with the server's settings and survive restarts.
     *
     * @param entityUuid        UUID of the entity to bind
     * @param urlTemplate       URL to open; supports {entity_id}, {entity_uuid}, {entity_type},
     *                          {player_name}, {player_uuid} placeholders
     * @param cancelInteraction if true, the default entity interaction (e.g. villager trade) is suppressed
     */
    public static void bindEntity(UUID entityUuid, String urlTemplate, boolean cancelInteraction) {
        EntityBindingStore.bind(entityUuid, new EntityBinding(urlTemplate, cancelInteraction));
    }

    /** Removes the WebGUI binding for the given entity UUID. */
    public static void unbindEntity(UUID entityUuid) {
        EntityBindingStore.unbind(entityUuid);
    }

    /** Returns the entity binding for the given UUID, if one exists. */
    public static Optional<EntityBinding> getEntityBinding(UUID entityUuid) {
        return EntityBindingStore.get(entityUuid);
    }

    /** Maximum URL length accepted by the client (16 384 characters). */
    public static int maxUrlLength() {
        return WebviewProtocol.MAX_URL_LENGTH;
    }

    /** S2C protocol version this build of the plugin uses. */
    public static int protocolVersion() {
        return WebviewProtocol.PROTOCOL_VERSION;
    }

    /**
     * Whether the player's client has WebGUI, and whether it is new enough to talk to this
     * server. Only meaningful once the client has announced its channels (see
     * {@link land.webgui.paper.event.WebGUIClientReadyEvent}); before that it reads as
     * {@link ClientKind#ABSENT}.
     */
    public static ClientKind clientKind(Player player) {
        return PaperNetworking.clientKind(player);
    }

    /**
     * Whether pages sent to the player now reach them: their client is a current WebGUI and the
     * join handshake has finished. True from {@link land.webgui.paper.event.WebGUIClientReadyEvent}
     * until they leave.
     */
    public static boolean isReady(Player player) {
        return PaperJoinFlow.isReady(player.getUniqueId());
    }
}
