package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.entity.Player;

/** {@code /depth}: how far above or below sea level the player is standing. */
public final class DepthCommand extends PlayerCommand {

    public DepthCommand(CommandSupport support) {
        super(support, "depth", "chorus.utility.depth");
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }
        settle(player);

        int sea = player.getWorld().getSeaLevel();
        int depth = player.getLocation().getBlockY() - sea;
        String key = depth > 0 ? "utility.depth-above"
                : depth < 0 ? "utility.depth-below"
                        : "utility.depth-level";

        messages.send(player, key,
                "blocks", String.valueOf(Math.abs(depth)),
                "y", String.valueOf(player.getLocation().getBlockY()));
    }
}
