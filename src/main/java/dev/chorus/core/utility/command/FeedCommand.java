package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.TargetedCommand;
import dev.chorus.core.utility.UtilityService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FeedCommand extends TargetedCommand {

    private final UtilityService utility;

    public FeedCommand(CommandSupport support, UtilityService utility) {
        super(support, "feed", "chorus.utility.feed");
        this.utility = utility;
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!ready(sender)) {
            return;
        }

        target.setFoodLevel(20);
        target.setSaturation(utility.settings().feed().saturation());
        target.setExhaustion(0);

        settle(sender);
        announce(sender, target, "utility.fed", "utility.fed-other", "utility.feed-received");
    }
}
