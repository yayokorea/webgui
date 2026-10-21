package land.webgui;

import land.webgui.browser.WebBrowser;
import net.minecraft.client.Minecraft;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///? } else {
import net.minecraft.client.gui.GuiGraphics;
//? }
//? if >=1.21.5 {
import net.minecraft.client.renderer.RenderPipelines;
//? }

/** Draws Chromium's final pixels without resampling them through rounded GUI dimensions. */
final class WebViewRenderer {
    private WebViewRenderer() {}

    //? if >=26 {
    /*static void draw(GuiGraphicsExtractor context, WebBrowser browser) {
    *///? } else {
    static void draw(GuiGraphics context, WebBrowser browser) {
    //? }
        var texture = browser.getTextureIdentifier();
        if (texture == null) return;

        var window = Minecraft.getInstance().getWindow();
        int textureWidth = browser.textureWidth();
        int textureHeight = browser.textureHeight();
        // While CEF catches up with a resize, crop the old image instead of stretching it.
        int width = Math.min(textureWidth, window.getWidth());
        int height = Math.min(textureHeight, window.getHeight());
        if (width <= 0 || height <= 0) return;

        float scale = WebViewLayout.pixelToGuiScale(window.getGuiScale());
        var pose = context.pose();
        //? if >=1.21.5 {
        pose.pushMatrix();
        //? } else {
        /*pose.pushPose();
        *///? }
        try {
            // Cancel only Minecraft's coordinate scale. Page zoom still controls UI size.
            //? if >=1.21.5 {
            pose.scale(scale, scale);
            context.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f,
                    width, height, textureWidth, textureHeight);
            //? } else {
            /*pose.scale(scale, scale, 1f);
            context.blit(texture, 0, 0, 0f, 0f, width, height, textureWidth, textureHeight);
            *///? }
        } finally {
            //? if >=1.21.5 {
            pose.popMatrix();
            //? } else {
            /*pose.popPose();
            *///? }
        }
    }
}
