package land.webgui.common;

/**
 * What a sender does to text before handing it to {@link WebviewWire}.
 *
 * The encoders enforce the length limits but never shorten anything: a string over the limit
 * is an error there, and a page URL that is merely too long should be cut, not crash the
 * command that opened it. Every sender trims the same way, so the trimming lives here.
 */
public final class WireText {
    private WireText() {}

    public static String sanitizeUrl(String url) {
        return sanitizeStr(url, WebviewProtocol.MAX_URL_LENGTH);
    }

    public static String sanitizeStr(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    /** An emit with no data still carries one: the page expects JSON, so an empty payload becomes {@code null}. */
    public static String emitData(String jsonPayload) {
        return (jsonPayload == null || jsonPayload.isBlank())
                ? "null"
                : sanitizeStr(jsonPayload, WebviewProtocol.MAX_EVENT_DATA_LENGTH);
    }
}
