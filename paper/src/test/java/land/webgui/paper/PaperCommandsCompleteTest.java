package land.webgui.paper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperCommandsCompleteTest {
    private static final Predicate<String> ALL = p -> true;
    private static final List<String> PLAYERS = List.of("Alex", "Steve");

    private static List<String> complete(String line) {
        return PaperCommands.complete(line, ALL, () -> PLAYERS);
    }

    @Test
    void offersSubcommands() {
        assertEquals(List.of("gui", "hud", "bind", "unbind", "reload"), complete(""));
        assertEquals(List.of("gui"), complete("g"));
        assertEquals(List.of("bind"), complete("B"));
    }

    @Test
    void onlyOffersWhatThePlayerMayUse() {
        Set<String> granted = Set.of("webgui.command.gui", "webgui.command.reload");
        assertEquals(List.of("gui", "reload"), PaperCommands.complete("", granted::contains, () -> PLAYERS));
        assertEquals(List.of(), PaperCommands.complete("hud ", granted::contains, () -> PLAYERS));
    }

    @Test
    void offersPlayersAndSelectorsForTargets() {
        assertEquals(List.of("Alex", "Steve", "@a", "@p", "@r", "@s", "@e"), complete("gui "));
        assertEquals(List.of("Steve"), complete("hud st"));
        assertEquals(List.of("@a"), complete("gui @a"));
    }

    @Test
    void doesNotCompleteTheUrl() {
        assertEquals(List.of(), complete("gui @a h"));
    }

    @Test
    void bindWalksThroughItsArguments() {
        assertEquals(List.of("entity"), complete("bind "));
        assertTrue(complete("bind entity ").contains("@e"));
        assertEquals(List.of(), complete("bind entity @e https://x.test/"));
        assertEquals(List.of("true", "false"), complete("bind entity @e https://x.test/ "));
        assertEquals(List.of("false"), complete("bind entity @e https://x.test/ f"));
    }

    @Test
    void spacesInsideASelectorOrQuotesDoNotShiftTheArgumentCount() {
        assertEquals(List.of("true", "false"), complete("bind entity @e[type=zombie, limit=1] \"a b\" "));
        assertEquals(List.of(), complete("bind entity @e[type=zombie, "));
    }

    @Test
    void unbindOffersEntityThenSelectors() {
        assertEquals(List.of("entity"), complete("unbind e"));
        assertTrue(complete("unbind entity ").contains("@s"));
        assertEquals(List.of(), complete("unbind entity @e "));
    }

    @Test
    void reloadAndUnknownTakeNothing() {
        assertEquals(List.of(), complete("reload "));
        assertEquals(List.of(), complete("nope "));
    }
}
