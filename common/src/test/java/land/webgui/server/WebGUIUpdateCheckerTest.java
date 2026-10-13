package land.webgui.server;

import com.sun.net.httpserver.HttpServer;
import land.webgui.WebGUIVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whom the update check asks, and what it says about itself when it does. */
class WebGUIUpdateCheckerTest {

    @TempDir
    Path dir;

    private HttpServer http;
    private String savedModrinth;
    private String savedGithub;
    /** "path User-Agent", one per request, in the order they arrived. */
    private final BlockingQueue<String> requests = new LinkedBlockingQueue<>();

    @BeforeEach
    void startServer() throws IOException {
        // IPv4 explicitly: NeoForge's test JVM prefers IPv6, where the loopback address is ::1
        // and nothing would be listening on the 127.0.0.1 the URLs below name.
        http = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        http.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(path + " " + exchange.getRequestHeaders().getFirst("User-Agent"));
            // Nothing newer anywhere: Modrinth lists no versions, GitHub an old release.
            String json = path.startsWith("/v2/") ? "[]" : "{\"tag_name\":\"v0.0.1\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.start();

        String local = "http://127.0.0.1:" + http.getAddress().getPort();
        savedModrinth = WebGUIUpdateChecker.modrinthApi;
        savedGithub = WebGUIUpdateChecker.githubApi;
        WebGUIUpdateChecker.modrinthApi = local;
        WebGUIUpdateChecker.githubApi = local;

        WebGUIVersion.init(() -> "1.0.0+mc1.21.11");
        WebviewServerConfig.init(dir);
        WebviewServerConfig.load();
    }

    @AfterEach
    void stopServer() {
        http.stop(0);
        WebGUIUpdateChecker.modrinthApi = savedModrinth;
        WebGUIUpdateChecker.githubApi = savedGithub;
        WebGUIVersion.init(() -> "test");
    }

    private String awaitRequest() throws InterruptedException {
        String r = requests.poll(10, TimeUnit.SECONDS);
        assertNotNull(r, "the check never reached the server");
        return r;
    }

    @Test
    void theModsAskModrinthForTheirLoaderAsTheMod() throws InterruptedException {
        WebGUIUpdateChecker.checkAsync("fabric");
        String first = awaitRequest();
        assertTrue(first.startsWith("/v2/project/"), first);
        assertTrue(first.endsWith(" WebGUI-Mod/1.0.0+mc1.21.11"), first);
        // Modrinth had nothing newer, so GitHub is asked too.
        assertTrue(awaitRequest().startsWith("/repos/"));
    }

    @Test
    void aPlatformModrinthDoesNotPublishForAsksGitHubAndNamesItself() throws InterruptedException {
        WebGUIUpdateChecker.checkAsync("WebGUI-Paper", null);
        String first = awaitRequest();
        assertTrue(first.startsWith("/repos/"), first);
        assertTrue(first.endsWith(" WebGUI-Paper/1.0.0+mc1.21.11"), first);
    }

    @Test
    void nothingNewerMeansNothingToSay() throws InterruptedException {
        WebGUIUpdateChecker.checkAsync("WebGUI-Paper", null);
        awaitRequest();
        assertEquals(java.util.List.of(), WebGUIUpdateChecker.joinNotice());
    }
}
