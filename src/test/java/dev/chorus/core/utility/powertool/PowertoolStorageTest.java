package dev.chorus.core.utility.powertool;

import dev.chorus.core.storage.TempStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowertoolStorageTest {

    private TempStorage storage;
    private PowertoolRepository repository;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        repository = new SqlPowertoolRepository(storage);
        repository.createTables();
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void creatingTablesTwiceIsFine() throws SQLException {
        repository.createTables();
    }

    @Test
    void somebodyWithNothingBoundHasNothing() throws SQLException {
        assertEquals(Map.of(), repository.findAll(UUID.randomUUID()));
    }

    @Test
    void severalCommandsOnOneItemComeBackInOrder() throws SQLException {
        UUID player = UUID.randomUUID();
        repository.save(player, "stick", List.of("heal", "feed %player%", "smite %player%"));

        assertEquals(List.of("heal", "feed %player%", "smite %player%"),
                repository.findAll(player).get("stick"));
    }

    @Test
    void savingTheSameItemTwiceReplacesIt() throws SQLException {
        UUID player = UUID.randomUUID();
        repository.save(player, "stick", List.of("heal"));
        repository.save(player, "stick", List.of("feed"));

        Map<String, List<String>> bound = repository.findAll(player);
        assertEquals(1, bound.size(), "the second save should not have made a second row");
        assertEquals(List.of("feed"), bound.get("stick"));
    }

    @Test
    void onePlayersToolsAreNotAnothers() throws SQLException {
        UUID one = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        repository.save(one, "stick", List.of("heal"));
        repository.save(other, "stick", List.of("feed"));

        assertEquals(List.of("heal"), repository.findAll(one).get("stick"));
        assertEquals(List.of("feed"), repository.findAll(other).get("stick"));
    }

    @Test
    void deletingOneItemLeavesTheRest() throws SQLException {
        UUID player = UUID.randomUUID();
        repository.save(player, "stick", List.of("heal"));
        repository.save(player, "bone", List.of("feed"));

        repository.delete(player, "stick");

        Map<String, List<String>> bound = repository.findAll(player);
        assertEquals(1, bound.size());
        assertTrue(bound.containsKey("bone"));
    }

    @Test
    void clearingTakesEverythingOfTheirsAndNobodyElses() throws SQLException {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        repository.save(player, "stick", List.of("heal"));
        repository.save(player, "bone", List.of("feed"));
        repository.save(other, "stick", List.of("heal"));

        repository.deleteAll(player);

        assertEquals(Map.of(), repository.findAll(player));
        assertEquals(1, repository.findAll(other).size(), "the other player should be untouched");
    }
}
