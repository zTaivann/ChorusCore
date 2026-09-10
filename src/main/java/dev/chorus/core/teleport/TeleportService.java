package dev.chorus.core.teleport;

import dev.chorus.core.api.TeleportApi;
import dev.chorus.core.api.event.ChorusTeleportEvent;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.feedback.CommandFeedback;
import dev.chorus.core.locale.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Delayed teleports shared by every module that moves a player around.
 *
 * <p>A pending teleport is dropped as soon as the player leaves the block they stood on or
 * takes damage, which is what keeps /home and /spawn from being a combat escape. The
 * service also remembers where each player came from, which is all /back needs.
 */
public final class TeleportService implements TeleportApi, Listener {

    private static final String INSTANT_PERMISSION = "chorus.teleport.instant";
    private static final String DEATH_PERMISSION = "chorus.back.ondeath";

    private static final long TICKS_PER_SECOND = 20L;

    private final Plugin plugin;
    private final Messages messages;
    private final Executor mainThread;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Deque<Location>> history = new HashMap<>();

    private volatile TeleportSettings settings;

    public TeleportService(Plugin plugin, Messages messages, Executor mainThread, TeleportSettings settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.mainThread = mainThread;
        this.settings = settings;
    }

    public void apply(TeleportSettings updated) {
        this.settings = updated;
        trimHistories();
    }

    public void teleport(Player player, Location destination, CommandRules rules, String cause) {
        teleport(player, destination, rules, cause, () -> {
        });
    }

    public void teleport(Player player, Location destination, CommandRules rules, String cause,
                         Runnable arrived) {
        // Addons get their say before anything is charged or any wait begins.
        ChorusTeleportEvent event = new ChorusTeleportEvent(player, destination, cause);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        Location target = event.getDestination();

        forget(player.getUniqueId(), null);

        int warmup = player.hasPermission(INSTANT_PERMISSION) ? 0 : rules.warmupSeconds();
        if (warmup == 0) {
            move(player, target, rules, arrived);
            return;
        }

        messages.send(player, "teleport.warmup", "seconds", String.valueOf(warmup));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Pending finished = pending.remove(player.getUniqueId());
            if (finished != null) {
                finished.cancelCountdown();
            }
            move(player, target, rules, arrived);
        }, warmup * TICKS_PER_SECOND);

        pending.put(player.getUniqueId(),
                new Pending(task, countdown(player, warmup), player.getLocation()));
    }

    /**
     * The overload addons get: a wait in seconds and nothing else. Everything the plugin's
     * own commands do beyond that comes from their config block.
     */
    @Override
    public void teleport(Player player, Location destination, int warmupSeconds) {
        teleport(player, destination,
                new CommandRules(true, Math.max(0, warmupSeconds), 0, 0, CommandFeedback.NONE), "api");
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
     * Takes {@code steps} places off the history and returns the last of them, so asking to
     * go two back does not leave the one in between waiting to be visited again.
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
        pending.values().forEach(Pending::cancelAll);
        pending.clear();
        history.clear();
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
    }

    /**
     * Checks the ground before committing, on the chunk the player is headed for rather than
     * the one they are standing in. The chunk is fetched asynchronously: reading blocks in an
     * unloaded chunk from the server thread would stall every player to answer one.
     */
    private void move(Player player, Location destination, CommandRules rules, Runnable arrived) {
        if (!settings.safeLanding()) {
            commit(player, destination, rules, arrived);
            return;
        }

        destination.getWorld().getChunkAtAsync(destination).thenAcceptAsync(loaded -> {
            if (!player.isOnline()) {
                return;
            }
            Location safe = SafeLanding.nearest(destination, settings.safeLandingRadius());
            if (safe == null) {
                messages.send(player, "teleport.unsafe");
                return;
            }
            commit(player, safe, rules, arrived);
        }, mainThread);
    }

    private void commit(Player player, Location destination, CommandRules rules, Runnable arrived) {
        Location origin = player.getLocation();
        if (settings.rememberPreviousLocation()) {
            remember(player.getUniqueId(), origin);
        }
        // The puff they leave behind. The arrival effect rides along with the command's
        // own feedback, which fires from the callback below once the move succeeded.
        rules.feedback().showAt(origin);

        player.teleportAsync(destination).thenAcceptAsync(moved -> {
            if (moved) {
                arrived.run();
            } else {
                messages.send(player, "teleport.failed");
            }
        }, mainThread);
    }

    /** Newest first, and only as deep as the config allows. */
    private void remember(UUID playerId, Location place) {
        Deque<Location> places = history.computeIfAbsent(playerId, key -> new ArrayDeque<>());
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

    private @Nullable BukkitTask countdown(Player player, int warmup) {
        if (!settings.warmupCountdown()) {
            return null;
        }
        // Counted from a deadline rather than a tally, so a lagging server shows the time
        // that is actually left instead of drifting away from it.
        long deadline = System.currentTimeMillis() + warmup * 1000L;
        return plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long remaining = (deadline - System.currentTimeMillis() + 999) / 1000;
            if (remaining > 0) {
                player.sendActionBar(messages.render("teleport.warmup-countdown",
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
        if (reason == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            messages.send(player, reason);
        }
    }

    private record Pending(BukkitTask task, @Nullable BukkitTask countdown, Location origin) {

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
