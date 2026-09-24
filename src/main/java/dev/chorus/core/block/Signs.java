package dev.chorus.core.block;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.jetbrains.annotations.Nullable;

/** Signs found by their type first, so a block that is not one is never copied to find out. */
public final class Signs {

    private Signs() {
    }

    /** Any kind of sign, hanging ones included. */
    public static boolean isSign(Block block) {
        return block.getType().name().endsWith("_SIGN");
    }

    /** The sign at a block for reading, or null. The live one, so nothing is copied. */
    public static @Nullable Sign at(Block block) {
        return isSign(block) && block.getState(false) instanceof Sign sign ? sign : null;
    }
}
