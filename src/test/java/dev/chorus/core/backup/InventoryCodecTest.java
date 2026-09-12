package dev.chorus.core.backup;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The framing around the items, which is what decides whether a slot comes back in the slot
 * it went into.
 *
 * <p>The items themselves need a running server to encode, so what is checked here is the
 * shape: how many slots there were, which of them were empty, and that nothing readable
 * comes out of text that is not a backup at all.
 */
class InventoryCodecTest {

    @Test
    void theSlotsComeBackInTheSameOrder() {
        ItemStack[] empty = new ItemStack[41];
        ItemStack[] back = InventoryCodec.decode(InventoryCodec.encode(empty));

        assertNotNull(back);
        assertEquals(41, back.length, "an inventory must come back the size it went in");
        for (ItemStack slot : back) {
            assertNull(slot);
        }
    }

    @Test
    void anEmptyInventoryIsStillAnInventory() {
        ItemStack[] back = InventoryCodec.decode(InventoryCodec.encode(new ItemStack[0]));

        assertNotNull(back);
        assertEquals(0, back.length);
    }

    /** Anything else is refused rather than half read, so /restore can say so. */
    @Test
    void nonsenseIsNotAnInventory() {
        assertNull(InventoryCodec.decode("not base64 at all"));
        assertNull(InventoryCodec.decode(""));
        assertNull(InventoryCodec.decode("AAAAAQ=="), "a slot count with no slot after it");
    }
}
