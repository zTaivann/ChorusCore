package dev.chorus.core.players;

import dev.chorus.core.storage.TempStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProfileStorageTest {

    private TempStorage storage;
    private PlayerProfileRepository repository;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        repository = new SqlPlayerProfileRepository(storage);
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
    void aSecondVisitUpdatesRatherThanDuplicates() throws SQLException {
        UUID player = UUID.randomUUID();
        repository.seen(player, "Notch", "1.2.3.4", 1000);
        repository.seen(player, "Notch", "5.6.7.8", 2000);

        PlayerProfile profile = repository.find(player);
        assertNotNull(profile);
        assertEquals("5.6.7.8", profile.address(), "the newest address should win");
        assertEquals(1000, profile.firstSeen(), "the first visit must not move");
        assertEquals(2000, profile.lastSeen());
    }

    @Test
    void aRenamedPlayerIsFoundByTheirNewName() throws SQLException {
        UUID player = UUID.randomUUID();
        repository.seen(player, "Before", "", 1000);
        repository.seen(player, "After", "", 2000);

        assertNotNull(repository.findByName("after"));
        assertNull(repository.findByName("before"), "the old name should no longer match");
    }

    @Test
    void namesAreMatchedWhateverTheCase() throws SQLException {
        UUID player = UUID.randomUUID();
        repository.seen(player, "Notch", "", 1000);

        assertNotNull(repository.findByName("NOTCH"));
        assertNotNull(repository.findByName("notch"));
    }

    @Test
    void whereTheyLoggedOutComesBack() throws SQLException {
        UUID player = UUID.randomUUID();
        repository.seen(player, "Notch", "", 1000);
        repository.left(player, 3000, "world_nether", 1.5, 64, -2.5, 90f, 10f);

        PlayerProfile profile = repository.find(player);
        assertNotNull(profile);
        assertEquals("world_nether", profile.world());
        assertEquals(1.5, profile.x(), 1e-9);
        assertEquals(-2.5, profile.z(), 1e-9);
        assertEquals(3000, profile.lastSeen());
    }

    @Test
    void aNicknameIsFoundAndCanBeTakenBack() throws SQLException {
        UUID player = UUID.randomUUID();
        repository.seen(player, "Notch", "", 1000);
        repository.nickname(player, "&aKing");

        PlayerProfile profile = repository.findByName("king");
        assertNotNull(profile, "a nickname should be matched on its letters, not its codes");
        assertEquals("&aKing", profile.nickname());

        repository.nickname(player, null);
        assertNull(repository.find(player).nickname());
    }

    @Test
    void sharedAddressesFindEachOtherButNotTheAsker() throws SQLException {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID elsewhere = UUID.randomUUID();
        repository.seen(first, "One", "1.2.3.4", 1000);
        repository.seen(second, "Two", "1.2.3.4", 2000);
        repository.seen(elsewhere, "Three", "9.9.9.9", 3000);

        List<String> shared = repository.sharing("1.2.3.4", first);
        assertEquals(List.of("Two"), shared);
    }

    @Test
    void anEmptyAddressSharesWithNobody() throws SQLException {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.seen(first, "One", "", 1000);
        repository.seen(second, "Two", "", 2000);

        assertTrue(repository.sharing("", first).isEmpty());
    }

    @Test
    void namesForCompletionComeBackNewestFirst() throws SQLException {
        repository.seen(UUID.randomUUID(), "Notch", "", 1000);
        repository.seen(UUID.randomUUID(), "Notchy", "", 2000);
        repository.seen(UUID.randomUUID(), "Somebody", "", 3000);

        List<String> found = repository.namesLike("not", 10);
        assertEquals(List.of("Notchy", "Notch"), found);
    }
}
