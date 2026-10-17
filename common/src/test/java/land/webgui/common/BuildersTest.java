package land.webgui.common;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JSON and text the pages and players see; their exact shape is the contract. */
class BuildersTest {

    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // --- DeathInfoJson ---

    @Test
    void deathByEnvironment() {
        assertEquals(
                "{\"cause\":\"fall\",\"deathMessage\":\"Steve fell from a high place\",\"hardcore\":false,"
                        + "\"canRespawn\":true,\"killer\":{\"type\":\"environment\",\"name\":null,\"uuid\":null}}",
                DeathInfoJson.buildJson("fall", "Steve fell from a high place", false, DeathInfoJson.Killer.environment()));
    }

    @Test
    void deathByMob() {
        assertEquals(
                "{\"cause\":\"mob\",\"deathMessage\":\"Steve was slain by Zombie\",\"hardcore\":false,"
                        + "\"canRespawn\":true,\"killer\":{\"type\":\"mob\",\"name\":\"Zombie\","
                        + "\"uuid\":\"22222222-2222-2222-2222-222222222222\",\"entityType\":\"minecraft:zombie\"}}",
                DeathInfoJson.buildJson("mob", "Steve was slain by Zombie", false,
                        DeathInfoJson.Killer.mob("Zombie", MOB, "minecraft:zombie")));
    }

    @Test
    void deathByPlayerHasNoEntityType() {
        JsonObject killer = JsonParser.parseString(DeathInfoJson.buildJson("player", "Steve was slain by Alex", false,
                DeathInfoJson.Killer.player("Alex", MOB))).getAsJsonObject().getAsJsonObject("killer");
        assertEquals("player", killer.get("type").getAsString());
        assertEquals("Alex", killer.get("name").getAsString());
        assertEquals(MOB.toString(), killer.get("uuid").getAsString());
        assertTrue(!killer.has("entityType"));
    }

    @Test
    void hardcoreCannotRespawn() {
        JsonObject o = JsonParser.parseString(
                DeathInfoJson.buildJson("fall", "m", true, DeathInfoJson.Killer.environment())).getAsJsonObject();
        assertTrue(o.get("hardcore").getAsBoolean());
        assertEquals(false, o.get("canRespawn").getAsBoolean());
    }

    @Test
    void missingValuesFallBackTheWayTheModDoes() {
        // No damage source: "generic", an empty message, and nobody to blame.
        assertEquals(
                "{\"cause\":\"generic\",\"deathMessage\":\"\",\"hardcore\":false,"
                        + "\"canRespawn\":true,\"killer\":{\"type\":\"environment\",\"name\":null,\"uuid\":null}}",
                DeathInfoJson.buildJson(null, null, false, null));
    }

    @Test
    void killersNeedAnId() {
        assertThrows(NullPointerException.class, () -> DeathInfoJson.Killer.player("Alex", null));
        assertThrows(NullPointerException.class, () -> DeathInfoJson.Killer.mob("Zombie", null, "minecraft:zombie"));
    }

    // --- EntityContextJson ---

    @Test
    void entityContextShape() {
        assertEquals(
                "{\"uuid\":\"22222222-2222-2222-2222-222222222222\",\"type\":\"minecraft:villager\","
                        + "\"name\":\"Bob\",\"pos\":{\"x\":1.5,\"y\":64.0,\"z\":-3.25}}",
                EntityContextJson.buildJson(MOB, "minecraft:villager", "Bob", 1.5, 64.0, -3.25));
    }

    @Test
    void entityContextEscapesLikeGson() {
        JsonObject o = JsonParser.parseString(
                EntityContextJson.buildJson(MOB, "minecraft:villager", "Bob \"the <builder>\"", 0, 0, 0)).getAsJsonObject();
        assertEquals("Bob \"the <builder>\"", o.get("name").getAsString());
    }

    // --- Placeholders ---

    @Test
    void placeholdersAreFilled() {
        assertEquals(
                "https://x.example/shop?e=22222222-2222-2222-2222-222222222222&u=22222222-2222-2222-2222-222222222222"
                        + "&t=minecraft:villager&p=Steve&id=11111111-1111-1111-1111-111111111111",
                Placeholders.resolve(
                        "https://x.example/shop?e={entity_id}&u={entity_uuid}&t={entity_type}&p={player_name}&id={player_uuid}",
                        "Steve", PLAYER, MOB, "minecraft:villager"));
    }

    @Test
    void entityIdIsTheEntityUuid() {
        assertEquals(Placeholders.resolve("{entity_uuid}", "Steve", PLAYER, MOB, "t"),
                Placeholders.resolve("{entity_id}", "Steve", PLAYER, MOB, "t"));
    }

    @Test
    void everyOccurrenceIsReplacedAndUnknownOnesAreLeft() {
        assertEquals("Steve/Steve/{nope}", Placeholders.resolve("{player_name}/{player_name}/{nope}", "Steve", PLAYER, MOB, "t"));
        assertEquals("no placeholders", Placeholders.resolve("no placeholders", "Steve", PLAYER, MOB, "t"));
    }

    // --- ClientCheck texts ---

    @Test
    void kickTextDependsOnWhatTheClientIs() {
        land.webgui.WebGUIVersion.init(() -> "9.9.9+mc1.21.11");
        assertEquals("Your WebGUI is out of date.\nThis server runs WebGUI 9.9.9+mc1.21.11 (protocol "
                        + WebviewProtocol.PROTOCOL_VERSION + ").",
                ClientCheck.kickText(ClientKind.OUTDATED));
        assertEquals("This server requires the WebGUI mod.\nIt runs WebGUI 9.9.9+mc1.21.11 (protocol "
                        + WebviewProtocol.PROTOCOL_VERSION + ").",
                ClientCheck.kickText(ClientKind.ABSENT));
        assertTrue(ClientCheck.outdatedText().contains("update it to 9.9.9+mc1.21.11"));
        assertTrue(ClientCheck.absentText().contains("runs WebGUI 9.9.9+mc1.21.11"));
    }
}
