package land.webgui.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.UUID;

/** The JSON a page receives about the entity it was opened for. */
public final class EntityContextJson {
    private static final Gson GSON = new Gson();

    private EntityContextJson() {}

    /** {@code typeId} is the registry id ("minecraft:zombie"); {@code name} the custom name, else the type's. */
    public static String buildJson(UUID uuid, String typeId, String name, double x, double y, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        o.addProperty("type", typeId);
        o.addProperty("name", name);
        JsonObject pos = new JsonObject();
        pos.addProperty("x", x);
        pos.addProperty("y", y);
        pos.addProperty("z", z);
        o.add("pos", pos);
        return GSON.toJson(o);
    }
}
