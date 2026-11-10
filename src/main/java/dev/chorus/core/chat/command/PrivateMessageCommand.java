package dev.chorus.core.chat.command;

import dev.chorus.core.chat.IgnoreList;
import dev.chorus.core.chat.PrivateMessages;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.preference.PlayerPreference;
import dev.chorus.core.preference.PlayerPreferenceService;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Shared delivery for /msg and /reply. */
abstract class PrivateMessageCommand extends PlayerCommand {

    private static final String SPY_PERMISSION = "chorus.chat.spy";

    protected final PrivateMessages chat;

    protected final PlayerPreferenceService preferences;
    private final IgnoreList ignores;

    protected PrivateMessageCommand(CommandSupport support, PrivateMessages chat,
                                    PlayerPreferenceService preferences, IgnoreList ignores,
                                    String name, String permission) {
        super(support, name, permission);
        this.chat = chat;
        this.preferences = preferences;
        this.ignores = ignores;
    }

    /** Whether this message should not arrive, having told the sender so. */
    protected final boolean refuses(Player from, Player to) {
        if (from.hasPermission(ignores.bypassPermission())) {
            return false;
        }
        if (preferences.isSet(to.getUniqueId(), PlayerPreference.MESSAGES_BLOCKED)
                || ignores.ignores(to.getUniqueId(), from.getUniqueId())) {
            messages.send(from, "chat.msg-refused", "player", to.getName());
            return true;
        }
        return false;
    }

    protected final void deliver(Player from, Player to, String text) {
        chat.remember(from.getUniqueId(), to.getUniqueId());
        settle(from);
        // Played where the recipient stands, so on the thread that owns them.
        CommandRules current = rules();
        onPlayer(to, () -> current.feedback().play(to));

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

    /** Joins the words after {@code firstWord} into plain text. */
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
