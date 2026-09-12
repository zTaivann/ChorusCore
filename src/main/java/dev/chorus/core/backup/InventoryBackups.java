package dev.chorus.core.backup;

import dev.chorus.core.storage.Queries;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A copy of everything a player was carrying, taken before they lose it.
 *
 * <p>Death is the reason this exists. A player who dies in lava, or to a bug, or to somebody
 * who should not have been able to kill them, has no way back without one of these. Clearing
 * an inventory and quitting are copied too, so a rollback has something to roll back to.
 *
 * <p>Taking one is fire and forget: the copy is made on the server thread, where the
 * inventory is, and written from a worker. Nobody waits on it.
 */
public final class InventoryBackups {

    private static final int MAX_LIST = 45;

    private final BackupRepository repository;
    private final Executor worker;
    private final Executor mainThread;
    private final Logger logger;

    private volatile boolean enabled = true;
    private volatile int keepDays = 14;
    private volatile int keepPerPlayer = 20;
    private volatile Set<BackupReason> reasons = EnumSet.allOf(BackupReason.class);

    public InventoryBackups(BackupRepository repository, Executor worker, Executor mainThread,
                            Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.mainThread = mainThread;
        this.logger = logger;
    }

    public void apply(ConfigurationSection backups) {
        this.enabled = backups.getBoolean("enabled", true);
        this.keepDays = Math.max(0, backups.getInt("keep-days", 14));
        this.keepPerPlayer = Math.max(0, backups.getInt("keep-per-player", 20));

        ConfigurationSection on = backups.getConfigurationSection("on");
        Set<BackupReason> wanted = EnumSet.noneOf(BackupReason.class);
        for (BackupReason reason : BackupReason.values()) {
            // A restore always leaves one behind. Undoing a mistaken restore is the whole
            // point of the copy, and switching that off leaves no way back from the way back.
            if (reason == BackupReason.RESTORE || on == null
                    || on.getBoolean(reason.setting(), true)) {
                wanted.add(reason);
            }
        }
        this.reasons = wanted;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean takes(BackupReason reason) {
        return enabled && reasons.contains(reason);
    }

    public int limit() {
        return keepPerPlayer == 0 ? MAX_LIST : Math.min(MAX_LIST, keepPerPlayer);
    }

    /** A copy with nothing unusual about it: a clear, a kit, a restore. */
    public void take(Player player, BackupReason reason, String detail, String actor) {
        take(player, reason, detail, actor, null, null);
    }

    /**
     * A copy, with what killed them when that is why it is being taken.
     *
     * @param detail what to call it on screen beyond the reason, such as the kit's name
     * @param cause  the damage that did it, or null
     * @param killer who did it, or null
     */
    public void take(Player player, BackupReason reason, String detail, String actor,
                     @Nullable String cause, @Nullable String killer) {
        if (!takes(reason)) {
            return;
        }

        // Encoded here, on the thread that owns the inventory. Handing the live arrays to a
        // worker would read them while the player carries on playing.
        Location where = player.getLocation();
        InventorySnapshot snapshot = new InventorySnapshot(0, player.getUniqueId(),
                System.currentTimeMillis(),
                detail.isEmpty() ? reason.stored() : reason.stored() + " " + detail,
                actor,
                InventoryCodec.encode(player.getInventory().getContents()),
                InventoryCodec.encode(player.getEnderChest().getContents()),
                player.getLevel(), player.getExp(),
                player.getHealth(), player.getFoodLevel(),
                where.getWorld() == null ? "" : where.getWorld().getName(),
                where.getBlockX(), where.getBlockY(), where.getBlockZ(),
                cause, killer);

        worker.execute(() -> {
            try {
                repository.save(snapshot);
            } catch (Exception failed) {
                logger.log(Level.WARNING,
                        "Could not save the inventory of " + player.getName(), failed);
            }
        });
    }

    public CompletableFuture<List<InventorySnapshot>> find(UUID owner) {
        return Queries.run(() -> repository.findFor(owner, limit()), worker, mainThread);
    }

    /** The newest copies from anybody, for a report that does not name who. */
    public CompletableFuture<List<InventorySnapshot>> recent() {
        return Queries.run(() -> repository.recent(limit()), worker, mainThread);
    }

    /** One by its row, read again so a screen left open never restores something stale. */
    public CompletableFuture<InventorySnapshot> find(long id) {
        return Queries.run(() -> repository.find(id), worker, mainThread);
    }

    /**
     * Puts one back, after copying what the player is carrying now.
     *
     * <p>Restoring is itself something that empties an inventory, so it leaves a copy of its
     * own behind. Reaching for the wrong one should not be the end of it.
     *
     * @return what was put back, so the caller can say. Empty when it cannot be read at all.
     */
    public Set<Part> restore(Player player, InventorySnapshot snapshot, Set<Part> parts,
                             String actor) {
        Set<Part> done = EnumSet.noneOf(Part.class);
        ItemStack[] contents = parts.contains(Part.INVENTORY)
                ? InventoryCodec.decode(snapshot.contents())
                : null;
        ItemStack[] ender = parts.contains(Part.ENDER_CHEST) && snapshot.hasEnderChest()
                ? InventoryCodec.decode(snapshot.enderChest())
                : null;

        if (contents == null && ender == null && !parts.contains(Part.STATS)) {
            return done;
        }
        take(player, BackupReason.RESTORE, "", actor);

        if (contents != null) {
            fill(player.getInventory(), contents);
            done.add(Part.INVENTORY);
        }
        if (ender != null) {
            fill(player.getEnderChest(), ender);
            done.add(Part.ENDER_CHEST);
        }
        if (parts.contains(Part.STATS)) {
            player.setLevel(snapshot.level());
            player.setExp(snapshot.experience());
            player.setHealth(Math.max(1, Math.min(snapshot.health(), player.getMaxHealth())));
            player.setFoodLevel(snapshot.food());
            done.add(Part.STATS);
        }
        return done;
    }

    /**
     * Hands the saved items over without taking anything away.
     *
     * <p>For the times somebody should get their diamonds back but has since gone and
     * earned a new set. Whatever does not fit falls at their feet rather than vanishing.
     *
     * @return how many items were handed over, or -1 when the copy cannot be read.
     */
    public int deliver(Player player, InventorySnapshot snapshot, boolean includeEnderChest) {
        ItemStack[] contents = InventoryCodec.decode(snapshot.contents());
        if (contents == null) {
            return -1;
        }
        ItemStack[] ender = includeEnderChest && snapshot.hasEnderChest()
                ? InventoryCodec.decode(snapshot.enderChest())
                : null;

        int handed = hand(player, contents);
        if (ender != null) {
            handed += hand(player, ender);
        }
        return handed;
    }

    private static int hand(Player player, ItemStack[] contents) {
        int handed = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            handed += stack.getAmount();
            player.getInventory().addItem(stack).values().forEach(
                    left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        return handed;
    }

    /** Leaves a restore waiting for somebody who is not online to receive it. */
    public CompletableFuture<Void> queue(UUID owner, long snapshot, Set<Part> parts, String actor) {
        String written = parts.stream().map(Enum::name).reduce((left, right) -> left + "," + right)
                .orElse("");
        return Queries.run(() -> {
            repository.queue(owner, snapshot, written, actor);
            return null;
        }, worker, mainThread);
    }

    /**
     * Applies whatever was waiting for a player who has just arrived.
     *
     * @param told runs with what was put back, so the caller can say so.
     */
    public void applyWaiting(Player player, BiConsumer<Set<Part>, String> told) {
        Queries.run(() -> repository.takeWaiting(player.getUniqueId()), worker, mainThread)
                .whenComplete((waiting, failure) -> {
                    if (failure != null || waiting == null || !player.isOnline()) {
                        return;
                    }
                    find(waiting.snapshot()).whenComplete((snapshot, missing) -> {
                        if (missing != null || snapshot == null || !player.isOnline()) {
                            return;
                        }
                        Set<Part> done = restore(player, snapshot, parts(waiting.parts()),
                                waiting.actor());
                        if (!done.isEmpty()) {
                            told.accept(done, waiting.actor());
                        }
                    });
                });
    }

    private static Set<Part> parts(String written) {
        Set<Part> parts = EnumSet.noneOf(Part.class);
        for (String name : written.split(",")) {
            try {
                parts.add(Part.valueOf(name.trim()));
            } catch (IllegalArgumentException unknown) {
                // A part this version no longer has. The rest still go back.
            }
        }
        return parts;
    }

    private static void fill(Inventory inventory, ItemStack[] contents) {
        inventory.clear();
        for (int slot = 0; slot < Math.min(contents.length, inventory.getSize()); slot++) {
            inventory.setItem(slot, contents[slot]);
        }
    }

    /** What a backup can be read back into the player. */
    public enum Part {
        INVENTORY, ENDER_CHEST,

        /** Experience, health and hunger, which are lost together and come back together. */
        STATS
    }

    /** Called at startup, on a worker. Old copies are the only thing this table grows. */
    public void prune() {
        if (!enabled || (keepDays == 0 && keepPerPlayer == 0)) {
            return;
        }
        long before = keepDays == 0
                ? 0
                : System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepDays);

        worker.execute(() -> {
            try {
                repository.prune(before, keepPerPlayer);
            } catch (Exception failed) {
                logger.log(Level.WARNING, "Could not clear out the old inventory copies", failed);
            }
        });
    }
}
