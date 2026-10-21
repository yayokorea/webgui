package land.webgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

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
    void renderedPixelsAndMouseInputAgreeAtRoundedGuiSizesAndHiDpi() {
        int[][] windows = {{1920, 1080}, {1280, 720}, {1024, 1024}, {2560, 1080}, {1001, 701}, {2002, 1402}};
        for (int[] window : windows) {
            for (int scale = 1; scale <= 4; scale++) {
                int guiWidth = (int) Math.ceil(window[0] / (double) scale);
                int guiHeight = (int) Math.ceil(window[1] / (double) scale);
                // Minecraft's render projection uses the unrounded framebuffer / scale.
                float undo = WebViewLayout.pixelToGuiScale(scale);
                var transform = new Matrix3x2f().scaling(scale, scale).scale(undo, undo);
                for (double dpi : new double[] {1.0, 1.5, 2.0}) {
                    for (float fraction : new float[] {0f, 0.237f, 0.5f, 0.999f, 1f}) {
                        float px = window[0] * fraction;
                        float py = window[1] * fraction;
                        Vector2f drawn = transform.transformPosition(new Vector2f(px, py));
                        assertEquals(px, drawn.x, 0.001);
                        assertEquals(py, drawn.y, 0.001);
                        // GLFW coordinates are window units; MC mouse events use rounded GUI units.
                        double rawX = drawn.x / dpi, rawY = drawn.y / dpi;
                        double guiX = rawX * guiWidth / (window[0] / dpi);
                        double guiY = rawY * guiHeight / (window[1] / dpi);
                        assertEquals(px, WebViewLayout.browserCoordinate(guiX, guiWidth, window[0]), 0.501);
                        assertEquals(py, WebViewLayout.browserCoordinate(guiY, guiHeight, window[1]), 0.501);
                    }
                }
            }
        }
    }

    @Test
    void previousFrameKeepsItsPixelSizeWhileWindowGrows() {
        for (int scale = 1; scale <= 8; scale++) {
            var transform = new Matrix3x2f().scaling(scale, scale)
                    .scale(WebViewLayout.pixelToGuiScale(scale));
            Vector2f edge = transform.transformPosition(new Vector2f(1001, 701));
            assertEquals(1001, edge.x, 0.001);
            assertEquals(701, edge.y, 0.001);
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
