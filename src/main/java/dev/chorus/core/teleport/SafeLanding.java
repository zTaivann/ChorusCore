package dev.chorus.core.teleport;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Finds somewhere a player can actually stand near where they asked to go.
 *
 * <p>Warps and homes outlive the world around them: the platform gets mined, lava flows in, a
 * new build swallows the spot. Rather than dropping the player inside a wall or into a lake of
 * lava, the destination is nudged to the nearest place with a floor and room to stand.
 *
 * <p>The test is the player's own box against the boxes of the blocks it reaches, not whether
 * the block at their feet is passable. A player standing on a slab, a stair or a snow layer is
 * half a block into the one below them, and the coarser test would move them up a block every
 * time they went home.
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

    private static final double WIDTH = 0.6;
    private static final double HEIGHT = 1.8;

    /** Keeps a box that only touches a block from counting as being inside it. */
    private static final double EPSILON = 1.0E-4;

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

    /**
     * A candidate that many blocks away, on the floor of its block rather than at the original
     * height. Once the exact spot is out, whole blocks are the only sensible granularity.
     */
    private static @Nullable Location shifted(Location from, int blocks) {
        World world = from.getWorld();
        double y = Math.floor(from.getY()) + blocks;
        if (y - 1 < world.getMinHeight() || y + HEIGHT >= world.getMaxHeight()) {
            return null;
        }
        Location moved = from.clone();
        moved.setY(y);
        return moved;
    }

    /** Room for the player, something holding them up, and nothing that hurts. */
    private static boolean standable(Location at) {
        World world = at.getWorld();
        if (at.getY() - 1 < world.getMinHeight() || at.getY() + HEIGHT >= world.getMaxHeight()) {
            return false;
        }

        BoundingBox body = bodyAt(at);
        return clear(world, body) && supported(world, at);
    }

    private static BoundingBox bodyAt(Location at) {
        double half = WIDTH / 2 - EPSILON;
        return new BoundingBox(
                at.getX() - half, at.getY() + EPSILON, at.getZ() - half,
                at.getX() + half, at.getY() + HEIGHT - EPSILON, at.getZ() + half);
    }

    /** Nothing solid and nothing deadly anywhere the player's box reaches. */
    private static boolean clear(World world, BoundingBox body) {
        for (int x = (int) Math.floor(body.getMinX()); x <= (int) Math.floor(body.getMaxX()); x++) {
            for (int y = (int) Math.floor(body.getMinY()); y <= (int) Math.floor(body.getMaxY()); y++) {
                for (int z = (int) Math.floor(body.getMinZ()); z <= (int) Math.floor(body.getMaxZ()); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (deadly(block)) {
                        return false;
                    }
                    if (!block.isPassable() && block.getBoundingBox().overlaps(body)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Something directly under the feet, reaching up to them.
     *
     * <p>The block below is the one that holds a player up on flat ground; on a slab or a
     * stair it is the block their feet are nominally inside, whose top happens to be exactly
     * where they stand.
     */
    private static boolean supported(World world, Location at) {
        Block under = world.getBlockAt(
                at.getBlockX(), (int) Math.floor(at.getY() - EPSILON), at.getBlockZ());
        if (under.isPassable() || deadly(under)) {
            return false;
        }
        return under.getBoundingBox().getMaxY() >= at.getY() - EPSILON;
    }

    private static boolean deadly(Block block) {
        return DEADLY.contains(block.getType().name());
    }
}
