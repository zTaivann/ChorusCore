package dev.chorus.core.backup;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * A player's inventory as one string, and back again.
 *
 * <p>Each slot is written with {@code serializeAsBytes}, which keeps the whole item: the
 * enchantments, the lore, the custom model data, and whatever another plugin wrote onto it.
 * A backup exists to give somebody exactly what they lost.
 *
 * <p>Unlike the kits, which are written in a readable form so they can be edited by hand,
 * these are never edited. They are taken, kept for a fortnight and handed back.
 */
public final class InventoryCodec {

    /** An empty slot. Written as a length nothing else can produce. */
    private static final int EMPTY = -1;

    private InventoryCodec() {
    }

    public static String encode(ItemStack[] contents) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(contents.length * 64);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                if (item == null || item.getType().isAir()) {
                    out.writeInt(EMPTY);
                    continue;
                }
                byte[] written = item.serializeAsBytes();
                out.writeInt(written.length);
                out.write(written);
            }
        } catch (IOException impossible) {
            // Writing to memory. Nothing here can fail without the JVM already having.
            throw new IllegalStateException(impossible);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    /**
     * @return the slots, or null when the text cannot be read as an inventory. An item form
     *         the running version no longer understands is the way that happens.
     */
    public static ItemStack @Nullable [] decode(String encoded) {
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            ItemStack[] contents = new ItemStack[in.readInt()];
            for (int slot = 0; slot < contents.length; slot++) {
                int length = in.readInt();
                if (length == EMPTY) {
                    continue;
                }
                contents[slot] = ItemStack.deserializeBytes(in.readNBytes(length));
            }
            return contents;
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }
}
