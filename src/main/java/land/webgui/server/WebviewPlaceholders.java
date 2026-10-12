package land.webgui.server;

import land.webgui.common.Placeholders;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

public final class WebviewPlaceholders {
    private WebviewPlaceholders() {}

    public static String resolve(String template, ServerPlayer player, Entity entity) {
        return Placeholders.resolve(template,
                player.getName().getString(), player.getUUID(),
                entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }
}
