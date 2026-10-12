package land.webgui.server;

import land.webgui.common.Placeholders;
//? if fabric {
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
//? } else {
/*import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;*/
//? }

public final class WebviewPlaceholders {
    private WebviewPlaceholders() {}

    //? if fabric {
    public static String resolve(String template, ServerPlayerEntity player, Entity entity) {
        return Placeholders.resolve(template,
                player.getName().getString(), player.getUuid(),
                entity.getUuid(), Registries.ENTITY_TYPE.getId(entity.getType()).toString());
    }
    //? } else {
    /*public static String resolve(String template, ServerPlayer player, Entity entity) {
        return Placeholders.resolve(template,
                player.getName().getString(), player.getUUID(),
                entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }*/
    //? }
}
