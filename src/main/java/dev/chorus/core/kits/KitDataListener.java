package dev.chorus.core.kits;

import dev.chorus.core.locale.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

final class KitDataListener implements Listener {

    private final KitService kits;
    private final Messages messages;
    private final Logger logger;

    KitDataListener(KitService kits, Messages messages, Logger logger) {
        this.kits = kits;
        this.messages = messages;
        this.logger = logger;
    }

    /** Loaded before the player is in, so a cooldown is never briefly wrong. */
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            kits.load(event.getUniqueId());
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Could not load the kit history of " + event.getName(), exception);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.render("error.load-failed"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Kit kit = kits.firstJoinKit();
        Player player = event.getPlayer();
        if (kit == null || player.hasPlayedBefore()) {
            return;
        }
        kits.give(player, kit).whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not give the first-join kit to " + player.getName(), failure);
                return;
            }
            messages.send(player, "kits.received", "kit", kit.name());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        kits.unload(event.getPlayer().getUniqueId());
    }
}
