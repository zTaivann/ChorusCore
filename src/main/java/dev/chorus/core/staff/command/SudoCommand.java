package dev.chorus.core.staff.command;

import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/** Runs a command as somebody else, with their permissions rather than yours. */
public final class SudoCommand extends ChorusCommand {

    private static final String EXEMPT_PERMISSION = "chorus.staff.sudo.exempt";

    private final AuditLog audit;

    public SudoCommand(CommandSupport support, AuditLog audit) {
        super(support, "sudo", "chorus.staff.sudo");
        this.audit = audit;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "staff.sudo-usage");
            return;
        }

        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }
        if (target.hasPermission(EXEMPT_PERMISSION) && !target.equals(sender)) {
            messages.send(sender, "staff.sudo-exempt", "player", target.getName());
            return;
        }

        String typed = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String command = typed.startsWith("/") ? typed.substring(1) : typed;
        if (command.isBlank()) {
            messages.send(sender, "staff.sudo-usage");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        settle(sender);
        // Written down before it runs.
        audit.record(sender, "sudo", target.getName(), command);

        messages.send(sender, "staff.sudo-ran", "player", target.getName(), "command", command);
        onPlayer(target, () -> target.performCommand(command));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? onlineNames(sender, args[0], true) : List.of();
    }
}
