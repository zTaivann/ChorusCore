package dev.chorus.core.chat.command;

import dev.chorus.core.chat.IgnoreList;
import dev.chorus.core.chat.PrivateMessages;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.flags.PlayerFlagService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class MessageCommand extends PrivateMessageCommand {

    public MessageCommand(CommandSupport support, PrivateMessages chat,
                          PlayerFlagService flags, IgnoreList ignores) {
        super(support, chat, flags, ignores, "msg", "chorus.chat.msg");
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "chat.msg-usage");
            return;
        }

        Player target = player.getServer().getPlayerExact(args[0]);
        if (target == null || !player.canSee(target)) {
            messages.send(player, "error.player-not-found", "player", args[0]);
            return;
        }
        if (target.equals(player)) {
            messages.send(player, "chat.msg-self");
            return;
        }
        if (refuses(player, target)) {
            return;
        }

        String text = textFrom(args, 1);
        if (text.isEmpty()) {
            messages.send(player, "chat.msg-usage");
            return;
        }
        if (!ready(player)) {
            return;
        }
        deliver(player, target, text);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player)) {
            return List.of();
        }

        return onlineNames(sender, args[args.length - 1], false);
    }
}
