package land.webgui.mixin;

import land.webgui.WebGUIKeyMenus;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//? }
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds every key event to the server-defined key combinations.
 *
 * <p>At the head, so the combination is known before the game acts on the key, and so the
 * press that completes one can be swallowed. Release events are only ever observed.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    //? if >=1.21.9 {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void webgui$keyCombos(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (WebGUIKeyMenus.onKey(Minecraft.getInstance(), event.key(), action)) {
            ci.cancel();
        }
    }
    //? } else {
    /*@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void webgui$keyCombos(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (WebGUIKeyMenus.onKey(Minecraft.getInstance(), key, action)) {
            ci.cancel();
        }
    }*/
    //? }
}
