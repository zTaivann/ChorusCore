package dev.chorus.core.staff;

import dev.chorus.core.locale.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Holds a player still while staff talk to them.
 *
 * <p>The hold is on the block they are standing on rather than their exact position, so
 * looking around and small nudges from the server still work and the client is never fighting
 * the server for control of the camera.
 *
 * <p>Frozen players keep the state if they reconnect, because logging out to escape being
 * frozen is the first thing anybody tries.
 */
public final class FreezeService implements Listener {

    private static final long REMINDER_TICKS = 20L * 3;

    private final Plugin plugin;
    private final Messages messages;
    private final Set<UUID> frozen = new HashSet<>();

    private BukkitTask reminder;

    FreezeService(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    void start() {
        reminder = plugin.getServer().getScheduler().runTaskTimer(plugin, this::remind,
                REMINDER_TICKS, REMINDER_TICKS);
    }

    public boolean isFrozen(UUID playerId) {
        return frozen.contains(playerId);
    }

    /** @return the state the player is now in. */
    public boolean toggle(Player player) {
        if (frozen.remove(player.getUniqueId())) {
            return false;
        }
        frozen.add(player.getUniqueId());
        return true;
    }

    public void shutdown() {
        if (reminder != null) {
            reminder.cancel();
        }
        frozen.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Runs for every player every tick, so the cheapest possible exit comes first.
        if (frozen.isEmpty() || !frozen.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getTo() != null && sameBlock(event)) {
            return;
        }
        event.setTo(event.getFrom());
    }

    /** Being pulled somewhere by a plugin or a portal would undo the freeze just as well. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!frozen.isEmpty() && frozen.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "staff.freeze-held");
        }
    }

    /** Otherwise /spawn, /home and every other way out is still open. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (frozen.isEmpty() || !frozen.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        String typed = event.getMessage().toLowerCase(Locale.ROOT);
        if (typed.startsWith("/msg") || typed.startsWith("/r ") || typed.equals("/r")) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "staff.freeze-no-commands");
    }

    private void remind() {
        if (frozen.isEmpty()) {
            return;
        }
        for (UUID playerId : frozen) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendActionBar(messages.render("staff.freeze-reminder"));
            }
        }
    }

    private static boolean sameBlock(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ();
    }
}
