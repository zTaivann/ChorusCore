package dev.chorus.core.utility.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.platform.Schedulers;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /smite [player]}: a lightning bolt on a player, or on whatever you are looking at.
 *
 * <p>The strike is asked for on the thread that owns where it lands rather than the one the
 * command was typed on, which on Folia is not the same thread when the target is standing in
 * another region.
 */
public final class SmiteCommand extends ChorusCommand {

    private static final int REACH = 120;

    private final Schedulers schedulers;

    public SmiteCommand(CommandSupport support, Schedulers schedulers) {
        super(support, "smite", "chorus.utility.smite");
        this.schedulers = schedulers;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            atCursor(sender);
            return;
        }

        Player target = online(sender, args[0]);
        if (target == null || !ready(sender)) {
            return;
        }

        schedulers.entity(target, () -> target.getWorld().strikeLightning(target.getLocation()));
        settle(sender);
        messages.send(sender, "utility.smite", "player", target.getName());
        if (!target.equals(sender)) {
            messages.send(target, "utility.smite-received");
        }
    }

    private void atCursor(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "utility.smite-usage");
            return;
        }

        Block looking = player.getTargetBlockExact(REACH);
        if (looking == null) {
            messages.send(sender, "utility.smite-nothing");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        Location where = looking.getLocation().add(0.5, 1, 0.5);
        schedulers.region(where, () -> where.getWorld().strikeLightning(where));
        settle(sender);
        messages.send(sender, "utility.smite-cursor");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? onlineNames(sender, args[0], true) : List.of();
    }
}
