package land.webgui;

import land.webgui.browser.WebBrowser;
import net.minecraft.client.Minecraft;

/** Shared coordinate policy for HUDs and screens, driven by Minecraft's GUI scale. */
final class WebViewLayout {
    // Shared design density for every player: two CSS pixels per vanilla GUI unit
    // (a 24px icon occupies 12 GUI units). This makes the UI 1.5x larger than the old
    // three-pixel density. At GUI scale 2 the page renders at its original pixel size.
    // All other scales follow
    // vanilla, including Auto, Unicode adjustments and HiDPI framebuffer scaling.
    static final double CSS_PIXELS_PER_GUI_UNIT = 2.0;

    private WebViewLayout() {}

    static double guiScale() {
        var client = Minecraft.getInstance();
        return client != null && client.getWindow() != null
                ? client.getWindow().getGuiScale() : 1.0;
    }

    /** Keep the texture at framebuffer resolution; page zoom supplies the GUI transform. */
    static int[] browserSize(int framebufferWidth, int framebufferHeight) {
        return new int[] {Math.max(1, framebufferWidth), Math.max(1, framebufferHeight)};
    }

    static double zoomLevel(double guiScale) {
        return Math.log(Math.max(1.0, guiScale) / CSS_PIXELS_PER_GUI_UNIT) / Math.log(1.2);
    }

    /**
     * Screen mouse coordinates are GUI units; CEF input is in browser view pixels before
     * page zoom. Use the same full-view mapping as the texture draw, including rounded GUI
     * dimensions. Chromium itself converts these view coordinates to DOM/CSS coordinates.
     */
    static int browserCoordinate(double guiCoordinate, int guiExtent, int browserExtent) {
        return (int) Math.round(guiCoordinate * browserExtent / (double) Math.max(1, guiExtent));
    }

    /** Also reapplied after navigation because Chromium can reset the origin's zoom. */
    static void applyZoom(WebBrowser browser) {
        if (browser == null) return;
        try {
            browser.setZoomLevel(zoomLevel(guiScale()));
        } catch (Throwable t) {
            WebGUIMod.LOGGER.warn("[WebGUI] setZoomLevel failed: {}", t.toString());
        }
    }
}
