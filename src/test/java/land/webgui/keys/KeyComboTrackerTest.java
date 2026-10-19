package land.webgui.keys;

import land.webgui.common.KeyNames;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Written against key <em>names</em> and the codes {@link KeyCodes} gives them, never numbers,
 * so the same test holds on the GLFW versions and on 26.3's SDL ones.
 */
class KeyComboTrackerTest {

    private record Bind(String id, KeyCombo combo) {}

    private static Bind bind(String id, String... keys) {
        KeyCombo combo = KeyCombo.of(List.of(keys));
        assertNotNull(combo, id);
        return new Bind(id, combo);
    }

    private static int code(String name) {
        return KeyCodes.codes(name)[0];
    }

    private final KeyComboTracker tracker = new KeyComboTracker();

    private String press(String name, List<Bind> binds) {
        Bind hit = tracker.press(code(name), binds, Bind::combo);
        return hit == null ? null : hit.id();
    }

    @Test
    void everyServerNameResolvesOnThisVersion() {
        for (String name : KeyNames.all()) {
            assertNotNull(KeyCodes.codes(name), name);
        }
    }

    @Test
    void aCodeReadsBackAsItsGenericName() {
        assertEquals("f", KeyCodes.name(code("f")));
        assertEquals("shift", KeyCodes.name(code("lshift")));
        assertEquals("shift", KeyCodes.name(code("rshift")));
        assertEquals("ctrl", KeyCodes.name(KeyCodes.codes("ctrl")[1]));
        assertNull(KeyCodes.name(-12345));
    }

    @Test
    void aComboFiresWhenTheLastKeyCompletesIt() {
        List<Bind> binds = List.of(bind("shop", "shift", "f"));
        assertNull(press("shift", binds), "half of it");
        assertEquals("shop", press("f", binds));
    }

    @Test
    void theOrderTheKeysAreHeldInDoesNotMatter() {
        List<Bind> binds = List.of(bind("shop", "shift", "f"));
        assertNull(press("f", binds));
        assertEquals("shop", press("shift", binds));
    }

    @Test
    void eitherKeyOfAModifierPairWorks() {
        List<Bind> binds = List.of(bind("shop", "shift", "f"));
        press("rshift", binds);
        assertEquals("shop", press("f", binds));
    }

    @Test
    void aSidedModifierIsOnlyThatSide() {
        List<Bind> binds = List.of(bind("left", "lshift", "f"));
        press("rshift", binds);
        assertNull(press("f", binds));
    }

    @Test
    void firesOnceAndNotAgainWhileHeld() {
        List<Bind> binds = List.of(bind("shop", "shift", "f"));
        press("shift", binds);
        assertEquals("shop", press("f", binds));
        assertNull(press("f", binds), "auto-repeat sends the press again");
        tracker.release(code("f"));
        assertEquals("shop", press("f", binds), "a fresh press while shift is still down fires again");
    }

    @Test
    void releasingAKeyBreaksTheCombo() {
        List<Bind> binds = List.of(bind("shop", "shift", "f"));
        press("shift", binds);
        tracker.release(code("shift"));
        assertNull(press("f", binds));
    }

    @Test
    void aSecondKeyOfAPairAlreadyCoveredDoesNotFireAgain() {
        List<Bind> binds = List.of(bind("shop", "shift", "f"));
        press("lshift", binds);
        assertEquals("shop", press("f", binds));
        assertNull(press("rshift", binds), "the combination was already complete");
    }

    @Test
    void aSingleKeyIsACombo() {
        List<Bind> binds = List.of(bind("map", "f7"));
        assertEquals("map", press("f7", binds));
        assertNull(press("f8", binds));
    }

    @Test
    void extraHeldKeysDoNotPreventIt() {
        List<Bind> binds = List.of(bind("shop", "shift", "f"));
        press("w", binds);
        press("shift", binds);
        assertEquals("shop", press("f", binds));
    }

    @Test
    void theMostSpecificComboWins() {
        List<Bind> binds = List.of(bind("plain", "f"), bind("shifted", "shift", "f"), bind("both", "ctrl", "shift", "f"));
        press("ctrl", binds);
        press("shift", binds);
        assertEquals("both", press("f", binds));
    }

    @Test
    void theFirstListedWinsATie() {
        List<Bind> binds = List.of(bind("first", "shift", "f"), bind("second", "shift", "f"));
        press("shift", binds);
        assertEquals("first", press("f", binds));
    }

    @Test
    void aLongerComboDoesNotStealAShorterOnesPress() {
        List<Bind> binds = List.of(bind("shifted", "shift", "f"), bind("both", "ctrl", "shift", "f"));
        press("shift", binds);
        assertEquals("shifted", press("f", binds), "ctrl is not held");
    }

    @Test
    void aKeyOutsideTheComboNeverFiresIt() {
        List<Bind> binds = List.of(bind("shop", "shift", "f"));
        press("shift", binds);
        press("f", binds);
        tracker.release(code("f"));
        assertNull(press("g", binds));
    }

    @Test
    void clearForgetsEverything() {
        List<Bind> binds = List.of(bind("shop", "shift", "f"));
        press("shift", binds);
        tracker.clear();
        assertTrue(tracker.held().isEmpty());
        assertNull(press("f", binds));
    }

    @Test
    void anUnknownNameMakesNoCombo() {
        assertNull(KeyCombo.of(List.of("shift", "hyper")));
        assertNull(KeyCombo.of(List.of()));
        assertNull(KeyCombo.of(null));
    }
}
