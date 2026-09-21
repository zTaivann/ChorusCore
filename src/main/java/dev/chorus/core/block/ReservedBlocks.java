package dev.chorus.core.block;

import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** The blocks the plugin itself is using, so nothing else edits them out from under it. */
public final class ReservedBlocks {

    private final List<Predicate<Block>> owners = new ArrayList<>();

    public void register(Predicate<Block> owner) {
        owners.add(owner);
    }

    public boolean isReserved(Block block) {
        for (Predicate<Block> owner : owners) {
            if (owner.test(block)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        owners.clear();
    }
}
