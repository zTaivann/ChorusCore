package dev.chorus.core.shops.chest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Working out which block is which around a shop.
 *
 * <p>Two things make this less obvious than it sounds. A chest can be half of a double chest,
 * in which case the stock lives in both halves and the shop has to see all of it. And a sign
 * can be on the side of a container or on top of it, which are different blocks in different
 * directions.
 */
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
        if (!isShopContainer(block) || !(block.getState() instanceof Container container)) {
            return null;
        }
        return container.getInventory();
    }

    /**
     * The other half of a double chest, or null.
     *
     * <p>Found by asking each neighbour whether it shares this chest's inventory, which is
     * the only answer that stays right whichever way the chest is facing.
     */
    public static @Nullable Block otherHalf(Block block) {
        if (!(block.getState() instanceof Chest chest)) {
            return null;
        }
        Inventory inventory = chest.getInventory();
        if (inventory.getSize() <= chest.getBlockInventory().getSize()) {
            return null;
        }

        for (BlockFace face : AROUND) {
            Block neighbour = block.getRelative(face);
            if (neighbour.getType() == block.getType()
                    && neighbour.getState() instanceof Chest other
                    && other.getInventory().equals(inventory)) {
                return neighbour;
            }
        }
        return null;
    }

    /** The block a sign is fixed to: behind a wall sign, under one standing on top. */
    public static @Nullable Block holderOf(Block sign) {
        if (!(sign.getState() instanceof Sign)) {
            return null;
        }
        BlockData data = sign.getBlockData();
        if (data instanceof WallSign wall) {
            return sign.getRelative(wall.getFacing().getOppositeFace());
        }
        if (data instanceof Directional) {
            return sign.getRelative(BlockFace.DOWN);
        }
        return sign.getRelative(BlockFace.DOWN);
    }

    /** Every sign fixed to a container, so the price on them can be kept in step. */
    public static List<Block> signsOn(Block container) {
        List<Block> signs = new ArrayList<>(2);
        for (BlockFace face : AROUND) {
            Block side = container.getRelative(face);
            if (side.getState() instanceof Sign && container.equals(holderOf(side))) {
                signs.add(side);
            }
        }
        Block above = container.getRelative(BlockFace.UP);
        if (above.getState() instanceof Sign && container.equals(holderOf(above))) {
            signs.add(above);
        }
        return signs;
    }
}
