package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.flags.PlayerFlag;
import dev.chorus.core.flags.PlayerFlagService;
import org.bukkit.entity.Player;

/**
 * Stops every powertool without forgetting any of them, for building with a tool that has a
 * command on it.
 */
public final class PowertoolToggleCommand extends PlayerCommand {

    private final PlayerFlagService flags;

    public PowertoolToggleCommand(CommandSupport support, PlayerFlagService flags) {
        super(support, "powertooltoggle", "chorus.utility.powertool");
        this.flags = flags;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean off = flags.toggle(player.getUniqueId(), PlayerFlag.POWERTOOLS_OFF);
        settle(player);
        messages.send(player, off ? "utility.powertool-off" : "utility.powertool-on");
    }
}
