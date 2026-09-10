package dev.chorus.core.home;

import dev.chorus.core.storage.TempStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeStorageTest {

    private TempStorage storage;
    private HomeRepository repository;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        repository = new SqlHomeRepository(storage);
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
    void savingTheSameNameMovesTheHomeRatherThanDuplicatingIt() throws SQLException {
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        long created = System.currentTimeMillis();

        repository.save(new Home(owner, "home", world, "world", 1.5, 64, -2.5, 90f, 0f, created, null));
        repository.save(new Home(owner, "mine", world, "world_nether", 8, 31, 8, 0f, 12f, created, "COMPASS"));
        repository.save(new Home(owner, "home", world, "world", 100, 70, 200, 45f, 5f, created + 1000, "BEACON"));

        List<Home> stored = repository.findByOwner(owner);
        assertEquals(2, stored.size(), "the second save of 'home' should have moved it");

        Home home = stored.stream().filter(each -> each.name().equals("home")).findFirst().orElseThrow();
        assertEquals(100, home.x());
        assertEquals(200, home.z());
        assertEquals(world, home.worldId(), "the world id should round-trip");
        assertEquals(created, home.createdAt(), "moving a home must not reset when it was made");
        assertEquals("BEACON", home.icon(), "the icon column should round-trip");
    }

    @Test
    void deletingReportsWhetherAnythingWent() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.save(new Home(owner, "home", UUID.randomUUID(), "world", 0, 64, 0, 0f, 0f, 1, null));

        assertTrue(repository.delete(owner, "home"));
        assertFalse(repository.delete(owner, "home"), "a second delete should report nothing");
        assertTrue(repository.findByOwner(owner).isEmpty());
    }

    @Test
    void anUnknownOwnerHasNoHomes() throws SQLException {
        assertTrue(repository.findByOwner(UUID.randomUUID()).isEmpty());
    }
}
