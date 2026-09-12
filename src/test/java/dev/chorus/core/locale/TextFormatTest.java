package dev.chorus.core.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both ways of writing coloured text have to work, in the same line if somebody wants.
 */
class TextFormatTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void anOldCodeBecomesATag() {
        assertEquals("<reset><red><bold>Red Bold", TextFormat.toTags("&c&lRed Bold"));
    }

    @Test
    void theSectionSignWorksToo() {
        assertEquals("<reset><gold>Gold", TextFormat.toTags("§6Gold"));
    }

    /** A hex colour, in both of the shapes servers write it. */
    @Test
    void hexColoursAreUnderstood() {
        assertEquals("<#a1b2c3>Hex", TextFormat.toTags("&#a1b2c3Hex"));
        assertEquals("<#a1b2c3>Hex", TextFormat.toTags("&x&a&1&b&2&c&3Hex"));
    }

    @Test
    void miniMessageIsLeftAlone() {
        String tags = "<gradient:#1f8f8c:#3fb8b4>Teal</gradient>";
        assertSame(tags, TextFormat.toTags(tags), "a line with no old codes should not be rebuilt");
    }

    @Test
    void theTwoMixInOneLine() {
        assertEquals("Gold and a gradient",
                PLAIN.serialize(TextFormat.parse("&6Gold and <gradient:#1f8f8c:#3fb8b4>a gradient")));
    }

    @Test
    void anOldCodeActuallyColoursTheText() {
        Component red = TextFormat.parse("&cRed");
        assertEquals(NamedTextColor.RED, colourOf(red));
    }

    @Test
    void aHexCodeActuallyColoursTheText() {
        assertEquals(TextColor.fromHexString("#a1b2c3"), colourOf(TextFormat.parse("&#a1b2c3Hex")));
    }

    /**
     * In the game a colour clears the formatting before it, so {@code &l&cText} is red and
     * not bold. Emitting the colour on its own would quietly restyle every config that
     * relies on that.
     */
    @Test
    void aColourClearsWhatCameBeforeIt() {
        Component text = TextFormat.parse("&l&cRed");
        assertEquals(TextDecoration.State.NOT_SET, lastOf(text).style().decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.RED, colourOf(text));
    }

    /** Item text is drawn in italics unless something says otherwise, and nobody wants that. */
    @Test
    void itemTextIsNotItalic() {
        Component item = TextFormat.forItem("&aGreen");
        assertEquals(TextDecoration.State.FALSE, item.style().decoration(TextDecoration.ITALIC));
    }

    /** But the text can still ask for italics itself, in either format. */
    @Test
    void itemTextCanStillAskForItalics() {
        assertEquals(TextDecoration.State.TRUE,
                lastOf(TextFormat.forItem("&oSlanted")).style().decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.TRUE,
                lastOf(TextFormat.forItem("<i>Slanted")).style().decoration(TextDecoration.ITALIC));
    }

    /**
     * A kit laid out in the screen is written back to the file, and may be laid out again
     * tomorrow. Each pass must leave the same text, not the same text plus a marker.
     */
    @Test
    void itemTextDoesNotCollectMarkersOnTheWayBack() {
        String once = TextFormat.toText(TextFormat.forItem("&aGreen"));
        String twice = TextFormat.toText(TextFormat.forItem(once));

        assertEquals(once, twice, "going round again must not grow the line");
        assertEquals("Green", PLAIN.serialize(TextFormat.parse(once)));
    }

    /** An item that really does want italics keeps them through the same trip. */
    @Test
    void italicsTheTextAskedForSurviveTheWayBack() {
        String written = TextFormat.toText(TextFormat.forItem("&oSlanted"));
        assertEquals(TextDecoration.State.TRUE,
                lastOf(TextFormat.forItem(written)).style().decoration(TextDecoration.ITALIC));
    }

    /**
     * A doubled marker is the marker itself, which is the only way a line can talk about a
     * colour code without turning into one. The help text in the menus depends on it.
     */
    @Test
    void aDoubledMarkerIsTheMarkerItself() {
        assertEquals("&a", TextFormat.toTags("&&a"));
        assertEquals("&a", PLAIN.serialize(TextFormat.parse("&&a")));
        assertEquals("AT&T", PLAIN.serialize(TextFormat.parse("AT&&T")));
    }

    @Test
    void aLoneAmpersandIsJustAnAmpersand() {
        assertFalse(TextFormat.hasLegacy("Bread & butter"));
        assertEquals("Bread & butter", PLAIN.serialize(TextFormat.parse("Bread & butter")));
    }

    @Test
    void textIsNeverLost() {
        assertEquals("Red Bold", PLAIN.serialize(TextFormat.parse("&c&lRed Bold")));
        assertTrue(PLAIN.serialize(TextFormat.parse("&kobf")).contains("obf"));
    }

    /** The deepest piece of text, which is where the last style applied ends up. */
    private static Component lastOf(Component text) {
        Component last = text;
        while (!last.children().isEmpty()) {
            last = last.children().get(last.children().size() - 1);
        }
        return last;
    }

    private static TextColor colourOf(Component text) {
        return lastOf(text).color();
    }
}
