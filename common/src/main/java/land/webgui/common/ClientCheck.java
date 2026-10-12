package land.webgui.common;

import land.webgui.WebGUIVersion;
import land.webgui.server.WebviewServerConfig;

/**
 * What a joining player is told when their client cannot see this server's pages.
 *
 * Only the words and the decision of whether to say them: sending the message or
 * disconnecting is the platform's job. See the loader-side {@code WebviewClientCheck} for
 * why the message exists.
 */
public final class ClientCheck {
    private ClientCheck() {}

    /** Whether the server has anything configured that a player without the mod would miss. */
    public static boolean serverUsesPages() {
        return !WebviewServerConfig.mainMenuUrl().isEmpty()
                || !WebviewServerConfig.deathScreenUrl().isEmpty()
                || WebviewServerConfig.autoHudOnJoin();
    }

    public static String outdatedText() {
        return "This server runs WebGUI " + WebGUIVersion.current()
                + " (protocol " + WebviewProtocol.PROTOCOL_VERSION + "). Your WebGUI is older and"
                + " cannot receive its pages — update it to " + WebGUIVersion.current() + ".";
    }

    public static String absentText() {
        return "This server runs WebGUI " + WebGUIVersion.current()
                + " and shows some of its interface through it. Your client does not have WebGUI,"
                + " so those pages will not appear.";
    }

    public static String kickText(ClientKind kind) {
        return kind == ClientKind.OUTDATED
                ? "Your WebGUI is out of date.\nThis server runs WebGUI " + WebGUIVersion.current()
                        + " (protocol " + WebviewProtocol.PROTOCOL_VERSION + ")."
                : "This server requires the WebGUI mod.\nIt runs WebGUI " + WebGUIVersion.current()
                        + " (protocol " + WebviewProtocol.PROTOCOL_VERSION + ").";
    }
}
