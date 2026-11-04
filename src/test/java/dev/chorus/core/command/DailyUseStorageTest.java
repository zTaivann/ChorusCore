package dev.chorus.core.command;

import dev.chorus.core.storage.TempStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyUseStorageTest {

    private TempStorage storage;
    private DailyUseRepository repository;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        repository = new SqlDailyUseRepository(storage);
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
    void aSecondSaveReplacesTheFirst() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.save(new DailyUseRepository.Row(owner, "home", 1000, 1));
        repository.save(new DailyUseRepository.Row(owner, "home", 1000, 2));

        assertEquals(List.of(new DailyUseRepository.Row(owner, "home", 1000, 2)),
                repository.findSince(0));
    }

    @Test
    void onlyRecentDaysAreReadBack() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.save(new DailyUseRepository.Row(owner, "home", 1000, 1));
        repository.save(new DailyUseRepository.Row(owner, "group:travel", 5000, 3));

        assertEquals(List.of(new DailyUseRepository.Row(owner, "group:travel", 5000, 3)),
                repository.findSince(2000));
    }

    @Test
    void oldDaysAreDeleted() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.save(new DailyUseRepository.Row(owner, "home", 1000, 1));
        repository.save(new DailyUseRepository.Row(owner, "warp", 5000, 1));

        assertEquals(1, repository.deleteBefore(2000));
        assertEquals(1, repository.findSince(0).size());
    }
}
