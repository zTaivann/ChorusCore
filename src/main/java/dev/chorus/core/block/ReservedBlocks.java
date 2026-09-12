package dev.chorus.core.block;

import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The blocks the plugin itself is using, so nothing else edits them out from under it.
 *
 * <p>A shop sign and the chest behind it mean something. A command that rewrites a sign, or
 * a tool that clears a chest, has to be able to ask before it does. Modules add whatever they
 * own as they start and the answer is a walk over a short list.
 */
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
