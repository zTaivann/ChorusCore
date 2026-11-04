package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.preference.PlayerPreference;
import dev.chorus.core.preference.PlayerPreferenceService;
import dev.chorus.core.request.TeleportRequest;
import dev.chorus.core.request.TeleportRequestService;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.entity.Player;

/** {@code /tpaall}: asks everybody online to come to you. */
public final class TpAllRequestCommand extends PlayerCommand {

    private final TeleportRequestService requests;
    private final TeleportService teleports;
    private final PlayerPreferenceService preferences;

    public TpAllRequestCommand(CommandSupport support, TeleportRequestService requests,
                               TeleportService teleports, PlayerPreferenceService preferences) {
        super(support, "tpaall", "chorus.tpa.all");
        this.requests = requests;
        this.teleports = teleports;
        this.preferences = preferences;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        int asked = 0;
        int came = 0;
        int timeout = requests.timeoutSeconds();
        long expires = System.currentTimeMillis() + timeout * 1000L;

        for (Player other : player.getServer().getOnlinePlayers()) {
            if (other.equals(player) || !player.canSee(other)) {
                continue;
            }
            if (preferences.isSet(other.getUniqueId(), PlayerPreference.TELEPORTS_BLOCKED)) {
                continue;
            }
            if (preferences.isSet(other.getUniqueId(), PlayerPreference.TELEPORTS_AUTOMATIC)) {
                teleports.teleport(other, player.getLocation(), rules(), name());
                came++;
                continue;
            }

            TeleportRequest request = new TeleportRequest(player.getUniqueId(),
                    other.getUniqueId(), TeleportRequest.Direction.TO_SENDER, expires);
            if (!requests.add(request)) {
                continue;
            }
            messages.send(other, "request.received-here", "player", player.getName());
            asked++;
        }

        if (asked == 0 && came == 0) {
            messages.send(player, "request.all-nobody");
            return;
        }

        settle(player);
        messages.send(player, "request.all-sent",
                "count", String.valueOf(asked),
                "came", String.valueOf(came),
                "seconds", String.valueOf(timeout));
    }
}
