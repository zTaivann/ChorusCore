package dev.chorus.core.staff.command;

import dev.chorus.commons.command.Durations;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.stafflog.StaffLog;
import dev.chorus.core.stafflog.StaffLogEntry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/** {@code /stafflog [player]}: what staff have been doing, newest first. */
public final class StaffLogCommand extends ChorusCommand {

    /** The stored name of each action, and the line that names it. */
    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("used", "staff.log-actions.used"),
            Map.entry("eco-give", "staff.log-actions.eco-give"),
            Map.entry("eco-take", "staff.log-actions.eco-take"),
            Map.entry("eco-set", "staff.log-actions.eco-set"),
            Map.entry("eco-reset", "staff.log-actions.eco-reset"),
            Map.entry("freeze", "staff.log-actions.freeze"),
            Map.entry("unfreeze", "staff.log-actions.unfreeze"),
            Map.entry("lockdown-on", "staff.log-actions.lockdown-on"),
            Map.entry("lockdown-off", "staff.log-actions.lockdown-off"),
            Map.entry("note", "staff.log-actions.note"),
            Map.entry("note-clear", "staff.log-actions.note-clear"),
            Map.entry("sudo", "staff.log-actions.sudo"),
            Map.entry("tempfly-on", "staff.log-actions.tempfly-on"),
            Map.entry("tempfly-off", "staff.log-actions.tempfly-off"));

    private final StaffLog staffLog;

    public StaffLogCommand(CommandSupport support, StaffLog staffLog) {
        super(support, "stafflog", "chorus.staff.log");
        this.staffLog = staffLog;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!staffLog.enabled()) {
            messages.send(sender, "staff.log-disabled");
            return;
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        String name = args.length > 0 ? args[0] : null;
        staffLog.recent(name, staffLog.pageSize()).thenAccept(entries -> show(sender, entries));
    }

    private void show(CommandSender sender, List<StaffLogEntry> entries) {
        if (entries.isEmpty()) {
            messages.send(sender, "staff.log-empty");
            return;
        }

        long now = System.currentTimeMillis();
        messages.send(sender, "staff.log-header", "count", String.valueOf(entries.size()));
        for (StaffLogEntry entry : entries) {
            String ago = Durations.format(Math.max(0, now - entry.at()));
            if (entry.subject() == null) {
                messages.send(sender, "staff.log-entry-plain",
                        "actor", entry.actor(), "action", action(sender, entry.action()), "ago", ago);
            } else if (entry.detail() == null) {
                messages.send(sender, "staff.log-entry",
                        "actor", entry.actor(), "action", action(sender, entry.action()),
                        "subject", entry.subject(), "ago", ago);
            } else {
                messages.send(sender, "staff.log-entry-detailed",
                        "actor", entry.actor(), "action", action(sender, entry.action()),
                        "subject", entry.subject(), "detail", entry.detail(), "ago", ago);
            }
        }
    }

    /** An action as the reader sees it, or its stored name when no line names it. */
    private String action(CommandSender reader, String action) {
        String key = ACTIONS.get(action);
        return key == null ? action : messages.plain(reader, key);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? onlineNames(sender, args[0], true) : List.of();
    }
}
