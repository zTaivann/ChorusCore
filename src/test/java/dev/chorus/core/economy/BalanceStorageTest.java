package dev.chorus.core.economy;

import dev.chorus.core.storage.TempStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ledger is money, so the table under it is worth checking properly. */
class BalanceStorageTest {

    private TempStorage storage;
    private BalanceRepository repository;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        repository = new SqlBalanceRepository(storage);
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
    void savingTheSameAccountTwiceReplacesIt() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.save(owner, "Notch", 100);
        repository.save(owner, "Notch", 250.75);

        List<BalanceRepository.Account> all = repository.all();
        assertEquals(1, all.size(), "one player should have one account");
        assertEquals(250.75, all.get(0).balance(), 1e-9);
    }

    @Test
    void aRenamedPlayerKeepsTheirMoney() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.save(owner, "Before", 500);
        repository.save(owner, "After", 500);

        List<BalanceRepository.Account> all = repository.all();
        assertEquals(1, all.size());
        assertEquals("After", all.get(0).name());
        assertEquals(500, all.get(0).balance(), 1e-9);
    }

    @Test
    void decimalsSurviveTheRoundTrip() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.save(owner, "Notch", 0.01);

        assertEquals(0.01, repository.all().get(0).balance(), 1e-9);
    }

    @Test
    void deletingLeavesNothingBehind() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.save(owner, "Notch", 10);
        repository.delete(owner);

        assertTrue(repository.all().isEmpty());
    }

    @Test
    void accountsAreKeptApart() throws SQLException {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.save(first, "One", 10);
        repository.save(second, "Two", 20);

        assertEquals(2, repository.all().size());
    }
}
