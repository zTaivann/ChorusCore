package dev.chorus.core.spawn.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.spawn.SpawnService;
import org.bukkit.entity.Player;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class SetSpawnCommand extends PlayerCommand {

    private final SpawnService spawn;
    private final Logger logger;

    public SetSpawnCommand(CommandSupport support, SpawnService spawn, Logger logger) {
        super(support, "setspawn", "chorus.spawn.set");
        this.spawn = spawn;
        this.logger = logger;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean perWorld = spawn.settings().perWorld();
        String world = player.getWorld().getName();

        spawn.moveTo(player.getLocation()).whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not save the spawn point", failure);
                messages.send(player, "error.storage");
                return;
            }
            settle(player);
            if (perWorld) {
                messages.send(player, "spawn.set-world", "world", world);
            } else {
                messages.send(player, "spawn.set");
            }
        });
    }
}
