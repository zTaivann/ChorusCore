package dev.chorus.core.world.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * {@code /world [name]}: lists the worlds, or goes to one.
 *
 * <p>A world can be locked behind {@code chorus.world.go.<name>} by turning per-world
 * permissions on, which is how a creative or event world stays shut without a second plugin.
 */
public final class WorldCommand extends PlayerCommand {

    private static final String PER_WORLD = "chorus.world.go.";

    private final TeleportService teleports;
    private final BooleanSupplier perWorldPermission;

    public WorldCommand(CommandSupport support, TeleportService teleports,
                        BooleanSupplier perWorldPermission) {
        super(support, "world", "chorus.world.go");
        this.teleports = teleports;
        this.perWorldPermission = perWorldPermission;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            list(player);
            return;
        }

        World world = player.getServer().getWorld(args[0]);
        if (world == null) {
            messages.send(player, "world.not-found", "world", args[0]);
            return;
        }
        if (world.equals(player.getWorld())) {
            messages.send(player, "world.already-there", "world", world.getName());
            return;
        }
        if (!allowedIn(player, world)) {
            messages.send(player, "error.no-permission");
            return;
        }
        if (!ready(player)) {
            return;
        }

        Location destination = world.getSpawnLocation();
        teleports.teleport(player, destination, rules(), name(), () -> {
            settle(player);
            messages.send(player, "world.went", "world", world.getName());
        });
    }

    private void list(Player player) {
        List<World> worlds = player.getServer().getWorlds();
        messages.send(player, "world.list-header", "count", String.valueOf(worlds.size()));
        for (World world : worlds) {
            messages.send(player, "world.list-entry",
                    "world", world.getName(),
                    "environment", world.getEnvironment().name().toLowerCase(Locale.ROOT)
                            .replace('_', ' '),
                    "players", String.valueOf(world.getPlayers().size()));
        }
    }

    private boolean allowedIn(Player player, World world) {
        return !perWorldPermission.getAsBoolean()
                || player.hasPermission(PER_WORLD + world.getName().toLowerCase(Locale.ROOT));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (World world : sender.getServer().getWorlds()) {
            if (allowedIn(player, world)) {
                names.add(world.getName());
            }
        }
        return startingWith(args[0], names);
    }
}
