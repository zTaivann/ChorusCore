package dev.chorus.core;

import dev.chorus.core.locale.TextFormat;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The name in the console is drawn out of characters, and characters are easy to lose.
 *
 * <p>One row a character short leans the whole word, and nothing else in the plugin would
 * ever say so: it is written once, looked at once, and then only ever seen by whoever starts
 * the server.
 */
class ConsoleTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void everyRowOfTheNameIsTheSameWidth() {
        for (String[] word : Console.words()) {
            int width = word[0].length();
            for (String row : word) {
                assertEquals(width, row.length(), "this row is a different width:\n" + row);
            }
        }
    }

    /** Nothing in the letters may be read as formatting and disappear on the way out. */
    @Test
    void theNameSurvivesBeingColoured() {
        for (String[] word : Console.words()) {
            for (String row : word) {
                assertEquals(row, PLAIN.serialize(TextFormat.parse("<color:#5b2ea8>" + row)));
            }
        }
    }
}
