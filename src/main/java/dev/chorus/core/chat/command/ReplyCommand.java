package dev.chorus.core.chat.command;

import dev.chorus.core.chat.IgnoreList;
import dev.chorus.core.chat.PrivateMessages;
import dev.chorus.core.flags.PlayerFlagService;
import dev.chorus.core.command.CommandSupport;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class ReplyCommand extends PrivateMessageCommand {

    public ReplyCommand(CommandSupport support, PrivateMessages chat,
                          PlayerFlagService flags, IgnoreList ignores) {
        super(support, chat, flags, ignores, "reply", "chorus.chat.reply");
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "chat.reply-usage");
            return;
        }

        UUID partnerId = chat.lastPartner(player.getUniqueId());
        Player partner = partnerId == null ? null : player.getServer().getPlayer(partnerId);
        if (partner == null || !player.canSee(partner)) {
            messages.send(player, "chat.no-reply-target");
            return;
        }
        if (refuses(player, partner)) {
            return;
        }

        String text = textFrom(args, 0);
        if (text.isEmpty()) {
            messages.send(player, "chat.reply-usage");
            return;
        }
        if (!ready(player)) {
            return;
        }
        deliver(player, partner, text);
    }
}
