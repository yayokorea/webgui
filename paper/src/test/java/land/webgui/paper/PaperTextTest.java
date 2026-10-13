package land.webgui.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Only what needs no server. Translatable text is rendered from the server's own language
 * files, so that half is checked by e2e.mjs (the entity name "Pig", the death message).
 */
class PaperTextTest {
    @Test
    void nothingStaysNothing() {
        assertNull(PaperText.plain(null));
    }

    @Test
    void dropsStylingAndJoinsTheParts() {
        Component c = Component.text("Steve", NamedTextColor.RED)
                .append(Component.text(" was ").decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text("slain"));
        assertEquals("Steve was slain", PaperText.plain(c));
    }

    @Test
    void emptyTextIsEmptyNotNull() {
        assertEquals("", PaperText.plain(Component.empty()));
    }
}
