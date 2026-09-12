package dev.chorus.core.backup;

import dev.chorus.core.storage.TempStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A restore left for an offline player has to be applied once and once only. Handing
 * somebody the same inventory twice is a duplication bug with extra steps.
 */
class PendingRestoreStorageTest {

    private TempStorage storage;
    private BackupRepository repository;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        repository = new SqlBackupRepository(storage);
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
    void nothingIsWaitingForSomebodyWithNothingWaiting() throws SQLException {
        assertNull(repository.takeWaiting(UUID.randomUUID()));
    }

    @Test
    void takingItLeavesNothingBehind() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.queue(owner, 42, "INVENTORY,STATS", "Notch");

        BackupRepository.Waiting waiting = repository.takeWaiting(owner);
        assertNotNull(waiting);
        assertEquals(42, waiting.snapshot());
        assertEquals("INVENTORY,STATS", waiting.parts());
        assertEquals("Notch", waiting.actor());

        assertNull(repository.takeWaiting(owner), "it must only ever be applied once");
    }

    @Test
    void aSecondQueueReplacesTheFirst() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.queue(owner, 1, "INVENTORY", "One");
        repository.queue(owner, 2, "ENDER_CHEST", "Two");

        BackupRepository.Waiting waiting = repository.takeWaiting(owner);
        assertNotNull(waiting);
        assertEquals(2, waiting.snapshot(), "the newest decision should be the one that stands");
        assertNull(repository.takeWaiting(owner), "only one can ever be waiting");
    }

    @Test
    void onePlayersRestoreIsNotAnothers() throws SQLException {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.queue(first, 1, "INVENTORY", "Notch");

        assertNull(repository.takeWaiting(second));
        assertNotNull(repository.takeWaiting(first));
    }

    @Test
    void aBackupSurvivesBeingWrittenAndRead() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.save(new InventorySnapshot(0, owner, 1234, "death", "Notch",
                "contents", "ender", 30, 0.5f, 14, 18, "world", 1, 64, -2,
                "fall", "Herobrine"));

        InventorySnapshot found = repository.findFor(owner, 10).get(0);
        assertEquals("death", found.reason());
        assertEquals(30, found.level());
        assertEquals(14, found.health(), 1e-9);
        assertEquals(18, found.food());
        assertEquals("fall", found.cause());
        assertEquals("Herobrine", found.killer());
        assertEquals("ender", found.enderChest());
    }
}
