package dev.chorus.core.economy;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How an amount is written. */
class CurrencyTest {

    @Test
    void anAmountIsWrittenWithThousandsAndDecimals() throws InvalidConfigurationException {
        Currency currency = read("");

        assertEquals("$1,234,567.50", currency.format(1_234_567.5));
        assertEquals("$0.00", currency.format(0));
    }

    @Test
    void theSeparatorsCanBeSwapped() throws InvalidConfigurationException {
        Currency currency = read("thousands-separator: '.'\ndecimal-separator: ','");

        assertEquals("$1.234,50", currency.format(1234.5));
    }

    @Test
    void anEmptyThousandsSeparatorWritesNone() throws InvalidConfigurationException {
        assertEquals("$1234.50", read("thousands-separator: ''").format(1234.5));
    }

    @Test
    void theShortFormKeepsTwoDecimalsAtMost() throws InvalidConfigurationException {
        Currency currency = read("short-form: true");

        assertEquals("$999.00", currency.format(999));
        assertEquals("$1.5K", currency.format(1500));
        assertEquals("$2.25M", currency.format(2_250_000));
        assertEquals("$1M", currency.format(1_000_000));
        assertEquals("$1.23B", currency.format(1_234_567_890));
        assertEquals("$-4.2K", currency.format(-4200));
    }

    /** 999,999 would round to 1000K, which says the same as 1M in more space. */
    @Test
    void anAmountThatRoundsUpMovesToTheNextUnit() throws InvalidConfigurationException {
        assertEquals("$1M", read("short-form: true").format(999_999));
    }

    @Test
    void theUnitsAndTheSeparatorFollowTheConfig() throws InvalidConfigurationException {
        Currency currency = read("""
                short-form: true
                short-units: [ ' mil', ' M' ]
                decimal-separator: ','
                suffix: true
                symbol: '€'
                """);

        assertEquals("1,5 mil€", currency.format(1500));
        assertEquals("2500 M€", currency.format(2_500_000_000d));
    }

    private static Currency read(String currencyLines) throws InvalidConfigurationException {
        YamlConfiguration economy = new YamlConfiguration();
        economy.loadFromString("currency:\n" + currencyLines.lines()
                .map(line -> "  " + line).reduce("", (all, line) -> all + line + "\n"));
        return Currency.read(economy);
    }
}
