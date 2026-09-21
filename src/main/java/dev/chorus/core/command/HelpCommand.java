package dev.chorus.core.command;

import dev.chorus.core.ChorusPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** {@code /commands [module|search] [page]}: what this player can actually run. */
public final class HelpCommand extends ChorusCommand {

    private static final int PAGE_SIZE = 10;

    private final ChorusPlugin plugin;

    public HelpCommand(ChorusPlugin plugin, CommandSupport support) {
        super(support, "commands", "chorus.help");
        this.plugin = plugin;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        List<String> modules = plugin.enabledModules();
        String filter = "";
        int page = 1;

        for (String argument : args) {
            int number = Numbers.integer(argument, -1);
            if (number > 0) {
                page = number;
            } else {
                filter = argument.toLowerCase(Locale.ROOT);
            }
        }

        List<Entry> entries = available(sender, filter);
        if (entries.isEmpty()) {
            messages.send(sender, filter.isEmpty() ? "core.help-empty" : "core.help-no-match",
                    "search", filter);
            return;
        }

        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(Math.max(1, page), pages);

        messages.send(sender, "core.help-header",
                "count", String.valueOf(entries.size()),
                "page", String.valueOf(page),
                "pages", String.valueOf(pages));

        int from = (page - 1) * PAGE_SIZE;
        String shown = "";
        for (Entry entry : entries.subList(from, Math.min(from + PAGE_SIZE, entries.size()))) {
            if (filter.isEmpty() && !entry.module().equals(shown)) {
                shown = entry.module();
                messages.send(sender, "core.help-module", "module", shown);
            }
            messages.send(sender, "core.help-entry",
                    "command", entry.name(), "description", entry.description());
        }
        if (page < pages) {
            messages.send(sender, "core.help-more",
                    "page", String.valueOf(page + 1),
                    "search", filter.isEmpty() ? String.valueOf(page + 1) : filter);
        }
        if (filter.isEmpty()) {
            messages.send(sender, "core.help-modules", "modules", String.join(", ", modules));
        }
    }

    /** In module order, since that is the order the config files are in. */
    private List<Entry> available(CommandSender sender, String filter) {
        List<Entry> entries = new ArrayList<>();
        for (ChorusPlugin.Registered registered : plugin.registeredCommands()) {
            ChorusCommand command = registered.command();
            if (!command.allowed(sender) || !command.rules().enabled()) {
                continue;
            }
            if (!filter.isEmpty() && !registered.module().equals(filter)
                    && !command.name().contains(filter)) {
                continue;
            }
            entries.add(new Entry(registered.module(), command.name(), describe(command.name())));
        }
        entries.sort((left, right) -> {
            int module = order(left.module()) - order(right.module());
            return module != 0 ? module : left.name().compareTo(right.name());
        });
        return entries;
    }

    private int order(String module) {
        int place = plugin.enabledModules().indexOf(module);
        return place < 0 ? Integer.MAX_VALUE : place;
    }

    private String describe(String command) {
        Command known = plugin.getCommand(command);
        String description = known == null ? "" : known.getDescription();
        return description == null || description.isEmpty() ? command : description;
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        Set<String> options = new LinkedHashSet<>(plugin.enabledModules());
        return startingWith(args[0], options);
    }

    private record Entry(String module, String name, String description) {
    }
}
