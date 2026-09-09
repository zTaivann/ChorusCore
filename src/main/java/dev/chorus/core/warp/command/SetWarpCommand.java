package dev.chorus.core.warp.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.location.Names;
import dev.chorus.core.warp.WarpService;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class SetWarpCommand extends PlayerCommand {

    private final WarpService warps;
    private final Logger logger;

    public SetWarpCommand(CommandSupport support, WarpService warps, Logger logger) {
        super(support, "setwarp", "chorus.warp.set");
        this.warps = warps;
        this.logger = logger;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "warp.set-usage");
            return;
        }
        if (!warps.isValidName(args[0])) {
            messages.send(player, "warp.invalid-name",
                    "max", String.valueOf(warps.settings().maxNameLength()));
            return;
        }

        String key = Names.normalise(args[0]);
        boolean replacing = warps.find(key).isPresent();
        if (!ready(player)) {
            return;
        }

        warps.save(NamedLocation.create(key, player.getLocation()))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        logger.log(Level.SEVERE, "Could not save the warp " + key, failure);
                        messages.send(player, "error.storage");
                        return;
                    }
                    settle(player);
                    messages.send(player, replacing ? "warp.updated" : "warp.created", "warp", key);
                });
    }
}
