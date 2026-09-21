package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.flags.PlayerFlag;
import dev.chorus.core.flags.PlayerFlagService;
import org.bukkit.entity.Player;

/** Accepts every teleport request on arrival instead of asking. */
public final class TpAutoCommand extends PlayerCommand {

    private final PlayerFlagService flags;

    public TpAutoCommand(CommandSupport support, PlayerFlagService flags) {
        super(support, "tpauto", "chorus.tpa.auto");
        this.flags = flags;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean automatic = flags.toggle(player.getUniqueId(), PlayerFlag.TELEPORTS_AUTOMATIC);
        if (automatic && flags.isSet(player.getUniqueId(), PlayerFlag.TELEPORTS_BLOCKED)) {
            flags.toggle(player.getUniqueId(), PlayerFlag.TELEPORTS_BLOCKED);
        }

        settle(player);
        messages.send(player, automatic ? "request.auto-on" : "request.auto-off");
    }
}
