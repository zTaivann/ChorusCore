package dev.chorus.core.command;

import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.config.ConfigCheck;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.config.ConfigProblem;
import dev.chorus.core.config.ConfigSnapshot;
import dev.chorus.core.importer.EssentialsImport;
import dev.chorus.core.importer.ImportReport;
import dev.chorus.core.importer.QuickShopImport;
import dev.chorus.core.importer.ShopImportReport;
import dev.chorus.core.papi.PlaceholderValues;
import dev.chorus.core.players.PlayerPurge;
import dev.chorus.core.storage.Queries;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class RootCommand extends ChorusCommand {


    private static final List<String> ACTIONS = List.of("reload", "status", "check",
            "permissions", "placeholders", "debug", "import", "purge");

    private static final String PERMISSIONS = "permissions.yml";

    /** The group of every node that is not in a module's own group. */
    private static final String FAMILIES = "families";

    /** How many changed settings a reload lists before it starts counting them instead. */
    private static final int LISTED_CHANGES = 5;

    /** A floor under the purge, so a slip of the finger cannot empty the database. */
    private static final int MIN_PURGE_DAYS = 30;
    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

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
            case "check" -> check(sender);
            case "permissions" -> permissions(sender, args.length > 1 ? args[1] : null);
            case "placeholders" -> placeholders(sender);
            case "debug" -> debug(sender);
            case "import" -> importFrom(sender, args);
            case "purge" -> purge(sender, args);
            default -> messages.send(sender, "core.usage",
                    "version", plugin.getDescription().getVersion());
        }
    }

    /** {@code /chorus import essentials [run] [overwrite]}. */
    private void importFrom(CommandSender sender, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (what) {
            case "essentials" -> fromEssentials(sender, args);
            case "quickshop" -> fromQuickShop(sender, args);
            default -> messages.send(sender, "core.import-usage");
        }
    }

    /** {@code /chorus import quickshop [run] [overwrite]}. */
    private void fromQuickShop(CommandSender sender, String[] args) {
        boolean live = args.length > 2 && args[2].equalsIgnoreCase("run");
        boolean overwrite = args.length > 3 && args[3].equalsIgnoreCase("overwrite");

        Path folder = QuickShopImport.folderIn(plugin.getDataFolder().toPath().getParent());
        if (folder == null) {
            messages.send(sender, "core.import-shops-not-found");
            return;
        }
        if (live && !confirmations.confirmed(sender, "import:quickshop",
                overwrite ? "core.import-shops-confirm-overwrite" : "core.import-shops-confirm")) {
            return;
        }

        messages.send(sender, live ? "core.import-started" : "core.import-checking",
                "folder", folder.getFileName().toString());

        QuickShopImport importer = new QuickShopImport(plugin.storage(), folder);
        Queries.run(() -> importer.run(!live, overwrite), plugin.worker(), plugin.mainThread())
                .whenComplete((report, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE, "The shop import failed", failure);
                        messages.send(sender, "core.import-failed");
                        return;
                    }
                    reportShops(sender, report, live);
                });
    }

    private void reportShops(CommandSender sender, ShopImportReport report, boolean live) {
        if (!report.foundAnything()) {
            messages.send(sender, "core.import-shops-empty");
            for (String problem : report.problems()) {
                messages.send(sender, "core.import-problem", "problem", problem);
            }
            return;
        }

        messages.send(sender, live ? "core.import-shops-done" : "core.import-shops-would",
                "shops", String.valueOf(report.imported()), "source", report.source());
        messages.send(sender, "core.import-shops-counts",
                "read", String.valueOf(report.read()),
                "skipped", String.valueOf(report.skipped()),
                "broken", String.valueOf(report.unreadable()));

        for (String problem : report.problems()) {
            messages.send(sender, "core.import-problem", "problem", problem);
        }
        if (report.hiddenProblems() > 0) {
            messages.send(sender, "core.import-problems-more",
                    "count", String.valueOf(report.hiddenProblems()));
        }
        messages.send(sender, live ? "core.import-shops-restart" : "core.import-shops-next");
    }

    private void fromEssentials(CommandSender sender, String[] args) {
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

    /** {@code /chorus purge <days> [run]}: forgets players nobody has seen in a long time. */
    private void purge(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "core.purge-usage", "min", String.valueOf(MIN_PURGE_DAYS));
            return;
        }

        int days = Numbers.integer(args[1], -1);
        if (days < MIN_PURGE_DAYS) {
            messages.send(sender, "core.purge-range", "min", String.valueOf(MIN_PURGE_DAYS));
            return;
        }

        boolean live = args.length > 2 && args[2].equalsIgnoreCase("run");
        if (live && !confirmations.confirmed(sender, "purge:" + days,
                "core.purge-confirm", "days", String.valueOf(days))) {
            return;
        }

        // Who is online is a question for the server thread; the purge runs on a worker.
        Set<UUID> online = new HashSet<>();
        plugin.getServer().getOnlinePlayers().forEach(player -> online.add(player.getUniqueId()));
        long before = System.currentTimeMillis() - days * MILLIS_PER_DAY;

        messages.send(sender, live ? "core.purge-started" : "core.purge-checking",
                "days", String.valueOf(days));

        PlayerPurge purge = new PlayerPurge(plugin.storage());
        Queries.run(() -> purge.run(before, online, live), plugin.worker(), plugin.mainThread())
                .whenComplete((report, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE, "The purge failed", failure);
                        messages.send(sender, "core.purge-failed");
                        return;
                    }
                    report(sender, report, live);
                });
    }

    private void report(CommandSender sender, PlayerPurge.Report report, boolean live) {
        if (!report.foundAnything()) {
            messages.send(sender, "core.purge-empty");
            return;
        }

        messages.send(sender, live ? "core.purge-done" : "core.purge-would",
                "players", String.valueOf(report.players()),
                "rows", String.valueOf(report.rows()));
        if (report.shopKeep() > 0) {
            messages.send(sender, "core.purge-shops", "count", String.valueOf(report.shopKeep()));
        }
        if (!live) {
            messages.send(sender, "core.purge-next");
        }
    }

    /** The whole plugin, or one module by name, on the global thread: a reload belongs to no region. */
    private void reload(CommandSender sender, String module) {
        plugin.schedulers().global(() -> reloadNow(sender, module));
    }

    private void reloadNow(CommandSender sender, String module) {
        ConfigSnapshot before = ConfigSnapshot.of(plugin.configs());
        if (module == null) {
            plugin.reload();
            messages.send(sender, "core.reloaded");
            changes(sender, before);
            return;
        }
        if (!plugin.reload(module)) {
            messages.send(sender, "core.reload-unknown", "module", module);
            return;
        }
        messages.send(sender, "core.reloaded-module", "module", module);
        changes(sender, before);
    }

    /** What the reload actually changed, rather than that it happened. */
    private void changes(CommandSender sender, ConfigSnapshot before) {
        ConfigSnapshot.Change change =
                ConfigSnapshot.of(plugin.configs()).since(before);
        if (change.isEmpty()) {
            messages.send(sender, "core.reload-nothing");
            return;
        }

        messages.send(sender, "core.reload-changed", "count", String.valueOf(change.total()));
        for (String entry : change.first(LISTED_CHANGES)) {
            messages.send(sender, "core.reload-change", "change", entry);
        }
        if (change.total() > LISTED_CHANGES) {
            messages.send(sender, "core.reload-change-more",
                    "count", String.valueOf(change.total() - LISTED_CHANGES));
        }
    }

    /** {@code /chorus check}: what in the config files will not work. */
    private void check(CommandSender sender) {
        List<ConfigProblem> problems =
                ConfigCheck.run(plugin, sender.getServer(), plugin.optionFiles(), true);
        if (problems.isEmpty()) {
            messages.send(sender, "core.check-clean");
            return;
        }

        messages.send(sender, "core.check-header", "count", String.valueOf(problems.size()));
        int number = 1;
        for (ConfigProblem problem : problems) {
            messages.send(sender, "core.check-problem",
                    "number", String.valueOf(number++), "problem", problem.describe());
        }
    }

    /** {@code /chorus permissions [group]}: every node, and which of them the reader holds. */
    private void permissions(CommandSender sender, @Nullable String group) {
        YamlConfiguration file = ConfigFile.bundled(plugin, PERMISSIONS);
        if (file == null) {
            messages.send(sender, "core.permissions-missing");
            return;
        }

        if (group == null) {
            groups(sender, file);
            return;
        }
        String wanted = group.toLowerCase(Locale.ROOT);
        List<String> nodes = wanted.equals(FAMILIES) ? List.of() : file.getStringList(wanted);
        if (nodes.isEmpty()) {
            messages.send(sender, "core.permissions-unknown", "group", group);
            return;
        }

        messages.send(sender, "core.permissions-group-header",
                "group", wanted, "count", String.valueOf(nodes.size()));
        for (String node : nodes) {
            messages.send(sender, sender.hasPermission(node)
                    ? "core.permissions-held" : "core.permissions-missing-node", "node", node);
        }
    }

    private void groups(CommandSender sender, YamlConfiguration file) {
        int total = 0;
        int held = 0;
        List<String> names = new ArrayList<>(file.getKeys(false));
        names.remove(FAMILIES);

        for (String name : names) {
            total += file.getStringList(name).size();
            held += (int) file.getStringList(name).stream().filter(sender::hasPermission).count();
        }

        messages.send(sender, "core.permissions-header",
                "count", String.valueOf(total), "held", String.valueOf(held));
        for (String name : names) {
            List<String> nodes = file.getStringList(name);
            messages.send(sender, "core.permissions-summary", "group", name,
                    "count", String.valueOf(nodes.size()),
                    "held", String.valueOf(nodes.stream().filter(sender::hasPermission).count()));
        }
        for (String family : file.getStringList(FAMILIES)) {
            messages.send(sender, "core.permissions-family", "node", family);
        }
        messages.send(sender, "core.permissions-usage");
    }

    /** {@code /chorus placeholders}: every one of ours, with what it says for the reader. */
    private void placeholders(CommandSender sender) {
        PlaceholderValues values = plugin.placeholders();
        OfflinePlayer reader = sender instanceof OfflinePlayer player ? player : null;

        messages.send(sender, "core.placeholders-header",
                "count", String.valueOf(PlaceholderValues.NAMES.size()));
        for (String name : PlaceholderValues.NAMES) {
            String value = values == null ? null : values.of(reader, name);
            messages.send(sender, "core.placeholders-entry", "name", "%chorus_" + name + "%",
                    "value", value == null ? messages.plain("core.placeholders-none") : value);
        }
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
                plugin.getServer().getPluginManager().isPluginEnabled(PlaceholderValues.PLUGIN)
                        ? "hooked in"
                        : "not installed");
        messages.send(sender, "core.status-modules",
                "modules", String.join(", ", plugin.enabledModules()));
        messages.send(sender, "core.status-updates", "updates", updateStatus());
    }

    private String updateStatus() {
        if (!plugin.updates().enabled()) {
            return messages.plain("core.update-off");
        }
        String newer = plugin.updates().newerVersion();
        return newer.isEmpty() ? messages.plain("core.update-current") : newer;
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
            return startingWith(args[1], List.of("essentials", "quickshop"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("permissions")) {
            YamlConfiguration file = ConfigFile.bundled(plugin, PERMISSIONS);
            List<String> groups = file == null
                    ? List.of() : new ArrayList<>(file.getKeys(false));
            groups.remove(FAMILIES);
            return startingWith(args[1], groups);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("purge")) {
            return startingWith(args[1], List.of("30", "90", "180", "365"));
        }
        if (args.length == 3
                && (args[0].equalsIgnoreCase("import") || args[0].equalsIgnoreCase("purge"))) {
            return startingWith(args[2], List.of("run"));
        }
        return List.of();
    }
}
