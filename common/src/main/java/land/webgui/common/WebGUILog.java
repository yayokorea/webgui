package land.webgui.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one logger every WebGUI class writes to, loader code and shared code alike.
 *
 * Lives here rather than on the mod class because the shared code cannot see the mod
 * class, and a second logger under another name would split one feature's output in two.
 */
public final class WebGUILog {
    public static final Logger LOGGER = LoggerFactory.getLogger(WebviewProtocol.NAMESPACE);

    private WebGUILog() {}
}
