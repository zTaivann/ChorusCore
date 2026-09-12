package dev.chorus.core.kits;

import dev.chorus.core.backup.BackupReason;
import dev.chorus.core.backup.InventoryBackups;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.kits.rules.KitAction;
import dev.chorus.core.kits.rules.Requirement;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.players.Playtime;
import dev.chorus.core.storage.Queries;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final InventoryBackups backups;
    private final Messages messages;
    private final Economy economy;
    private final Executor worker;
    private final Executor mainThread;
    private final Map<UUID, Map<String, KitRepository.Use>> uses = new ConcurrentHashMap<>();

    private volatile Map<String, Kit> kits = Map.of();
    private volatile String firstJoinKit = "";

    KitService(KitRepository repository, InventoryBackups backups, Messages messages,
               Economy economy, Executor worker, Executor mainThread) {
        this.repository = repository;
        this.backups = backups;
        this.messages = messages;
        this.economy = economy;
        this.worker = worker;
        this.mainThread = mainThread;
    }

    void apply(Map<String, Kit> loaded, String firstJoin) {
        this.kits = loaded;
        this.firstJoinKit = firstJoin;
    }

    public Optional<Kit> find(String name) {
        return Optional.ofNullable(kits.get(name.toLowerCase(Locale.ROOT)));
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
     * The first requirement this player does not meet, or null when they meet them all.
     *
     * <p>The numbers behind them are gathered lazily: a kit with no money requirement never
     * asks the economy anything, and /kits builds this for every kit on the screen.
     */
    public @Nullable Requirement unmet(Player player, Kit kit) {
        if (kit.requirements().isEmpty()) {
            return null;
        }
        Requirement.Context context = new Requirement.Context() {
            @Override
            public double balance() {
                return economy.balance(player);
            }

            @Override
            public long playtimeSeconds() {
                return TimeUnit.MILLISECONDS.toSeconds(Playtime.of(player));
            }

            @Override
            public boolean hasClaimed(String other) {
                Map<String, KitRepository.Use> taken = uses.get(player.getUniqueId());
                return taken != null && taken.containsKey(other);
            }
        };

        for (Requirement requirement : kit.requirements()) {
            if (!requirement.met(player, context)) {
                return requirement;
            }
        }
        return null;
    }

    /**
     * Hands the kit over and records it. Anything that will not fit lands at the player's
     * feet rather than quietly disappearing.
     */
    public CompletableFuture<Void> give(Player player, Kit kit) {
        long now = System.currentTimeMillis();
        UUID owner = player.getUniqueId();

        // Written down before the database rather than after it. Everything that decides
        // whether a kit may be taken reads this map, and the write is a round trip off the
        // server thread: a player pressing the button twice in the same second would pass
        // the check twice and be handed a one-time kit twice over. Put back below if the
        // write turns out to have failed.
        KitRepository.Use before = remember(owner, kit.name(), now);

        return Queries.<Void>run(() -> {
            repository.markUsed(owner, kit.name(), now);
            return null;
        }, worker, mainThread).whenComplete((ignored, failure) -> {
            if (failure != null) {
                forget(owner, kit.name(), before);
                return;
            }
            hand(player, kit);
            KitAction.runAll(kit.claimActions(), player, messages, kit.name());
        });
    }

    private @Nullable KitRepository.Use remember(UUID owner, String kit, long now) {
        Map<String, KitRepository.Use> taken =
                uses.computeIfAbsent(owner, id -> new ConcurrentHashMap<>());
        KitRepository.Use before = taken.get(kit);
        taken.put(kit, new KitRepository.Use(now, before == null ? 1 : before.times() + 1));
        return before;
    }

    /** Undoes that, for the claim that was recorded and then could not be saved. */
    private void forget(UUID owner, String kit, @Nullable KitRepository.Use before) {
        Map<String, KitRepository.Use> taken = uses.get(owner);
        if (taken == null) {
            return;
        }
        if (before == null) {
            taken.remove(kit);
        } else {
            taken.put(kit, before);
        }
    }

    /**
     * Puts the kit where it belongs.
     *
     * <p>Armour goes on rather than into the inventory when {@code auto-armor} is on and the
     * slot is free. Taking off what somebody is already wearing to put the kit's on would be a
     * good way to lose enchanted diamond, so an occupied slot is left alone and the piece
     * goes in the inventory as any other item would.
     */
    private void hand(Player player, Kit kit) {
        PlayerInventory inventory = player.getInventory();
        if (kit.clearInventory()) {
            backups.take(player, BackupReason.KIT, kit.name(), player.getName());
            inventory.clear();
        }

        // Built for this player, so an item whose lore mentions them says their name.
        List<ItemStack> contents = kit.contents(player);
        List<ItemStack> loose = new ArrayList<>(contents.size());
        for (ItemStack item : contents) {
            if (!kit.autoArmor() || !equip(inventory, item)) {
                loose.add(item);
            }
        }

        for (ItemStack leftover : inventory.addItem(loose.toArray(new ItemStack[0])).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** @return whether the piece was worn. */
    private static boolean equip(PlayerInventory inventory, ItemStack item) {
        ArmourSlot slot = ArmourSlot.of(item.getType());
        if (slot == null || slot.worn(inventory) != null) {
            return false;
        }
        slot.wear(inventory, item);
        return true;
    }

    /**
     * Which armour slot a material belongs in, worked out from the end of its name.
     *
     * <p>By suffix rather than by listing every piece: the game has gained whole armour sets
     * since 1.18, and a list written today would not know about the next one.
     */
    private enum ArmourSlot {
        HELMET, CHESTPLATE, LEGGINGS, BOOTS;

        static @Nullable ArmourSlot of(Material material) {
            String name = material.name();
            for (ArmourSlot slot : values()) {
                if (name.endsWith("_" + slot.name())) {
                    return slot;
                }
            }
            // The two that break the pattern, and have since the game had armour at all.
            return switch (name) {
                case "TURTLE_HELMET", "CARVED_PUMPKIN" -> HELMET;
                case "ELYTRA" -> CHESTPLATE;
                default -> null;
            };
        }

        @Nullable ItemStack worn(PlayerInventory inventory) {
            ItemStack piece = switch (this) {
                case HELMET -> inventory.getHelmet();
                case CHESTPLATE -> inventory.getChestplate();
                case LEGGINGS -> inventory.getLeggings();
                case BOOTS -> inventory.getBoots();
            };
            return piece == null || piece.getType().isAir() ? null : piece;
        }

        void wear(PlayerInventory inventory, ItemStack item) {
            switch (this) {
                case HELMET -> inventory.setHelmet(item);
                case CHESTPLATE -> inventory.setChestplate(item);
                case LEGGINGS -> inventory.setLeggings(item);
                case BOOTS -> inventory.setBoots(item);
            }
        }
    }

    public CompletableFuture<Boolean> reset(UUID owner, String kit) {
        String key = kit.toLowerCase(Locale.ROOT);
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
