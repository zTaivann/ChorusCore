package dev.chorus.core.staff.command;

import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.staff.TempFlyService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** {@code /tempfly <player> <minutes>}, or {@code 0} to take it back. */
public final class TempFlyCommand extends ChorusCommand {

    private static final int MAX_MINUTES = 1440;

    private final TempFlyService flights;
    private final AuditLog audit;

    public TempFlyCommand(CommandSupport support, TempFlyService flights, AuditLog audit) {
        super(support, "tempfly", "chorus.staff.tempfly");
        this.flights = flights;
        this.audit = audit;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "staff.tempfly-usage");
            return;
        }

        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }

        int minutes = parse(args[1]);
        if (minutes < 0 || minutes > MAX_MINUTES) {
            messages.send(sender, "staff.tempfly-range", "max", String.valueOf(MAX_MINUTES));
            return;
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        if (minutes == 0) {
            if (!flights.take(target)) {
                messages.send(sender, "staff.tempfly-none", "player", target.getName());
                return;
            }
            audit.record(sender, "tempfly-off", target.getName(), null);
            messages.send(sender, "staff.tempfly-taken", "player", target.getName());
            messages.send(target, "staff.tempfly-expired");
            return;
        }

        int seconds = (int) TimeUnit.MINUTES.toSeconds(minutes);
        flights.grant(target, seconds);
        audit.record(sender, "tempfly-on", target.getName(), minutes + "m");

        String time = Durations.format(TimeUnit.MINUTES.toMillis(minutes));
        messages.send(sender, "staff.tempfly-given", "player", target.getName(), "time", time);
        messages.send(target, "staff.tempfly-received", "time", time);
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
        return args.length == 2
                ? startingWith(args[1], List.of("0", "5", "15", "30", "60"))
                : List.of();
    }
}
