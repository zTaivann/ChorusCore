package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.request.TeleportRequest;
import dev.chorus.core.request.TeleportRequestService;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.List;

public final class TpCancelCommand extends PlayerCommand {

    private final TeleportRequestService requests;

    public TpCancelCommand(CommandSupport support, TeleportRequestService requests) {
        super(support, "tpcancel", "chorus.tpa.use");
        this.requests = requests;
    }

    @Override
    protected void execute(Player player, String[] args) {
        Server server = player.getServer();

        if (args.length == 0) {
            List<TeleportRequest> cancelled = requests.removeAllSentBy(player.getUniqueId());
            if (cancelled.isEmpty()) {
                messages.send(player, "request.none-sent");
                return;
            }
            cancelled.forEach(request -> announce(player, server, request));
            return;
        }

        // Hidden players are treated as not being here; see TeleportResponseCommand.
        Player target = server.getPlayerExact(args[0]);
        if (target == null || !player.canSee(target)) {
            messages.send(player, "error.player-not-found", "player", args[0]);
            return;
        }

        TeleportRequest request = requests.remove(target.getUniqueId(), player.getUniqueId());
        if (request == null) {
            messages.send(player, "request.none-to", "player", target.getName());
            return;
        }
        announce(player, server, request);
    }

    private void announce(Player sender, Server server, TeleportRequest request) {
        Player target = server.getPlayer(request.target());
        if (target == null) {
            return;
        }
        messages.send(sender, "request.cancelled", "player", target.getName());
        messages.send(target, "request.cancelled-by", "player", sender.getName());
    }
}
