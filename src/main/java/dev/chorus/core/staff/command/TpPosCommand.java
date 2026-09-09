package dev.chorus.core.staff.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** {@code /tppos <x> <y> <z> [world] [player]}. */
public final class TpPosCommand extends ChorusCommand {

    private final TeleportService teleports;

    public TpPosCommand(CommandSupport support, TeleportService teleports) {
        super(support, "tppos", "chorus.staff.tppos");
        this.teleports = teleports;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "staff.tppos-usage");
            return;
        }

        Player traveller = args.length >= 5 ? online(sender, args[4]) : self(sender);
        if (traveller == null) {
            return;
        }

        World world = traveller.getWorld();
        if (args.length >= 4) {
            world = sender.getServer().getWorld(args[3]);
            if (world == null) {
                messages.send(sender, "staff.tppos-world", "world", args[3]);
                return;
            }
        }

        Double x = parse(args[0]);
        Double y = parse(args[1]);
        Double z = parse(args[2]);
        if (x == null || y == null || z == null) {
            messages.send(sender, "staff.tppos-numbers");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        Location standing = traveller.getLocation();
        // Keep the direction they were already facing rather than snapping them north.
        Location destination = new Location(world, x, y, z, standing.getYaw(), standing.getPitch());

        settle(sender);
        teleports.teleport(traveller, destination, rules(), name());
        messages.send(sender, "staff.tppos-done",
                "player", traveller.getName(),
                "x", String.valueOf(destination.getBlockX()),
                "y", String.valueOf(destination.getBlockY()),
                "z", String.valueOf(destination.getBlockZ()),
                "world", world.getName());
    }

    private @Nullable Player self(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        messages.send(sender, "error.players-only");
        return null;
    }

    private static @Nullable Double parse(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 4) {
            List<String> worlds = new ArrayList<>();
            sender.getServer().getWorlds().forEach(world -> worlds.add(world.getName()));
            return startingWith(args[3], worlds);
        }
        if (args.length == 5) {
            return onlineNames(sender, args[4], true);
        }
        return List.of();
    }
}
