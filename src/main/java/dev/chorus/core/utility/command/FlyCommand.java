package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.TargetedCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FlyCommand extends TargetedCommand {

    public FlyCommand(CommandSupport support) {
        super(support, "fly", "chorus.utility.fly");
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!ready(sender)) {
            return;
        }

        boolean enabled = !target.getAllowFlight();
        target.setAllowFlight(enabled);
        if (!enabled) {
            // Leaving them flying with flight revoked drops them out of the sky.
            target.setFlying(false);
        }

        settle(sender);
        if (enabled) {
            announce(sender, target, "utility.fly-enabled",
                    "utility.fly-enabled-other", "utility.fly-enabled-received");
        } else {
            announce(sender, target, "utility.fly-disabled",
                    "utility.fly-disabled-other", "utility.fly-disabled-received");
        }
    }
}
