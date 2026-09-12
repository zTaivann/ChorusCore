package dev.chorus.core.backup;

import dev.chorus.core.locale.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The moments a player can lose everything without asking to.
 *
 * <p>Death is read at the lowest priority, before another plugin has had a chance to empty
 * the drops or keep the inventory. What is copied is what they had when they died.
 */
public final class BackupListener implements Listener {

    private final InventoryBackups backups;
    private final Messages messages;

    public BackupListener(InventoryBackups backups, Messages messages) {
        this.backups = backups;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        backups.take(player, BackupReason.DEATH, "", player.getName(),
                cause(player), killer(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        backups.take(player, BackupReason.JOIN, "", player.getName());
        backups.applyWaiting(player, (parts, actor) ->
                messages.send(player, "items.restore-waiting", "player", actor));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        backups.take(player, BackupReason.QUIT, "", player.getName());
    }

    /** Named after the world they left, which is the one the items went missing in. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        backups.take(player, BackupReason.WORLD, event.getFrom().getName(), player.getName());
    }

    /** What did it, written the way a person reads it: "entity attack", not "ENTITY_ATTACK". */
    private static @Nullable String cause(Player player) {
        EntityDamageEvent damage = player.getLastDamageCause();
        return damage == null
                ? null
                : damage.getCause().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static @Nullable String killer(Player player) {
        Player killer = player.getKiller();
        return killer == null ? null : killer.getName();
    }
}
