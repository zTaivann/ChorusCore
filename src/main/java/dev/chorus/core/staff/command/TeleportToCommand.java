package dev.chorus.core.staff.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.teleport.Coordinates;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        // Three numbers are coordinates, which is what the vanilla command does with them
        // and what anybody typing /tp 100 64 -200 is expecting.
        if (Coordinates.areNumbers(args, 0)) {
            toCoordinates(sender, args, 0, self(sender));
            return;
        }
        if (args.length == 4 && Coordinates.areNumbers(args, 1)) {
            toCoordinates(sender, args, 1, online(sender, args[0]));
            return;
        }
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
        teleports.teleport(traveller, destination.getLocation(), rules(), name(), () -> {
            messages.send(sender, "staff.tp-done",
                    "player", traveller.getName(), "target", destination.getName());
            if (!traveller.equals(sender)) {
                messages.send(traveller, "staff.tp-moved", "target", destination.getName());
            }
        });
    }

    /** {@code /tp <x> <y> <z>}, and {@code /tp <player> <x> <y> <z>} for somebody else. */
    private void toCoordinates(CommandSender sender, String[] args, int from,
                               @Nullable Player traveller) {
        if (traveller == null) {
            return;
        }
        Location destination =
                Coordinates.read(args, from, traveller.getWorld(), traveller.getLocation());
        if (destination == null) {
            messages.send(sender, "staff.tppos-numbers");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        settle(sender);
        teleports.teleport(traveller, destination, rules(), name(),
                TeleportService.Landing.EXACT, () -> messages.send(sender, "staff.tppos-done",
                        "player", traveller.getName(),
                        "x", String.valueOf(destination.getBlockX()),
                        "y", String.valueOf(destination.getBlockY()),
                        "z", String.valueOf(destination.getBlockZ()),
                        "world", destination.getWorld().getName()));
    }

    private @Nullable Player self(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages.send(sender, "error.players-only");
        return null;
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
