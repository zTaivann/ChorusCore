package dev.chorus.core.economy;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The economy module's settings, as modules/economy.yml writes them. */
class EconomySettingsTest {

    @Test
    void theTaxIsAPercentToTheCent() throws InvalidConfigurationException {
        EconomySettings settings = read("pay-tax: 5");

        assertEquals(5, settings.taxOn(100));
        assertEquals(0.62, settings.taxOn(12.34));
    }

    @Test
    void noTaxIsTheDefaultAndATaxOutOfRangeIsHeldInside() throws InvalidConfigurationException {
        assertEquals(0, read("").taxOn(100));
        assertEquals(100, read("pay-tax: 250").taxOn(100));
        assertEquals(0, read("pay-tax: -5").taxOn(100));
    }

    @Test
    void baltopShowsEveryoneAndRanksEveryHalfMinuteByDefault()
            throws InvalidConfigurationException {
        EconomySettings settings = read("");

        assertFalse(settings.topHideEmpty());
        assertEquals(30, settings.topRefreshSeconds());
        assertTrue(read("top-hide-empty: true").topHideEmpty());
    }

    private static EconomySettings read(String yaml) throws InvalidConfigurationException {
        YamlConfiguration economy = new YamlConfiguration();
        economy.loadFromString(yaml);
        return EconomySettings.read(economy);
    }
}
