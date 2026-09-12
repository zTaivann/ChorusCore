package dev.chorus.core.chat.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.flags.PlayerFlag;
import dev.chorus.core.flags.PlayerFlagService;
import org.bukkit.entity.Player;

/**
 * {@code /rtoggle}: which end of a conversation {@code /reply} answers.
 *
 * <p>It only matters when a conversation is interrupted: you write to Anna, Ben writes to
 * you, and {@code /r} has to pick one. The choice is kept between sessions.
 */
public final class ReplyToggleCommand extends PlayerCommand {

    private final PlayerFlagService flags;

    public ReplyToggleCommand(CommandSupport support, PlayerFlagService flags) {
        super(support, "rtoggle", "chorus.chat.reply");
        this.flags = flags;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean toSender = flags.toggle(player.getUniqueId(), PlayerFlag.REPLY_TO_SENDER);
        settle(player);
        messages.send(player, toSender ? "chat.rtoggle-sender" : "chat.rtoggle-recipient");
    }
}
