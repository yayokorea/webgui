package land.webgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class WebViewLayoutTest {
    private static double zoomFactor(double guiScale) {
        return Math.pow(1.2, WebViewLayout.zoomLevel(guiScale));
    }

    @Test
    void webElementsKeepTheirSizeInMinecraftGuiUnits() {
        // A 24 CSS px icon must occupy 12 GUI units at every GUI scale.
        for (int scale = 1; scale <= 8; scale++) {
            assertEquals(12.0, 24 * zoomFactor(scale) / scale, 1e-9);
        }
        assertEquals(0.0, WebViewLayout.zoomLevel(2), 1e-9);
    }

    @Test
    void resizeAndAspectRatioDoNotIntroduceAnotherContentScale() {
        int[][] windows = {{1920, 1080}, {1280, 720}, {1024, 1024}, {2560, 1080}, {1001, 701}};
        for (int[] window : windows) {
            for (int scale = 1; scale <= 4; scale++) {
                int guiWidth = (int) Math.ceil(window[0] / (double) scale);
                int guiHeight = (int) Math.ceil(window[1] / (double) scale);
                // A point in CSS space -> displayed GUI position -> browser input -> DOM.
                double cssX = 237.0, cssY = 123.0;
                double guiX = cssX * zoomFactor(scale) * guiWidth / window[0];
                double guiY = cssY * zoomFactor(scale) * guiHeight / window[1];
                assertEquals(cssX, WebViewLayout.browserCoordinate(guiX, guiWidth, window[0])
                        / zoomFactor(scale), 0.5 / zoomFactor(scale) + 1e-9);
                assertEquals(cssY, WebViewLayout.browserCoordinate(guiY, guiHeight, window[1])
                        / zoomFactor(scale), 0.5 / zoomFactor(scale) + 1e-9);
            }
        }
    }

    @Test
    void textureRemainsAtFramebufferResolution() {
        int[] size = WebViewLayout.browserSize(3840, 2160);
        assertEquals(3840, size[0]);
        assertEquals(2160, size[1]);
        assertEquals(1, WebViewLayout.browserSize(0, 0)[0]);
        assertEquals(1, WebViewLayout.browserSize(0, 0)[1]);
    }
}
