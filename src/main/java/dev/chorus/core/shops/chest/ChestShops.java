package dev.chorus.core.shops.chest;

import dev.chorus.core.backup.InventoryCodec;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every chest shop on the server, kept in memory.
 *
 * <p>A right-click has to know in the same tick whether the block under the cursor is a shop,
 * so the table is read once at startup and written back whenever one changes. Shops are
 * counted in thousands rather than millions, and a shop is a dozen fields.
 *
 * <p>Everything here runs on the server thread. The writes are queued to a worker, but the
 * map is only ever touched from the thread that handles the click, which is what lets a trade
 * be a single uninterrupted decision.
 */
public final class ChestShops {

    private final ChestShopRepository repository;
    private final Executor worker;
    private final Logger logger;

    private final Map<String, ChestShop> byBlock = new HashMap<>();
    private final Map<UUID, Integer> counts = new HashMap<>();

    public ChestShops(ChestShopRepository repository, Executor worker, Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
    }

    /** Blocking, once, at startup. */
    public void load() throws SQLException {
        byBlock.clear();
        counts.clear();
        for (ChestShop shop : repository.all()) {
            byBlock.put(shop.key(), shop);
            counts.merge(shop.owner(), 1, Integer::sum);
        }
    }

    public int size() {
        return byBlock.size();
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

    /**
     * The shop at a block, whichever part of it was touched.
     *
     * <p>A sign resolves to what it is fixed to, and either half of a double chest resolves
     * to the half the shop was made on.
     */
    public @Nullable ChestShop at(Block block) {
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
            if (holderHalf != null) {
                return byBlock.get(ChestShop.key(holderHalf));
            }
            return null;
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

        byBlock.put(shop.key(), shop);
        counts.merge(shop.owner(), 1, Integer::sum);

        worker.execute(() -> {
            try {
                ChestShop saved = repository.save(shop);
                // The row it was given matters for nothing but the logs, so it is written
                // back quietly rather than being waited on.
                byBlock.computeIfPresent(saved.key(),
                        (key, current) -> current.id() == 0 ? saved : current);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not save the shop of " + owner.getName(), exception);
            }
        });
        return shop;
    }

    public void replace(ChestShop shop) {
        byBlock.put(shop.key(), shop);
        worker.execute(() -> {
            try {
                repository.update(shop);
            } catch (SQLException exception) {
                logger.log(Level.WARNING, "Could not update the shop at " + shop.key(), exception);
            }
        });
    }

    public void remove(ChestShop shop) {
        if (byBlock.remove(shop.key()) == null) {
            return;
        }
        counts.computeIfPresent(shop.owner(), (owner, count) -> count <= 1 ? null : count - 1);

        long id = shop.id();
        if (id == 0) {
            // Saved a moment ago and not yet given a row. Nothing to delete that would not
            // delete the wrong thing.
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
        counts.clear();
    }
}
