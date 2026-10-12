package land.webgui.common;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.UUID;

/**
 * The JSON a custom death page receives about the death.
 *
 * Takes plain values, so the loader code only has to read them off its own damage
 * source and every platform ends up with the same keys.
 */
public final class DeathInfoJson {
    private DeathInfoJson() {}

    /** Who or what dealt the killing blow. */
    public record Killer(String type, String name, UUID uuid, String entityType) {
        /** Fell, drowned, burned — no entity to name. */
        public static Killer environment() {
            return new Killer("environment", null, null, null);
        }

        public static Killer player(String name, UUID uuid) {
            return new Killer("player", name, Objects.requireNonNull(uuid, "uuid"), null);
        }

        /** {@code entityType} is the registry id ("minecraft:zombie"), not a translation key. */
        public static Killer mob(String name, UUID uuid, String entityType) {
            return new Killer("mob", name, Objects.requireNonNull(uuid, "uuid"), entityType);
        }
    }

    /**
     * @param cause        the damage type id, e.g. {@code "fall"}; {@code null} reads as {@code "generic"}
     * @param deathMessage the rendered death message; {@code null} reads as empty
     * @param killer       {@code null} reads as {@link Killer#environment()}
     */
    public static String buildJson(String cause, String deathMessage, boolean hardcore, Killer killer) {
        JsonObject o = new JsonObject();
        o.addProperty("cause", cause == null ? "generic" : cause);
        o.addProperty("deathMessage", deathMessage == null ? "" : deathMessage);
        o.addProperty("hardcore", hardcore);
        // Hardcore has no respawn button, so the page must not offer one either.
        o.addProperty("canRespawn", !hardcore);
        o.add("killer", killerJson(killer == null ? Killer.environment() : killer));
        return o.toString();
    }

    private static JsonObject killerJson(Killer killer) {
        JsonObject k = new JsonObject();
        k.addProperty("type", killer.type());
        if (killer.name() == null && killer.uuid() == null) {
            k.add("name", JsonNull.INSTANCE);
            k.add("uuid", JsonNull.INSTANCE);
            return k;
        }
        k.addProperty("name", killer.name());
        k.addProperty("uuid", killer.uuid().toString());
        if (killer.entityType() != null) {
            k.addProperty("entityType", killer.entityType());
        }
        return k;
    }
}
