package land.webgui.common;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reading, checking and carrying the {@code keyMenus} list.
 *
 * <p>The list is written by hand into {@code server.json}, so it is read leniently: an entry
 * that cannot work is skipped with a line in the log rather than failing the whole file, which
 * would take every other setting down with it. The same JSON text is what travels to the client
 * (one string on the wire, like {@code trusted_origins}), so both ends parse with the same code.
 */
public final class KeyMenus {
    private KeyMenus() {}

    private static final Gson GSON = new Gson();

    /**
     * Reads the {@code keyMenus} value of a config file.
     *
     * <p>Each entry is {@code {"id": ..., "keys": [...], "url": ...}}. {@code keys} may also be
     * one string, {@code "shift+F"}. Entries with a bad or duplicate id, unknown keys, or no url
     * are dropped. Never null, never throws.
     */
    public static List<KeyMenu> parse(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return Collections.emptyList();
        }
        if (!value.isJsonArray()) {
            WebGUILog.LOGGER.warn("webgui: keyMenus must be a list - ignoring it");
            return Collections.emptyList();
        }
        List<KeyMenu> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int index = -1;
        for (JsonElement element : value.getAsJsonArray()) {
            index++;
            KeyMenu menu = parseEntry(element, index);
            if (menu == null) {
                continue;
            }
            if (!ids.add(menu.id())) {
                WebGUILog.LOGGER.warn("webgui: keyMenus[{}]: id '{}' is already used - skipped", index, menu.id());
                continue;
            }
            out.add(menu);
        }
        return Collections.unmodifiableList(out);
    }

    private static KeyMenu parseEntry(JsonElement element, int index) {
        if (element == null || !element.isJsonObject()) {
            WebGUILog.LOGGER.warn("webgui: keyMenus[{}] is not an object - skipped", index);
            return null;
        }
        JsonObject o = element.getAsJsonObject();
        String id = string(o, "id");
        if (!KeyMenu.ID.matcher(id).matches()) {
            WebGUILog.LOGGER.warn("webgui: keyMenus[{}]: id '{}' must be 1-64 of letters, digits, _ . - - skipped", index, id);
            return null;
        }
        String url = string(o, "url").trim();
        if (url.isEmpty()) {
            WebGUILog.LOGGER.warn("webgui: keyMenus '{}': no url - skipped", id);
            return null;
        }
        List<String> raw = rawKeys(o.get("keys"));
        List<String> keys = KeyNames.canonicalCombo(raw);
        if (keys == null) {
            WebGUILog.LOGGER.warn("webgui: keyMenus '{}': keys {} are not 1-{} known, distinct key names - skipped",
                    id, raw, KeyNames.MAX_KEYS);
            return null;
        }
        return new KeyMenu(id, keys, url);
    }

    private static String string(JsonObject o, String member) {
        JsonElement e = o.get(member);
        if (e == null || !e.isJsonPrimitive()) {
            return "";
        }
        return e.getAsString();
    }

    private static List<String> rawKeys(JsonElement keys) {
        List<String> raw = new ArrayList<>();
        if (keys == null) {
            return raw;
        }
        if (keys.isJsonPrimitive()) {
            return KeyNames.split(keys.getAsString());
        }
        if (keys.isJsonArray()) {
            for (JsonElement k : keys.getAsJsonArray()) {
                // A wrong type becomes a name nothing matches, which rejects the entry.
                raw.add(k.isJsonPrimitive() ? k.getAsString() : "");
            }
        }
        return raw;
    }

    /**
     * The list as it goes on the wire: a JSON array of {@code {id, keys, url}}.
     *
     * <p>Entries that would push the text past {@code maxChars} are left off, with a warning:
     * a payload over its limit is an error in the encoder, and this cannot fail a join.
     */
    public static String toWire(List<KeyMenu> menus, int maxChars) {
        JsonArray array = new JsonArray();
        int size = 2;
        for (KeyMenu menu : menus) {
            JsonObject o = new JsonObject();
            o.addProperty("id", menu.id());
            JsonArray keys = new JsonArray();
            menu.keys().forEach(keys::add);
            o.add("keys", keys);
            o.addProperty("url", WireText.sanitizeUrl(menu.url()));
            int entry = GSON.toJson(o).length() + 1;
            if (size + entry > maxChars) {
                WebGUILog.LOGGER.warn("webgui: keyMenus is too long to send ({} chars) - '{}' and the entries after it are left out",
                        maxChars, menu.id());
                break;
            }
            size += entry;
            array.add(o);
        }
        return GSON.toJson(array);
    }

    /** What {@link #toWire} wrote, or whatever a server sent: bad input yields fewer entries, never an exception. */
    public static List<KeyMenu> fromWire(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return parse(GSON.fromJson(json, JsonElement.class));
        } catch (JsonParseException e) {
            WebGUILog.LOGGER.warn("webgui: the server sent an unreadable keyMenus list: {}", e.toString());
            return Collections.emptyList();
        }
    }
}
