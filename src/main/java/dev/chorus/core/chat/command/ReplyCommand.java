package dev.chorus.core.chat.command;

import dev.chorus.core.chat.PrivateMessages;
import dev.chorus.core.command.CommandSupport;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class ReplyCommand extends PrivateMessageCommand {

    public ReplyCommand(CommandSupport support, PrivateMessages chat) {
        super(support, chat, "reply", "chorus.chat.reply");
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
