package dev.chorus.core.utility.powertool;

import dev.chorus.core.flags.PlayerFlag;
import dev.chorus.core.flags.PlayerFlagService;
import dev.chorus.core.locale.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs what a player has tied to the item in their hand. */
public final class PowertoolListener implements Listener {

    private static final String PERMISSION = "chorus.utility.powertool";
    private static final String SUBJECT = "%player%";

    private final Powertools powertools;
    private final PlayerFlagService flags;
    private final Messages messages;
    private final Logger logger;

    /** Whoever is in the middle of a powertool, so one that clicks cannot call itself. */
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public PowertoolListener(Powertools powertools, PlayerFlagService flags, Messages messages,
                             Logger logger) {
        this.powertools = powertools;
        this.flags = flags;
        this.messages = messages;
        this.logger = logger;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            powertools.load(event.getUniqueId());
        } catch (SQLException exception) {
            logger.log(Level.WARNING,
                    "Could not load the powertools of " + event.getName(), exception);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        powertools.unload(event.getPlayer().getUniqueId());
        running.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        fire(event.getPlayer(), event.getMaterial(), null, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        String subject = event.getRightClicked() instanceof Player clicked ? clicked.getName() : null;
        fire(player, player.getInventory().getItemInMainHand().getType(), subject, event);
    }

    private void fire(Player player, Material material, @Nullable String subject,
                      Cancellable event) {
        if (material.isAir() || !player.hasPermission(PERMISSION)
                || flags.isSet(player.getUniqueId(), PlayerFlag.POWERTOOLS_OFF)) {
            return;
        }

        List<String> commands = powertools.on(player.getUniqueId(), material);
        if (commands.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        if (!running.add(player.getUniqueId())) {
            return;
        }
        try {
            for (String command : commands) {
                run(player, command, subject);
            }
        } finally {
            running.remove(player.getUniqueId());
        }
    }

    private void run(Player player, String command, @Nullable String subject) {
        if (!command.contains(SUBJECT)) {
            player.performCommand(command);
            return;
        }
        if (subject == null) {
            messages.send(player, "utility.powertool-needs-player");
            return;
        }
        player.performCommand(command.replace(SUBJECT, subject));
    }
}
