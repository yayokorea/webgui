package land.webgui.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import land.webgui.WebGUIVersion;
import land.webgui.common.VersionCompare;
import land.webgui.common.WebGUILog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class WebGUIUpdateChecker {
    private static final String DEFAULT_PRODUCT = "WebGUI-Mod";

    private WebGUIUpdateChecker() {}

    /** {@link #checkAsync(String)} as the WebGUI mod. */
    public static void checkAsync() {
        checkAsync(DEFAULT_PRODUCT);
    }

    /**
     * Checks {@code updateCheckUrl} for a newer release, on a background thread.
     *
     * Compares against {@link WebGUIVersion#current()}, so the platform must have called
     * {@link WebGUIVersion#init} first; with no version known there is nothing to compare.
     *
     * @param product what the request's User-Agent calls this build ("WebGUI-Mod",
     *                "WebGUI-Paper"), so an update server can tell the two apart
     */
    public static void checkAsync(String product) {
        String url = WebviewServerConfig.updateCheckUrl();
        if (url.isEmpty()) {
            return;
        }
        if (!WebGUIVersion.isKnown()) {
            return;
        }
        String current = WebGUIVersion.current();

        Thread t = new Thread(() -> {
            try {
                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", product + "/" + current)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    WebGUILog.LOGGER.debug("webgui: update check returned HTTP {}", resp.statusCode());
                    return;
                }
                parseAndLog(current, resp.body(), url);
            } catch (Exception e) {
                WebGUILog.LOGGER.debug("webgui: update check failed: {}", e.getMessage());
            }
        }, "webgui-update-check");
        t.setDaemon(true);
        t.start();
    }

    private static void parseAndLog(String current, String body, String checkUrl) {
        String remoteRaw;
        String downloadUrl = "";
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            if (o.has("version") && !o.get("version").isJsonNull()) {
                // {"version":"1.2.3","url":"..."}
                remoteRaw = o.get("version").getAsString();
                if (o.has("url") && !o.get("url").isJsonNull()) {
                    downloadUrl = o.get("url").getAsString();
                }
            } else if (o.has("tag_name") && !o.get("tag_name").isJsonNull()) {
                // GitHub Releases API: {"tag_name":"v1.2.3","html_url":"..."}
                remoteRaw = o.get("tag_name").getAsString();
                if (o.has("html_url") && !o.get("html_url").isJsonNull()) {
                    downloadUrl = o.get("html_url").getAsString();
                }
            } else {
                WebGUILog.LOGGER.debug("webgui: update check: unrecognised response format");
                return;
            }
        } catch (Exception e) {
            WebGUILog.LOGGER.debug("webgui: update check: could not parse response: {}", e.getMessage());
            return;
        }

        String remote = stripBuildMeta(remoteRaw.trim().replaceFirst("^[vV]", ""));
        String local  = stripBuildMeta(current.trim().replaceFirst("^[vV]", ""));

        boolean newer;
        try {
            newer = VersionCompare.isNewer(remote, local);
        } catch (IllegalArgumentException e) {
            // fallback: simple string compare is not reliable, skip
            WebGUILog.LOGGER.debug("webgui: update check: could not compare versions '{}' vs '{}': {}", remote, local, e.getMessage());
            return;
        }

        if (newer) {
            WebGUILog.LOGGER.info("┌─────────────────────────────────────────────────");
            WebGUILog.LOGGER.info("│ WebGUI update available: {} → {}", local, remote);
            if (!downloadUrl.isEmpty()) {
                WebGUILog.LOGGER.info("│ Download: {}", downloadUrl);
            }
            WebGUILog.LOGGER.info("└─────────────────────────────────────────────────");
        } else {
            WebGUILog.LOGGER.debug("webgui: up to date ({} >= {})", local, remote);
        }
    }

    /** Strips the +mcX.Y.Z build-metadata suffix added by our Gradle build. */
    private static String stripBuildMeta(String version) {
        int plus = version.indexOf('+');
        return plus >= 0 ? version.substring(0, plus) : version;
    }
}
