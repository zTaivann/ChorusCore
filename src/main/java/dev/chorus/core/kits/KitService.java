package dev.chorus.core.kits;

import dev.chorus.core.storage.Queries;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * The kits themselves and who has taken what.
 *
 * <p>Uses are read from the database once, while the player is still logging in, and kept in
 * memory from then on, so checking a cooldown never costs a query.
 */
public final class KitService {

    private final KitRepository repository;
    private final Executor worker;
    private final Executor mainThread;
    private final Map<UUID, Map<String, Long>> uses = new ConcurrentHashMap<>();

    private volatile Map<String, Kit> kits = Map.of();
    private volatile String firstJoinKit = "";

    KitService(KitRepository repository, Executor worker, Executor mainThread) {
        this.repository = repository;
        this.worker = worker;
        this.mainThread = mainThread;
    }

    void apply(Map<String, Kit> loaded, String firstJoin) {
        this.kits = loaded;
        this.firstJoinKit = firstJoin;
    }

    public Optional<Kit> find(String name) {
        return Optional.ofNullable(kits.get(name.toLowerCase(java.util.Locale.ROOT)));
    }

    public List<Kit> all() {
        return List.copyOf(kits.values());
    }

    /** The kits this player is allowed to see, in the order the config lists them. */
    public List<Kit> visibleTo(Player player) {
        return kits.values().stream().filter(kit -> kit.allowed(player)).toList();
    }

    public @Nullable Kit firstJoinKit() {
        return firstJoinKit.isEmpty() ? null : kits.get(firstJoinKit);
    }

    /** Blocking. Called from the login thread before the player is let in. */
    public void load(UUID owner) throws SQLException {
        uses.put(owner, new ConcurrentHashMap<>(repository.findUses(owner)));
    }

    public void unload(UUID owner) {
        uses.remove(owner);
    }

    public boolean isLoaded(UUID owner) {
        return uses.containsKey(owner);
    }

    void clear() {
        uses.clear();
    }

    /** Milliseconds left before the player may take this kit again, or zero. */
    public long remaining(UUID owner, Kit kit, long now) {
        Map<String, Long> taken = uses.get(owner);
        if (taken == null) {
            return 0;
        }
        Long when = taken.get(kit.name());
        if (when == null) {
            return 0;
        }
        if (kit.oneTime()) {
            return Long.MAX_VALUE;
        }
        long ready = when + TimeUnit.SECONDS.toMillis(kit.cooldownSeconds());
        return Math.max(0, ready - now);
    }

    /**
     * Hands the kit over and records it. Anything that will not fit lands at the player's
     * feet rather than quietly disappearing.
     */
    public CompletableFuture<Void> give(Player player, Kit kit) {
        long now = System.currentTimeMillis();
        CompletableFuture<Void> saved = Queries.run(() -> {
            repository.markUsed(player.getUniqueId(), kit.name(), now);
            return null;
        }, worker, mainThread);

        return saved.thenRun(() -> {
            uses.computeIfAbsent(player.getUniqueId(), owner -> new ConcurrentHashMap<>())
                    .put(kit.name(), now);
            for (ItemStack leftover : player.getInventory()
                    .addItem(kit.contents().toArray(new ItemStack[0])).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        });
    }

    public CompletableFuture<Boolean> reset(UUID owner, String kit) {
        String key = kit.toLowerCase(java.util.Locale.ROOT);
        return Queries.run(() -> repository.clear(owner, key), worker, mainThread)
                .thenApply(cleared -> {
                    Map<String, Long> taken = uses.get(owner);
                    if (cleared && taken != null) {
                        taken.remove(key);
                    }
                    return cleared;
                });
    }
}
