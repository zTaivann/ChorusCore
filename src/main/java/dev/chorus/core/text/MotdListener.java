package dev.chorus.core.text;

import dev.chorus.core.text.command.TextCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Shows the message of the day to whoever just walked in. */
public final class MotdListener implements Listener {

    private final TextCommand motd;

    private volatile boolean onJoin = true;

    public MotdListener(TextCommand motd) {
        this.motd = motd;
    }

    public void apply(boolean showOnJoin) {
        this.onJoin = showOnJoin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (onJoin && !motd.text().isEmpty()) {
            motd.show(event.getPlayer(), motd.text().opening(), 1);
        }
    }
}
