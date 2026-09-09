package dev.chorus.core.players.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.players.AfkService;
import org.bukkit.entity.Player;

public final class AfkCommand extends PlayerCommand {

    private final AfkService afk;

    public AfkCommand(CommandSupport support, AfkService afk) {
        super(support, "afk", "chorus.players.afk");
        this.afk = afk;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (!ready(player)) {
            return;
        }
        afk.toggle(player);
        settle(player);
    }
}
