package dev.chorus.core.update;

import dev.chorus.core.update.UpdateCheck.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three places ChorusCore is published, with nothing for the server owner to look up. */
class UpdateSourceTest {

    @Test
    void theNamesAreReadHoweverTheyAreWritten() {
        assertEquals(Source.SPIGOT, Source.of("spigot"));
        assertEquals(Source.MODRINTH, Source.of(" Modrinth "));
        assertEquals(Source.BUILTBYBIT, Source.of("builtbybit"));
        assertEquals(Source.BUILTBYBIT, Source.of("Built-By-Bit"));
    }

    @Test
    void aSiteItIsNotOnIsNotASource() {
        assertNull(Source.of("hangar"));
        assertNull(Source.of("plain"));
    }

    @Test
    void theFirstListedModrinthVersionIsTheLatest() {
        String list = "[{\"name\":\"1.8\",\"version_number\":\"0.1.8\"},"
                + "{\"name\":\"1.7\",\"version_number\":\"0.1.7\"}]";
        assertEquals("0.1.8", UpdateCheck.clean(list));
        assertEquals("", UpdateCheck.clean("[]"));
    }

    @Test
    void everySourceAsksOverHttpsAndSendsStaffToItsOwnPage() {
        for (Source source : Source.values()) {
            assertTrue(source.answer().startsWith("https://"), source.name());
            assertTrue(source.page().startsWith("https://"), source.name());
        }
        assertTrue(Source.MODRINTH.page().contains("modrinth.com/plugin/chorus-core"));
        assertTrue(Source.BUILTBYBIT.page().contains("builtbybit.com"));
    }

    /** BuiltByBit wants a key, so its version comes from Spigot, where the same release is. */
    @Test
    void builtByBitIsAskedThroughSpigot() {
        assertEquals(Source.SPIGOT.answer(), Source.BUILTBYBIT.answer());
    }

    @Test
    void onlyAVersionCountsAsAnAnswer() {
        assertEquals("0.1.5", UpdateCheck.clean("0.1.5"));
        assertEquals("0.1.5", UpdateCheck.clean("  0.1.5\n"));
        assertEquals("", UpdateCheck.clean("<html><body>Rate limited</body></html>"));
        assertEquals("", UpdateCheck.clean("{\"error\":\"not found\"}"));
        assertEquals("", UpdateCheck.clean(""));
    }
}
