package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.flags.PlayerFlag;
import dev.chorus.core.flags.PlayerFlagService;
import org.bukkit.entity.Player;

/** Turns incoming teleport requests off, and keeps the choice between sessions. */
public final class TpToggleCommand extends PlayerCommand {

    private final PlayerFlagService flags;

    public TpToggleCommand(CommandSupport support, PlayerFlagService flags) {
        super(support, "tptoggle", "chorus.tpa.toggle");
        this.flags = flags;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean blocked = flags.toggle(player.getUniqueId(), PlayerFlag.TELEPORTS_BLOCKED);
        settle(player);
        messages.send(player, blocked ? "request.toggle-off" : "request.toggle-on");
    }
}
