package dev.chorus.core.chat.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.preference.PlayerPreference;
import dev.chorus.core.preference.PlayerPreferenceService;
import org.bukkit.entity.Player;

/** Turns incoming private messages off, and keeps the choice between sessions. */
public final class MessageToggleCommand extends PlayerCommand {

    private final PlayerPreferenceService preferences;

    public MessageToggleCommand(CommandSupport support, PlayerPreferenceService preferences) {
        super(support, "msgtoggle", "chorus.chat.toggle");
        this.preferences = preferences;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean blocked = preferences.toggle(player.getUniqueId(), PlayerPreference.MESSAGES_BLOCKED);
        settle(player);
        messages.send(player, blocked ? "chat.toggle-off" : "chat.toggle-on");
    }
}
