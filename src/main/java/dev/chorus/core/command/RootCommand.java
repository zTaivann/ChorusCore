package dev.chorus.core.command;

import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.importer.EssentialsImport;
import dev.chorus.core.importer.ImportReport;
import dev.chorus.core.storage.Queries;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class RootCommand extends ChorusCommand {

    private static final String PLACEHOLDER_PLUGIN = "PlaceholderAPI";
    private static final List<String> ACTIONS = List.of("reload", "status", "debug", "import");

    private final ChorusPlugin plugin;
    private final Confirmations confirmations;

    public RootCommand(ChorusPlugin plugin, CommandSupport support, Confirmations confirmations) {
        super(support, "chorus", "chorus.admin");
        this.plugin = plugin;
        this.confirmations = confirmations;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        String action = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "reload" -> reload(sender, args.length > 1 ? args[1] : null);
            case "status" -> status(sender);
            case "debug" -> debug(sender);
            case "import" -> importFrom(sender, args);
            default -> messages.send(sender, "core.usage",
                    "version", plugin.getDescription().getVersion());
        }
    }

    /**
     * {@code /chorus import essentials [run] [overwrite]}.
     *
     * <p>Without {@code run} it reads everything and writes nothing, which is the version
     * worth doing first. The real run asks to be confirmed and never touches the Essentials
     * folder, so putting the old plugin back is always possible.
     */
    private void importFrom(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("essentials")) {
            messages.send(sender, "core.import-usage");
            return;
        }

        boolean live = args.length > 2 && args[2].equalsIgnoreCase("run");
        boolean overwrite = args.length > 3 && args[3].equalsIgnoreCase("overwrite");

        Path folder = null;
        for (Path candidate : EssentialsImport.candidates(plugin.getDataFolder().toPath().getParent())) {
            if (Files.isDirectory(candidate)) {
                folder = candidate;
                break;
            }
        }
        if (folder == null) {
            messages.send(sender, "core.import-not-found");
            return;
        }
        if (live && !confirmations.confirmed(sender, "import:essentials",
                overwrite ? "core.import-confirm-overwrite" : "core.import-confirm")) {
            return;
        }

        messages.send(sender, live ? "core.import-started" : "core.import-checking",
                "folder", folder.getFileName().toString());

        EssentialsImport importer = new EssentialsImport(plugin.storage(), folder,
                plugin.configs().get("modules/shops.yml"));
        Queries.run(() -> importer.run(!live, overwrite), plugin.worker(), plugin.mainThread())
                .whenComplete((report, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE, "The import failed", failure);
                        messages.send(sender, "core.import-failed");
                        return;
                    }
                    report(sender, report, live);
                });
    }

    private void report(CommandSender sender, ImportReport report, boolean live) {
        if (!report.foundAnything()) {
            messages.send(sender, "core.import-empty");
            return;
        }

        messages.send(sender, live ? "core.import-done" : "core.import-would",
                "players", String.valueOf(report.players()));
        messages.send(sender, "core.import-counts",
                "homes", String.valueOf(report.homes()),
                "balances", String.valueOf(report.balances()),
                "warps", String.valueOf(report.warps()));
        messages.send(sender, "core.import-counts-more",
                "mail", String.valueOf(report.mail()),
                "nicknames", String.valueOf(report.nicknames()),
                "prices", String.valueOf(report.worth()));
        if (report.skipped() > 0) {
            messages.send(sender, "core.import-skipped",
                    "count", String.valueOf(report.skipped()));
        }

        for (String problem : report.problems()) {
            messages.send(sender, "core.import-problem", "problem", problem);
        }
        if (report.hiddenProblems() > 0) {
            messages.send(sender, "core.import-problems-more",
                    "count", String.valueOf(report.hiddenProblems()));
        }

        messages.send(sender, live ? "core.import-restart" : "core.import-next");
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
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            return startingWith(args[1], List.of("essentials"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("import")) {
            return startingWith(args[2], List.of("run"));
        }
        return List.of();
    }
}
