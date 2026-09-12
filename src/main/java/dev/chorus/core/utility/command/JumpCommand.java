package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

/**
 * {@code /jump}: goes to whatever the player is looking at.
 *
 * <p>The ray stops at the first solid block, so looking through a window lands on the
 * window rather than on the hillside behind it.
 */
public final class JumpCommand extends PlayerCommand {

    private static final int RANGE = 200;

    private final TeleportService teleports;

    public JumpCommand(CommandSupport support, TeleportService teleports) {
        super(support, "jump", "chorus.utility.jump");
        this.teleports = teleports;
    }

    @Override
    protected void execute(Player player, String[] args) {
        RayTraceResult hit = player.rayTraceBlocks(RANGE);
        Block block = hit == null ? null : hit.getHitBlock();
        if (block == null) {
            messages.send(player, "utility.jump-none");
            return;
        }
        if (!ready(player)) {
            return;
        }

        Location destination = block.getLocation().add(0.5, 1, 0.5);
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());

        teleports.teleport(player, destination, rules(), name(), () -> {
            settle(player);
            messages.send(player, "utility.jump-done");
        });
    }
}
