package dev.chorus.core.location;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A cap per world, as homes and chest shops write it. */
class WorldLimitsTest {

    @Test
    void aWorldWithACapHasItWhateverTheCase() throws InvalidConfigurationException {
        WorldLimits limits = read("world-limits:\n  World_Nether: 2\n  world_the_end: -4\n");

        assertEquals(2, limits.in("world_nether"));
        assertEquals(2, limits.in("WORLD_NETHER"));
        assertEquals(0, limits.in("world_the_end"), "a negative cap is read as none allowed");
    }

    @Test
    void aWorldWithoutOneHasNone() throws InvalidConfigurationException {
        assertEquals(-1, read("world-limits:\n  world_nether: 2\n").in("world"));
        assertEquals(-1, WorldLimits.read(null).in("world"));
    }

    private static WorldLimits read(String yaml) throws InvalidConfigurationException {
        YamlConfiguration data = new YamlConfiguration();
        data.loadFromString(yaml);
        return WorldLimits.read(data.getConfigurationSection("world-limits"));
    }
}
