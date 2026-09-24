package dev.chorus.core.players;

import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.ChorusTask;
import dev.chorus.core.platform.Schedulers;
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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Tracks who is away. */
public final class AfkService implements Listener {

    private static final long SWEEP_TICKS = 20L * 20;

    private final Plugin plugin;
    private final Messages messages;
    private final Schedulers schedulers;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, String> reasons = new ConcurrentHashMap<>();
    private final Set<UUID> away = ConcurrentHashMap.newKeySet();

    private volatile PlayerSettings settings;
    private ChorusTask sweeper;

    AfkService(Plugin plugin, Messages messages, Schedulers schedulers, PlayerSettings settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.schedulers = schedulers;
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
        reasons.clear();
        away.clear();
    }

    public boolean isAway(UUID playerId) {
        return away.contains(playerId);
    }

    /** What they said they were doing, or an empty string. */
    public String reason(UUID playerId) {
        return reasons.getOrDefault(playerId, "");
    }

    /** @return the state the player is now in. */
    public boolean toggle(Player player, String reason) {
        UUID id = player.getUniqueId();
        lastActivity.put(id, System.currentTimeMillis());
        if (away.remove(id)) {
            reasons.remove(id);
            announce(player, "players.afk-back", "players.afk-back-broadcast");
            return false;
        }

        away.add(id);
        if (reason.isEmpty()) {
            reasons.remove(id);
            announce(player, "players.afk-now", "players.afk-now-broadcast");
        } else {
            reasons.put(id, reason);
            announce(player, "players.afk-now-reason", "players.afk-now-reason-broadcast",
                    "reason", reason);
        }
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
        reasons.remove(event.getPlayer().getUniqueId());
        away.remove(event.getPlayer().getUniqueId());
    }

    private void touch(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (away.remove(player.getUniqueId())) {
            reasons.remove(player.getUniqueId());
            announce(player, "players.afk-back", "players.afk-back-broadcast");
        }
    }

    private void restartSweeper() {
        if (sweeper != null) {
            sweeper.cancel();
            sweeper = null;
        }
        if (settings.autoAfk() || settings.kicks()) {
            sweeper = schedulers.globalTimer(this::sweep, SWEEP_TICKS, SWEEP_TICKS);
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
                schedulers.withEntity(player,
                        () -> player.kick(messages.render("players.afk-kicked")));
                continue;
            }
            if (current.autoAfk() && idle >= afkAfter && away.add(entry.getKey())) {
                announce(player, "players.afk-now", "players.afk-now-broadcast");
            }
        }
    }

    private void announce(Player player, String toPlayer, String toEveryone,
                          String... extra) {
        messages.send(player, toPlayer, extra);
        if (!settings.broadcast()) {
            return;
        }

        String[] placeholders = new String[extra.length + 2];
        placeholders[0] = "player";
        placeholders[1] = player.getName();
        System.arraycopy(extra, 0, placeholders, 2, extra.length);
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(player)) {
                messages.send(other, toEveryone, placeholders);
            }
        }
    }
}
