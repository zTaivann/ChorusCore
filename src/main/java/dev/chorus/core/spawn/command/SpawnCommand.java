package dev.chorus.core.spawn.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.spawn.SpawnService;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@code /spawn}, or {@code /spawn <world>} for the spawn of somewhere else. */
public final class SpawnCommand extends PlayerCommand {

    private static final String WORLD_PERMISSION = "chorus.spawn.world";

    private final SpawnService spawn;
    private final TeleportService teleports;

    public SpawnCommand(CommandSupport support, SpawnService spawn, TeleportService teleports) {
        super(support, "spawn", "chorus.spawn.use");
        this.spawn = spawn;
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        World world = player.getWorld();
        if (args.length > 0) {
            if (!player.hasPermission(WORLD_PERMISSION)) {
                messages.send(player, "error.no-permission");
                return;
            }
            world = player.getServer().getWorld(args[0]);
            if (world == null) {
                messages.send(player, "spawn.no-such-world", "world", args[0]);
                return;
            }
        }

        Optional<NamedLocation> found = spawn.find(world);
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(WORLD_PERMISSION)) {
            return List.of();
        }
        List<String> worlds = new ArrayList<>();
        for (World world : sender.getServer().getWorlds()) {
            worlds.add(world.getName());
        }
        return startingWith(args[0], worlds);
    }
}
