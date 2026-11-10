package dev.chorus.core.teleport;

import dev.chorus.commons.platform.ChorusTask;
import dev.chorus.commons.platform.Schedulers;
import dev.chorus.core.api.TeleportApi;
import dev.chorus.core.api.event.ChorusTeleportEvent;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.locale.Messages;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Delayed teleports shared by every module that moves a player around. */
public final class TeleportService implements TeleportApi, Listener {

    private static final String INSTANT_PERMISSION = "chorus.teleport.instant";
    private static final String DEATH_PERMISSION = "chorus.back.ondeath";

    private static final long TICKS_PER_SECOND = 20L;
    private static final Runnable NOTHING = () -> { };

    private final Plugin plugin;
    private final Messages messages;
    private final Schedulers schedulers;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Location>> history = new ConcurrentHashMap<>();
    private final Map<UUID, Long> protectedUntil = new ConcurrentHashMap<>();

    private volatile TeleportSettings settings;

    public TeleportService(Plugin plugin, Messages messages, Schedulers schedulers,
                           TeleportSettings settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.schedulers = schedulers;
        this.settings = settings;
    }

    public void apply(TeleportSettings updated) {
        this.settings = updated;
        trimHistories();
    }

    /** Whether a destination may be nudged to somewhere the player can stand. */
    public enum Landing {

        /**
         * Looks for a floor nearby. For saved places that may have changed: homes, warps, spawn,
         * death points.
         */
        SAFE,

        /** Exactly where asked. For typed coordinates. */
        EXACT
    }

    public void teleport(Player player, Location destination, CommandRules rules, String cause) {
        teleport(player, destination, rules, cause, () -> {
        });
    }

    public void teleport(Player player, Location destination, CommandRules rules, String cause,
                         Runnable arrived) {
        teleport(player, destination, rules, cause, Landing.SAFE, arrived);
    }

    public void teleport(Player player, Location destination, CommandRules rules, String cause,
                         Landing landing, Runnable arrived) {
        teleport(player, destination, rules, cause, landing, arrived, NOTHING);
    }

    public void teleport(Player player, Location destination, CommandRules rules, String cause,
                         Runnable arrived, Runnable cancelled) {
        teleport(player, destination, rules, cause, Landing.SAFE, arrived, cancelled);
    }

    /**
     * @param arrived   runs once the player is there
     * @param cancelled runs instead if they never arrive: moved, hurt or left during the warmup,
     *                  an addon stopped it, or nowhere safe to land
     */
    public void teleport(Player player, Location destination, CommandRules rules, String cause,
                         Landing landing, Runnable arrived, Runnable cancelled) {
        schedulers.withEntity(player,
                () -> begin(player, destination, rules, cause, landing, arrived, cancelled));
    }

    /** On the player's own thread, wherever the teleport was asked for. */
    private void begin(Player player, Location destination, CommandRules rules, String cause,
                       Landing landing, Runnable arrived, Runnable cancelled) {
        // Addons get their say before anything is charged or any wait begins.
        ChorusTeleportEvent event = new ChorusTeleportEvent(player, destination, cause);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            cancelled.run();
            return;
        }
        Location target = event.getDestination();

        forget(player.getUniqueId(), null);

        int warmup = player.hasPermission(INSTANT_PERMISSION) ? 0 : rules.warmupSeconds();
        if (warmup == 0) {
            move(player, target, rules, landing, arrived, cancelled);
            return;
        }

        messages.send(player, "teleport.warmup", "seconds", String.valueOf(warmup));
        ChorusTask task = schedulers.entityLater(player, () -> {
            Pending finished = pending.remove(player.getUniqueId());
            if (finished != null) {
                finished.cancelCountdown();
            }
            move(player, target, rules, landing, arrived, cancelled);
        }, warmup * TICKS_PER_SECOND);

