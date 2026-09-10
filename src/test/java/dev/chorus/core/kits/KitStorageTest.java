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

        Map<String, KitRepository.Use> uses = repository.findUses(owner);
        assertEquals(2, uses.size());
        assertEquals(3000L, uses.get("daily").lastTaken(),
                "the cooldown should run from the most recent take");
        assertEquals(2000L, uses.get("starter").lastTaken());
    }

    /** What max-claims is counted against, so it has to survive a restart. */
    @Test
    void everyClaimIsCounted() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.markUsed(owner, "daily", 1000);
        assertEquals(1, repository.findUses(owner).get("daily").times());

        repository.markUsed(owner, "daily", 2000);
        repository.markUsed(owner, "daily", 3000);
        assertEquals(3, repository.findUses(owner).get("daily").times(),
                "the count is kept by the database, so two claims at once cannot lose one");
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
    void clearingStartsTheCountAgain() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.markUsed(owner, "daily", 1000);
        repository.markUsed(owner, "daily", 2000);
        repository.clear(owner, "daily");

        repository.markUsed(owner, "daily", 3000);
        assertEquals(1, repository.findUses(owner).get("daily").times(),
                "a reset should give the kit back rather than leave the count where it was");
    }

    @Test
    void oneOwnersHistoryIsNotAnothers() throws SQLException {
        UUID first = UUID.randomUUID();
        repository.markUsed(first, "daily", 1000);

        assertTrue(repository.findUses(UUID.randomUUID()).isEmpty());
    }
}
