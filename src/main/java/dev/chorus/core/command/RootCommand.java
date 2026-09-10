package dev.chorus.core.command;

import dev.chorus.core.ChorusPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public final class RootCommand extends ChorusCommand {

    private static final String PLACEHOLDER_PLUGIN = "PlaceholderAPI";

    private final ChorusPlugin plugin;

    public RootCommand(ChorusPlugin plugin, CommandSupport support) {
        super(support, "chorus", "chorus.admin");
        this.plugin = plugin;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        String action = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "reload" -> {
                plugin.reload();
                messages.send(sender, "core.reloaded");
            }
            case "status" -> status(sender);
            default -> messages.send(sender, "core.usage",
                    "version", plugin.getDescription().getVersion());
        }
    }

    /**
     * What the plugin found when it started. Most of what gets reported as a bug in a core
     * plugin is one of these four lines saying something the owner did not expect.
     */
    private void status(CommandSender sender) {
        messages.send(sender, "core.status-header",
                "version", plugin.getDescription().getVersion());
        messages.send(sender, "core.status-storage",
                "storage", plugin.storage().dialect().name().toLowerCase(Locale.ROOT));
        messages.send(sender, "core.status-economy", "economy", plugin.economy().status());
        messages.send(sender, "core.status-placeholders", "placeholders",
                plugin.getServer().getPluginManager().isPluginEnabled(PLACEHOLDER_PLUGIN)
                        ? "hooked in"
                        : "not installed");
        messages.send(sender, "core.status-modules",
                "modules", String.join(", ", plugin.enabledModules()));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !allowed(sender)) {
            return List.of();
        }
        return startingWith(args[0], List.of("reload", "status"));
    }
}
