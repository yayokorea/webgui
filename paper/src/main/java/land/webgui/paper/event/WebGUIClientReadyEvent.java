package land.webgui.paper.event;

import land.webgui.common.ClientKind;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player's client has been identified as one that can receive WebGUI pages, and the server's
 * settings have been sent to it.
 *
 * This is the first moment a page sent from another plugin reaches the player. Anything sent
 * earlier — in {@code PlayerJoinEvent}, say — is dropped without a trace: Bukkit discards
 * messages on a channel the client has not registered, and a Fabric client registers its
 * channels only after it has joined. Open a welcome screen or HUD from here.
 *
 * Fired on the main thread, once per join, only for clients that can receive pages
 * ({@link ClientKind#CURRENT}); a vanilla or outdated client never gets it. After the plugin's
 * own automatic HUD ({@code autoHudOnJoin}), so a page opened here replaces that one.
 */
public class WebGUIClientReadyEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ClientKind clientKind;

    public WebGUIClientReadyEvent(@NotNull Player player, @NotNull ClientKind clientKind) {
        super(player);
        this.clientKind = clientKind;
    }

    /** What kind of WebGUI client this is; {@link ClientKind#CURRENT} today. */
    public @NotNull ClientKind getClientKind() {
        return clientKind;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
