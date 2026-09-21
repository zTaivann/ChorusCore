package dev.chorus.core.teleport;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Finds somewhere a player can actually stand near where they asked to go. */
public final class SafeLanding {

    /** Matched by name: a block a version does not have simply never matches. */
    private static final Set<String> DEADLY = Set.of(
            "LAVA", "FIRE", "SOUL_FIRE", "CAMPFIRE", "SOUL_CAMPFIRE", "MAGMA_BLOCK", "CACTUS",
            "SWEET_BERRY_BUSH", "WITHER_ROSE", "POWDER_SNOW", "END_PORTAL", "NETHER_PORTAL",
            "END_GATEWAY", "POINTED_DRIPSTONE");

    private static final String WATER = "WATER";

    private static final double WIDTH = 0.6;
    private static final double HEIGHT = 1.8;

    /** Keeps a box that only touches a block from counting as being inside it. */
    private static final double EPSILON = 1.0E-4;

    private SafeLanding() {
    }

    /**
     * The given spot if it will do, otherwise the closest one above or below it within
     * {@code radius} blocks, otherwise null.
     *
     * @param needsFloor false for a player who can fly, who needs the room and not the ground
     */
    public static @Nullable Location nearest(Location wanted, int radius, boolean needsFloor) {
        if (standable(wanted, needsFloor)) {
            return wanted;
        }

        // Upwards first: being buried is the common case, and the surface is up.
        for (int offset = 1; offset <= radius; offset++) {
            Location above = shifted(wanted, offset);
            if (above != null && standable(above, needsFloor)) {
                return above;
            }
            Location below = shifted(wanted, -offset);
            if (below != null && standable(below, needsFloor)) {
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
    private static boolean standable(Location at, boolean needsFloor) {
        World world = at.getWorld();
        if (at.getY() - 1 < world.getMinHeight() || at.getY() + HEIGHT >= world.getMaxHeight()) {
            return false;
        }

        BoundingBox body = bodyAt(at);
        return clear(world, body) && (!needsFloor || supported(world, at));
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

    /** Something directly under the feet, reaching up to them. */
    private static boolean supported(World world, Location at) {
        Block under = world.getBlockAt(
                at.getBlockX(), (int) Math.floor(at.getY() - EPSILON), at.getBlockZ());
        if (deadly(under)) {
            return false;
        }
        // Water holds a player up.
        if (WATER.equals(under.getType().name())) {
            return true;
        }
        if (under.isPassable()) {
            return false;
        }
        return under.getBoundingBox().getMaxY() >= at.getY() - EPSILON;
    }

    private static boolean deadly(Block block) {
        return DEADLY.contains(block.getType().name());
    }
}
