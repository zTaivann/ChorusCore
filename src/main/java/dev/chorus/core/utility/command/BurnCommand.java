package dev.chorus.core.utility.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BurnCommand extends ChorusCommand {

    private static final int TICKS_PER_SECOND = 20;
    private static final int MAX_SECONDS = 3600;

    public BurnCommand(CommandSupport support) {
        super(support, "burn", "chorus.utility.burn");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "utility.burn-usage");
            return;
        }

        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }

        int seconds = parse(args[1]);
        if (seconds < 1 || seconds > MAX_SECONDS) {
            messages.send(sender, "utility.burn-range", "max", String.valueOf(MAX_SECONDS));
            return;
        }
        if (!ready(sender)) {
            return;
        }

        target.setFireTicks(seconds * TICKS_PER_SECOND);
        settle(sender);
        messages.send(sender, "utility.burn",
                "player", target.getName(), "seconds", String.valueOf(seconds));
        if (!target.equals(sender)) {
            messages.send(target, "utility.burn-received", "seconds", String.valueOf(seconds));
        }
    }

    private static int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return onlineNames(sender, args[0], true);
        }
        return args.length == 2 ? startingWith(args[1], List.of("5", "10", "30", "60")) : List.of();
    }
}
