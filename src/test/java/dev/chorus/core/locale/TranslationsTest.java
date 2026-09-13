package dev.chorus.core.locale;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A translation is allowed to be incomplete, so what it does with the lines it leaves out is
 * the whole of what these check.
 */
class TranslationsTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void aTranslatedLineWins() throws InvalidConfigurationException {
        Translations english = english("home.created: 'Home saved.'");
        Translations spanish = translation(english, "home.created: 'Casa guardada.'");

        assertEquals("Casa guardada.", drawn(spanish, "home.created"));
    }

    @Test
    void aLineItDoesNotTranslateComesFromEnglish() throws InvalidConfigurationException {
        Translations english = english("home.created: 'Home saved.'\nhome.deleted: 'Home deleted.'");
        Translations spanish = translation(english, "home.created: 'Casa guardada.'");

        assertEquals("Home deleted.", drawn(spanish, "home.deleted"));
    }

    @Test
    void aLineNobodyHasIsNothingAtAll() throws InvalidConfigurationException {
        Translations english = english("home.created: 'Home saved.'");
        Translations spanish = translation(english, "");

        assertNull(spanish.template("home.nothing"));
        assertNull(spanish.entry("home.nothing"));
    }

    /** A blank line is an admin silencing a message, and a translation may silence its own. */
    @Test
    void aBlankLineSilencesThatMessageInThatLanguageOnly() throws InvalidConfigurationException {
        Translations english = english("home.created: 'Home saved.'");
        Translations spanish = translation(english, "home.created: ''");

        assertTrue(spanish.isMuted("home.created"));
        assertFalse(english.isMuted("home.created"));
    }

    @Test
    void aTranslationWithNoPrefixOfItsOwnBorrowsTheEnglishOne() throws InvalidConfigurationException {
        Translations english = english("prefix: 'Chorus | '");
        english.prefix("Chorus | ");
        Translations spanish = translation(english, "home.created: 'Casa guardada.'");
        spanish.prefix("");

        assertEquals("Chorus | ", spanish.rawPrefix());
    }

    @Test
    void thePrefixIsPutIntoATranslatedLine() throws InvalidConfigurationException {
        Translations english = english("home.created: 'Home saved.'");
        Translations spanish = new Translations(english);
        spanish.read(yaml("home.created: '%prefix%Casa guardada.'"), "Chorus | ");

        assertEquals("Chorus | Casa guardada.", drawn(spanish, "home.created"));
    }

    private static Translations english(String lines) throws InvalidConfigurationException {
        Translations english = new Translations(null);
        english.read(yaml(lines), "");
        return english;
    }

    private static Translations translation(Translations fallback, String lines)
            throws InvalidConfigurationException {
        Translations language = new Translations(fallback);
        language.read(yaml(lines), "");
        return language;
    }

    private static YamlConfiguration yaml(String lines) throws InvalidConfigurationException {
        YamlConfiguration data = new YamlConfiguration();
        data.loadFromString(lines);
        return data;
    }

    private static String drawn(Translations language, String key) {
        assertNotNull(language.entry(key), "no language in the chain has '" + key + "'");
        return PLAIN.serialize(language.entry(key));
    }
}
