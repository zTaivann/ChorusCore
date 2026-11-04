package dev.chorus.core.economy.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.preference.PlayerPreference;
import dev.chorus.core.preference.PlayerPreferenceService;
import org.bukkit.entity.Player;

/** Stops other players sending payments, and keeps the preference between sessions. */
public final class PayToggleCommand extends PlayerCommand {

    private final PlayerPreferenceService preferences;

    public PayToggleCommand(CommandSupport support, PlayerPreferenceService preferences) {
        super(support, "paytoggle", "chorus.economy.toggle");
        this.preferences = preferences;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean blocked = preferences.toggle(player.getUniqueId(), PlayerPreference.PAYMENTS_BLOCKED);
        settle(player);
        messages.send(player, blocked ? "economy.toggle-off" : "economy.toggle-on");
    }
}
