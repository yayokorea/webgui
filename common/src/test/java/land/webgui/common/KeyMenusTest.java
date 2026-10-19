package land.webgui.common;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyMenusTest {

    private static List<KeyMenu> parse(String json) {
        return KeyMenus.parse(JsonParser.parseString(json));
    }

    @Test
    void readsTheDocumentedShape() {
        List<KeyMenu> menus = parse("""
                [{"id": "shop",   "keys": ["shift", "F"], "url": "https://example.com/shop"},
                 {"id": "quests", "keys": ["ctrl", "G"],  "url": " https://example.com/quests "},
                 {"id": "map",    "keys": ["alt", "1"],   "url": "https://example.com/map"}]
                """);
        assertEquals(3, menus.size());
        assertEquals(new KeyMenu("shop", List.of("shift", "f"), "https://example.com/shop"), menus.get(0));
        assertEquals("https://example.com/quests", menus.get(1).url(), "url trimmed");
        assertEquals(List.of("alt", "1"), menus.get(2).keys());
    }

    @Test
    void keysMayBeOneStringToo() {
        List<KeyMenu> menus = parse("[{\"id\": \"a\", \"keys\": \"Shift+F\", \"url\": \"u\"}]");
        assertEquals(List.of("shift", "f"), menus.get(0).keys());
    }

    @Test
    void thereIsNoLimitOnHowManyMenusThereAre() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 300; i++) {
            json.append(i == 0 ? "" : ",").append("{\"id\":\"m").append(i).append("\",\"keys\":[\"f1\"],\"url\":\"u\"}");
        }
        assertEquals(300, parse(json.append("]").toString()).size());
    }

    @Test
    void anEntryThatCannotWorkIsSkippedAndTheRestSurvive() {
        List<KeyMenu> menus = parse("""
                [{"id": "ok1", "keys": ["f"], "url": "u"},
                 {"id": "bad id!", "keys": ["f"], "url": "u"},
                 {"id": "nokeys", "keys": [], "url": "u"},
                 {"id": "unknown", "keys": ["shift", "hyper"], "url": "u"},
                 {"id": "nourl", "keys": ["g"]},
                 {"id": "blankurl", "keys": ["g"], "url": "  "},
                 {"id": "dup", "keys": ["h"], "url": "first"},
                 {"id": "dup", "keys": ["i"], "url": "second"},
                 "not an object",
                 {"id": "weird", "keys": [["shift"], 5], "url": "u"},
                 {"id": "ok2", "keys": ["F7"], "url": "u"}]
                """);
        assertEquals(List.of("ok1", "dup", "ok2"), menus.stream().map(KeyMenu::id).toList());
        assertEquals("first", menus.get(1).url(), "the first of a duplicate id wins");
    }

    @Test
    void aWrongShapeYieldsNothingInsteadOfFailing() {
        assertTrue(parse("{\"id\": \"a\"}").isEmpty());
        assertTrue(parse("\"shift+f\"").isEmpty());
        assertTrue(parse("[]").isEmpty());
        assertTrue(KeyMenus.parse(null).isEmpty());
        assertTrue(KeyMenus.parse(JsonParser.parseString("null")).isEmpty());
    }

    @Test
    void theWireFormRoundTrips() {
        List<KeyMenu> menus = List.of(
                new KeyMenu("shop", List.of("shift", "f"), "https://example.com/shop?a=1&b=2"),
                new KeyMenu("map", List.of("f7"), "webgui://map.html"),
                new KeyMenu("jp", List.of("a"), "https://example.com/日本語.html"));
        assertEquals(menus, KeyMenus.fromWire(KeyMenus.toWire(menus, WebviewProtocol.MAX_KEY_MENUS_LENGTH)));
        assertEquals("[]", KeyMenus.toWire(List.of(), WebviewProtocol.MAX_KEY_MENUS_LENGTH));
        assertTrue(KeyMenus.fromWire("[]").isEmpty());
    }

    @Test
    void whatDoesNotFitIsLeftOffWholeNotCut() {
        List<KeyMenu> menus = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            menus.add(new KeyMenu("menu" + i, List.of("f" + (1 + i % 12)), "https://example.com/page/" + i));
        }
        String wire = KeyMenus.toWire(menus, 2000);
        assertTrue(wire.length() <= 2000, wire.length() + " chars");
        List<KeyMenu> sent = KeyMenus.fromWire(wire);
        assertTrue(sent.size() > 0 && sent.size() < 100, "some, not all: " + sent.size());
        assertEquals(menus.subList(0, sent.size()), sent, "a prefix, in order");
    }

    @Test
    void aMenuListFillsTheRealLimitWithoutEncoderErrors() {
        List<KeyMenu> menus = new java.util.ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            menus.add(new KeyMenu("m" + i, List.of("f1"), "https://example.com/" + "x".repeat(40)));
        }
        String wire = KeyMenus.toWire(menus, WebviewProtocol.MAX_KEY_MENUS_LENGTH);
        // Must be encodable: an over-long string is a WireException in the encoder.
        byte[] bytes = WebviewWire.encodeKeyMenus(new WebviewWire.KeyMenuList(wire));
        assertEquals(wire, WebviewWire.decodeKeyMenus(bytes).json());
    }

    @Test
    void garbageFromAServerNeverThrows() {
        assertTrue(KeyMenus.fromWire("{not json").isEmpty());
        assertTrue(KeyMenus.fromWire("").isEmpty());
        assertTrue(KeyMenus.fromWire(null).isEmpty());
        assertTrue(KeyMenus.fromWire("42").isEmpty());
    }
}
