package dev.chorus.core.staff.command;

import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.staff.NoteService;
import dev.chorus.core.staff.StaffNote;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** {@code /note add|list|clear <player> [text]}. */
public final class NoteCommand extends ChorusCommand {

    private static final String CLEAR_PERMISSION = "chorus.staff.note.clear";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final NoteService notes;
    private final AuditLog audit;

    public NoteCommand(CommandSupport support, NoteService notes, AuditLog audit) {
        super(support, "note", "chorus.staff.note");
        this.notes = notes;
        this.audit = audit;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "staff.note-usage");
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        OfflinePlayer subject = known(sender, args[1]);
        if (subject == null) {
            return;
        }
        String name = subject.getName() == null ? args[1] : subject.getName();

        switch (action) {
            case "add" -> add(sender, subject, name, args);
            case "list" -> list(sender, subject, name);
            case "clear" -> clear(sender, subject, name);
            default -> messages.send(sender, "staff.note-usage");
        }
    }

    private void add(CommandSender sender, OfflinePlayer subject, String name, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "staff.note-usage");
            return;
        }
        if (!ready(sender)) {
            return;
        }

        String text = NoteService.trim(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        settle(sender);
        notes.add(new StaffNote(subject.getUniqueId(), name, sender.getName(), text,
                System.currentTimeMillis()));
        audit.record(sender, "note", name, text);
        messages.send(sender, "staff.note-added", "player", name);
    }

    private void list(CommandSender sender, OfflinePlayer subject, String name) {
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        notes.find(subject.getUniqueId()).thenAccept(found -> {
            if (found.isEmpty()) {
                messages.send(sender, "staff.note-none", "player", name);
                return;
            }
            messages.send(sender, "staff.note-header",
                    "player", name, "count", String.valueOf(found.size()));
            for (StaffNote note : found) {
                messages.send(sender, "staff.note-entry",
                        "author", note.author(),
                        "date", DATE.format(Instant.ofEpochMilli(note.written())),
                        "note", note.text());
            }
        });
    }

    private void clear(CommandSender sender, OfflinePlayer subject, String name) {
        if (!sender.hasPermission(CLEAR_PERMISSION)) {
            messages.send(sender, "error.no-permission");
            return;
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        notes.clear(subject.getUniqueId()).thenAccept(removed -> {
            if (removed == 0) {
                messages.send(sender, "staff.note-none", "player", name);
                return;
            }
            audit.record(sender, "note-clear", name, removed + " notes");
            messages.send(sender, "staff.note-cleared",
                    "player", name, "count", String.valueOf(removed));
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], sender.hasPermission(CLEAR_PERMISSION)
                    ? List.of("add", "list", "clear")
                    : List.of("add", "list"));
        }
        return args.length == 2 ? onlineNames(sender, args[1], true) : List.of();
    }
}
