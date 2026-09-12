package dev.chorus.core.mail;

import dev.chorus.core.locale.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Tells a player how much is waiting for them, once, when they arrive. */
public final class MailListener implements Listener {

    private final MailService mail;
    private final Messages messages;

    public MailListener(MailService mail, Messages messages) {
        this.mail = mail;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        mail.unread(player.getUniqueId()).whenComplete((count, failure) -> {
            if (failure != null || count == null || count == 0 || !player.isOnline()) {
                return;
            }
            messages.send(player, "chat.mail-waiting", "count", String.valueOf(count));
        });
    }
}
