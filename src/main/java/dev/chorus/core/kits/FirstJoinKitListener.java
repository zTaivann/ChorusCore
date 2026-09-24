package dev.chorus.core.kits;

import dev.chorus.core.locale.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Hands the first-join kit to somebody who has never played here before. */
final class FirstJoinKitListener implements Listener {

    private final KitService kits;
    private final Messages messages;
    private final Logger logger;

    FirstJoinKitListener(KitService kits, Messages messages, Logger logger) {
        this.kits = kits;
        this.messages = messages;
        this.logger = logger;
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
}
