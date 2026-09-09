package dev.chorus.core.warp.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.location.Names;
import dev.chorus.core.teleport.TeleportService;
import dev.chorus.core.warp.WarpService;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public final class WarpCommand extends PlayerCommand {

    private final WarpService warps;
    private final TeleportService teleports;

    public WarpCommand(CommandSupport support, WarpService warps, TeleportService teleports) {
        super(support, "warp", "chorus.warp.use");
        this.warps = warps;
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        List<NamedLocation> usable = warps.visibleTo(player);
        if (usable.isEmpty()) {
            messages.send(player, "warp.none");
            return;
        }
        if (args.length == 0) {
            messages.send(player, "warp.use-usage");
            return;
        }

        String key = Names.normalise(args[0]);
        Optional<NamedLocation> found = warps.find(key);
        if (found.isEmpty()) {
            messages.send(player, "warp.unknown", "warp", key);
            return;
        }
        if (!warps.settings().canUse(player, key)) {
            messages.send(player, "warp.locked", "warp", key);
            return;
        }

        NamedLocation warp = found.get();
        Location destination = warp.toLocation();
        if (destination == null) {
            messages.send(player, "warp.world-missing", "world", warp.worldName());
            return;
        }
        if (!ready(player)) {
            return;
        }

        teleports.teleport(player, destination, rules(), name(), () -> {
            settle(player);
            messages.send(player, "warp.teleported", "warp", warp.name());
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return startingWith(args[0], warps.visibleTo(sender).stream().map(NamedLocation::name).toList());
    }
}
