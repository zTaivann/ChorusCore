package dev.chorus.core.chat.command;

import dev.chorus.core.chat.PrivateMessages;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Shared delivery for /msg and /reply. */
abstract class PrivateMessageCommand extends PlayerCommand {

    private static final String SPY_PERMISSION = "chorus.chat.spy";

    protected final PrivateMessages chat;

    protected PrivateMessageCommand(CommandSupport support, PrivateMessages chat,
                                    String name, String permission) {
        super(support, name, permission);
        this.chat = chat;
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
