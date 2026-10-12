package land.webgui.server;

import land.webgui.WebviewNetworking;
import land.webgui.common.DeathInfoJson;
//? if fabric {
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
//? } else {
/*import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;*/
//? }

/**
 * Reports a player's death to their custom death page.
 *
 * Runs only when the server configured {@code deathScreenUrl}; with no page to
 * show there is nothing to report and the vanilla screen handles it.
 */
public final class WebviewDeathListener {

    private WebviewDeathListener() {}

    //? if fabric {
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                onPlayerDeath(player, source);
            }
        });
    }

    private static void onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        String url = WebviewServerConfig.deathScreenUrl();
        if (url.isEmpty()) {
            return;
        }
        WebviewNetworking.sendDeathScreen(player, url, describe(player, source));
    }

    private static String describe(ServerPlayerEntity player, DamageSource source) {
        boolean hardcore = player.getEntityWorld().getLevelProperties().isHardcore();
        return DeathInfoJson.buildJson(
                source == null ? null : source.getName(),
                source == null ? null : source.getDeathMessage(player).getString(),
                hardcore,
                killer(source));
    }

    private static DeathInfoJson.Killer killer(DamageSource source) {
        Entity attacker = source == null ? null : source.getAttacker();
        if (attacker == null) {
            return DeathInfoJson.Killer.environment();
        }
        String name = attacker.getName().getString();
        if (attacker instanceof ServerPlayerEntity) {
            return DeathInfoJson.Killer.player(name, attacker.getUuid());
        }
        // The registry id ("minecraft:zombie"), not EntityType.toString(),
        // which yields the translation key and is awkward to match on.
        return DeathInfoJson.Killer.mob(name, attacker.getUuid(), EntityType.getId(attacker.getType()).toString());
    }
    //? } else {
    /*public static void register() {
        NeoForge.EVENT_BUS.addListener(WebviewDeathListener::onLivingDeath);
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        String url = WebviewServerConfig.deathScreenUrl();
        if (url.isEmpty()) {
            return;
        }
        WebviewNetworking.sendDeathScreen(player, url, describe(player, event.getSource()));
    }

    private static String describe(ServerPlayer player, DamageSource source) {
        boolean hardcore = player.level().getLevelData().isHardcore();
        return DeathInfoJson.buildJson(
                source == null ? null : source.getMsgId(),
                source == null ? null : source.getLocalizedDeathMessage(player).getString(),
                hardcore,
                killer(source));
    }

    private static DeathInfoJson.Killer killer(DamageSource source) {
        Entity attacker = source == null ? null : source.getEntity();
        if (attacker == null) {
            return DeathInfoJson.Killer.environment();
        }
        String name = attacker.getName().getString();
        if (attacker instanceof ServerPlayer) {
            return DeathInfoJson.Killer.player(name, attacker.getUUID());
        }
        // The registry id ("minecraft:zombie"), not EntityType.toString(),
        // which yields the translation key and is awkward to match on.
        return DeathInfoJson.Killer.mob(name, attacker.getUUID(), EntityType.getKey(attacker.getType()).toString());
    }*/
    //? }
}
