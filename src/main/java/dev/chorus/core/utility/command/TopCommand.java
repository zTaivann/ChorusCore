package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.teleport.TeleportService;
import dev.chorus.core.utility.UtilityService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class TopCommand extends PlayerCommand {

    private final UtilityService utility;
    private final TeleportService teleports;

    public TopCommand(CommandSupport support, UtilityService utility, TeleportService teleports) {
        super(support, "top", "chorus.utility.top");
        this.utility = utility;
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        World world = player.getWorld();
        if (world.getEnvironment() == World.Environment.NETHER
                && !utility.settings().top().allowInNether()) {
            // The highest block in the Nether is the bedrock roof, which is not somewhere
            // anyone wants to be dropped.
            messages.send(player, "utility.top-unavailable");
            return;
        }

        Location destination = highestStanding(player.getLocation());
        if (destination == null) {
            messages.send(player, "utility.top-none");
            return;
        }
        if (!ready(player)) {
            return;
        }

        teleports.teleport(player, destination, rules(), name(), () -> {
            settle(player);
            messages.send(player, "utility.top-teleported",
                    "y", String.valueOf(destination.getBlockY()));
        });
    }

    /**
     * Walks down from the top of the world for the first solid block with room to stand on
     * it. Starting from {@code getHighestBlockYAt} alone would put a player inside whatever
     * is directly above, such as leaves or a roof.
     */
    private static Location highestStanding(Location from) {
        World world = from.getWorld();
        int x = from.getBlockX();
        int z = from.getBlockZ();
        int floor = from.getBlockY();

        for (int y = Math.min(world.getMaxHeight() - 3, world.getHighestBlockYAt(x, z)); y >= floor; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid()) {
                continue;
            }
            if (world.getBlockAt(x, y + 1, z).isPassable()
                    && world.getBlockAt(x, y + 2, z).isPassable()) {
                Location standing = from.clone();
                standing.setY(y + 1.0);
                return standing;
            }
        }
        return null;
    }
}
