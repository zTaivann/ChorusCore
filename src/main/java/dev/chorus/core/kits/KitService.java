package dev.chorus.core.kits;

import dev.chorus.core.storage.Queries;
import org.bukkit.Bukkit;
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
    private final Map<UUID, Map<String, KitRepository.Use>> uses = new ConcurrentHashMap<>();

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
        KitRepository.Use taken = use(owner, kit);
        if (taken == null) {
            return 0;
        }
        if (kit.oneTime() || (kit.maxClaims() > 0 && taken.times() >= kit.maxClaims())) {
            return Long.MAX_VALUE;
        }
        long ready = taken.lastTaken() + TimeUnit.SECONDS.toMillis(kit.cooldownSeconds());
        return Math.max(0, ready - now);
    }

    /** How many times this player has taken the kit, for the message that says so. */
    public int timesTaken(UUID owner, Kit kit) {
        KitRepository.Use taken = use(owner, kit);
        return taken == null ? 0 : taken.times();
    }

    /** Whether the kit is gone for good rather than merely waiting out a cooldown. */
    public boolean isSpent(UUID owner, Kit kit) {
        KitRepository.Use taken = use(owner, kit);
        if (taken == null) {
            return false;
        }
        return kit.oneTime() || (kit.maxClaims() > 0 && taken.times() >= kit.maxClaims());
    }

    private @Nullable KitRepository.Use use(UUID owner, Kit kit) {
        Map<String, KitRepository.Use> taken = uses.get(owner);
        return taken == null ? null : taken.get(kit.name());
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
            Map<String, KitRepository.Use> taken = uses.computeIfAbsent(
                    player.getUniqueId(), owner -> new ConcurrentHashMap<>());
            KitRepository.Use before = taken.get(kit.name());
            taken.put(kit.name(),
                    new KitRepository.Use(now, before == null ? 1 : before.times() + 1));

            for (ItemStack leftover : player.getInventory()
                    .addItem(kit.contents().toArray(new ItemStack[0])).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            run(player, kit);
        });
    }

    /**
     * The commands a kit runs when it is taken, after the items are in.
     *
     * <p>{@code run-as-console} is how a kit hands out something the player could not give
     * themselves, which is most of what makes a kit more than a box of items. It is also
     * how a kit could hand out anything at all, so it is worth reading twice.
     */
    private void run(Player player, Kit kit) {
        if (!kit.runsCommands()) {
            return;
        }
        for (String command : kit.runAsPlayer()) {
            player.performCommand(forPlayer(command, player));
        }
        for (String command : kit.runAsConsole()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), forPlayer(command, player));
        }
    }

    private static String forPlayer(String command, Player player) {
        String cleaned = command.startsWith("/") ? command.substring(1) : command;
        return cleaned.replace("%player%", player.getName());
    }

    public CompletableFuture<Boolean> reset(UUID owner, String kit) {
        String key = kit.toLowerCase(java.util.Locale.ROOT);
        return Queries.run(() -> repository.clear(owner, key), worker, mainThread)
                .thenApply(cleared -> {
                    Map<String, KitRepository.Use> taken = uses.get(owner);
                    if (cleared && taken != null) {
                        taken.remove(key);
                    }
                    return cleared;
                });
    }
}
