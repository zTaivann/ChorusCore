package dev.chorus.core.staff.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** {@code /tp <target>} takes you there, {@code /tp <who> <target>} takes somebody else. */
public final class TeleportToCommand extends ChorusCommand {

    private final TeleportService teleports;

    public TeleportToCommand(CommandSupport support, TeleportService teleports) {
        super(support, "tp", "chorus.staff.tp");
        this.teleports = teleports;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0 || args.length > 2) {
            messages.send(sender, "staff.tp-usage");
            return;
        }

        Player traveller;
        Player destination;
        if (args.length == 1) {
            if (!(sender instanceof Player self)) {
                messages.send(sender, "error.players-only");
                return;
            }
            traveller = self;
            destination = online(sender, args[0]);
        } else {
            traveller = online(sender, args[0]);
            destination = traveller == null ? null : online(sender, args[1]);
        }
        if (traveller == null || destination == null) {
            return;
        }
        if (traveller.equals(destination)) {
            messages.send(sender, "staff.tp-self");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        settle(sender);
        teleports.teleport(traveller, destination.getLocation(), rules(), name());
        messages.send(sender, "staff.tp-done",
                "player", traveller.getName(), "target", destination.getName());
        if (!traveller.equals(sender)) {
            messages.send(traveller, "staff.tp-moved", "target", destination.getName());
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length < 1 || args.length > 2) {
            return List.of();
        }
        return onlineNames(sender, args[args.length - 1], true);
    }
}
