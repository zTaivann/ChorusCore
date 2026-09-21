package dev.chorus.core.shops.chest;

import dev.chorus.core.backup.InventoryCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One player-run shop: a container, a sign on it, and a price.
 *
 * @param selling   true when the shop hands items to the player, false when it takes them
 * @param unlimited true for a shop with no stock and no till, which only an admin may make
 */
public record ChestShop(long id, UUID owner, String ownerName, String world, int x, int y, int z,
                        String item, double price, boolean selling, boolean unlimited,
                        long createdAt) {

    /** How a shop is looked up: one string per block, cheap to build and to compare. */
    public static String key(String world, int x, int y, int z) {
        return world + ':' + x + ':' + y + ':' + z;
    }

    public static String key(Block block) {
        return key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public String key() {
        return key(world, x, y, z);
    }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    /** Null when the world is gone, which happens on a server that drops one. */
    public @Nullable Location location() {
        World found = Bukkit.getWorld(world);
        return found == null ? null : new Location(found, x, y, z);
    }

    /** Null when the stored item cannot be read, which means the shop is unusable. */
    public @Nullable ItemStack template() {
        ItemStack[] decoded = InventoryCodec.decode(item);
        return decoded == null || decoded.length == 0 ? null : decoded[0];
    }

    public ChestShop withPrice(double updated) {
        return new ChestShop(id, owner, ownerName, world, x, y, z, item, updated, selling,
                unlimited, createdAt);
    }

    public ChestShop withMode(boolean nowSelling) {
        return new ChestShop(id, owner, ownerName, world, x, y, z, item, price, nowSelling,
                unlimited, createdAt);
    }

    public ChestShop withUnlimited(boolean nowUnlimited) {
        return new ChestShop(id, owner, ownerName, world, x, y, z, item, price, selling,
                nowUnlimited, createdAt);
    }
}
