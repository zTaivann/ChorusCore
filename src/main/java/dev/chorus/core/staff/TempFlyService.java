package dev.chorus.core.staff;

import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.ChorusTask;
import dev.chorus.core.platform.Schedulers;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flight that runs out.
 *
 * <p>Deliberately not remembered between sessions: a player who logs out lands, and the
 * alternative is a plugin that has to decide whether a timer keeps ticking while nobody is
 * there to use it. Every answer to that is a surprise to somebody.
 */
public final class TempFlyService implements Listener {

    private static final long TICKS_PER_SECOND = 20L;
    private static final long WARNING_SECONDS = 10;

    private final Schedulers schedulers;
    private final Messages messages;
    private final Map<UUID, Grant> grants = new HashMap<>();

    TempFlyService(Messages messages, Schedulers schedulers) {
        this.messages = messages;
        this.schedulers = schedulers;
    }

    /** Replaces any grant already running, so a second call extends rather than stacks. */
    public void grant(Player player, int seconds) {
        cancel(player.getUniqueId());
        schedulers.entity(player, () -> player.setAllowFlight(true));

        ChorusTask warning = seconds > WARNING_SECONDS
                ? schedulers.entityLater(player,
                () -> messages.send(player, "staff.tempfly-ending",
                        "seconds", String.valueOf(WARNING_SECONDS)),
                (seconds - WARNING_SECONDS) * TICKS_PER_SECOND)
                : null;

        ChorusTask expiry = schedulers.entityLater(player, () -> {
            grants.remove(player.getUniqueId());
            revoke(player);
            messages.send(player, "staff.tempfly-expired");
        }, seconds * TICKS_PER_SECOND);

        grants.put(player.getUniqueId(), new Grant(expiry, warning));
    }

    /** @return true when there was a grant to take away. */
    public boolean take(Player player) {
        if (!cancel(player.getUniqueId())) {
            return false;
        }
        schedulers.entity(player, () -> revoke(player));
        return true;
    }

    public boolean hasGrant(UUID playerId) {
        return grants.containsKey(playerId);
    }

    public void shutdown() {
        grants.values().forEach(Grant::cancelAll);
        grants.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    private boolean cancel(UUID playerId) {
        Grant grant = grants.remove(playerId);
        if (grant == null) {
            return false;
        }
        grant.cancelAll();
        return true;
    }

    /** Creative flight is not ours to take away, whoever granted it. */
    private static void revoke(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    private record Grant(ChorusTask expiry, ChorusTask warning) {

        void cancelAll() {
            expiry.cancel();
            if (warning != null) {
                warning.cancel();
            }
        }
    }
}
