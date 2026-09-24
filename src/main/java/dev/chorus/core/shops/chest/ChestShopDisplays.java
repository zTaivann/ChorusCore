package dev.chorus.core.shops.chest;

import dev.chorus.core.platform.Schedulers;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The item turning slowly above a shop, so you can see what it sells without reading the sign. */
public final class ChestShopDisplays {

    /** Roughly a block above the lid, which is where the eye expects it. */
    private static final double HEIGHT = 1.2;
    private static final String MARKER = "chorus-shop-display";

    private final Plugin plugin;
    private final ChestShops shops;
    private final Schedulers schedulers;

    /** Where each one is as well as which one it is, since removing it needs both. */
    private final Map<String, Shown> shown = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;

    public ChestShopDisplays(Plugin plugin, ChestShops shops, Schedulers schedulers) {
        this.plugin = plugin;
        this.shops = shops;
        this.schedulers = schedulers;
    }

    /** An item that is out there, and the place whose thread is allowed to touch it. */
    private record Shown(UUID id, Location where) {
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

    /** True for the floating item of a shop. Gravity first: nearly no other item has it off. */
    public boolean isDisplay(Entity entity) {
        return entity instanceof Item item && !item.hasGravity() && item.hasMetadata(MARKER);
    }

    /** Puts one above a shop, replacing whatever was there. */
    public void show(ChestShop shop) {
        if (!enabled) {
            return;
        }
        Location where = displayPoint(shop);
        ItemStack template = shop.template();
        if (where == null || template == null || !isLoaded(where)) {
            return;
        }

        hide(shop.key());
        // Spawning belongs to whoever owns those blocks, which on Folia is another thread.
        schedulers.region(where, () -> spawn(shop.key(), where, template));
    }

    private void spawn(String key, Location where, ItemStack template) {
        if (!enabled || !isLoaded(where)) {
            return;
        }
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
        item.setMetadata(MARKER, new FixedMetadataValue(plugin, key));

        shown.put(key, new Shown(item.getUniqueId(), where));
    }

    public void hide(String shopKey) {
        remove(shown.remove(shopKey));
    }

    private void remove(@Nullable Shown display) {
        if (display == null) {
            return;
        }
        schedulers.region(display.where(), () -> {
            Entity entity = plugin.getServer().getEntity(display.id());
            if (entity != null) {
                entity.remove();
            }
        });
    }

    /** Every shop in a chunk that has just come back. */
    public void showIn(Chunk chunk) {
        if (!enabled) {
            return;
        }
        for (ChestShop shop : in(chunk)) {
            show(shop);
        }
    }

    /** The server takes the entities with the chunk, so only the record has to go. */
    public void forgetIn(Chunk chunk) {
        for (ChestShop shop : in(chunk)) {
            shown.remove(shop.key());
        }
    }

    public void showEverything() {
        if (!enabled || shops.isEmpty()) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                showIn(chunk);
            }
        }
    }

    /** Takes them all away, for when the setting is switched off while the server is up. */
    public void clear() {
        for (String key : List.copyOf(shown.keySet())) {
            hide(key);
        }
    }

    /** Forgets them without removing them, for shutdown. */
    public void forgetAll() {
        shown.clear();
    }

    /** Every shop standing in a chunk, for whoever has to walk them all. */
    public List<ChestShop> in(Chunk chunk) {
        if (shops.isEmpty()) {
            return List.of();
        }
        return shops.in(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    /** Anything left over from a reload that did not shut down cleanly. */
    private void sweepStrays(Location where) {
        for (Entity entity : where.getWorld().getNearbyEntities(where, 0.6, 0.6, 0.6)) {
            if (isDisplay(entity)) {
                entity.remove();
            }
        }
    }

    /** Worked out from the numbers, so looking at an unloaded shop never loads its chunk. */
    private static @Nullable Location displayPoint(ChestShop shop) {
        Location where = shop.location();
        return where == null ? null : where.add(0.5, HEIGHT, 0.5);
    }

    private static boolean isLoaded(Location where) {
        return where.getWorld().isChunkLoaded(where.getBlockX() >> 4, where.getBlockZ() >> 4);
    }
}
