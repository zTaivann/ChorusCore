package dev.chorus.core.staff.command;

import dev.chorus.core.audit.AuditEntry;
import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** {@code /stafflog [player]}: what staff have been doing, newest first. */
public final class StaffLogCommand extends ChorusCommand {

    private final AuditLog audit;

    public StaffLogCommand(CommandSupport support, AuditLog audit) {
        super(support, "stafflog", "chorus.staff.log");
        this.audit = audit;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!audit.enabled()) {
            messages.send(sender, "staff.log-disabled");
            return;
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        String name = args.length > 0 ? args[0] : null;
        audit.recent(name, audit.pageSize()).thenAccept(entries -> show(sender, entries));
    }

    private void show(CommandSender sender, List<AuditEntry> entries) {
        if (entries.isEmpty()) {
            messages.send(sender, "staff.log-empty");
            return;
        }

        long now = System.currentTimeMillis();
        messages.send(sender, "staff.log-header", "count", String.valueOf(entries.size()));
        for (AuditEntry entry : entries) {
            // Three shapes rather than one with blanks in it: an entry with nobody on the
            // other end reads badly when the template still has an arrow in it.
            String ago = Durations.format(Math.max(0, now - entry.at()));
            if (entry.subject() == null) {
                messages.send(sender, "staff.log-entry-plain",
                        "actor", entry.actor(), "action", entry.action(), "ago", ago);
            } else if (entry.detail() == null) {
                messages.send(sender, "staff.log-entry",
                        "actor", entry.actor(), "action", entry.action(),
                        "subject", entry.subject(), "ago", ago);
            } else {
                messages.send(sender, "staff.log-entry-detailed",
                        "actor", entry.actor(), "action", entry.action(),
                        "subject", entry.subject(), "detail", entry.detail(), "ago", ago);
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? onlineNames(sender, args[0], true) : List.of();
    }
}
