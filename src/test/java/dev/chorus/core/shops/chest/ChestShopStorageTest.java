package dev.chorus.core.shops.chest;

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

class ChestShopStorageTest {

    private TempStorage storage;
    private ChestShopRepository repository;

    @BeforeEach
    void open() throws IOException, SQLException {
        storage = TempStorage.open();
        repository = new SqlChestShopRepository(storage);
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
    void aSavedShopComesBackWithARow() throws SQLException {
        ChestShop saved = repository.save(shop(UUID.randomUUID(), 10, 64, 20, 12.5, true));

        assertTrue(saved.id() > 0, "the row is what the delete later needs");
        List<ChestShop> all = repository.all();
        assertEquals(1, all.size());
        assertEquals(12.5, all.get(0).price(), 1e-9);
        assertTrue(all.get(0).selling());
    }

    @Test
    void twoShopsCannotShareOneBlock() throws SQLException {
        UUID owner = UUID.randomUUID();
        repository.save(shop(owner, 1, 64, 1, 10, true));

        boolean refused = false;
        try {
            repository.save(shop(UUID.randomUUID(), 1, 64, 1, 20, true));
        } catch (SQLException expected) {
            refused = true;
        }
        assertTrue(refused, "the block is the key, so a second shop on it must be refused");
    }

    @Test
    void updatingChangesTheShopRatherThanAddingOne() throws SQLException {
        ChestShop saved = repository.save(shop(UUID.randomUUID(), 5, 64, 5, 10, true));
        repository.update(saved.withPrice(99).withMode(false));

        List<ChestShop> all = repository.all();
        assertEquals(1, all.size());
        assertEquals(99, all.get(0).price(), 1e-9);
        assertFalse(all.get(0).selling());
    }

    @Test
    void deletingLeavesNothingBehind() throws SQLException {
        ChestShop saved = repository.save(shop(UUID.randomUUID(), 7, 64, 7, 10, true));
        repository.delete(saved.id());

        assertTrue(repository.all().isEmpty());
    }

    @Test
    void theUnlimitedFlagSurvives() throws SQLException {
        repository.save(new ChestShop(0, UUID.randomUUID(), "Admin", "world", 0, 64, 0,
                "item", 5, false, true, 1000));

        ChestShop stored = repository.all().get(0);
        assertTrue(stored.unlimited());
        assertFalse(stored.selling());
    }

    @Test
    void theBlockKeyIsBuiltTheSameWayEveryTime() {
        ChestShop shop = shop(UUID.randomUUID(), 1, 2, 3, 10, true);
        assertEquals(ChestShop.key("world", 1, 2, 3), shop.key());
    }

    private static ChestShop shop(UUID owner, int x, int y, int z, double price, boolean selling) {
        return new ChestShop(0, owner, "Owner", "world", x, y, z, "item", price, selling,
                false, System.currentTimeMillis());
    }
}
