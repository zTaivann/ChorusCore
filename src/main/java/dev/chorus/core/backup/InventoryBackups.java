package dev.chorus.core.backup;

import dev.chorus.core.platform.Schedulers;
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

/** A backup of everything a player was carrying, made before they lose it. */
public final class InventoryBackups {

    private static final int MAX_LIST = 45;

    private final BackupRepository repository;
    private final Executor worker;
    private final Executor mainThread;
    private final Schedulers schedulers;
    private final Logger logger;

    private volatile boolean enabled = true;
    private volatile int keepDays = 14;
    private volatile int keepPerPlayer = 20;
    private volatile Set<BackupReason> reasons = EnumSet.allOf(BackupReason.class);

    public InventoryBackups(BackupRepository repository, Executor worker, Executor mainThread,
                            Schedulers schedulers, Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.mainThread = mainThread;
        this.schedulers = schedulers;
        this.logger = logger;
    }

    public void apply(ConfigurationSection backups) {
        this.enabled = backups.getBoolean("enabled", true);
        this.keepDays = Math.max(0, backups.getInt("keep-days", 14));
        this.keepPerPlayer = Math.max(0, backups.getInt("keep-per-player", 20));

        ConfigurationSection on = backups.getConfigurationSection("on");
        Set<BackupReason> wanted = EnumSet.noneOf(BackupReason.class);
        for (BackupReason reason : BackupReason.values()) {
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

    /** A backup with nothing unusual about it: a clear, a kit, a restore. */
    public void take(Player player, BackupReason reason, String detail, String actor) {
        take(player, reason, detail, actor, null, null);
    }

    /**
     * A backup, with what killed them when that is why it is being made.
     *
     * @param detail what to call it in menus beyond the reason, such as the kit's name
     * @param cause  the damage that did it, or null
     * @param killer who did it, or null
     */
    public void take(Player player, BackupReason reason, String detail, String actor,
                     @Nullable String cause, @Nullable String killer) {
        if (!takes(reason)) {
            return;
        }

        // Encoded on the thread that owns the inventory, not on the worker.
        Location where = player.getLocation();
        InventoryBackup backup = new InventoryBackup(0, player.getUniqueId(),
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
                repository.save(backup);
            } catch (Exception failed) {
                logger.log(Level.WARNING,
                        "Could not save the inventory of " + player.getName(), failed);
            }
        });
    }

    public CompletableFuture<List<InventoryBackup>> find(UUID owner) {
        return Queries.run(() -> repository.findFor(owner, limit()), worker, mainThread);
    }

    /** The newest backups from anybody, for a report that does not name who. */
    public CompletableFuture<List<InventoryBackup>> recent() {
        return Queries.run(() -> repository.recent(limit()), worker, mainThread);
    }

    /** One by its row, read from the database. */
    public CompletableFuture<InventoryBackup> find(long id) {
        return Queries.run(() -> repository.find(id), worker, mainThread);
    }

    /**
     * Puts one back, after copying what the player is carrying now.
     *
     * @return what was put back. Empty when it cannot be read.
     */
    public Set<Part> restore(Player player, InventoryBackup backup, Set<Part> parts,
                             String actor) {
        Set<Part> done = EnumSet.noneOf(Part.class);
        ItemStack[] contents = parts.contains(Part.INVENTORY)
                ? InventoryCodec.decode(backup.contents())
                : null;
        ItemStack[] ender = parts.contains(Part.ENDER_CHEST) && backup.hasEnderChest()
                ? InventoryCodec.decode(backup.enderChest())
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
            player.setLevel(backup.level());
            player.setExp(backup.experience());
            player.setHealth(Math.max(1, Math.min(backup.health(), player.getMaxHealth())));
            player.setFoodLevel(backup.food());
            done.add(Part.STATS);
        }
        return done;
    }

    /**
     * Hands the saved items over without taking anything away.
     *
     * @return how many items were handed over, or -1 when the backup cannot be read.
     */
    public int deliver(Player player, InventoryBackup backup, boolean includeEnderChest) {
        ItemStack[] contents = InventoryCodec.decode(backup.contents());
        if (contents == null) {
            return -1;
        }
        ItemStack[] ender = includeEnderChest && backup.hasEnderChest()
                ? InventoryCodec.decode(backup.enderChest())
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
    public CompletableFuture<Void> queue(UUID owner, long backupId, Set<Part> parts, String actor) {
        String written = parts.stream().map(Enum::name).reduce((left, right) -> left + "," + right)
                .orElse("");
        return Queries.run(() -> {
            repository.queue(owner, backupId, written, actor);
            return null;
        }, worker, mainThread);
    }

    /**
     * Applies whatever was waiting for a player who has just arrived.
     *
     * @param told runs with what was put back.
     */
    public void applyWaiting(Player player, BiConsumer<Set<Part>, String> told) {
        UUID owner = player.getUniqueId();
        Queries.run(() -> repository.takeWaiting(owner), worker, mainThread)
                .whenComplete((waiting, failure) -> {
                    if (failure != null || waiting == null) {
                        return;
                    }
                    find(waiting.backupId()).whenComplete((backup, missing) -> {
                        if (missing != null || backup == null) {
                            return;
                        }
                        Set<Part> parts = parts(waiting.parts());
                        if (!player.isOnline()) {
                            // Taken off the queue for somebody who has left again: it goes back on.
                            queue(owner, backup.id(), parts, waiting.actor());
                            return;
                        }
                        schedulers.withEntity(player, () -> {
                            Set<Part> done = restore(player, backup, parts, waiting.actor());
                            if (!done.isEmpty()) {
                                told.accept(done, waiting.actor());
                            }
                        });
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

    /** Called at startup, on a worker. Old backups are the only thing this table grows. */
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
                logger.log(Level.WARNING, "Could not clear out the old inventory backups", failed);
            }
        });
    }
}
