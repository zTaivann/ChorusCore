package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.preference.PlayerPreference;
import dev.chorus.core.preference.PlayerPreferenceService;
import org.bukkit.entity.Player;

/**
 * Stops every powertool without forgetting any of them, for building with a tool that has a
 * command on it.
 */
public final class PowertoolToggleCommand extends PlayerCommand {

    private final PlayerPreferenceService preferences;

    public PowertoolToggleCommand(CommandSupport support, PlayerPreferenceService preferences) {
        super(support, "powertooltoggle", "chorus.utility.powertool");
        this.preferences = preferences;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean off = preferences.toggle(player.getUniqueId(), PlayerPreference.POWERTOOLS_OFF);
        settle(player);
        messages.send(player, off ? "utility.powertool-off" : "utility.powertool-on");
    }
}
