package dev.chorus.core.block;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.jetbrains.annotations.Nullable;

/** Sign lookups that check the block type before reading its state. */
public final class Signs {

    private Signs() {
    }

    /** Any kind of sign, hanging ones included. */
    public static boolean isSign(Block block) {
        return block.getType().name().endsWith("_SIGN");
    }

    /** The sign at a block, read without a snapshot, or null. */
    public static @Nullable Sign at(Block block) {
        return isSign(block) && block.getState(false) instanceof Sign sign ? sign : null;
    }
}
