package dev.chorus.core.kits;

import dev.chorus.core.storage.TempStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitStorageTest {

    private TempStorage storage;
    private KitRepository repository;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        repository = new SqlKitRepository(storage);
        repository.createTables();
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void takingAKitTwiceKeepsOnlyTheLatestTime() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.markUsed(owner, "daily", 1000);
        repository.markUsed(owner, "starter", 2000);
        repository.markUsed(owner, "daily", 3000);

        Map<String, Long> uses = repository.findUses(owner);
        assertEquals(2, uses.size());
        assertEquals(3000L, uses.get("daily"), "the cooldown should run from the most recent take");
        assertEquals(2000L, uses.get("starter"));
    }

    @Test
    void clearingReportsWhetherAnythingWent() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.markUsed(owner, "daily", 1000);

        assertTrue(repository.clear(owner, "daily"));
        assertFalse(repository.clear(owner, "daily"));
        assertTrue(repository.findUses(owner).isEmpty());
    }

    @Test
    void oneOwnersHistoryIsNotAnothers() throws SQLException {
        UUID first = UUID.randomUUID();
        repository.markUsed(first, "daily", 1000);

        assertTrue(repository.findUses(UUID.randomUUID()).isEmpty());
    }
}
