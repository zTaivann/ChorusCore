package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.flags.PlayerFlag;
import dev.chorus.core.flags.PlayerFlagService;
import dev.chorus.core.request.TeleportRequest;
import dev.chorus.core.request.TeleportRequestService;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.entity.Player;

/**
 * {@code /tpaall}: asks everybody online to come to you.
 *
 * <p>Unlike {@code /tpall}, which simply moves them, this one asks. Anybody with teleport
 * requests switched off is skipped in silence, and anybody who accepts everything comes
 * straight away.
 */
public final class TpAllRequestCommand extends PlayerCommand {

    private final TeleportRequestService requests;
    private final TeleportService teleports;
    private final PlayerFlagService flags;

    public TpAllRequestCommand(CommandSupport support, TeleportRequestService requests,
                               TeleportService teleports, PlayerFlagService flags) {
        super(support, "tpaall", "chorus.tpa.all");
        this.requests = requests;
        this.teleports = teleports;
        this.flags = flags;
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
            if (flags.isSet(other.getUniqueId(), PlayerFlag.TELEPORTS_BLOCKED)) {
                continue;
            }
            if (flags.isSet(other.getUniqueId(), PlayerFlag.TELEPORTS_AUTOMATIC)) {
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
