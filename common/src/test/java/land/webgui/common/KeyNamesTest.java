package land.webgui.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyNamesTest {

    @Test
    void namesAreCaseInsensitiveAndTrimmed() {
        assertEquals("f", KeyNames.canonical("F"));
        assertEquals("shift", KeyNames.canonical("  Shift "));
        assertEquals("f6", KeyNames.canonical("F6"));
        assertEquals("7", KeyNames.canonical("7"));
    }

    @Test
    void aliasesFoldIntoOneSpelling() {
        assertEquals("ctrl", KeyNames.canonical("Control"));
        assertEquals("escape", KeyNames.canonical("esc"));
        assertEquals("enter", KeyNames.canonical("Return"));
        assertEquals("grave", KeyNames.canonical("`"));
        assertEquals("lshift", KeyNames.canonical("LeftShift"));
    }

    @Test
    void unknownNamesAreRejected() {
        assertNull(KeyNames.canonical("hyper"));
        assertNull(KeyNames.canonical("f25"));
        assertNull(KeyNames.canonical(""));
        assertNull(KeyNames.canonical("  "));
        assertNull(KeyNames.canonical(null));
        assertNull(KeyNames.canonical("shift+f"));
    }

    @Test
    void everyCanonicalNameIsItsOwnCanonicalForm() {
        for (String name : KeyNames.all()) {
            assertEquals(name, KeyNames.canonical(name));
        }
    }

    @Test
    void aComboIsCheckedAsAWhole() {
        assertEquals(List.of("shift", "f"), KeyNames.canonicalCombo(List.of("Shift", "F")));
        assertEquals(List.of("f7"), KeyNames.canonicalCombo(List.of("F7")));
        assertNull(KeyNames.canonicalCombo(List.of()), "empty");
        assertNull(KeyNames.canonicalCombo(null));
        assertNull(KeyNames.canonicalCombo(List.of("shift", "nope")), "unknown key");
        assertNull(KeyNames.canonicalCombo(List.of("shift", "SHIFT")), "the same key twice");
        assertNull(KeyNames.canonicalCombo(List.of("ctrl", "shift", "alt", "a", "b", "c", "d")), "too many keys");
        assertEquals(KeyNames.MAX_KEYS,
                KeyNames.canonicalCombo(List.of("ctrl", "shift", "alt", "a", "b", "c")).size());
    }

    @Test
    void aWrittenComboSplitsOnPlus() {
        assertEquals(List.of("shift", "F"), KeyNames.split("shift + F"));
        assertEquals(List.of("f6"), KeyNames.split("f6"));
        assertTrue(KeyNames.split(null).isEmpty());
        assertEquals("shift+f", KeyNames.join(List.of("shift", "f")));
    }
}