        pending.put(player.getUniqueId(),
                new Pending(task, countdown(player, warmup), player.getLocation(), cancelled));
    }

    /**
     * The overload for addons: a warmup in seconds only. Everything else comes from the
     * command's config block.
     */
    @Override
    public void teleport(Player player, Location destination, int warmupSeconds) {
        teleport(player, destination, CommandRules.FREE.waiting(Math.max(0, warmupSeconds)), "api");
    }

    /** Where the player was standing before their last teleport, or before they died. */
    @Override
    public Optional<Location> previousLocation(UUID playerId) {
        Deque<Location> places = history.get(playerId);
        return Optional.ofNullable(places == null ? null : places.peekFirst());
    }

    /** The place {@code steps} back in the history, without taking anything off it. */
    public Optional<Location> previousLocation(UUID playerId, int steps) {
        Deque<Location> places = history.get(playerId);
        if (places == null || steps < 1 || steps > places.size()) {
            return Optional.empty();
        }
        int seen = 0;
        for (Location place : places) {
            if (++seen == steps) {
                return Optional.of(place);
            }
        }
        return Optional.empty();
    }

    /** How many steps back {@code /back} could take this player. */
    public int historyDepth(UUID playerId) {
        Deque<Location> places = history.get(playerId);
        return places == null ? 0 : places.size();
    }

    /**
     * Removes {@code steps} places from the history and returns the last, so going two back
     * doesn't leave the one between waiting to be visited.
     */
    public Optional<Location> takePrevious(UUID playerId, int steps) {
        Deque<Location> places = history.get(playerId);
        if (places == null || places.size() < steps || steps < 1) {
            return Optional.empty();
        }
        Location taken = null;
        for (int step = 0; step < steps; step++) {
            taken = places.pollFirst();
        }
        if (places.isEmpty()) {
            history.remove(playerId);
        }
        return Optional.ofNullable(taken);
    }

    public void shutdown() {
        pending.values().forEach(waiting -> {
            waiting.cancelAll();
            waiting.cancelled().run();
        });
        pending.clear();
        history.clear();
        protectedUntil.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Runs for every player every tick, so the cheapest possible exit comes first.
        if (pending.isEmpty() || !settings.cancelOnMove()) {
            return;
        }
        Pending waiting = pending.get(event.getPlayer().getUniqueId());
        Location to = event.getTo();
        if (waiting == null || to == null || waiting.coversSameBlock(to)) {
            return;
        }
        forget(event.getPlayer().getUniqueId(), "teleport.cancelled-move");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (pending.isEmpty() || !settings.cancelOnDamage()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            forget(player.getUniqueId(), "teleport.cancelled-damage");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (settings.rememberPreviousLocation() && player.hasPermission(DEATH_PERMISSION)) {
            remember(player.getUniqueId(), player.getLocation());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        forget(playerId, null);
        history.remove(playerId);
        protectedUntil.remove(playerId);
    }

    /** Checks the ground at the destination once it has loaded, on the region that owns it. */
    private void move(Player player, Location destination, CommandRules rules, Landing landing,
                      Runnable arrived, Runnable cancelled) {
        // Creative and spectator: no fall to take and no wall to suffocate in.
        GameMode mode = player.getGameMode();
        boolean unstoppable = mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;

        if (landing == Landing.EXACT || unstoppable || !settings.safeLanding()) {
            commit(player, destination, rules, arrived, cancelled);
            return;
        }

        // Flying in survival: the room is checked, the ground is not.
        boolean needsFloor = !player.getAllowFlight();

        destination.getWorld().getChunkAtAsync(destination).whenComplete((loaded, failure) -> {
            if (failure != null) {
                messages.send(player, "teleport.failed");
                cancelled.run();
                return;
            }
            schedulers.region(destination, () -> {
                Location safe = SafeLanding.nearest(
                        destination, settings.safeLandingRadius(), needsFloor);
                schedulers.withEntity(player, () -> {
                    if (!player.isOnline()) {
                        cancelled.run();
                        return;
                    }
                    if (safe == null) {
                        messages.send(player, "teleport.unsafe");
                        cancelled.run();
                        return;
                    }
                    commit(player, safe, rules, arrived, cancelled);
                });
            });
        });
    }

    private void commit(Player player, Location destination, CommandRules rules, Runnable arrived,
                        Runnable cancelled) {
        Location origin = player.getLocation();
        if (settings.rememberPreviousLocation()) {
            remember(player.getUniqueId(), origin);
        }
        // The puff they leave behind. Arrival rides with the command's own feedback.
        rules.feedback().showAt(origin);

        player.teleportAsync(destination).whenComplete((moved, failure) ->
                schedulers.withEntity(player, () -> {
                    if (Boolean.TRUE.equals(moved)) {
                        protect(player);
                        arrived.run();
                    } else {
                        messages.send(player, "teleport.failed");
                        cancelled.run();
                    }
                }));
    }

    /** A few seconds of not being hittable on arrival. */
    private void protect(Player player) {
        int seconds = settings.invulnerableSeconds();
        if (seconds <= 0) {
            return;
        }
        protectedUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean isProtected(UUID playerId) {
        Long until = protectedUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (until > System.currentTimeMillis()) {
            return true;
        }
        protectedUntil.remove(playerId);
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onProtectedDamage(EntityDamageEvent event) {
        if (protectedUntil.isEmpty() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (isProtected(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Hitting somebody gives it up, which is the whole of the rule. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (protectedUntil.isEmpty()) {
            return;
        }
        if (event.getDamager() instanceof Player attacker) {
            protectedUntil.remove(attacker.getUniqueId());
        }
    }

    /** Newest first, and only as deep as the config allows. */
    private void remember(UUID playerId, Location place) {
        Deque<Location> places = history.computeIfAbsent(playerId, key -> new ConcurrentLinkedDeque<>());
        places.addFirst(place);
        while (places.size() > settings.historySize()) {
            places.pollLast();
        }
    }

    private void trimHistories() {
        int allowed = settings.historySize();
        history.values().forEach(places -> {
            while (places.size() > allowed) {
                places.pollLast();
            }
        });
    }

    private @Nullable ChorusTask countdown(Player player, int warmup) {
        if (!settings.warmupCountdown()) {
            return null;
        }
        // Counted from a deadline, so a lagging server does not drift.
        long deadline = System.currentTimeMillis() + warmup * 1000L;
        return schedulers.entityTimer(player, () -> {
            long remaining = (deadline - System.currentTimeMillis() + 999) / 1000;
            if (remaining > 0) {
                player.sendActionBar(messages.render(player, "teleport.warmup-countdown",
                        "seconds", String.valueOf(remaining)));
            }
        }, 0L, TICKS_PER_SECOND);
    }

    private void forget(UUID playerId, @Nullable String reason) {
        Pending waiting = pending.remove(playerId);
        if (waiting == null) {
            return;
        }
        waiting.cancelAll();
        Player player = reason == null ? null : plugin.getServer().getPlayer(playerId);
        if (player != null) {
            messages.send(player, reason);
        }
        waiting.cancelled().run();
    }

    private record Pending(ChorusTask task, @Nullable ChorusTask countdown, Location origin,
                           Runnable cancelled) {

        boolean coversSameBlock(Location other) {
            return origin.getWorld() == other.getWorld()
                    && origin.getBlockX() == other.getBlockX()
                    && origin.getBlockY() == other.getBlockY()
                    && origin.getBlockZ() == other.getBlockZ();
        }

        void cancelCountdown() {
            if (countdown != null) {
                countdown.cancel();
            }
        }

        void cancelAll() {
            task.cancel();
            cancelCountdown();
        }
    }
}
