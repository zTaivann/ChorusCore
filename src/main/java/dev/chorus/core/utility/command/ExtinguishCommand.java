package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.TargetedCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /ext [player]}: puts out a player who is on fire. */
public final class ExtinguishCommand extends TargetedCommand {

    public ExtinguishCommand(CommandSupport support) {
        super(support, "ext", "chorus.utility.ext");
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!ready(sender)) {
            return;
        }

        target.setFireTicks(0);
        settle(sender);
        announce(sender, target,
                "utility.ext-self", "utility.ext-other", "utility.ext-received");
    }
}
