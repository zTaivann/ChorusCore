package dev.chorus.core.shops.chest;

import dev.chorus.core.block.Signs;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Which block is which around a shop, from type and data only: this runs on every hopper move. */
public final class ShopContainers {

    private static final BlockFace[] AROUND =
            {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private ShopContainers() {
    }

    /** The kinds of block a shop may be built on. */
    public static boolean isShopContainer(Block block) {
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }


    /** The whole inventory, both halves of a double chest included. */
    public static @Nullable Inventory inventoryOf(Block block) {
        if (!isShopContainer(block) || !(block.getState(false) instanceof Container container)) {
            return null;
        }
        return container.getInventory();
    }

    /** The other half of a double chest, or null. */
    public static @Nullable Block otherHalf(Block block) {
        if (!(block.getBlockData() instanceof Chest chest) || chest.getType() == Chest.Type.SINGLE) {
            return null;
        }
        // A left half has its partner clockwise of the way it faces, a right half the other way.
        BlockFace facing = chest.getFacing();
        Block partner = block.getRelative(chest.getType() == Chest.Type.LEFT
                ? clockwise(facing) : clockwise(facing).getOppositeFace());
        if (partner.getType() != block.getType()
                || !(partner.getBlockData() instanceof Chest other)
                || other.getFacing() != facing || other.getType() == chest.getType()) {
            return null;
        }
        return partner;
    }

    /** The block a sign is fixed to: behind a wall sign, under one standing on top. */
    public static @Nullable Block holderOf(Block sign) {
        if (!Signs.isSign(sign)) {
            return null;
        }
        String type = sign.getType().name();
        if (type.contains("HANGING")) {
            // A wall hanging sign holds nothing; a ceiling one hangs from the block above.
            return type.contains("WALL") ? null : sign.getRelative(BlockFace.UP);
        }
        BlockData data = sign.getBlockData();
        if (type.contains("WALL") && data instanceof Directional wall) {
            return sign.getRelative(wall.getFacing().getOppositeFace());
        }
        return sign.getRelative(BlockFace.DOWN);
    }

    /** Every sign fixed to a container, so the price on them can be kept in step. */
    public static List<Block> signsOn(Block container) {
        List<Block> signs = new ArrayList<>(2);
        for (BlockFace face : AROUND) {
            Block side = container.getRelative(face);
            if (Signs.isSign(side) && container.equals(holderOf(side))) {
                signs.add(side);
            }
        }
        Block above = container.getRelative(BlockFace.UP);
        if (Signs.isSign(above) && container.equals(holderOf(above))) {
            signs.add(above);
        }
        return signs;
    }

    private static BlockFace clockwise(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }
}
