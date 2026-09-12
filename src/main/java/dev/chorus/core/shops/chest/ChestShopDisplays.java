package dev.chorus.core.shops.chest;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The item turning slowly above a shop, so you can see what it sells without reading the sign.
 *
 * <p>It is a dropped item with everything that makes a dropped item behave taken away: no
 * gravity, no ageing, no despawning, no picking up, no merging with the one over the shop next
 * door, and no writing to the world file. That last one matters most. An item that is never
 * saved cannot be left behind by a crash, so there is no way for these to build up into a
 * field of floating diamonds that nobody can explain.
 *
 * <p>Because they are never saved they also go when a chunk unloads, and come back when it
 * loads again.
 */
public final class ChestShopDisplays {

    /** Roughly a block above the lid, which is where the eye expects it. */
    private static final double HEIGHT = 1.2;
    private static final String MARKER = "chorus-shop-display";

    private final Plugin plugin;
    private final ChestShops shops;
    private final Map<String, UUID> shown = new HashMap<>();

    private volatile boolean enabled = true;

    public ChestShopDisplays(Plugin plugin, ChestShops shops) {
        this.plugin = plugin;
        this.shops = shops;
    }

    public void apply(boolean displayEnabled) {
        if (this.enabled == displayEnabled) {
            return;
        }
        this.enabled = displayEnabled;
        if (displayEnabled) {
            showEverything();
        } else {
            clear();
        }
    }

    /** True for the floating item of a shop, and nothing else. */
    public boolean isDisplay(Entity entity) {
        return entity instanceof Item && entity.hasMetadata(MARKER);
    }

    /** Puts one above a shop, replacing whatever was there. */
    public void show(ChestShop shop) {
        if (!enabled) {
            return;
        }
        Location where = displayPoint(shop);
        ItemStack template = shop.template();
        if (where == null || template == null || !where.getChunk().isLoaded()) {
            return;
        }

        hide(shop.key());
        sweepStrays(where);

        ItemStack showing = template.clone();
        showing.setAmount(1);

        Item item = where.getWorld().dropItem(where, showing);
        item.setGravity(false);
        item.setVelocity(new Vector());
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setUnlimitedLifetime(true);
        item.setWillAge(false);
        item.setCanMobPickup(false);
        item.setInvulnerable(true);
        item.setSilent(true);
        // Never written to the world file, which is what stops a crash leaving one behind.
        item.setPersistent(false);
        item.setMetadata(MARKER, new FixedMetadataValue(plugin, shop.key()));

        shown.put(shop.key(), item.getUniqueId());
    }

    public void hide(String shopKey) {
        UUID id = shown.remove(shopKey);
        if (id == null) {
            return;
        }
        Entity entity = plugin.getServer().getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    /** Every shop in a chunk that has just come back. */
    public void showIn(Chunk chunk) {
        if (!enabled) {
            return;
        }
        for (ChestShop shop : shopsIn(chunk)) {
            show(shop);
        }
    }

    /** The server takes the entities with the chunk, so only the record has to go. */
    public void forgetIn(Chunk chunk) {
        for (ChestShop shop : shopsIn(chunk)) {
            shown.remove(shop.key());
        }
    }

    public void showEverything() {
        if (!enabled) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                showIn(chunk);
            }
        }
    }

    public void clear() {
        for (UUID id : List.copyOf(shown.values())) {
            Entity entity = plugin.getServer().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        shown.clear();
    }

    /**
     * Anything left over from a reload that did not shut down cleanly.
     *
     * <p>Only items carrying the marker are touched, so a player who happens to have dropped
     * something on top of a shop keeps it.
     */
    private void sweepStrays(Location where) {
        for (Entity entity : where.getWorld().getNearbyEntities(where, 0.6, 0.6, 0.6)) {
            if (entity instanceof Item && entity.hasMetadata(MARKER)) {
                entity.remove();
            }
        }
    }

    private List<ChestShop> shopsIn(Chunk chunk) {
        List<ChestShop> found = new ArrayList<>();
        String world = chunk.getWorld().getName();

        for (ChestShop shop : shops.all()) {
            if (shop.world().equals(world)
                    && shop.x() >> 4 == chunk.getX()
                    && shop.z() >> 4 == chunk.getZ()) {
                found.add(shop);
            }
        }
        return found;
    }

    private static @Nullable Location displayPoint(ChestShop shop) {
        Location where = shop.location();
        if (where == null) {
            return null;
        }
        Block block = where.getBlock();
        return block.getLocation().add(0.5, HEIGHT, 0.5);
    }
}
