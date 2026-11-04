package dev.chorus.core.command;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code price-item}, however it is written. */
class ItemPriceTest {

    private static final ItemPrice BASE = new ItemPrice(Material.EMERALD, 1);

    private final List<String> problems = new ArrayList<>();

    @Test
    void anItemAndHowManyAreRead() throws InvalidConfigurationException {
        assertEquals(new ItemPrice(Material.DIAMOND, 2), read("price-item: 'diamond 2'"));
        assertEquals(new ItemPrice(Material.IRON_INGOT, 1), read("price-item: IRON_INGOT"));
        assertTrue(problems.isEmpty());
    }

    @Test
    void emptyMeansNoItems() throws InvalidConfigurationException {
        assertTrue(read("price-item: ''").none());
    }

    @Test
    void missingKeepsWhatTheDefaultsSay() throws InvalidConfigurationException {
        assertSame(BASE, read("price: 5"));
    }

    @Test
    void somethingItCannotReadKeepsTheDefaultAndIsReported() throws InvalidConfigurationException {
        assertSame(BASE, read("price-item: 'nothing 2'"));
        assertSame(BASE, read("price-item: 'diamond none'"));
        assertSame(BASE, read("price-item: 'diamond 0'"));
        assertSame(BASE, read("price-item: 'air 1'"));
        assertSame(BASE, read("price-item: 'diamond 2 3'"));
        assertEquals(5, problems.size());
    }

    @Test
    void itIsNamedTheWayOtherCommandsNameItems() {
        assertEquals("iron_ingot", new ItemPrice(Material.IRON_INGOT, 3).itemName());
    }

    private ItemPrice read(String yaml) throws InvalidConfigurationException {
        YamlConfiguration block = new YamlConfiguration();
        block.loadFromString(yaml);
        return ItemPrice.read(block, BASE, "/home", problems::add);
    }
}
