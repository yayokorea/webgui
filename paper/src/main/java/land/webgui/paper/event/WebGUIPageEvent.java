package land.webgui.paper.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A web page sent the server a message, through {@code window.webgui.postToGame({ channel: ... })}.
 *
 * Fired on the server's main thread, after the per-player rate limit, so a handler can use
 * the Bukkit API directly. Built-in channels ({@code log}, {@code close}) are handled by the
 * client and never arrive here. The payload is whatever JSON text the page sent: it comes
 * from a player's machine, so parse it defensively and check what it asks for.
 */
public class WebGUIPageEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String channel;
    private final String jsonPayload;

    public WebGUIPageEvent(@NotNull Player player, @NotNull String channel, @NotNull String jsonPayload) {
        super(player);
        this.channel = channel;
        this.jsonPayload = jsonPayload;
    }

    /** The page's own name for the message, at most 256 characters. */
    public @NotNull String getChannel() {
        return channel;
    }

    /** The message body as JSON text, at most 32768 characters. */
    public @NotNull String getJsonPayload() {
        return jsonPayload;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
