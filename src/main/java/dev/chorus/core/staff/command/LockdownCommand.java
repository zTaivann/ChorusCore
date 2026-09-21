package dev.chorus.core.staff.command;

import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.staff.LockdownService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** {@code /lockdown on [reason]} or {@code /lockdown off}. */
public final class LockdownCommand extends ChorusCommand {

    private static final String BYPASS_PERMISSION = "chorus.staff.lockdown.bypass";

    private final LockdownService lockdown;
    private final AuditLog audit;

    public LockdownCommand(CommandSupport support, LockdownService lockdown, AuditLog audit) {
        super(support, "lockdown", "chorus.staff.lockdown");
        this.lockdown = lockdown;
        this.audit = audit;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, lockdown.isActive()
                    ? "staff.lockdown-status-on"
                    : "staff.lockdown-status-off");
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!action.equals("on") && !action.equals("off")) {
            messages.send(sender, "staff.lockdown-usage");
            return;
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        if (action.equals("off")) {
            lockdown.open();
            audit.record(sender, "lockdown-off", null, null);
            announce(sender, "staff.lockdown-opened");
            return;
        }

        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "";
        lockdown.close(reason);
        audit.record(sender, "lockdown-on", null, reason.isEmpty() ? null : reason);
        announce(sender, "staff.lockdown-closed");
    }

    /** Everyone who could have closed it themselves deserves to know that somebody did. */
    private void announce(CommandSender sender, String key) {
        messages.send(sender, key);
        for (Player staff : sender.getServer().getOnlinePlayers()) {
            if (!staff.equals(sender) && staff.hasPermission(BYPASS_PERMISSION)) {
                messages.send(staff, key);
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? startingWith(args[0], List.of("on", "off")) : List.of();
    }
}
