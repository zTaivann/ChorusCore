package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /bottom}: the lowest place under you with room to stand.
 *
 * <p>Walks up from the floor of the world rather than down from the player, so it finds the
 * cave under them rather than the ledge they are already on.
 */
public final class BottomCommand extends PlayerCommand {

    private final TeleportService teleports;

    public BottomCommand(CommandSupport support, TeleportService teleports) {
        super(support, "bottom", "chorus.utility.bottom");
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        Location destination = lowestStanding(player.getLocation());
        if (destination == null) {
            messages.send(player, "utility.bottom-none");
            return;
        }
        if (!ready(player)) {
            return;
        }

        teleports.teleport(player, destination, rules(), name(), () -> {
            settle(player);
            messages.send(player, "utility.bottom-teleported",
                    "y", String.valueOf(destination.getBlockY()));
        });
    }

    private static @Nullable Location lowestStanding(Location from) {
        World world = from.getWorld();
        int x = from.getBlockX();
        int z = from.getBlockZ();
        int ceiling = from.getBlockY();

        for (int y = world.getMinHeight(); y < ceiling; y++) {
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
