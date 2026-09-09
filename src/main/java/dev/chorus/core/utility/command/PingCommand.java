package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.TargetedCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PingCommand extends TargetedCommand {

    public PingCommand(CommandSupport support) {
        super(support, "ping", "chorus.utility.ping");
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        String ping = String.valueOf(target.getPing());
        if (target.equals(sender)) {
            messages.send(sender, "utility.ping", "ping", ping);
        } else {
            messages.send(sender, "utility.ping-other", "player", target.getName(), "ping", ping);
        }
    }
}
