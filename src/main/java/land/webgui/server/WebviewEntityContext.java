package land.webgui.server;

import land.webgui.common.EntityContextJson;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;

public final class WebviewEntityContext {
    private WebviewEntityContext() {}

    public static String buildJson(Entity entity) {
        return EntityContextJson.buildJson(
                entity.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.hasCustomName()
                        ? entity.getCustomName().getString()
                        : entity.getType().getDescription().getString(),
                entity.getX(), entity.getY(), entity.getZ());
    }
}
