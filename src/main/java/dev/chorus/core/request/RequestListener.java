package dev.chorus.core.request;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

final class RequestListener implements Listener {

    private final TeleportRequestService requests;

    RequestListener(TeleportRequestService requests) {
        this.requests = requests;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requests.forget(event.getPlayer().getUniqueId());
    }
}
