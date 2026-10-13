package land.webgui.paper;

import land.webgui.EntityBindingStore;
import land.webgui.common.WebviewProtocol;
import land.webgui.paper.api.WebGUIPaperApi;
import land.webgui.server.EntityBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of the API that do not touch a player. */
class WebGUIPaperApiTest {
    @TempDir
    Path dir;

    @BeforeEach
    void freshStore() {
        EntityBindingStore.init(dir);
        EntityBindingStore.load();
    }

    @Test
    void bindsLooksUpAndUnbinds() {
        UUID entity = UUID.randomUUID();
        assertTrue(WebGUIPaperApi.getEntityBinding(entity).isEmpty());

        WebGUIPaperApi.bindEntity(entity, "https://x.example/{entity_type}", true);
        assertEquals(new EntityBinding("https://x.example/{entity_type}", true),
                WebGUIPaperApi.getEntityBinding(entity).orElseThrow());

        WebGUIPaperApi.unbindEntity(entity);
        assertFalse(WebGUIPaperApi.getEntityBinding(entity).isPresent());
    }

    @Test
    void reportsTheWireLimitsItSpeaks() {
        assertEquals(WebviewProtocol.PROTOCOL_VERSION, WebGUIPaperApi.protocolVersion());
        assertEquals(WebviewProtocol.MAX_URL_LENGTH, WebGUIPaperApi.maxUrlLength());
    }

    @Test
    void nobodyIsReadyUntilTheJoinFlowSaysSo() {
        assertFalse(PaperJoinFlow.isReady(UUID.randomUUID()));
    }
}
