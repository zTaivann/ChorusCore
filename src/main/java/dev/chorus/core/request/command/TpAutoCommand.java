package dev.chorus.core.request.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.preference.PlayerPreference;
import dev.chorus.core.preference.PlayerPreferenceService;
import org.bukkit.entity.Player;

/** Accepts every teleport request on arrival instead of asking. */
public final class TpAutoCommand extends PlayerCommand {

    private final PlayerPreferenceService preferences;

    public TpAutoCommand(CommandSupport support, PlayerPreferenceService preferences) {
        super(support, "tpauto", "chorus.tpa.auto");
        this.preferences = preferences;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }

        boolean automatic = preferences.toggle(player.getUniqueId(), PlayerPreference.TELEPORTS_AUTOMATIC);
        if (automatic && preferences.isSet(player.getUniqueId(), PlayerPreference.TELEPORTS_BLOCKED)) {
            preferences.toggle(player.getUniqueId(), PlayerPreference.TELEPORTS_BLOCKED);
        }

        settle(player);
        messages.send(player, automatic ? "request.auto-on" : "request.auto-off");
    }
}
