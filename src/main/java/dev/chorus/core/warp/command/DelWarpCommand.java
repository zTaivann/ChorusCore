package dev.chorus.core.warp.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.location.Names;
import dev.chorus.core.warp.WarpService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DelWarpCommand extends ChorusCommand {

    private final WarpService warps;
    private final Logger logger;

    public DelWarpCommand(CommandSupport support, WarpService warps, Logger logger) {
        super(support, "delwarp", "chorus.warp.delete");
        this.warps = warps;
        this.logger = logger;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (warps.all().isEmpty()) {
            messages.send(sender, "warp.none");
            return;
        }
        if (args.length == 0) {
            messages.send(sender, "warp.delete-usage");
            return;
        }

        String key = Names.normalise(args[0]);
        if (warps.find(key).isEmpty()) {
            messages.send(sender, "warp.unknown", "warp", key);
            return;
        }
        if (!ready(sender)) {
            return;
        }

        warps.delete(key).whenComplete((removed, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not delete the warp " + key, failure);
                messages.send(sender, "error.storage");
                return;
            }
            if (!removed) {
                messages.send(sender, "warp.unknown", "warp", key);
                return;
            }
            settle(sender);
            messages.send(sender, "warp.deleted", "warp", key);
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !allowed(sender)) {
            return List.of();
        }
        return startingWith(args[0], warps.all().stream().map(NamedLocation::name).toList());
    }
}
