package dev.chorus.core.mail;

import dev.chorus.core.storage.TempStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailStorageTest {

    private TempStorage storage;
    private MailRepository repository;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        repository = new SqlMailRepository(storage);
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
    void lettersComeBackNewestFirst() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.send(owner, "One", "first", 1000);
        repository.send(owner, "Two", "second", 2000);

        List<Mail> inbox = repository.inbox(owner);
        assertEquals(2, inbox.size());
        assertEquals("second", inbox.get(0).body(), "the newest letter should be first");
    }

    @Test
    void oneInboxIsNotAnother() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.send(owner, "Sender", "for you", 1000);
        repository.send(UUID.randomUUID(), "Sender", "for somebody else", 1000);

        assertEquals(1, repository.inbox(owner).size());
    }

    @Test
    void readingMarksEverythingSeen() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.send(owner, "Sender", "one", 1000);
        repository.send(owner, "Sender", "two", 2000);
        assertEquals(2, repository.unread(owner));

        repository.markRead(owner);
        assertEquals(0, repository.unread(owner));
        assertTrue(repository.inbox(owner).get(0).read());
    }

    @Test
    void clearingOneLeavesTheRest() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.send(owner, "Sender", "keep", 1000);
        repository.send(owner, "Sender", "drop", 2000);

        long id = repository.inbox(owner).get(0).id();
        assertTrue(repository.clear(owner, id));
        assertFalse(repository.clear(owner, id), "a second clear should report nothing");

        List<Mail> left = repository.inbox(owner);
        assertEquals(1, left.size());
        assertEquals("keep", left.get(0).body());
    }

    @Test
    void clearingSaysHowManyWent() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.send(owner, "Sender", "one", 1000);
        repository.send(owner, "Sender", "two", 2000);

        assertEquals(2, repository.clear(owner));
        assertTrue(repository.inbox(owner).isEmpty());
    }

    @Test
    void pruningTakesTheOldAndLeavesTheNew() throws SQLException {
        UUID owner = UUID.randomUUID();
        long now = System.currentTimeMillis();
        repository.send(owner, "Sender", "old", now - TimeUnit.DAYS.toMillis(40));
        repository.send(owner, "Sender", "new", now);

        repository.prune(30);
        List<Mail> left = repository.inbox(owner);
        assertEquals(1, left.size());
        assertEquals("new", left.get(0).body());
    }

    @Test
    void keepingForEverPrunesNothing() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.send(owner, "Sender", "old", 1000);

        repository.prune(0);
        assertEquals(1, repository.inbox(owner).size());
    }
}
