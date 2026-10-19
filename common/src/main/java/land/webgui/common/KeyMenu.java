package land.webgui.common;

import java.util.List;
import java.util.regex.Pattern;

/**
 * One server-defined key combination and the page it opens.
 *
 * @param id   what the player's own override is filed under; stable across the server's edits
 * @param keys canonical key names (see {@link KeyNames}), all held at once
 * @param url  the page to open
 */
public record KeyMenu(String id, List<String> keys, String url) {

    /** Letters, digits and {@code _ . -}: an id is used as a config key and in messages. */
    public static final Pattern ID = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");

    public KeyMenu {
        keys = List.copyOf(keys);
    }
}
