package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.TargetedCommand;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Where somebody is standing, in a form that can be pasted straight into /tppos. */
public final class PositionCommand extends TargetedCommand {

    public PositionCommand(CommandSupport support) {
        super(support, "getpos", "chorus.utility.getpos");
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        Location at = target.getLocation();
        String key = target.equals(sender) ? "utility.getpos" : "utility.getpos-other";
        messages.send(sender, key,
                "player", target.getName(),
                "world", at.getWorld().getName(),
                "x", String.valueOf(at.getBlockX()),
                "y", String.valueOf(at.getBlockY()),
                "z", String.valueOf(at.getBlockZ()),
                "yaw", String.format(Locale.ROOT, "%.1f", at.getYaw()),
                "pitch", String.format(Locale.ROOT, "%.1f", at.getPitch()));
    }
}
