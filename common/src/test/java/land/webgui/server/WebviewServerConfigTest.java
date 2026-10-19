package land.webgui.server;

import land.webgui.EntityBindingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The config now lives wherever the platform says ({@code init}); these pin what it does
 * with that directory, since a mod loader and a Paper plugin hand it different ones.
 */
class WebviewServerConfigTest {

    @TempDir
    Path dir;

    @BeforeEach
    void freshConfig() throws IOException {
        // Reading "{}" replaces whatever an earlier test left in the static instance;
        // without a file, load() would keep it.
        WebviewServerConfig.init(dir);
        Files.writeString(dir.resolve("server.json"), "{}", StandardCharsets.UTF_8);
        WebviewServerConfig.load();
        Files.delete(dir.resolve("server.json"));
    }

    private String serverJson() throws IOException {
        return Files.readString(dir.resolve("server.json"), StandardCharsets.UTF_8);
    }

    @Test
    void firstLoadWritesTheExampleAndGeneratesASecret() throws IOException {
        Files.deleteIfExists(dir.resolve("server.example.json"));
        Files.writeString(dir.resolve("server.json"), "{}", StandardCharsets.UTF_8);
        WebviewServerConfig.load();

        assertTrue(Files.isRegularFile(dir.resolve("server.example.json")));
        assertTrue(serverJson().contains("tokenSecretBase64"));
        assertEquals(32, WebviewServerConfig.tokenSecretBytes().length);
        assertEquals(32, Base64.getDecoder().decode(
                serverJson().replaceAll("(?s).*\"tokenSecretBase64\":\\s*\"([^\"]+)\".*", "$1")).length);
    }

    @Test
    void theSecretSurvivesAReload() throws IOException {
        Files.writeString(dir.resolve("server.json"), "{}", StandardCharsets.UTF_8);
        WebviewServerConfig.load();
        byte[] secret = WebviewServerConfig.tokenSecretBytes();

        WebviewServerConfig.load();
        assertEquals(Base64.getEncoder().encodeToString(secret),
                Base64.getEncoder().encodeToString(WebviewServerConfig.tokenSecretBytes()));
    }

    @Test
    void readsWhatTheOperatorWrote() throws IOException {
        Files.writeString(dir.resolve("server.json"), """
                {"enableTokens": false, "queryParamName": "t", "autoHudOnJoin": true,
                 "autoHudUrl": "  http://x.example/hud  ", "deathScreenUrl": "http://x.example/death",
                 "pageEventsPerSecond": 5, "trustedCommandOrigins": [" https://a.example ", "", "https://b.example"]}
                """, StandardCharsets.UTF_8);
        WebviewServerConfig.load();

        assertFalse(WebviewServerConfig.enableTokens());
        assertEquals("t", WebviewServerConfig.queryParamName());
        assertTrue(WebviewServerConfig.autoHudOnJoin());
        assertEquals("http://x.example/hud", WebviewServerConfig.autoHudUrl());
        assertEquals("http://x.example/death", WebviewServerConfig.deathScreenUrl());
        assertEquals(5, WebviewServerConfig.pageEventsPerSecond());
        assertEquals("https://a.example\nhttps://b.example", WebviewServerConfig.trustedCommandOriginsJoined());
    }

    @Test
    void keyMenusAreReadAndCheckedLeniently() throws IOException {
        assertTrue(WebviewServerConfig.keyMenus().isEmpty(), "none by default");

        Files.writeString(dir.resolve("server.json"), """
                {"keyMenus": [
                  {"id": "shop", "keys": ["shift", "F"], "url": "https://x.example/shop"},
                  {"id": "broken", "keys": ["shift", "nope"], "url": "https://x.example/b"},
                  {"id": "map", "keys": "alt+1", "url": "https://x.example/map"}]}
                """, StandardCharsets.UTF_8);
        WebviewServerConfig.load();

        assertEquals(2, WebviewServerConfig.keyMenus().size());
        assertEquals(java.util.List.of("shift", "f"), WebviewServerConfig.keyMenus().get(0).keys());
        assertEquals(java.util.List.of("alt", "1"), WebviewServerConfig.keyMenus().get(1).keys());
        // The generated secret is written back; the key menus must survive that save.
        assertTrue(serverJson().contains("\"shop\""));
    }

