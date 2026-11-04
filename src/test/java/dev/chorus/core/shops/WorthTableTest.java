package dev.chorus.core.shops;

import dev.chorus.core.command.PermissionValue;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** worth.yml: one price per item, or one price for a group of them. */
class WorthTableTest {

    private final List<String> problems = new ArrayList<>();

    @Test
    void anItemHasItsOwnPrice() throws InvalidConfigurationException {
        Map<Material, Double> prices = read("""
                worth:
                  diamond: 60.0
                  IRON_INGOT: 6
                """);

        assertEquals(60.0, prices.get(Material.DIAMOND));
        assertEquals(6.0, prices.get(Material.IRON_INGOT));
    }

    @Test
    void aGroupPricesEveryItemItNames() throws InvalidConfigurationException {
        Map<Material, Double> prices = read("""
                groups:
                  crops:
                    price: 1.0
                    items: [ wheat, carrot ]
                """);

        assertEquals(1.0, prices.get(Material.WHEAT));
        assertEquals(1.0, prices.get(Material.CARROT));
        assertEquals(2, prices.size());
    }

    @Test
    void aStarStandsForAnyPartOfTheName() throws InvalidConfigurationException {
        Map<Material, Double> prices = read("""
                groups:
                  logs:
                    price: 1.5
                    items: [ '*_log' ]
                """);

        assertEquals(1.5, prices.get(Material.OAK_LOG));
        assertEquals(1.5, prices.get(Material.SPRUCE_LOG));
        assertFalse(prices.containsKey(Material.OAK_PLANKS));
        assertTrue(prices.keySet().stream().allMatch(item -> item.name().endsWith("_LOG")));
    }

    @Test
    void anItemsOwnPriceWinsOverItsGroup() throws InvalidConfigurationException {
        Map<Material, Double> prices = read("""
                worth:
                  oak_log: 3.0
                groups:
                  logs:
                    price: 1.5
                    items: [ '*_log' ]
                """);

        assertEquals(3.0, prices.get(Material.OAK_LOG));
        assertEquals(1.5, prices.get(Material.BIRCH_LOG));
    }

    @Test
    void theFirstGroupToNameAnItemWins() throws InvalidConfigurationException {
        Map<Material, Double> prices = read("""
                groups:
                  oak:
                    price: 4.0
                    items: [ oak_log ]
                  logs:
                    price: 1.5
                    items: [ '*_log' ]
                """);

        assertEquals(4.0, prices.get(Material.OAK_LOG));
    }

    @Test
    void zeroMeansItCannotBeSoldEvenInAGroup() throws InvalidConfigurationException {
        Map<Material, Double> prices = read("""
                worth:
                  oak_log: 0
                groups:
                  logs:
                    price: 1.5
                    items: [ '*_log' ]
                """);

        assertFalse(prices.containsKey(Material.OAK_LOG));
        assertTrue(prices.containsKey(Material.BIRCH_LOG));
    }

    @Test
    void mistakesAreReportedAndSkipped() throws InvalidConfigurationException {
        Map<Material, Double> prices = read("""
                worth:
                  diamondd: 60.0
                groups:
                  nothing:
                    items: [ stone ]
                  typo:
                    price: 2.0
                    items: [ cobblestonee, '*_nothing_like_this' ]
                """);

        assertEquals(Map.of(), prices);
        assertEquals(4, problems.size(), problems.toString());
    }

    @Test
    void aGroupMultiplierCountsForItsItemsAndAnItemsOwnWins()
            throws InvalidConfigurationException {
        YamlConfiguration data = yaml("""
                groups:
                  logs:
                    price: 1.5
                    items: [ '*_log' ]
                item-multipliers:
                  logs: 2.0
                  oak_log: 0.5
                  diamond: 3
                """);

        Map<Material, Double> multipliers = WorthTable.itemMultipliers(data, problems::add);

        assertEquals(2.0, multipliers.get(Material.BIRCH_LOG));
        assertEquals(0.5, multipliers.get(Material.OAK_LOG));
        assertEquals(3.0, multipliers.get(Material.DIAMOND));
        assertEquals(List.of(), problems);
    }

    @Test
    void aMultiplierItCannotPlaceIsReported() throws InvalidConfigurationException {
        YamlConfiguration data = yaml("""
                item-multipliers:
                  nothing_at_all: 2.0
                  diamond: -1
                permission-multipliers:
                  - 'group.vip: 1.25'
                  - 'group.vip'
                  - 'group.bad: -2'
                """);

        assertEquals(Map.of(), WorthTable.itemMultipliers(data, problems::add));
        assertEquals(List.of(new PermissionValue("group.vip", 1.25)),
                WorthTable.permissionMultipliers(data, problems::add));
        assertEquals(4, problems.size(), problems.toString());
    }

    /** The file as it ships names nothing this API does not know. */
    @Test
    void theShippedFileReadsCleanly() throws Exception {
        YamlConfiguration shipped = new YamlConfiguration();
        shipped.loadFromString(Files.readString(Path.of("src", "main", "resources", "worth.yml")));

        Map<Material, Double> prices = WorthTable.read(shipped, problems::add);

        assertEquals(60.0, prices.get(Material.DIAMOND));
        assertEquals(1.5, prices.get(Material.DARK_OAK_LOG));
        assertEquals(Map.of(), WorthTable.itemMultipliers(shipped, problems::add));
        assertEquals(List.of(), WorthTable.permissionMultipliers(shipped, problems::add));
        assertEquals(List.of(), problems);
    }

    private Map<Material, Double> read(String text) throws InvalidConfigurationException {
        return WorthTable.read(yaml(text), problems::add);
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration data = new YamlConfiguration();
        data.loadFromString(text);
        return data;
    }
}
