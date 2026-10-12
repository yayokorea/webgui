package land.webgui.paper;

import land.webgui.common.ClientKind;
import land.webgui.common.WebviewProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinHandshakeTest {
    /** A clock the test turns by hand. */
    private static final class FakeScheduler implements JoinHandshake.Scheduler {
        private record Job(long due, Runnable task, boolean[] cancelled) {}

        private final List<Job> jobs = new ArrayList<>();
        private long now;

        @Override
        public JoinHandshake.Task later(long ticks, Runnable task) {
            boolean[] cancelled = new boolean[1];
            jobs.add(new Job(now + ticks, task, cancelled));
            return () -> cancelled[0] = true;
        }

        void advance(long ticks) {
            long target = now + ticks;
            while (now < target) {
                now++;
                for (Job job : List.copyOf(jobs)) {
                    if (job.due == now && !job.cancelled[0]) {
                        jobs.remove(job);
                        job.task.run();
                    }
                }
            }
        }
    }

    private final UUID player = UUID.randomUUID();
    private FakeScheduler clock;
    private Map<UUID, Set<String>> channels;
    private List<ClientKind> outcomes;
    private JoinHandshake handshake;

    @BeforeEach
    void setUp() {
        clock = new FakeScheduler();
        channels = new HashMap<>();
        channels.put(player, new HashSet<>());
        outcomes = new ArrayList<>();
        handshake = new JoinHandshake(clock,
                id -> PaperNetworking.clientKind(channels.get(id)),
                (id, kind) -> outcomes.add(kind));
    }

    /** What Paper does with one register payload: adds each channel and fires an event per channel. */
    private void clientRegisters(String... registered) {
        Set<String> set = channels.get(player);
        for (String channel : registered) {
            set.add(channel);
            handshake.onChannelRegistered(player, channel);
        }
    }

    @Test
    void clientThatRegisteredDuringConfigurationIsGreetedAtOnce() {
        channels.get(player).add(WebviewProtocol.CHANNEL_HELLO);
        handshake.onJoin(player);
        assertEquals(List.of(ClientKind.CURRENT), outcomes);
    }

    @Test
    void registerEventsOnlyStartAWait_soTheWholePayloadIsInBeforeAnyoneActs() {
        handshake.onJoin(player);
        // Hello comes first and the rest of the payload is not in the set yet.
        channels.get(player).add(WebviewProtocol.CHANNEL_HELLO);
        handshake.onChannelRegistered(player, WebviewProtocol.CHANNEL_HELLO);
        assertTrue(outcomes.isEmpty(), "must not act inside the event");
        channels.get(player).add(WebviewProtocol.CHANNEL_ASSET_MANIFEST);
        handshake.onChannelRegistered(player, WebviewProtocol.CHANNEL_ASSET_MANIFEST);

        clock.advance(JoinHandshake.SETTLE_TICKS);
        assertEquals(List.of(ClientKind.CURRENT), outcomes);
    }

    @Test
    void registeringCancelsTheTimeout() {
        handshake.onJoin(player);
        clientRegisters(WebviewProtocol.CHANNEL_OPEN_WEB, WebviewProtocol.CHANNEL_HELLO);
        clock.advance(JoinHandshake.TIMEOUT_TICKS + 10);
        assertEquals(List.of(ClientKind.CURRENT), outcomes, "decided once, not again at the timeout");
    }

    @Test
    void clientWithoutTheHandshakeChannelIsOutdated() {
        handshake.onJoin(player);
        clientRegisters(WebviewProtocol.CHANNEL_OPEN_WEB, WebviewProtocol.CHANNEL_EMIT_TO_PAGE);
        clock.advance(JoinHandshake.SETTLE_TICKS);
        assertEquals(List.of(ClientKind.OUTDATED), outcomes);
    }

    @Test
    void silentClientIsAbsentOnlyAfterTheTimeout() {
        handshake.onJoin(player);
        clock.advance(JoinHandshake.TIMEOUT_TICKS - 1);
        assertTrue(outcomes.isEmpty());
        clock.advance(1);
        assertEquals(List.of(ClientKind.ABSENT), outcomes);
    }

    @Test
    void unrelatedChannelsDoNotStartAnything() {
        handshake.onJoin(player);
        clientRegisters("fabric:some_other_mod");
        clock.advance(JoinHandshake.SETTLE_TICKS);
        assertTrue(outcomes.isEmpty());
        clock.advance(JoinHandshake.TIMEOUT_TICKS);
        assertEquals(List.of(ClientKind.ABSENT), outcomes);
    }

    @Test
    void slowClientIsUpgradedWhenItRegistersAfterTheTimeout() {
        handshake.onJoin(player);
        clock.advance(JoinHandshake.TIMEOUT_TICKS);
        assertEquals(List.of(ClientKind.ABSENT), outcomes);

        clientRegisters(WebviewProtocol.CHANNEL_OPEN_WEB, WebviewProtocol.CHANNEL_HELLO);
        clock.advance(JoinHandshake.SETTLE_TICKS);
        assertEquals(List.of(ClientKind.ABSENT, ClientKind.CURRENT), outcomes);

        // And it is done: further registers (another mod, a reconnect of a channel) do nothing.
        clientRegisters(WebviewProtocol.CHANNEL_HELLO);
        clock.advance(JoinHandshake.TIMEOUT_TICKS);
        assertEquals(2, outcomes.size());
    }

    @Test
    void quittingCancelsEverything() {
        handshake.onJoin(player);
        clientRegisters(WebviewProtocol.CHANNEL_HELLO);
        handshake.onQuit(player);
        clock.advance(JoinHandshake.TIMEOUT_TICKS * 2);
        assertTrue(outcomes.isEmpty());

        // A late register for a player who is gone is ignored, not remembered.
        handshake.onChannelRegistered(player, WebviewProtocol.CHANNEL_HELLO);
        clock.advance(JoinHandshake.SETTLE_TICKS);
        assertTrue(outcomes.isEmpty());
    }

    @Test
    void rejoiningStartsOver() {
        handshake.onJoin(player);
        handshake.onQuit(player);
        handshake.onJoin(player);
        clock.advance(JoinHandshake.TIMEOUT_TICKS);
        assertEquals(List.of(ClientKind.ABSENT), outcomes, "the first join's timeout must not also fire");
    }

    @Test
    void shutdownCancelsEverything() {
        handshake.onJoin(player);
        handshake.shutdown();
        clock.advance(JoinHandshake.TIMEOUT_TICKS * 2);
        assertTrue(outcomes.isEmpty());
    }
}
