package dev.chorus.core.locale;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Menus once showed a literal {@code %home%} because values were put into the finished
 * component instead of the parse.
 */
class PlaceholderTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void aPlaceholderInsideAGradientIsStillFilledIn() {
        assertEquals("base", plain("<gradient:#a06bff:#e0c3fc><bold>%home%", "home", "base"));
    }

    @Test
    void aPlaceholderInsideARainbowIsStillFilledIn() {
        assertEquals("zTaivann", plain("<rainbow>%player%", "player", "zTaivann"));
    }

    @Test
    void anOrdinaryTemplateStillWorks() {
        assertEquals("Home base created.",
                plain("<gray>Home <white>%home%</white> created.", "home", "base"));
    }

    @Test
    void severalPlaceholdersInOneLine() {
        assertEquals("base is in world at 1, 2, 3",
                plain("%home% is in %world% at %x%, %y%, %z%",
                        "home", "base", "world", "world", "x", "1", "y", "2", "z", "3"));
    }

    /** A player called {@code <red>} must not be able to colour anybody else's screen. */
    @Test
    void aValueCannotSmuggleInTags() {
        String rendered = plain("<gray>Hello %player%", "player", "<red><bold>Bob");
        assertEquals("Hello <red><bold>Bob", rendered);
    }

    /** The same goes for a value that arrives holding a placeholder of its own. */
    @Test
    void aValueIsNotSearchedForMorePlaceholders() {
        String rendered = plain("<gray>%player% and %home%",
                "player", "%home%", "home", "base");
        assertTrue(rendered.startsWith("%home% and "), rendered);
        assertTrue(rendered.endsWith(" base"), rendered);
    }

    /**
     * "key" is a real MiniMessage tag; a placeholder of that name must stay apart from it, or the
     * message becomes a keybind.
     */
    @Test
    void aPlaceholderNamedAfterATagIsNotMistakenForIt() {
        assertEquals("chorus.admin", plain("%key%", "key", "chorus.admin"));
    }

    /**
     * A lore line with a newline once rendered a box in the tooltip. Lore is a list of lines;
     * this splits it.
     */
    @Test
    void aTemplateSplitsIntoTheLinesItAsksFor() {
        List<String> lines = Messages.fillLines(
                "<gray>Holds <white>%count%<newline><green>Click to edit", "count", "3")
                .stream().map(PLAIN::serialize).toList();

        assertEquals(2, lines.size());
        assertEquals("Holds 3", lines.get(0));
        assertEquals("Click to edit", lines.get(1));
    }

    @Test
    void aTemplateWithNoBreakIsStillOneLine() {
        assertEquals(1, Messages.fillLines("<gray>Just the one").size());
    }

    private static String plain(String template, String... placeholders) {
        return PLAIN.serialize(Messages.fill(template, placeholders));
    }
}
