package land.webgui.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WireTextTest {
    @Test
    void nullBecomesEmpty() {
        assertEquals("", WireText.sanitizeStr(null, 5));
        assertEquals("", WireText.sanitizeUrl(null));
    }

    @Test
    void longTextIsCutToTheLimitAndShortTextIsUntouched() {
        assertEquals("abc", WireText.sanitizeStr("abcdef", 3));
        assertEquals("abc", WireText.sanitizeStr("abc", 3));
        String url = "x".repeat(WebviewProtocol.MAX_URL_LENGTH + 10);
        assertEquals(WebviewProtocol.MAX_URL_LENGTH, WireText.sanitizeUrl(url).length());
    }

    @Test
    void emptyEmitPayloadBecomesJsonNull() {
        assertEquals("null", WireText.emitData(null));
        assertEquals("null", WireText.emitData(""));
        assertEquals("null", WireText.emitData("   "));
        assertEquals("{\"a\":1}", WireText.emitData("{\"a\":1}"));
        assertEquals(WebviewProtocol.MAX_EVENT_DATA_LENGTH,
                WireText.emitData("y".repeat(WebviewProtocol.MAX_EVENT_DATA_LENGTH + 1)).length());
    }
}