    @Test
    void aMalformedKeyMenusValueDoesNotTakeTheConfigDown() throws IOException {
        Files.writeString(dir.resolve("server.json"),
                "{\"deathScreenUrl\": \"http://x.example/d\", \"keyMenus\": \"shift+F\"}", StandardCharsets.UTF_8);
        WebviewServerConfig.load();
        assertTrue(WebviewServerConfig.keyMenus().isEmpty());
        assertEquals("http://x.example/d", WebviewServerConfig.deathScreenUrl());
    }

    @Test
    void reloadPicksUpChangedKeyMenus() throws IOException {
        Files.writeString(dir.resolve("server.json"),
                "{\"keyMenus\": [{\"id\": \"a\", \"keys\": [\"f7\"], \"url\": \"u\"}]}", StandardCharsets.UTF_8);
        WebviewServerConfig.load();
        assertEquals(1, WebviewServerConfig.keyMenus().size());

        Files.writeString(dir.resolve("server.json"), "{\"keyMenus\": []}", StandardCharsets.UTF_8);
        WebviewServerConfig.reload();
        assertTrue(WebviewServerConfig.keyMenus().isEmpty());
    }

    @Test
    void bundledPagesLiveUnderTheConfigDirectory() throws IOException {
        Files.writeString(dir.resolve("server.json"), "{}", StandardCharsets.UTF_8);
        WebviewServerConfig.load();
        assertEquals(dir.resolve("web"), WebviewServerConfig.bundledPagesRoot());

        Files.writeString(dir.resolve("server.json"), "{\"bundledPagesDir\": \"pages/site\"}", StandardCharsets.UTF_8);
        WebviewServerConfig.load();
        assertEquals(dir.resolve("pages").resolve("site"), WebviewServerConfig.bundledPagesRoot());
    }

    @Test
    void bundledPagesCannotEscapeTheConfigDirectory() throws IOException {
        Files.writeString(dir.resolve("server.json"), "{\"bundledPagesDir\": \"../../etc\"}", StandardCharsets.UTF_8);
        WebviewServerConfig.load();
        assertEquals(dir.resolve("web"), WebviewServerConfig.bundledPagesRoot());
    }

    @Test
    void reloadReportsWhatItDid() throws IOException {
        assertTrue(WebviewServerConfig.reload().startsWith("server.json not found"));

        Files.writeString(dir.resolve("server.json"), "", StandardCharsets.UTF_8);
        assertEquals("server.json is empty — using current settings", WebviewServerConfig.reload());

        Files.writeString(dir.resolve("server.json"), "{\"deathScreenUrl\": \"http://x.example/d\"}", StandardCharsets.UTF_8);
        assertEquals("WebGUI config reloaded from server.json", WebviewServerConfig.reload());
        assertEquals("http://x.example/d", WebviewServerConfig.deathScreenUrl());
        // A reload also fills in the secret an edited file left out.
        assertEquals(32, WebviewServerConfig.tokenSecretBytes().length);
    }

    @Test
    void reloadRescansThePagesFolder() throws IOException {
        Files.writeString(dir.resolve("server.json"), "{}", StandardCharsets.UTF_8);
        WebviewServerConfig.reload();
        assertTrue(Files.isRegularFile(dir.resolve("web").resolve("index.html")), "starter page");
        assertTrue(WebviewAssets.enabled());
    }

    @Test
    void entityBindingsPersistInTheSameDirectory() {
        EntityBindingStore.init(dir);
        UUID entity = UUID.randomUUID();
        EntityBindingStore.bind(entity, new EntityBinding("http://x.example/{entity_id}", true));
        assertTrue(Files.isRegularFile(dir.resolve("entity_bindings.json")));

        EntityBindingStore.load();
        assertEquals(new EntityBinding("http://x.example/{entity_id}", true), EntityBindingStore.get(entity).orElseThrow());

        assertTrue(EntityBindingStore.unbind(entity));
        EntityBindingStore.load();
        assertTrue(EntityBindingStore.get(entity).isEmpty());
    }
}
