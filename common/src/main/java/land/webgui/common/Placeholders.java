package land.webgui.common;

import java.util.UUID;

/** Fills the {@code {...}} placeholders of an entity binding's URL template. */
public final class Placeholders {
    private Placeholders() {}

    public static String resolve(String template, String playerName, UUID playerUuid, UUID entityUuid, String entityType) {
        String entityId = entityUuid.toString();
        return template
                .replace("{entity_id}",   entityId)
                .replace("{entity_uuid}", entityId)
                .replace("{entity_type}", entityType)
                .replace("{player_name}", playerName)
                .replace("{player_uuid}", playerUuid.toString());
    }
}
