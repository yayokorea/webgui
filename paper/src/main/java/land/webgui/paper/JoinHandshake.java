package land.webgui.paper;

import land.webgui.common.ClientKind;
import land.webgui.common.WebviewProtocol;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Decides when a joining player's client has said what it can receive, and what it is.
 *
 * No server API in here, so the timing can be tested without a server. The reason it has
 * timing at all: Bukkit silently drops a message for a channel the client has not
 * registered, and a client registers its channels <em>after</em> it joins.
 * <ul>
 *   <li>A Fabric client only sends its {@code minecraft:register} in the play phase, once
 *       it has the game's login packet. In the configuration phase it registers only in
 *       answer to a register from the server, which Paper does not send. That payload
 *       therefore reaches the server after {@code PlayerJoinEvent}, in one piece listing
 *       every channel.</li>
 *   <li>Paper turns that payload into one {@code PlayerRegisterChannelEvent} per channel,
 *       fired in a row while the later channels are not yet in the player's channel set.
 *       Acting inside the event for {@code webgui:hello} would send on channels Bukkit
 *       does not yet consider open. So a register event only starts a one-tick wait; by
 *       then the whole payload has been processed.</li>
 *   <li>A client that registers in the configuration phase (Paper keeps those since 1.21.8)
 *       is already complete at join, and one that never registers — vanilla — sends
 *       nothing, which is only knowable by waiting.</li>
 * </ul>
 * All methods run on the server's main thread.
 */
final class JoinHandshake {
    /**
     * How long a client gets to register after joining before it is treated as having no
     * WebGUI. The register goes out as soon as the client has the login packet, so this is
     * a round trip plus however long a heavily modded client takes to get through its
     * join; far more than the usual case, short enough that a vanilla player is not left
     * wondering whether the server noticed. A client slower than this is not lost: a
     * later register still upgrades it.
     */
    static final long TIMEOUT_TICKS = 100;

    /** One tick: lets the rest of a register payload land before the channel set is read. */
    static final long SETTLE_TICKS = 1;

    interface Task {
        void cancel();
    }

    interface Scheduler {
        Task later(long ticks, Runnable task);
    }

    private static final class Entry {
        Task timeout;
        Task settle;
    }

    private final Scheduler scheduler;
    private final Function<UUID, ClientKind> probe;
    private final BiConsumer<UUID, ClientKind> proceed;
    private final Map<UUID, Entry> waiting = new HashMap<>();

    /**
     * @param probe   what the player's client has registered <em>right now</em>
     * @param proceed runs once the kind is known: greets a WebGUI client, warns or refuses
     *                one that is not
     */
    JoinHandshake(Scheduler scheduler, Function<UUID, ClientKind> probe, BiConsumer<UUID, ClientKind> proceed) {
        this.scheduler = scheduler;
        this.probe = probe;
        this.proceed = proceed;
    }

    /** The player has joined (or the plugin was enabled while they were online). */
    void onJoin(UUID player) {
        forget(player);
        ClientKind kind = probe.apply(player);
        if (kind != ClientKind.ABSENT) {
            // Registered during configuration, so already complete.
            proceed.accept(player, kind);
            return;
        }
        Entry entry = new Entry();
        entry.timeout = scheduler.later(TIMEOUT_TICKS, () -> onTimeout(player));
        waiting.put(player, entry);
    }

    /** Bukkit reports one channel as newly registered by the player's client. */
    void onChannelRegistered(UUID player, String channel) {
        Entry entry = waiting.get(player);
        if (entry == null || entry.settle != null) {
            return;
        }
        if (!WebviewProtocol.CHANNEL_HELLO.equals(channel) && !WebviewProtocol.CHANNEL_OPEN_WEB.equals(channel)) {
            return;
        }
        entry.settle = scheduler.later(SETTLE_TICKS, () -> onSettled(player));
    }

    /** The player left; nothing about them may fire afterwards. */
    void onQuit(UUID player) {
        forget(player);
    }

    /** The plugin is going away. */
    void shutdown() {
        for (UUID player : java.util.List.copyOf(waiting.keySet())) {
            forget(player);
        }
    }

    private void onSettled(UUID player) {
        Entry entry = waiting.get(player);
        if (entry == null) {
            return;
        }
        entry.settle = null;
        ClientKind kind = probe.apply(player);
        if (kind == ClientKind.ABSENT) {
            return;
        }
        // Known now, whether the timeout had already fired (a slow client) or not.
        forget(player);
        proceed.accept(player, kind);
    }

    private void onTimeout(UUID player) {
        Entry entry = waiting.get(player);
        if (entry == null) {
            return;
        }
        entry.timeout = null;
        ClientKind kind = probe.apply(player);
        if (kind != ClientKind.ABSENT) {
            forget(player);
        }
        // An absent client stays on the list, so a register that turns up late still
        // finds it: the player has been told, but gets the real thing once it arrives.
        proceed.accept(player, kind);
    }

    private void forget(UUID player) {
        Entry entry = waiting.remove(player);
        if (entry != null) {
            if (entry.timeout != null) entry.timeout.cancel();
            if (entry.settle != null) entry.settle.cancel();
        }
    }
}
