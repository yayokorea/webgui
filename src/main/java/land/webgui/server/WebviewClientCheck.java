package land.webgui.server;

import land.webgui.WebGUIMod;
import land.webgui.WebGUIVersion;
import land.webgui.WebviewNetworking;
import land.webgui.common.ClientCheck;
import land.webgui.common.ClientKind;
//? if fabric {
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
//? } else {
/*import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;*/
//? }

/**
 * Tells a joining player, in plain words, whether their client can see this server's pages.
 *
 * Without this the two loaders fail in two unhelpful ways: NeoForge rejects the connection
 * with "Incompatible client! Please use NeoForge &lt;x&gt;" — naming a mod that is not the
 * problem — while Fabric admits the player and then silently drops every WebGUI packet, so
 * nothing ever opens and nothing says why. Both now end with a message naming this mod and
 * the version the server runs.
 */
public final class WebviewClientCheck {

    private WebviewClientCheck() {}

    //? if fabric {
    public static boolean onJoin(ServerPlayerEntity player) {
        ClientKind kind = WebviewNetworking.clientKind(player);
        if (kind == ClientKind.CURRENT) {
            WebviewNetworking.sendHello(player);
            return true;
        }

        String name = player.getName().getString();
        if (WebviewServerConfig.requireClientMod()) {
            WebGUIMod.LOGGER.info("webgui: refused {} — client is {}", name, kind);
            player.networkHandler.disconnect(Text.literal(ClientCheck.kickText(kind)));
            return false;
        }

        if (kind == ClientKind.OUTDATED) {
            WebGUIMod.LOGGER.warn("webgui: {} has an outdated WebGUI; server runs {}", name, WebGUIVersion.current());
            player.sendMessage(Text.literal("[WebGUI] " + ClientCheck.outdatedText()).formatted(Formatting.YELLOW), false);
        } else if (ClientCheck.serverUsesPages()) {
            WebGUIMod.LOGGER.info("webgui: {} joined without WebGUI; server runs {}", name, WebGUIVersion.current());
            player.sendMessage(Text.literal("[WebGUI] " + ClientCheck.absentText()).formatted(Formatting.GRAY), false);
        }
        return true;
    }
    //? } else {
    /*public static boolean onJoin(ServerPlayer player) {
        ClientKind kind = WebviewNetworking.clientKind(player);
        if (kind == ClientKind.CURRENT) {
            WebviewNetworking.sendHello(player);
            return true;
        }

        String name = player.getName().getString();
        if (WebviewServerConfig.requireClientMod()) {
            WebGUIMod.LOGGER.info("webgui: refused {} - client is {}", name, kind);
            player.connection.disconnect(Component.literal(ClientCheck.kickText(kind)));
            return false;
        }

        if (kind == ClientKind.OUTDATED) {
            WebGUIMod.LOGGER.warn("webgui: {} has an outdated WebGUI; server runs {}", name, WebGUIVersion.current());
            player.sendSystemMessage(Component.literal("[WebGUI] " + ClientCheck.outdatedText()).withStyle(ChatFormatting.YELLOW));
        } else if (ClientCheck.serverUsesPages()) {
            WebGUIMod.LOGGER.info("webgui: {} joined without WebGUI; server runs {}", name, WebGUIVersion.current());
            player.sendSystemMessage(Component.literal("[WebGUI] " + ClientCheck.absentText()).withStyle(ChatFormatting.GRAY));
        }
        return true;
    }*/
    //? }
}
