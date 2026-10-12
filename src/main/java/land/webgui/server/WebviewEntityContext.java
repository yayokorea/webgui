package land.webgui.server;

import land.webgui.common.EntityContextJson;
//? if fabric {
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
//? } else {
/*import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;*/
//? }

public final class WebviewEntityContext {
    private WebviewEntityContext() {}

    public static String buildJson(Entity entity) {
        //? if fabric {
        return EntityContextJson.buildJson(
                entity.getUuid(),
                Registries.ENTITY_TYPE.getId(entity.getType()).toString(),
                entity.hasCustomName()
                        ? entity.getCustomName().getString()
                        : entity.getType().getName().getString(),
                entity.getX(), entity.getY(), entity.getZ());
        //? } else {
        /*return EntityContextJson.buildJson(
                entity.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.hasCustomName()
                        ? entity.getCustomName().getString()
                        : entity.getType().getDescription().getString(),
                entity.getX(), entity.getY(), entity.getZ());*/
        //? }
    }
}
