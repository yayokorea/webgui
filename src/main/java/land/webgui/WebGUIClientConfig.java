package land.webgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
//? } else {
/*import net.neoforged.fml.loading.FMLPaths;*/
//? }

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings that belong to the player, not the server.
 *
 * Written on change rather than on exit: the choice here is often made to diagnose a
 * crash, and a crash must not lose it.
 */
public final class WebGUIClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** As fast as this setting goes. Past it a notch throws a page across its own length. */
    public static final int SCROLL_MAX = 40;

    /** Off by default: a developer's tool, and it makes the log noisier. */
    private boolean devTools = false;

    /** How often the browser may paint. 0 leaves whatever CEF defaults to. */
    private int browserFrameRate = 144;

    /**
     * How many wheel notches a page is told about for each one the game reports.
     *
     * One for one is what a browser does, and in a page it reads as barely moving — the game
     * counts a notch where a desktop browser counts several lines.
     */
    private int scrollNotches = 25;

    /** Whether Chromium opens the system save dialog instead of filing downloads itself. */
    private boolean askWhereToSaveDownloads = false;

    /** Whether a page may start audio or video without the player touching it first. */
    private boolean allowPageAutoplay = false;

    /**
     * The player's own keys for a server's key menus, by menu id. An empty list unbinds the
     * menu. Not tied to a server: ids are the server's to choose, and one that two servers
     * share is most likely the same thing.
     */
    private Map<String, List<String>> keyMenuKeys = new LinkedHashMap<>();

    private static WebGUIClientConfig instance = new WebGUIClientConfig();

    private WebGUIClientConfig() {}

    private static Path configPath() {
        //? if fabric {
        return FabricLoader.getInstance().getConfigDir().resolve("webgui").resolve("client.json");
        //? } else {
        /*return FMLPaths.CONFIGDIR.get().resolve("webgui").resolve("client.json");*/
        //? }
    }

    public static void load() {
        Path path = configPath();
        try {
            if (Files.isRegularFile(path)) {
                WebGUIClientConfig read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                        WebGUIClientConfig.class);
                if (read != null) {
                    instance = read;
                }
            }
        } catch (IOException | RuntimeException e) {
            // A typo in a hand-edited file must not stop the game starting.
            WebGUIMod.LOGGER.warn("webgui: could not read {} - using defaults ({})", path, e.toString());
        }
    }

    private static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            WebGUIMod.LOGGER.warn("webgui: could not write {}: {}", path, e.toString());
        }
    }

    public static boolean devTools() {
        return instance.devTools;
    }

    public static int browserFrameRate() {
        return Math.max(0, instance.browserFrameRate);
    }

    /** Clamped, because a hand-edited zero would stop a page scrolling at all. */
    public static int scrollNotches() {
        return Math.min(SCROLL_MAX, Math.max(1, instance.scrollNotches));
    }

    public static void setScrollNotches(int value) {
        int wanted = Math.min(SCROLL_MAX, Math.max(1, value));
        if (instance.scrollNotches == wanted) {
            return;
        }
        instance.scrollNotches = wanted;
        save();
    }

    public static void setDevTools(boolean value) {
        if (instance.devTools == value) {
            return;
        }
        instance.devTools = value;
        save();
    }

    public static boolean askWhereToSaveDownloads() {
        return instance.askWhereToSaveDownloads;
    }

    public static void setAskWhereToSaveDownloads(boolean value) {
        if (instance.askWhereToSaveDownloads == value) {
            return;
        }
        instance.askWhereToSaveDownloads = value;
        save();
    }

    public static boolean allowPageAutoplay() {
        return instance.allowPageAutoplay;
    }

    public static void setAllowPageAutoplay(boolean value) {
        if (instance.allowPageAutoplay == value) {
            return;
        }
        instance.allowPageAutoplay = value;
        save();
    }

    /** The keys the player chose for a menu, or null if they never did. */
    public static List<String> keyOverride(String id) {
        Map<String, List<String>> map = instance.keyMenuKeys;
        List<String> keys = map == null ? null : map.get(id);
        return keys == null ? null : List.copyOf(keys);
    }

    /** Files the player's choice; an empty list unbinds the menu. */
    public static void setKeyOverride(String id, List<String> keys) {
        if (instance.keyMenuKeys == null) {
            instance.keyMenuKeys = new LinkedHashMap<>();
        }
        instance.keyMenuKeys.put(id, new ArrayList<>(keys));
        save();
    }

    /** Forgets the player's choice, so the server's keys apply again. */
    public static void clearKeyOverride(String id) {
        if (instance.keyMenuKeys != null && instance.keyMenuKeys.remove(id) != null) {
            save();
        }
    }

    public static void setBrowserFrameRate(int value) {
        int wanted = Math.max(0, value);
        if (instance.browserFrameRate == wanted) {
            return;
        }
        instance.browserFrameRate = wanted;
        save();
    }
}
