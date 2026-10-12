package land.webgui;

import land.webgui.common.WebviewProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebGUIVersionTest {

    @AfterEach
    void reset() {
        WebGUIVersion.init(() -> "test");
    }

    @Test
    void reportsWhatThePlatformSupplied() {
        WebGUIVersion.init(() -> "1.8.1+mc1.21.11");
        assertEquals("1.8.1+mc1.21.11", WebGUIVersion.current());
        assertTrue(WebGUIVersion.isKnown());
    }

    @Test
    void fallsBackToUnknownForNothing() {
        for (String empty : new String[] {null, "", "   "}) {
            WebGUIVersion.init(() -> empty);
            assertEquals("unknown", WebGUIVersion.current());
            assertFalse(WebGUIVersion.isKnown());
        }
    }

    @Test
    void truncatesToWhatTheHandshakeCanCarry() {
        WebGUIVersion.init(() -> "v".repeat(WebviewProtocol.MAX_VERSION_LENGTH + 20));
        assertEquals(WebviewProtocol.MAX_VERSION_LENGTH, WebGUIVersion.current().length());
    }

    @Test
    void readsTheSourceOnlyOnceAndOnlyWhenAsked() {
        AtomicInteger reads = new AtomicInteger();
        WebGUIVersion.init(() -> {
            reads.incrementAndGet();
            return "1.0.0";
        });
        assertEquals(0, reads.get(), "init must not touch loader metadata");
        WebGUIVersion.current();
        WebGUIVersion.current();
        assertEquals(1, reads.get());
    }

    @Test
    void aSecondInitReplacesTheFirst() {
        WebGUIVersion.init(() -> "1.0.0");
        assertEquals("1.0.0", WebGUIVersion.current());
        WebGUIVersion.init(() -> "2.0.0");
        assertEquals("2.0.0", WebGUIVersion.current());
    }
}
