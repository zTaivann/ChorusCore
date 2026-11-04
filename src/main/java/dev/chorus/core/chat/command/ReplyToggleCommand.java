package dev.chorus.core.chat.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.preference.PlayerPreference;
import dev.chorus.core.preference.PlayerPreferenceService;
import org.bukkit.entity.Player;

/** {@code /rtoggle}: which end of a conversation {@code /reply} answers. */
public final class ReplyToggleCommand extends PlayerCommand {

    private final PlayerPreferenceService preferences;

    public ReplyToggleCommand(CommandSupport support, PlayerPreferenceService preferences) {
        super(support, "rtoggle", "chorus.chat.reply");
        this.preferences = preferences;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean toSender = preferences.toggle(player.getUniqueId(), PlayerPreference.REPLY_TO_SENDER);
        settle(player);
        messages.send(player, toSender ? "chat.rtoggle-sender" : "chat.rtoggle-recipient");
    }
}
