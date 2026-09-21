package dev.chorus.core.command;

import dev.chorus.core.locale.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The second press on anything that cannot be taken back. */
public final class Confirmations implements Listener {

    private static final String BYPASS = "chorus.bypass.confirm";

    private final Messages messages;
    private final Map<UUID, Pending> pending = new HashMap<>();

    private volatile int seconds;

    public Confirmations(Messages messages, int seconds) {
        this.messages = messages;
        this.seconds = seconds;
    }

    public void apply(int updated) {
        this.seconds = updated;
    }

    /** Whether the action should happen now. */
    public boolean confirmed(CommandSender sender, String token, String warningKey,
                             String... placeholders) {
        int window = seconds;
        if (window <= 0 || !(sender instanceof Player player) || player.hasPermission(BYPASS)) {
            return true;
        }

        long now = System.currentTimeMillis();
        Pending waiting = pending.get(player.getUniqueId());
        if (waiting != null && waiting.token.equals(token) && waiting.expiresAt > now) {
            pending.remove(player.getUniqueId());
            return true;
        }

        pending.put(player.getUniqueId(), new Pending(token, now + window * 1000L));
        messages.send(player, warningKey, placeholders);
        messages.send(player, "core.confirm-again", "seconds", String.valueOf(window));
        return false;
    }

    public void forget(UUID playerId) {
        pending.remove(playerId);
    }

    public void clear() {
        pending.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private record Pending(String token, long expiresAt) {
    }
}
