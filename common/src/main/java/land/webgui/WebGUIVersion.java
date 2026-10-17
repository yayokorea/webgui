package land.webgui;

import land.webgui.common.WebviewProtocol;

import java.util.function.Supplier;

/**
 * The server's own WebGUI version string, as the platform reports it.
 *
 * The platform says how to find it ({@link #init}) because only it can read its own
 * metadata; everything shared just asks. Read once on first use: the metadata cannot
 * change while the game runs, and the handshake asks for it on every join.
 */
public final class WebGUIVersion {
    private static final String UNKNOWN = "unknown";

    private static volatile Supplier<String> source;
    private static volatile String current;

    private WebGUIVersion() {}

    /**
     * Tells this class where the version comes from; call before anything asks for it.
     *
     * The supplier is not run here — loader metadata may not be readable yet at the point
     * a mod's entrypoint runs, and nothing needs the answer until a player joins.
     */
    public static void init(Supplier<String> versionSource) {
        source = versionSource;
        current = null;
    }

    /** e.g. {@code 1.7.0+mc1.21.11}, or {@code "unknown"} if the platform has no metadata for us. */
    public static String current() {
        String v = current;
        if (v != null) {
            return v;
        }
        Supplier<String> s = source;
        if (s == null) {
            // Not cached: a later init() should still be heard.
            return UNKNOWN;
        }
        v = read(s);
        current = v;
        return v;
    }

    /** Whether the platform actually gave us a version, rather than the {@code "unknown"} fallback. */
    public static boolean isKnown() {
        return !UNKNOWN.equals(current());
    }

    private static String read(Supplier<String> s) {
        String v = s.get();
        if (v == null || v.isBlank()) {
            return UNKNOWN;
        }
        return v.length() > WebviewProtocol.MAX_VERSION_LENGTH
                ? v.substring(0, WebviewProtocol.MAX_VERSION_LENGTH)
                : v;
    }
}
