package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Locale;

/**
 * {@code /break}: breaks the block you are looking at, drops and all.
 *
 * <p>A {@link BlockBreakEvent} is fired first, so a protection plugin refuses it exactly as
 * it would refuse a pickaxe. Blocks the game itself will not let anybody mine are left alone,
 * which is what keeps this from being a way through bedrock.
 */
public final class BreakCommand extends PlayerCommand {

    private static final int RANGE = 10;

    public BreakCommand(CommandSupport support) {
        super(support, "break", "chorus.utility.break");
    }

    @Override
    protected void execute(Player player, String[] args) {
        Block block = player.getTargetBlockExact(RANGE);
        if (block == null || block.getType().isAir()) {
            messages.send(player, "utility.break-none");
            return;
        }
        if (block.getType().getHardness() < 0) {
            messages.send(player, "utility.break-indestructible",
                    "block", name(block));
            return;
        }

        BlockBreakEvent event = new BlockBreakEvent(block, player);
        player.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        if (!ready(player)) {
            return;
        }

        String broken = name(block);
        if (event.isDropItems()) {
            block.breakNaturally(player.getInventory().getItemInMainHand());
        } else {
            block.setType(Material.AIR);
        }

        settle(player);
        messages.send(player, "utility.break-done", "block", broken);
    }

    private static String name(Block block) {
        return block.getType().name().toLowerCase(Locale.ROOT);
    }
}
