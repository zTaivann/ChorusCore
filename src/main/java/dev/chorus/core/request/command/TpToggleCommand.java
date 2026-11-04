package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.preference.PlayerPreference;
import dev.chorus.core.preference.PlayerPreferenceService;
import org.bukkit.entity.Player;

/** Turns incoming teleport requests off, and keeps the choice between sessions. */
public final class TpToggleCommand extends PlayerCommand {

    private final PlayerPreferenceService preferences;

    public TpToggleCommand(CommandSupport support, PlayerPreferenceService preferences) {
        super(support, "tptoggle", "chorus.tpa.toggle");
        this.preferences = preferences;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean blocked = preferences.toggle(player.getUniqueId(), PlayerPreference.TELEPORTS_BLOCKED);
        settle(player);
        messages.send(player, blocked ? "request.toggle-off" : "request.toggle-on");
    }
}
