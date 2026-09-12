package dev.chorus.core.chat.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.flags.PlayerFlag;
import dev.chorus.core.flags.PlayerFlagService;
import org.bukkit.entity.Player;

/** Turns incoming private messages off, and keeps the choice between sessions. */
public final class MessageToggleCommand extends PlayerCommand {

    private final PlayerFlagService flags;

    public MessageToggleCommand(CommandSupport support, PlayerFlagService flags) {
        super(support, "msgtoggle", "chorus.chat.toggle");
        this.flags = flags;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean blocked = flags.toggle(player.getUniqueId(), PlayerFlag.MESSAGES_BLOCKED);
        settle(player);
        messages.send(player, blocked ? "chat.toggle-off" : "chat.toggle-on");
    }
}
