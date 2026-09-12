package dev.chorus.core.command;

import dev.chorus.core.ChorusPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RootCommand extends ChorusCommand {

    private static final String PLACEHOLDER_PLUGIN = "PlaceholderAPI";
    private static final List<String> ACTIONS = List.of("reload", "status", "debug");

    private final ChorusPlugin plugin;

    public RootCommand(ChorusPlugin plugin, CommandSupport support) {
        super(support, "chorus", "chorus.admin");
        this.plugin = plugin;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        String action = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "reload" -> reload(sender, args.length > 1 ? args[1] : null);
            case "status" -> status(sender);
            case "debug" -> debug(sender);
            default -> messages.send(sender, "core.usage",
                    "version", plugin.getDescription().getVersion());
        }
    }

    /** The whole plugin, or one module by name. */
    private void reload(CommandSender sender, String module) {
        if (module == null) {
            plugin.reload();
            messages.send(sender, "core.reloaded");
            return;
        }
        if (!plugin.reload(module)) {
            messages.send(sender, "core.reload-unknown", "module", module);
            return;
        }
        messages.send(sender, "core.reloaded-module", "module", module);
    }

    /**
     * What the plugin found when it started. Most of what gets reported as a bug in a core
     * plugin is one of these lines saying something the owner did not expect.
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

    /** Everything worth pasting into a bug report, in one block. */
    private void debug(CommandSender sender) {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long max = runtime.maxMemory() / (1024 * 1024);

        messages.send(sender, "core.debug-header",
                "version", plugin.getDescription().getVersion());
        messages.send(sender, "core.debug-server",
                "server", plugin.getServer().getName(),
                "game", plugin.getServer().getBukkitVersion(),
                "java", System.getProperty("java.version", "unknown"));
        messages.send(sender, "core.debug-platform",
                "platform", plugin.schedulers().describe(),
                "memory", used + "/" + max + "MB");
        messages.send(sender, "core.debug-storage",
                "storage", plugin.storage().dialect().name().toLowerCase(Locale.ROOT),
                "economy", plugin.economy().status());
        messages.send(sender, "core.debug-modules",
                "on", String.join(", ", plugin.enabledModules()),
                "off", plugin.switchedOffModules().isEmpty()
                        ? messages.plain("core.debug-none")
                        : String.join(", ", plugin.switchedOffModules()));
        messages.send(sender, "core.debug-commands",
                "count", String.valueOf(plugin.registeredCommands().size()),
                "players", String.valueOf(plugin.getServer().getOnlinePlayers().size()));

        List<String> others = new ArrayList<>();
        for (var other : plugin.getServer().getPluginManager().getPlugins()) {
            if (other.isEnabled() && !other.equals(plugin)) {
                others.add(other.getName());
            }
        }
        messages.send(sender, "core.debug-plugins",
                "count", String.valueOf(others.size()),
                "plugins", String.join(", ", others));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!allowed(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return startingWith(args[0], ACTIONS);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return startingWith(args[1], plugin.enabledModules());
        }
        return List.of();
    }
}
