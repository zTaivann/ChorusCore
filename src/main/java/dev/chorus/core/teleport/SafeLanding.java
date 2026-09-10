package dev.chorus.core.teleport;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Finds somewhere a player can actually stand near where they asked to go.
 *
 * <p>Warps and homes outlive the world around them: the platform gets mined, lava flows in,
 * a new build swallows the spot. Rather than dropping the player inside a wall or into a lake
 * of lava, the destination is nudged to the nearest place with a floor and two blocks of air.
 *
 * <p>Materials are matched by name rather than by enum constant, because the list of blocks
 * that hurt has grown over the versions and a constant that does not exist yet will not
 * compile. A name this server has never heard of simply never matches.
 */
public final class SafeLanding {

    private static final Set<String> DEADLY = Set.of(
            "LAVA", "FIRE", "SOUL_FIRE", "CAMPFIRE", "SOUL_CAMPFIRE", "MAGMA_BLOCK", "CACTUS",
            "SWEET_BERRY_BUSH", "WITHER_ROSE", "POWDER_SNOW", "END_PORTAL", "NETHER_PORTAL",
            "END_GATEWAY", "POINTED_DRIPSTONE");

    private SafeLanding() {
    }

    /**
     * The given spot if it will do, otherwise the closest one above or below it within
     * {@code radius} blocks, otherwise null.
     */
    public static @Nullable Location nearest(Location wanted, int radius) {
        if (standable(wanted)) {
            return wanted;
        }

        // Upwards first: being buried is the common case, and the surface is up.
        for (int offset = 1; offset <= radius; offset++) {
            Location above = shifted(wanted, offset);
            if (above != null && standable(above)) {
                return above;
            }
            Location below = shifted(wanted, -offset);
            if (below != null && standable(below)) {
                return below;
            }
        }
        return null;
    }

    private static @Nullable Location shifted(Location from, int blocks) {
        World world = from.getWorld();
        double y = from.getY() + blocks;
        if (y < world.getMinHeight() || y > world.getMaxHeight() - 2) {
            return null;
        }
        Location moved = from.clone();
        moved.setY(y);
        return moved;
    }

    /** Two blocks of room to stand in, something solid under the feet, and nothing that hurts. */
    private static boolean standable(Location at) {
        World world = at.getWorld();
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        if (y - 1 < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }

        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block floor = world.getBlockAt(x, y - 1, z);

        return feet.isPassable() && head.isPassable() && floor.getType().isSolid()
                && !deadly(feet) && !deadly(head) && !deadly(floor);
    }

    private static boolean deadly(Block block) {
        return DEADLY.contains(block.getType().name());
    }
}
