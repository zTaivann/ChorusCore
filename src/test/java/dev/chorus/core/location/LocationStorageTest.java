package dev.chorus.core.location;

import dev.chorus.core.storage.TempStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationStorageTest {

    private TempStorage storage;
    private LocationRepository repository;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        repository = new SqlLocationRepository(storage);
        repository.createTables();
    }

    @AfterEach
    void close() {
        storage.close();
    }

    /** Warps and spawns share one table, so a warp must never be able to shadow a spawn. */
    @Test
    void categoriesStayApart() throws SQLException {
        UUID world = UUID.randomUUID();
        repository.save("warp", new NamedLocation("shop", world, "world", 0, 64, 0, 0f, 0f, 1));
        repository.save("warp", new NamedLocation("pvp", world, "world", 10, 64, 10, 0f, 0f, 1));
        repository.save("system", new NamedLocation("spawn", world, "world", 5, 70, 5, 0f, 0f, 1));
        repository.save("warp", new NamedLocation("spawn", world, "world", 99, 99, 99, 0f, 0f, 1));

        assertEquals(3, repository.findAll("warp").size());
        assertEquals(1, repository.findAll("system").size());

        NamedLocation spawn = repository.findAll("system").get(0);
        assertEquals(5, spawn.x(), "the warp called 'spawn' overwrote the real spawn");
        assertEquals(70, spawn.y());

        assertTrue(repository.delete("warp", "spawn"));
        assertEquals(1, repository.findAll("system").size(), "deleting a warp took the spawn with it");
        assertFalse(repository.delete("warp", "nope"));
    }
}
