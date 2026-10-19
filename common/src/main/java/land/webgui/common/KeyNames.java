package land.webgui.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The names a server's {@code keyMenus} may use for keys, and what they are written as.
 *
 * <p>Names, not key codes: the game's numbering changed in 26.3, and a config written for one
 * version must keep working on the next. The client turns a name into the codes of the version
 * it runs (see the mod's {@code KeyCodes}); here a name is only checked and spelled one way.
 *
 * <p>Canonical names are lowercase. A few aliases are folded into them so an operator can write
 * what comes naturally ({@code "Control"}, {@code "Esc"}, {@code "`"}), and {@code "shift"},
 * {@code "ctrl"} and {@code "alt"} mean either key of the pair. There is no Windows/Command key:
 * the operating system keeps most of its combinations for itself.
 */
public final class KeyNames {
    private KeyNames() {}

    /** Most keys one combination may hold. More than a hand can press, and bounds the payload. */
    public static final int MAX_KEYS = 6;

    private static final Set<String> NAMES;

    static {
        Set<String> names = new LinkedHashSet<>();
        for (char c = 'a'; c <= 'z'; c++) names.add(String.valueOf(c));
        for (char c = '0'; c <= '9'; c++) names.add(String.valueOf(c));
        for (int i = 1; i <= 24; i++) names.add("f" + i);
        for (int i = 0; i <= 9; i++) names.add("kp" + i);
        names.addAll(List.of(
                "shift", "lshift", "rshift",
                "ctrl", "lctrl", "rctrl",
                "alt", "lalt", "ralt",
                "space", "tab", "enter", "escape", "backspace", "delete", "insert",
                "home", "end", "pageup", "pagedown",
                "up", "down", "left", "right",
                "grave", "minus", "equal", "comma", "period", "slash", "backslash",
                "semicolon", "apostrophe", "leftbracket", "rightbracket",
                "capslock", "kpadd", "kpmultiply", "kpenter"));
        NAMES = Collections.unmodifiableSet(names);
    }

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("control", "ctrl"),
            Map.entry("lcontrol", "lctrl"),
            Map.entry("rcontrol", "rctrl"),
            Map.entry("leftshift", "lshift"),
            Map.entry("rightshift", "rshift"),
            Map.entry("leftctrl", "lctrl"),
            Map.entry("rightctrl", "rctrl"),
            Map.entry("leftcontrol", "lctrl"),
            Map.entry("rightcontrol", "rctrl"),
            Map.entry("leftalt", "lalt"),
            Map.entry("rightalt", "ralt"),
            Map.entry("esc", "escape"),
            Map.entry("return", "enter"),
            Map.entry("del", "delete"),
            Map.entry("ins", "insert"),
            Map.entry("pgup", "pageup"),
            Map.entry("pgdn", "pagedown"),
            Map.entry("pgdown", "pagedown"),
            Map.entry("spacebar", "space"),
            Map.entry("`", "grave"),
            Map.entry("backtick", "grave"),
            Map.entry("-", "minus"),
            Map.entry("=", "equal"),
            Map.entry(",", "comma"),
            Map.entry(".", "period"),
            Map.entry("/", "slash"),
            Map.entry("\\", "backslash"),
            Map.entry(";", "semicolon"),
            Map.entry("'", "apostrophe"),
            Map.entry("[", "leftbracket"),
            Map.entry("]", "rightbracket"),
            Map.entry("caps", "capslock"));

    /** Every canonical name, in a stable order. */
    public static Set<String> all() {
        return NAMES;
    }

    /** The canonical spelling of a key name, or null if this mod has no such key. */
    public static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return null;
        }
        String aliased = ALIASES.get(name);
        if (aliased != null) {
            return aliased;
        }
        return NAMES.contains(name) ? name : null;
    }

    /**
     * Turns a written combination into canonical names.
     *
     * <p>Accepts {@code "shift+F"} as well as a list; a plus sign alone ({@code "shift++"}) is
     * not supported, which is why {@code "equal"} exists. Returns null if any part is unknown, the
     * combination is empty or longer than {@link #MAX_KEYS}, or a key is named twice.
     */
    public static List<String> canonicalCombo(List<String> raw) {
        if (raw == null || raw.isEmpty() || raw.size() > MAX_KEYS) {
            return null;
        }
        List<String> out = new ArrayList<>(raw.size());
        for (String part : raw) {
            String name = canonical(part);
            if (name == null || out.contains(name)) {
                return null;
            }
            out.add(name);
        }
        return Collections.unmodifiableList(out);
    }

    /** {@code "shift+F"} to {@code ["shift", "F"]}, unvalidated. */
    public static List<String> split(String combo) {
        List<String> parts = new ArrayList<>();
        if (combo == null) {
            return parts;
        }
        for (String part : combo.split("\\+", -1)) {
            parts.add(part.trim());
        }
        return parts;
    }

    /** Canonical names joined for display, e.g. {@code shift+f}. */
    public static String join(List<String> combo) {
        return String.join("+", combo);
    }
}
