package dev.chorus.core.staff.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TeleportHereCommand extends PlayerCommand {

    private final TeleportService teleports;

    public TeleportHereCommand(CommandSupport support, TeleportService teleports) {
        super(support, "tphere", "chorus.staff.tphere");
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "staff.tphere-usage");
            return;
        }

        Player target = online(player, args[0]);
        if (target == null) {
            return;
        }
        if (target.equals(player)) {
            messages.send(player, "staff.tp-self");
            return;
        }
        if (!ready(player)) {
            return;
        }

        settle(player);
        teleports.teleport(target, player.getLocation(), rules(), name(), () -> {
            messages.send(player, "staff.tphere-done", "player", target.getName());
            messages.send(target, "staff.tp-moved", "target", player.getName());
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? onlineNames(sender, args[0], false) : List.of();
    }
}
