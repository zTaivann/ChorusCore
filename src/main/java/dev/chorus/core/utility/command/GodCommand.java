package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.TargetedCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class GodCommand extends TargetedCommand {

    public GodCommand(CommandSupport support) {
        super(support, "god", "chorus.utility.god");
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!ready(sender)) {
            return;
        }

        boolean enabled = !target.isInvulnerable();
        target.setInvulnerable(enabled);

        settle(sender);
        if (enabled) {
            announce(sender, target, "utility.god-enabled",
                    "utility.god-enabled-other", "utility.god-enabled-received");
        } else {
            announce(sender, target, "utility.god-disabled",
                    "utility.god-disabled-other", "utility.god-disabled-received");
        }
    }
}
