package land.webgui.paper;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a command line the way Brigadier does, which Bukkit does not.
 *
 * Bukkit hands a command its arguments already cut at every space. WebGUI's commands cannot
 * live with that: an entity selector such as {@code @e[type=zombie, limit=1]} has spaces
 * inside its brackets, and a quoted URL may have them too. So the executor joins the
 * arguments back into the line and reads it from here.
 */
final class CommandInput {

    /** A line that cannot be read; the message is meant for the player. */
    static final class ParseException extends Exception {
        ParseException(String message) {
            super(message);
        }
    }

    private final String input;
    private int pos;

    CommandInput(String[] args) {
        this(String.join(" ", args));
    }

    CommandInput(String input) {
        this.input = input;
    }

    /** Whether anything but whitespace is left. */
    boolean hasMore() {
        skipWhitespace();
        return pos < input.length();
    }

    /** The next word, up to whitespace; an empty string at the end of the line. */
    String readWord() {
        skipWhitespace();
        int start = pos;
        while (pos < input.length() && !Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
        return input.substring(start, pos);
    }

    /**
     * An entity selector: a word, except that whitespace inside {@code [...]} or quotes
     * belongs to it. Brackets nest ({@code @e[nbt={a:[1, 2]}]}).
     */
    String readSelector() throws ParseException {
        skipWhitespace();
        int start = pos;
        int depth = 0;
        char quote = 0;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (quote != 0) {
                if (c == '\\' && pos + 1 < input.length()) {
                    pos++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth = Math.max(0, depth - 1);
            } else if (Character.isWhitespace(c) && depth == 0) {
                break;
            }
            pos++;
        }
        if (quote != 0 || depth != 0) {
            throw new ParseException("Unclosed selector: " + input.substring(start));
        }
        return input.substring(start, pos);
    }

    /**
     * A Brigadier {@code string()}: a quoted string with {@code \"} and {@code \\} escapes,
     * or a plain word.
     *
     * More forgiving than Brigadier in one way. Brigadier's plain form stops at the first
     * character outside {@code [0-9A-Za-z_.+-]}, so a URL has to be quoted there. Here a
     * plain word runs to the next space, so {@code https://example.com/a?b=c} works bare.
     */
    String readString() throws ParseException {
        skipWhitespace();
        if (pos >= input.length()) {
            throw new ParseException("Expected a value");
        }
        char first = input.charAt(pos);
        if (first != '"' && first != '\'') {
            return readWord();
        }
        pos++;
        StringBuilder out = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos++);
            if (c == '\\') {
                if (pos >= input.length()) {
                    break;
                }
                char escaped = input.charAt(pos++);
                if (escaped != first && escaped != '\\') {
                    throw new ParseException("Invalid escape sequence '\\" + escaped + "' in quoted string");
                }
                out.append(escaped);
            } else if (c == first) {
                if (pos < input.length() && !Character.isWhitespace(input.charAt(pos))) {
                    throw new ParseException("Expected a space after the closing quote");
                }
                return out.toString();
            } else {
                out.append(c);
            }
        }
        throw new ParseException("Unclosed quoted string");
    }

    /** A Brigadier {@code bool()}: exactly {@code true} or {@code false}. */
    boolean readBool() throws ParseException {
        String word = readWord();
        if (word.equals("true")) return true;
        if (word.equals("false")) return false;
        throw new ParseException(word.isEmpty()
                ? "Expected bool"
                : "Invalid bool, expected true or false but found '" + word + "'");
    }

    /** A Brigadier {@code greedyString()}: whatever is left, without the whitespace around it. */
    String readRest() {
        String rest = input.substring(pos).strip();
        pos = input.length();
        return rest;
    }

    /**
     * The completed words of a line and the partial word being typed, for tab completion.
     * Splits like {@link #readSelector()}, but never fails: half-typed input is the normal case.
     *
     * @return the words before the last one; the last (possibly empty) comes back separately
     */
    static Split split(String line) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        boolean inWord = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < line.length()) {
                    current.append(c).append(line.charAt(++i));
                    continue;
                }
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth = Math.max(0, depth - 1);
            } else if (Character.isWhitespace(c) && depth == 0) {
                if (inWord) {
                    words.add(current.toString());
                    current.setLength(0);
                    inWord = false;
                }
                continue;
            }
            current.append(c);
            inWord = true;
        }
        return new Split(words, current.toString());
    }

    record Split(List<String> completed, String partial) {}

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }
}
