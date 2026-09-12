package dev.chorus.core.economy.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.flags.PlayerFlag;
import dev.chorus.core.flags.PlayerFlagService;
import org.bukkit.entity.Player;

/** Stops other players sending money, and keeps the choice between sessions. */
public final class PayToggleCommand extends PlayerCommand {

    private final PlayerFlagService flags;

    public PayToggleCommand(CommandSupport support, PlayerFlagService flags) {
        super(support, "paytoggle", "chorus.economy.toggle");
        this.flags = flags;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean blocked = flags.toggle(player.getUniqueId(), PlayerFlag.PAYMENTS_BLOCKED);
        settle(player);
        messages.send(player, blocked ? "economy.toggle-off" : "economy.toggle-on");
    }
}
