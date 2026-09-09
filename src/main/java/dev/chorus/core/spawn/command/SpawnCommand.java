package dev.chorus.core.spawn.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.spawn.SpawnService;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class SpawnCommand extends PlayerCommand {

    private final SpawnService spawn;
    private final TeleportService teleports;

    public SpawnCommand(CommandSupport support, SpawnService spawn, TeleportService teleports) {
        super(support, "spawn", "chorus.spawn.use");
        this.spawn = spawn;
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        Optional<NamedLocation> found = spawn.find(player.getWorld());
        if (found.isEmpty()) {
            messages.send(player, "spawn.not-set");
            return;
        }

        NamedLocation point = found.get();
        Location destination = point.toLocation();
        if (destination == null) {
            messages.send(player, "spawn.world-missing", "world", point.worldName());
            return;
        }
        if (!ready(player)) {
            return;
        }

        teleports.teleport(player, destination, rules(), name(), () -> {
            settle(player);
            messages.send(player, "spawn.teleported");
        });
    }
}
