package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** {@code /back}, or {@code /back <n>} to go further than one step down the history. */
public final class BackCommand extends PlayerCommand {

    private final TeleportService teleports;

    public BackCommand(CommandSupport support, TeleportService teleports) {
        super(support, "back", "chorus.back.use");
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        int depth = teleports.historyDepth(player.getUniqueId());
        if (depth == 0) {
            messages.send(player, "back.none");
            return;
        }

        int steps = args.length == 0 ? 1 : Numbers.integer(args[0], -1);
        if (steps < 1 || steps > depth) {
            messages.send(player, "back.out-of-range", "depth", String.valueOf(depth));
            return;
        }

        Location previous = teleports.previousLocation(player.getUniqueId(), steps).orElse(null);
        if (previous == null) {
            messages.send(player, "back.none");
            return;
        }
        if (!previous.isWorldLoaded()) {
            messages.send(player, "back.world-missing");
            return;
        }
        if (!ready(player)) {
            return;
        }

        // Taken off the history only once the command is going through.
        Location destination = teleports.takePrevious(player.getUniqueId(), steps).orElse(previous);

        teleports.teleport(player, destination, rules(), name(), () -> {
            settle(player);
            messages.send(player, "back.teleported");
        });
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> steps = new ArrayList<>();
        for (int step = 1; step <= teleports.historyDepth(player.getUniqueId()); step++) {
            steps.add(String.valueOf(step));
        }
        return startingWith(args[0], steps);
    }
}
