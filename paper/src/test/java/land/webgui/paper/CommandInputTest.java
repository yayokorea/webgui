package land.webgui.paper;

import land.webgui.paper.CommandInput.ParseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandInputTest {
    private static CommandInput of(String... bukkitArgs) {
        return new CommandInput(bukkitArgs);
    }

    @Test
    void selectorWithSpacesInBracketsComesBackInOnePiece() throws ParseException {
        // What Bukkit hands over for: bind entity @e[type=zombie, limit=1] "https://x.test/a b" true
        CommandInput in = of("entity", "@e[type=zombie,", "limit=1]", "\"https://x.test/a", "b\"", "true");
        assertEquals("entity", in.readWord());
        assertEquals("@e[type=zombie, limit=1]", in.readSelector());
        assertEquals("https://x.test/a b", in.readString());
        assertTrue(in.readBool());
        assertFalse(in.hasMore());
    }

    @Test
    void bracketsNest() throws ParseException {
        assertEquals("@e[nbt={a:[1, 2]},limit=1]", of("@e[nbt={a:[1,", "2]},limit=1]", "rest").readSelector());
    }

    @Test
    void quotesInsideASelectorHideBrackets() throws ParseException {
        assertEquals("@e[name=\"a ] b\"]", of("@e[name=\"a", "]", "b\"]", "x").readSelector());
    }

    @Test
    void plainSelectorsAndNamesStopAtWhitespace() throws ParseException {
        CommandInput in = of("@a", "Steve", "url");
        assertEquals("@a", in.readSelector());
        assertEquals("Steve", in.readSelector());
        assertEquals("url", in.readWord());
    }

    @Test
    void unclosedSelectorIsAnError() {
        assertThrows(ParseException.class, () -> of("@e[type=zombie,", "limit=1").readSelector());
        assertThrows(ParseException.class, () -> of("@e[name=\"abc]").readSelector());
    }

    @Test
    void quotedStringEscapes() throws ParseException {
        assertEquals("say \"hi\" \\ there", of("\"say", "\\\"hi\\\"", "\\\\", "there\"").readString());
        assertEquals("it's", of("'it\\'s'").readString());
        assertEquals("it's", of("\"it's\"").readString());
    }

    @Test
    void invalidEscapeAndUnclosedQuoteAreErrors() {
        assertThrows(ParseException.class, () -> of("\"a\\nb\"").readString());
        assertThrows(ParseException.class, () -> of("\"never closed").readString());
        assertThrows(ParseException.class, () -> of("\"closed\"junk").readString());
    }

    @Test
    void plainStringRunsToTheNextSpace_soUrlsNeedNoQuotes() throws ParseException {
        CommandInput in = of("https://example.com/a?b=c&d=1", "true");
        assertEquals("https://example.com/a?b=c&d=1", in.readString());
        assertTrue(in.readBool());
    }

    @Test
    void emptyQuotedStringIsAllowed() throws ParseException {
        assertEquals("", of("\"\"").readString());
    }

    @Test
    void missingStringIsAnError() {
        assertThrows(ParseException.class, () -> of().readString());
    }

    @Test
    void boolIsStrict() throws ParseException {
        assertFalse(of("false").readBool());
        assertThrows(ParseException.class, () -> of("True").readBool());
        assertThrows(ParseException.class, () -> of("yes").readBool());
        assertThrows(ParseException.class, () -> of().readBool());
    }

    @Test
    void greedyRestKeepsInnerSpacingAndDropsTheEdges() throws ParseException {
        CommandInput in = of("@a", "http://x.test/", "with", "", "gaps");
        in.readSelector();
        assertEquals("http://x.test/ with  gaps", in.readRest());
        assertFalse(in.hasMore());
    }

    @Test
    void splitTreatsSelectorsAndQuotesAsOneWord() {
        CommandInput.Split s = CommandInput.split("bind entity @e[type=zombie, limit=1] \"a b\" tr");
        assertEquals(List.of("bind", "entity", "@e[type=zombie, limit=1]", "\"a b\""), s.completed());
        assertEquals("tr", s.partial());
    }

    @Test
    void splitWithTrailingSpaceHasAnEmptyPartial() {
        CommandInput.Split s = CommandInput.split("gui ");
        assertEquals(List.of("gui"), s.completed());
        assertEquals("", s.partial());
        assertEquals(List.of(), CommandInput.split("").completed());
    }

    @Test
    void splitToleratesAnUnfinishedSelector() {
        CommandInput.Split s = CommandInput.split("unbind entity @e[type=zombie, ");
        assertEquals(List.of("unbind", "entity"), s.completed());
        assertEquals("@e[type=zombie, ", s.partial());
    }
}
