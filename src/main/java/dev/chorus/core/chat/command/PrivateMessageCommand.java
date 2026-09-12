package dev.chorus.core.chat.command;

import dev.chorus.core.chat.IgnoreList;
import dev.chorus.core.chat.PrivateMessages;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.flags.PlayerFlag;
import dev.chorus.core.flags.PlayerFlagService;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Shared delivery for /msg and /reply. */
abstract class PrivateMessageCommand extends PlayerCommand {

    private static final String SPY_PERMISSION = "chorus.chat.spy";

    protected final PrivateMessages chat;

    protected final PlayerFlagService flags;
    private final IgnoreList ignores;

    protected PrivateMessageCommand(CommandSupport support, PrivateMessages chat,
                                    PlayerFlagService flags, IgnoreList ignores,
                                    String name, String permission) {
        super(support, name, permission);
        this.chat = chat;
        this.flags = flags;
        this.ignores = ignores;
    }

    /**
     * Whether this message should not arrive, having told the sender so.
     *
     * <p>One line for both reasons. A player who has been ignored is told the same thing as
     * one writing to somebody with messages off, which spares both of them the argument.
     */
    protected final boolean refuses(Player from, Player to) {
        if (from.hasPermission(ignores.bypassPermission())) {
            return false;
        }
        if (flags.isSet(to.getUniqueId(), PlayerFlag.MESSAGES_BLOCKED)
                || ignores.ignores(to.getUniqueId(), from.getUniqueId())) {
            messages.send(from, "chat.msg-refused", "player", to.getName());
            return true;
        }
        return false;
    }

    protected final void deliver(Player from, Player to, String text) {
        chat.remember(from.getUniqueId(), to.getUniqueId());
        settle(from);
        // The point of a message sound is that the person receiving it hears something.
        rules().feedback().play(to);

        messages.send(from, "chat.msg-sent", "player", to.getName(), "message", text);
        messages.send(to, "chat.msg-received", "player", from.getName(), "message", text);
        relayToSpies(from, to, text);
    }

    private void relayToSpies(Player from, Player to, String text) {
        if (!chat.settings().spyEnabled()) {
            return;
        }
        for (UUID watcherId : chat.spies()) {
            if (watcherId.equals(from.getUniqueId()) || watcherId.equals(to.getUniqueId())) {
                continue;
            }
            Player watcher = from.getServer().getPlayer(watcherId);
            if (watcher != null && watcher.hasPermission(SPY_PERMISSION)) {
                messages.send(watcher, "chat.msg-spy",
                        "from", from.getName(), "to", to.getName(), "message", text);
            }
        }
    }

    /**
     * Joins the words after {@code firstWord} back together. The result reaches the message
     * as plain text, so a player cannot smuggle formatting tags into someone else's chat.
     */
    protected static String textFrom(String[] args, int firstWord) {
        StringBuilder text = new StringBuilder();
        for (int index = firstWord; index < args.length; index++) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(args[index]);
        }
        return text.toString().trim();
    }
}
