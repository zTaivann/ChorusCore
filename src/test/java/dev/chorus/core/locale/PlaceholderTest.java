package dev.chorus.core.locale;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The menus once showed a literal {@code %home%} because the value was being put into the
 * finished component rather than into the parse.
 *
 * <p>{@code <gradient>} is what exposed it: it colours a line one character at a time, so the
 * placeholder no longer exists as a single piece of text by the time anything looks for it.
 * These check that it survives the tags that do that, and that a value still cannot smuggle
 * in tags of its own.
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
     * A tag called "key" is a real MiniMessage tag, so a placeholder of that name has to be
     * kept apart from it or the message would come out as a keybind.
     */
    @Test
    void aPlaceholderNamedAfterATagIsNotMistakenForIt() {
        assertEquals("chorus.admin", plain("%key%", "key", "chorus.admin"));
    }

    private static String plain(String template, String... placeholders) {
        return PLAIN.serialize(Messages.fill(template, placeholders));
    }
}
