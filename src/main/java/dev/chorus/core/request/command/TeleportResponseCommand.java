package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.request.TeleportRequest;
import dev.chorus.core.request.TeleportRequestService;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Backs both /tpaccept and /tpdeny. With no argument it answers the newest request. */
public final class TeleportResponseCommand extends PlayerCommand {

    private final TeleportRequestService requests;
    private final TeleportService teleports;
    private final boolean accept;

    public TeleportResponseCommand(CommandSupport support, TeleportRequestService requests,
                                   TeleportService teleports, boolean accept,
                                   String name, String permission) {
        super(support, name, permission);
        this.requests = requests;
        this.teleports = teleports;
        this.accept = accept;
    }

    @Override
    protected void execute(Player player, String[] args) {
        Server server = player.getServer();
        if (requests.incoming(player.getUniqueId()).isEmpty()) {
            messages.send(player, "request.none");
            return;
        }
        if (!ready(player)) {
            return;
        }

        TeleportRequest request;
        if (args.length == 0) {
            request = requests.removeLatest(player.getUniqueId());
            if (request == null) {
                messages.send(player, "request.none");
                return;
            }
        } else {
            Player from = server.getPlayerExact(args[0]);
            if (from == null) {
                messages.send(player, "error.player-not-found", "player", args[0]);
                return;
            }
            request = requests.remove(player.getUniqueId(), from.getUniqueId());
            if (request == null) {
                messages.send(player, "request.none-from", "player", from.getName());
                return;
            }
        }

        Player sender = server.getPlayer(request.sender());
        if (sender == null) {
            messages.send(player, "request.none");
            return;
        }

        if (!accept) {
            settle(player);
            messages.send(player, "request.denied", "player", sender.getName());
            messages.send(sender, "request.denied-by", "player", player.getName());
            return;
        }

        settle(player);
        messages.send(player, "request.accepted", "player", sender.getName());
        messages.send(sender, "request.accepted-by", "player", player.getName());

        Player traveller = server.getPlayer(request.traveller());
        Player anchor = server.getPlayer(request.anchor());
        if (traveller != null && anchor != null) {
            teleports.teleport(traveller, anchor.getLocation(), rules(), name());
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (TeleportRequest waiting : requests.incoming(player.getUniqueId())) {
            Player from = player.getServer().getPlayer(waiting.sender());
            if (from != null) {
                names.add(from.getName());
            }
        }
        return startingWith(args[0], names);
    }
}
