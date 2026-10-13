package land.webgui.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import land.webgui.WebGUIVersion;
import land.webgui.common.VersionCompare;
import land.webgui.common.WebGUILog;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Asks whether a newer WebGUI exists, once, when the server starts.
 *
 * <p>Modrinth first: it knows which builds exist for this Minecraft version and this loader,
 * so it will not offer one that cannot be installed here. GitHub answers only what is newest
 * overall, so it is the fallback for when Modrinth cannot be reached.
 *
 * <p>The console notice is not optional. Telling operators in chat on join is, through
 * {@code updateNotifyInChat}.
 */
public final class WebGUIUpdateChecker {

    private static final String MODRINTH_PROJECT = "tDw1xQja";
    private static final String GITHUB_REPO = "mc-webgui/webgui";
    private static final String DEFAULT_PRODUCT = "WebGUI-Mod";

    // Where the two services are. Fields rather than constants so a test can point them at a
    // local server.
    static volatile String modrinthApi = "https://api.modrinth.com";
    static volatile String githubApi = "https://api.github.com";

    /** What was found, or null while nothing newer is known. Read from the join thread. */
    private static volatile Update available;

    private WebGUIUpdateChecker() {}

    /** A version newer than the one running. */
    public record Update(String version, String url, String channel) {}

    /** {@link #checkAsync(String, String)} as the WebGUI mod. */
    public static void checkAsync(String loader) {
        checkAsync(DEFAULT_PRODUCT, loader);
    }

    /**
     * Looks for a newer version on a background thread.
     *
     * Compares against {@link WebGUIVersion#current()}, so the platform must have called
     * {@link WebGUIVersion#init} first; with no version known there is nothing to compare.
     *
     * @param product what the request's User-Agent calls this build ("WebGUI-Mod",
     *                "WebGUI-Paper"), so the services asked can tell the two apart
     * @param loader  the Modrinth loader this build is for ({@code "fabric"}, {@code "neoforge"}),
     *                or null where Modrinth publishes nothing for the platform, which leaves GitHub
     */
    public static void checkAsync(String product, String loader) {
        if (!WebGUIVersion.isKnown()) {
            return;
        }
        String full = WebGUIVersion.current();
        String current = stripBuildMeta(full);
        if (current.isEmpty()) {
            return;
        }
        String game = gameVersion(full);

        Thread t = new Thread(() -> {
            Update found = loader == null ? null : fromModrinth(product, current, loader, game);
            if (found == null) {
                found = fromGitHub(product, current);
            }
            if (found == null) {
                return;
            }
            available = found;
            WebGUILog.LOGGER.info("+------------------------------------------------");
            WebGUILog.LOGGER.info("| WebGUI update available: {} -> {}{}",
                    current, found.version(), channelSuffix(found));
            if (!found.url().isEmpty()) {
                WebGUILog.LOGGER.info("| Download: {}", found.url());
            }
            WebGUILog.LOGGER.info("+------------------------------------------------");
        }, "webgui-update-check");
        t.setDaemon(true);
        t.start();
    }

    /** What the check found, or null while nothing newer is known. */
    public static Update available() {
        return available;
    }

    /**
     * The chat lines an operator who just joined gets, or none.
     *
     * Empty while nothing newer is known, or when the server was told not to say it in chat.
     * Whether the player is an operator is the platform's to decide.
     */
    public static java.util.List<String> joinNotice() {
        Update found = available;
        if (found == null || !WebviewServerConfig.updateNotifyInChat()) {
            return java.util.List.of();
        }
        String first = "[WebGUI] Update available: " + stripBuildMeta(WebGUIVersion.current())
                + " -> " + found.version() + channelSuffix(found);
        return found.url().isEmpty()
                ? java.util.List.of(first)
                : java.util.List.of(first, "[WebGUI] " + found.url());
    }

    // Filtered by loader and game version, so anything that comes back can be installed here.
    private static Update fromModrinth(String product, String current, String loader, String game) {
        if (game.isEmpty()) {
            return null;
        }
        String url = modrinthApi + "/v2/project/" + MODRINTH_PROJECT + "/version"
                + "?loaders=" + encode("[\"" + loader + "\"]")
                + "&game_versions=" + encode("[\"" + game + "\"]");
        String body = get(product, url);
        if (body == null) {
            return null;
        }
        try {
            JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
            Update best = null;
            for (JsonElement element : versions) {
                JsonObject o = element.getAsJsonObject();
                String number = stripBuildMeta(text(o, "version_number"));
                if (number.isEmpty() || !isNewer(number, current)) {
                    continue;
                }
                if (best != null && !isNewer(number, best.version())) {
                    continue;
                }
                best = new Update(number, pageUrl(o), text(o, "version_type"));
            }
            return best;
        } catch (Exception malformed) {
            WebGUILog.LOGGER.debug("webgui: update check: Modrinth answered unexpectedly: {}",
                    malformed.getMessage());
            return null;
        }
    }

    // Newest release overall, which may have no build for this Minecraft version. Only reached
    // when Modrinth is unavailable, where an imprecise answer beats none.
    private static Update fromGitHub(String product, String current) {
        String body = get(product, githubApi + "/repos/" + GITHUB_REPO + "/releases/latest");
        if (body == null) {
            return null;
        }
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            String number = stripBuildMeta(text(o, "tag_name"));
            if (number.isEmpty() || !isNewer(number, current)) {
                return null;
            }
            return new Update(number, text(o, "html_url"), "release");
        } catch (Exception malformed) {
            WebGUILog.LOGGER.debug("webgui: update check: GitHub answered unexpectedly: {}",
                    malformed.getMessage());
            return null;
        }
    }

    private static String get(String product, String url) {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", product + "/" + WebGUIVersion.current())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                WebGUILog.LOGGER.debug("webgui: update check: HTTP {} from {}", response.statusCode(), url);
                return null;
            }
            return response.body();
        } catch (Exception unreachable) {
            WebGUILog.LOGGER.debug("webgui: update check failed: {}", unreachable.getMessage());
            return null;
        }
    }

    private static boolean isNewer(String remote, String local) {
        try {
            return VersionCompare.isNewer(remote, local);
        } catch (IllegalArgumentException unparseable) {
            WebGUILog.LOGGER.debug("webgui: update check: cannot compare {} with {}", remote, local);
            return false;
        }
    }

    private static String channelSuffix(Update found) {
        return "release".equals(found.channel()) || found.channel().isEmpty()
                ? "" : " (" + found.channel() + ")";
    }

    private static String pageUrl(JsonObject version) {
        String id = text(version, "id");
        return id.isEmpty()
                ? "https://modrinth.com/mod/webgui/versions"
                : "https://modrinth.com/mod/webgui/version/" + id;
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull()
                ? o.get(key).getAsString().trim().replaceFirst("^[vV]", "")
                : "";
    }

    /** This mod reports {@code 2.0.0+mc26.3}; Modrinth adds the loader after that. */
    private static String stripBuildMeta(String version) {
        String v = version == null ? "" : version.trim().replaceFirst("^[vV]", "");
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }

    /** The Minecraft version out of our own, which carries it as {@code +mc26.3}. */
    private static String gameVersion(String full) {
        if (full == null) {
            return "";
        }
        int mc = full.indexOf("+mc");
        if (mc < 0) {
            return "";
        }
        String rest = full.substring(mc + 3);
        int dash = rest.indexOf('-');
        return dash >= 0 ? rest.substring(0, dash) : rest;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
