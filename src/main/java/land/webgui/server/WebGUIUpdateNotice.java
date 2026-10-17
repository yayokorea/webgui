package land.webgui.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Tells an operator who just joined about a newer WebGUI, in chat. */
public final class WebGUIUpdateNotice {

    private WebGUIUpdateNotice() {}

    /** Says it to an operator who just joined, if the server was told to. */
    public static void notifyOnJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<String> lines = WebGUIUpdateChecker.joinNotice();
        if (lines.isEmpty()) {
            return;
        }
        //? if >=1.21.5 {
        boolean operator = net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions());
        //? } else {
        /*boolean operator = player.hasPermissions(2);
        *///? }
        if (!operator) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            player.sendSystemMessage(Component.literal(lines.get(i))
                    .withStyle(i == 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
    }
}
