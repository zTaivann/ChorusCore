package dev.chorus.core.players;

import dev.chorus.core.locale.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Tracks who is away.
 *
 * <p>Activity is only recorded when a player crosses into a new block, types a command or
 * interacts, which keeps the move listener down to two comparisons for a player who is
 * simply looking around.
 */
public final class AfkService implements Listener {

    private static final long SWEEP_TICKS = 20L * 20;

    private final Plugin plugin;
    private final Messages messages;
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Set<UUID> away = new HashSet<>();

    private volatile PlayerSettings settings;
    private BukkitTask sweeper;

    AfkService(Plugin plugin, Messages messages, PlayerSettings settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.settings = settings;
    }

    void apply(PlayerSettings updated) {
        this.settings = updated;
        restartSweeper();
    }

    void start() {
        restartSweeper();
    }

    void shutdown() {
        if (sweeper != null) {
            sweeper.cancel();
            sweeper = null;
        }
        lastActivity.clear();
        away.clear();
    }

    public boolean isAway(UUID playerId) {
        return away.contains(playerId);
    }

    /** @return the state the player is now in. */
    public boolean toggle(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (away.remove(player.getUniqueId())) {
            announce(player, "players.afk-back", "players.afk-back-broadcast");
            return false;
        }
        away.add(player.getUniqueId());
        announce(player, "players.afk-now", "players.afk-now-broadcast");
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!settings.autoAfk() && away.isEmpty()) {
            return;
        }
        var to = event.getTo();
        var from = event.getFrom();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastActivity.remove(event.getPlayer().getUniqueId());
        away.remove(event.getPlayer().getUniqueId());
    }

    private void touch(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (away.remove(player.getUniqueId())) {
            announce(player, "players.afk-back", "players.afk-back-broadcast");
        }
    }

    private void restartSweeper() {
        if (sweeper != null) {
            sweeper.cancel();
            sweeper = null;
        }
        if (settings.autoAfk() || settings.kicks()) {
            sweeper = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, this::sweep, SWEEP_TICKS, SWEEP_TICKS);
        }
    }

    private void sweep() {
        PlayerSettings current = settings;
        long now = System.currentTimeMillis();
        long afkAfter = TimeUnit.MINUTES.toMillis(current.autoAfkMinutes());
        long kickAfter = TimeUnit.MINUTES.toMillis(current.kickAfterMinutes());

        Iterator<Map.Entry<UUID, Long>> entries = lastActivity.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Long> entry = entries.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) {
                entries.remove();
                continue;
            }

            long idle = now - entry.getValue();
            if (current.kicks() && idle >= kickAfter && away.contains(entry.getKey())) {
                player.kick(messages.render("players.afk-kicked"));
                continue;
            }
            if (current.autoAfk() && idle >= afkAfter && away.add(entry.getKey())) {
                announce(player, "players.afk-now", "players.afk-now-broadcast");
            }
        }
    }

    private void announce(Player player, String toPlayer, String toEveryone) {
        messages.send(player, toPlayer);
        if (!settings.broadcast()) {
            return;
        }
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(player)) {
                messages.send(other, toEveryone, "player", player.getName());
            }
        }
    }
}
