package land.webgui.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The damage type's msgId is the contract with the page; Bukkit only hands it over inside a translation key. */
class PaperDeathListenerTest {
    @Test
    void stripsTheDeathAttackPrefix() {
        assertEquals("fall", PaperDeathListener.causeFromTranslationKey("death.attack.fall"));
        assertEquals("mob", PaperDeathListener.causeFromTranslationKey("death.attack.mob"));
        assertEquals("genericKill", PaperDeathListener.causeFromTranslationKey("death.attack.genericKill"));
        assertEquals("outOfWorld", PaperDeathListener.causeFromTranslationKey("death.attack.outOfWorld"));
    }

    @Test
    void aKeyWithoutThePrefixIsPassedThrough() {
        // A datapack damage type can only be worded by its msgId, but a server that changes the
        // key scheme must show up as a different value, not as a lost one.
        assertEquals("something.else", PaperDeathListener.causeFromTranslationKey("something.else"));
    }

    @Test
    void noKeyIsNoCause() {
        assertNull(PaperDeathListener.causeFromTranslationKey(null));
    }
}
