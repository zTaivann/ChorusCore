package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.entity.Player;

public final class SuicideCommand extends PlayerCommand {

    public SuicideCommand(CommandSupport support) {
        super(support, "suicide", "chorus.utility.suicide");
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (player.isDead()) {
            messages.send(player, "utility.suicide-already-dead");
            return;
        }
        if (!ready(player)) {
            return;
        }

        settle(player);
        messages.send(player, "utility.suicide");

        // Killed outright rather than damaged, so god mode is no protection.
        player.setHealth(0.0);
    }
}
