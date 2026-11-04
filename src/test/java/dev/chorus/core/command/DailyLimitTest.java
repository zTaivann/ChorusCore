package dev.chorus.core.command;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** uses-per-day and uses-reset-at, however they are written. */
class DailyLimitTest {

    private final List<String> problems = new ArrayList<>();

    @Test
    void bothAreRead() throws InvalidConfigurationException {
        DailyLimit limit = read("uses-per-day: 3\nuses-reset-at: '04:00'");

        assertEquals(3, limit.uses());
        assertEquals(LocalTime.of(4, 0), limit.resetAt());
        assertTrue(limit.limited());
    }

    /** YAML turns an unquoted 12:30 into the number 750, and 4:00 into 240. */
    @Test
    void aTimeWrittenWithoutQuotesStillWorks() throws InvalidConfigurationException {
        assertEquals(LocalTime.of(12, 30), read("uses-reset-at: 12:30").resetAt());
        assertEquals(LocalTime.of(4, 0), read("uses-reset-at: 4:00").resetAt());
        assertEquals(LocalTime.of(4, 0), read("uses-reset-at: 04:00").resetAt());
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void whatIsLeftOutComesFromTheDefaults() throws InvalidConfigurationException {
        DailyLimit base = new DailyLimit(5, LocalTime.of(6, 0));
        YamlConfiguration block = new YamlConfiguration();
        block.loadFromString("uses-per-day: 2");

        DailyLimit limit = DailyLimit.read(block, base, "/home", problems::add);

        assertEquals(2, limit.uses());
        assertEquals(LocalTime.of(6, 0), limit.resetAt());
    }

    @Test
    void aTimeThatIsNotOneIsNamedAndIgnored() throws InvalidConfigurationException {
        DailyLimit limit = read("uses-reset-at: 25:00");

        assertEquals(LocalTime.MIDNIGHT, limit.resetAt());
        assertEquals(List.of("/home has a uses-reset-at that is not a time of day: '25:00'."
                + " Write it in quotes, like '04:00'."), problems);
    }

    @Test
    void noLimitUnlessOneIsSet() throws InvalidConfigurationException {
        assertFalse(read("uses-reset-at: '04:00'").limited());
        assertFalse(read("uses-per-day: -3").limited());
    }

    private DailyLimit read(String yaml) throws InvalidConfigurationException {
        YamlConfiguration block = new YamlConfiguration();
        block.loadFromString(yaml);
        return DailyLimit.read(block, DailyLimit.NONE, "/home", problems::add);
    }
}
