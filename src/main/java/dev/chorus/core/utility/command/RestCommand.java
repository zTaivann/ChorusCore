package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.TargetedCommand;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /rest [player]}: counts the player as having slept. */
public final class RestCommand extends TargetedCommand {

    public RestCommand(CommandSupport support) {
        super(support, "rest", "chorus.utility.rest");
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!ready(sender)) {
            return;
        }

        target.setStatistic(Statistic.TIME_SINCE_REST, 0);
        settle(sender);
        announce(sender, target, "utility.rested", "utility.rested-other", "utility.rest-received");
    }
}
