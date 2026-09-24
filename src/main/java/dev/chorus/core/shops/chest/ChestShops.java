package dev.chorus.core.shops.chest;

import dev.chorus.core.backup.InventoryCodec;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Every chest shop on the server, kept in memory. */
public final class ChestShops {

    private final ChestShopRepository repository;
    private final Executor worker;
    private final Logger logger;

    private final Map<String, ChestShop> byBlock = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> byChunk = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> counts = new ConcurrentHashMap<>();

    public ChestShops(ChestShopRepository repository, Executor worker, Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
    }

    /** Blocking, once, at startup. */
    public void load() throws SQLException {
        clear();
        for (ChestShop shop : repository.all()) {
            add(shop);
        }
    }

    public int size() {
        return byBlock.size();
    }

    public boolean isEmpty() {
        return byBlock.isEmpty();
    }

    public int ownedBy(UUID player) {
        return counts.getOrDefault(player, 0);
    }

    /** Every shop, for the things that have to walk all of them. */
    public Collection<ChestShop> all() {
        return List.copyOf(byBlock.values());
    }

    public List<ChestShop> allOwnedBy(UUID player) {
        List<ChestShop> owned = new ArrayList<>();
        for (ChestShop shop : byBlock.values()) {
            if (shop.isOwner(player)) {
                owned.add(shop);
            }
        }
        return owned;
    }

    /** Every shop standing in one chunk. */
    public List<ChestShop> in(String world, int chunkX, int chunkZ) {
        Set<String> keys = byChunk.get(chunkKey(world, chunkX, chunkZ));
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<ChestShop> found = new ArrayList<>(keys.size());
        for (String key : keys) {
            ChestShop shop = byBlock.get(key);
            if (shop != null) {
                found.add(shop);
            }
        }
        return found;
    }

    /** The shop at a block, whichever part of it was touched. */
    public @Nullable ChestShop at(Block block) {
        if (byBlock.isEmpty()) {
            return null;
        }
        ChestShop direct = byBlock.get(ChestShop.key(block));
        if (direct != null) {
            return direct;
        }

        Block holder = ShopContainers.holderOf(block);
        if (holder != null) {
            ChestShop onHolder = byBlock.get(ChestShop.key(holder));
            if (onHolder != null) {
                return onHolder;
            }
            Block holderHalf = ShopContainers.otherHalf(holder);
            return holderHalf == null ? null : byBlock.get(ChestShop.key(holderHalf));
        }

        Block half = ShopContainers.otherHalf(block);
        return half == null ? null : byBlock.get(ChestShop.key(half));
    }

    /** Only the block the shop is registered on, for the checks that must not wander. */
    public @Nullable ChestShop exactlyAt(Block block) {
        return byBlock.get(ChestShop.key(block));
    }

    public boolean isShopBlock(Block block) {
        return at(block) != null;
    }

    public ChestShop create(Player owner, Block container, ItemStack item, double price,
                            boolean selling, boolean unlimited) {
        ChestShop shop = new ChestShop(0, owner.getUniqueId(), owner.getName(),
                container.getWorld().getName(), container.getX(), container.getY(),
                container.getZ(), InventoryCodec.encode(new ItemStack[] {item}), price,
                selling, unlimited, System.currentTimeMillis());
        add(shop);

        worker.execute(() -> {
            try {
                ChestShop saved = repository.save(shop);
                ChestShop now = byBlock.computeIfPresent(shop.key(), (key, current) ->
                        isSameShop(current, shop) ? current.withId(saved.id()) : current);
                // Taken down before its row existed, so the row goes as well.
                if (now == null || !isSameShop(now, shop)) {
                    repository.delete(saved.id());
                }
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not save the shop of " + owner.getName(), exception);
            }
        });
        return shop;
    }

    public void replace(ChestShop shop) {
        byBlock.computeIfPresent(shop.key(), (key, current) -> shop.withId(current.id()));
        worker.execute(() -> {
            // The latest state by the time the storage thread gets here, with its row.
            ChestShop current = byBlock.get(shop.key());
            if (current == null || current.id() == 0) {
                return;
            }
            try {
                repository.update(current);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not update the shop at " + shop.key(), exception);
            }
        });
    }

    public void remove(ChestShop shop) {
        ChestShop removed = byBlock.remove(shop.key());
        if (removed == null) {
            return;
        }
        forgetPlace(removed);
        counts.computeIfPresent(removed.owner(), (owner, count) -> count <= 1 ? null : count - 1);

        long id = removed.id();
        if (id == 0) {
            return;
        }
        worker.execute(() -> {
            try {
                repository.delete(id);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not delete the shop at " + shop.key(), exception);
            }
        });
    }

    public void clear() {
        byBlock.clear();
        byChunk.clear();
        counts.clear();
    }

    private void add(ChestShop shop) {
        byBlock.put(shop.key(), shop);
        byChunk.computeIfAbsent(chunkKey(shop.world(), shop.x() >> 4, shop.z() >> 4),
                chunk -> ConcurrentHashMap.newKeySet()).add(shop.key());
        counts.merge(shop.owner(), 1, Integer::sum);
    }

    private void forgetPlace(ChestShop shop) {
        byChunk.computeIfPresent(chunkKey(shop.world(), shop.x() >> 4, shop.z() >> 4),
                (chunk, keys) -> {
                    keys.remove(shop.key());
                    return keys.isEmpty() ? null : keys;
                });
    }

    /** The same shop at a later moment, as opposed to another one made on the same block. */
    private static boolean isSameShop(ChestShop one, ChestShop other) {
        return one.createdAt() == other.createdAt() && one.owner().equals(other.owner());
    }

    private static String chunkKey(String world, int chunkX, int chunkZ) {
        return world + ':' + chunkX + ':' + chunkZ;
    }
}
