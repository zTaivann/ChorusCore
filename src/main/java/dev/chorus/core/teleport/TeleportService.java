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

    private final Plugin plugin;
    private final Messages messages;
    private final Executor mainThread;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Location> previous = new HashMap<>();

    private volatile TeleportSettings settings;

    public TeleportService(Plugin plugin, Messages messages, Executor mainThread, TeleportSettings settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.mainThread = mainThread;
        this.settings = settings;
    }

    public void apply(TeleportSettings updated) {
        this.settings = updated;
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
            pending.remove(player.getUniqueId());
            move(player, target, rules, arrived);
        }, warmup * 20L);

        pending.put(player.getUniqueId(), new Pending(task, player.getLocation()));
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
    public Optional<Location> previousLocation(UUID playerId) {
        return Optional.ofNullable(previous.get(playerId));
    }

    public void shutdown() {
        pending.values().forEach(waiting -> waiting.task().cancel());
        pending.clear();
        previous.clear();
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
            previous.put(player.getUniqueId(), player.getLocation());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        forget(playerId, null);
        previous.remove(playerId);
    }

    private void move(Player player, Location destination, CommandRules rules, Runnable arrived) {
        Location origin = player.getLocation();
        if (settings.rememberPreviousLocation()) {
            previous.put(player.getUniqueId(), origin);
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

    private void forget(UUID playerId, String reason) {
        Pending waiting = pending.remove(playerId);
        if (waiting == null) {
            return;
        }
        waiting.task().cancel();
        if (reason == null) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            messages.send(player, reason);
        }
    }

    private record Pending(BukkitTask task, Location origin) {

        boolean coversSameBlock(Location other) {
            return origin.getWorld() == other.getWorld()
                    && origin.getBlockX() == other.getBlockX()
                    && origin.getBlockY() == other.getBlockY()
                    && origin.getBlockZ() == other.getBlockZ();
        }
    }
}
