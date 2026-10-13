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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** What the update check says about itself to the server it asks. */
class WebGUIUpdateCheckerTest {

    @TempDir
    Path dir;

    private HttpServer http;
    private final BlockingQueue<String> userAgents = new LinkedBlockingQueue<>();

    @BeforeEach
    void startServer() throws IOException {
        http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        http.createContext("/", exchange -> {
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] body = "{\"version\":\"0.0.1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.start();

        WebGUIVersion.init(() -> "1.0.0+test");
        WebviewServerConfig.init(dir);
        Files.writeString(dir.resolve("server.json"),
                "{\"updateCheckUrl\":\"http://127.0.0.1:" + http.getAddress().getPort() + "/\"}", StandardCharsets.UTF_8);
        WebviewServerConfig.load();
    }

    @AfterEach
    void stopServer() {
        http.stop(0);
        WebGUIVersion.init(() -> "test");
    }

    private String awaitUserAgent() throws InterruptedException {
        String ua = userAgents.poll(10, TimeUnit.SECONDS);
        assertNotNull(ua, "the check never reached the server");
        return ua;
    }

    @Test
    void theModsIdentifyThemselvesAsTheMod() throws InterruptedException {
        WebGUIUpdateChecker.checkAsync();
        assertEquals("WebGUI-Mod/1.0.0+test", awaitUserAgent());
    }

    @Test
    void anotherPlatformNamesItself() throws InterruptedException {
        WebGUIUpdateChecker.checkAsync("WebGUI-Paper");
        assertEquals("WebGUI-Paper/1.0.0+test", awaitUserAgent());
    }
}
