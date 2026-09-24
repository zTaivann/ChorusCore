package dev.chorus.core;

import dev.chorus.core.api.ChorusApi;
import dev.chorus.core.api.HomeApi;
import dev.chorus.core.api.SpawnApi;
import dev.chorus.core.api.WarpApi;
import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.audit.SqlAuditRepository;
import dev.chorus.core.backup.BackupListener;
import dev.chorus.core.backup.InventoryBackups;
import dev.chorus.core.backup.SqlBackupRepository;
import dev.chorus.core.block.ReservedBlocks;
import dev.chorus.core.chat.ChatModule;
import dev.chorus.core.command.ActionGuard;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandAliases;
import dev.chorus.core.command.CommandOverrides;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Confirmations;
import dev.chorus.core.command.Cooldowns;
import dev.chorus.core.command.DisabledCommand;
import dev.chorus.core.command.HelpCommand;
import dev.chorus.core.command.RootCommand;
import dev.chorus.core.config.ConfigCheck;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.config.ConfigFiles;
import dev.chorus.core.config.ConfigProblem;
import dev.chorus.core.custom.CustomCommandsModule;
import dev.chorus.core.economy.Balances;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.economy.EconomyModule;
import dev.chorus.core.economy.EconomySetup;
import dev.chorus.core.economy.NoEconomy;
import dev.chorus.core.flags.PlayerFlagService;
import dev.chorus.core.flags.SqlPlayerFlagRepository;
import dev.chorus.core.home.HomeModule;
import dev.chorus.core.items.ItemsModule;
import dev.chorus.core.kits.KitsModule;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.menu.ChatPrompts;
import dev.chorus.core.menu.MenuListener;
import dev.chorus.core.papi.ChorusExpansion;
import dev.chorus.core.papi.PlaceholderValues;
import dev.chorus.core.platform.Schedulers;
import dev.chorus.core.players.PlayerProfileListener;
import dev.chorus.core.players.PlayerProfiles;
import dev.chorus.core.players.PlayersModule;
import dev.chorus.core.players.SqlPlayerProfileRepository;
import dev.chorus.core.request.TeleportRequestModule;
import dev.chorus.core.shops.ShopsModule;
import dev.chorus.core.spawn.SpawnModule;
import dev.chorus.core.staff.StaffModule;
import dev.chorus.core.storage.LoginData;
import dev.chorus.core.storage.Queries;
import dev.chorus.core.storage.SqlStorage;
import dev.chorus.core.storage.Storage;
import dev.chorus.core.storage.StorageOptions;
import dev.chorus.core.teleport.TeleportService;
import dev.chorus.core.teleport.TeleportSettings;
import dev.chorus.core.update.UpdateCheck;
import dev.chorus.core.utility.UtilityModule;
import dev.chorus.core.warp.WarpModule;
import dev.chorus.core.world.WorldModule;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class ChorusPlugin extends JavaPlugin {

    public static final String TELEPORT_CONFIG = "modules/teleport.yml";

    private static final long COOLDOWN_SWEEP_TICKS = 20L * 60 * 5;
    private static final int BSTATS_ID = 34019;
    private static final String CORE = "core";

    private final Deque<ChorusModule> modules = new ArrayDeque<>();
    private final Map<String, ChorusModule> byName = new LinkedHashMap<>();
    private final List<Registered> commands = new ArrayList<>();
    private final List<String> modulesOff = new ArrayList<>();
    private final Set<String> switchedOff = new HashSet<>();
    private final Cooldowns cooldowns = new Cooldowns();
    private final ReservedBlocks reserved = new ReservedBlocks();

    private String installing = CORE;

    private ConfigFiles configs;
    private Schedulers schedulers;
    private Executor mainThread;
    private LoginData loginData;
    private ExecutorService worker;
    private Messages messages;
    private EconomySetup economySetup;
    private Storage storage;
    private TeleportService teleports;
    private PlayerFlagService flags;
    private PlayerProfiles profiles;
    private Confirmations confirmations;
    private AuditLog audit;
    private InventoryBackups backups;
    private ChatPrompts prompts;
    private CommandSupport support;
    private ChorusServices services;
    private UpdateCheck updates;
    private PlaceholderValues placeholders;
    private Metrics metrics;

    @Override
    public void onEnable() {
        long started = System.currentTimeMillis();
        configs = new ConfigFiles(this);
        ConfigFile core = configs.get("config.yml");

        try {
            schedulers = new Schedulers(this);
        } catch (RuntimeException noSchedulers) {
            // On Folia every Bukkit scheduler method throws, so there is no second best.
            getLogger().log(Level.SEVERE, noSchedulers.getMessage(), noSchedulers.getCause());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        mainThread = schedulers::global;
        // One thread, so every write reaches the database in the order it was made.
        worker = Executors.newSingleThreadExecutor(Queries.storageThreads());
        messages = Messages.load(this, configs);
        messages.apply(core.section("language"));

        try {
            storage = SqlStorage.open(this, StorageOptions.read(core.data()));
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "The database could not be opened, disabling the plugin", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economySetup = new EconomySetup(this, storage, worker);
        economySetup.start(core.section("economy"));
        register(economySetup);
        if (economySetup.economy() instanceof Listener listener) {
            register(listener);
        }

        confirmations = new Confirmations(messages, core.section("confirmations").getInt("seconds", 0));
        register(confirmations);
        support = new CommandSupport(messages, new ActionGuard(messages, cooldowns, economy()),
                schedulers);

        SqlPlayerFlagRepository flagStore = new SqlPlayerFlagRepository(storage);
        if (!open(flagStore::createTables, "player settings")) {
            return;
        }
        flags = new PlayerFlagService(flagStore, worker, getLogger());
        loginData = new LoginData(messages, getLogger());
        loginData.add("settings", flags, true);
        register(loginData);

        SqlPlayerProfileRepository profileStore = new SqlPlayerProfileRepository(storage);
        if (!open(profileStore::createTables, "player")) {
            return;
        }
        profiles = new PlayerProfiles(profileStore, worker, mainThread, schedulers, getLogger());
        register(new PlayerProfileListener(profiles));

        SqlAuditRepository auditStore = new SqlAuditRepository(storage);
        if (!open(auditStore::createTables, "staff log")) {
            return;
        }
        audit = new AuditLog(auditStore, worker, mainThread, getLogger());
        audit.apply(core.section("staff-log"));
        audit.prune();

        SqlBackupRepository backupStore = new SqlBackupRepository(storage);
        if (!open(backupStore::createTables, "inventory backup")) {
            return;
        }
        backups = new InventoryBackups(backupStore, worker, mainThread, schedulers, getLogger());
        backups.prune();
        register(new BackupListener(backups, messages));

        teleports = new TeleportService(this, messages, schedulers,
                TeleportSettings.read(configs.get(TELEPORT_CONFIG).section("teleport")));
        register(teleports);
        register(new MenuListener());
        prompts = new ChatPrompts(this, messages, schedulers);
        register(prompts);
        prompts.start();
        updates = new UpdateCheck(this, messages, schedulers);
        updates.apply(core.section("updates"));
        register(updates);

        register(new RootCommand(this, support, confirmations));
        register(new HelpCommand(this, support));

        services = new ChorusServices(getDescription().getVersion(), economy(), teleports, messages);

        install(new HomeModule(this, support, teleports));
        install(new WarpModule(this, support, teleports));
        install(new SpawnModule(this, support, teleports));
        install(new TeleportRequestModule(this, support, teleports, flags));
        install(new UtilityModule(this, support, teleports));
        install(new EconomyModule(this, support));
        install(new ShopsModule(this, support));
        install(new ChatModule(this, support));
        install(new ItemsModule(this, support));
        install(new WorldModule(this, support, teleports));

        StaffModule staff = new StaffModule(this, support, teleports);
        PlayersModule players = new PlayersModule(this, support);
        install(staff);
        install(players);
        install(new KitsModule(this, support));
        install(new CustomCommandsModule(this));

        if (!isEnabled()) {
            return;
        }

        getServer().getServicesManager().register(ChorusApi.class, services, this, ServicePriority.Normal);
        hookPlaceholders(staff, players);
        loginData.loadOnline(getServer().getOnlinePlayers(), worker);

        CommandOverrides overrides = new CommandOverrides(
                CommandAliases.apply(this, configs.get("aliases.yml"), commandNames(), switchedOff));
        if (!overrides.isEmpty()) {
            register(overrides);
        }
        schedulers.globalTimer(() -> cooldowns.sweep(System.currentTimeMillis()),
                COOLDOWN_SWEEP_TICKS, COOLDOWN_SWEEP_TICKS);

        updates.start();
        startMetrics(core);
        checkConfig();
        announce(core, System.currentTimeMillis() - started);
    }

    /**
     * What in the config will not work, said once at startup.
     *
     * <p>Worlds are left out here: a world another plugin makes has not been made yet.
     * {@code /chorus check} runs the same check with them included.
     */
    private void checkConfig() {
        List<ConfigProblem> problems = ConfigCheck.run(this, getServer(), optionFiles(), false);
        if (problems.isEmpty()) {
            return;
        }
        getLogger().warning(problems.size() + " problem"
                + (problems.size() == 1 ? "" : "s") + " in the configuration:");
        int number = 1;
        for (ConfigProblem problem : problems) {
            getLogger().warning("  " + number++ + ". " + problem.describe());
        }
    }

    /** The files that hold options, which are the ones a typo can break. */
    public List<String> optionFiles() {
        return configs.paths().stream()
                .filter(path -> path.equals("config.yml") || path.equals("aliases.yml")
                        || path.startsWith("modules/"))
                .sorted()
                .toList();
    }

    public PlaceholderValues placeholders() {
        return placeholders;
    }

    /** The console at the end of a start that worked, so it can say what was found. */
    private void announce(ConfigFile core, long millis) {
        if (!core.data().getBoolean("startup-banner", true)) {
            getLogger().info("Started in " + millis + "ms");
            return;
        }

        Console console = new Console(this);
        console.banner();
        console.title(getDescription().getVersion(), serverVersion());

        console.connected("Storage", storage.dialect().name().toLowerCase(Locale.ROOT));
        if (economy() instanceof NoEconomy) {
            console.absent("Economy", economy().status());
        } else {
            console.connected("Economy", economy().status());
        }
        if (getServer().getPluginManager().isPluginEnabled(PlaceholderValues.PLUGIN)) {
            console.connected("Placeholders", PlaceholderValues.PLUGIN);
        } else {
            console.absent("Placeholders", "PlaceholderAPI not installed");
        }
        if (messages.languages().isEmpty()) {
            console.absent("Languages", "English only");
        } else {
            console.connected("Languages", "English, " + String.join(", ", messages.languages()));
        }
        console.connected("Scheduling", schedulers.describe());
        console.connected("Modules", modules.size() + " of " + (modules.size() + modulesOff.size()));
        console.connected("Commands", commands.size() + " registered");
        console.ready(millis, String.join(", ", getDescription().getAuthors()));
    }

    /** "1.21.4" out of the long string Bukkit reports. */
    private String serverVersion() {
        String bukkit = getServer().getBukkitVersion();
        int dash = bukkit.indexOf('-');
        return getServer().getName() + " " + (dash < 0 ? bukkit : bukkit.substring(0, dash));
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);

        while (!modules.isEmpty()) {
            ChorusModule module = modules.pop();
            try {
                module.disable();
            } catch (RuntimeException exception) {
                getLogger().log(Level.WARNING, "Module '" + module.name() + "' did not shut down cleanly", exception);
            }
        }

        if (updates != null) {
            updates.shutdown();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
        cooldowns.clear();
        reserved.clear();
        if (confirmations != null) {
            confirmations.clear();
        }
        if (prompts != null) {
            prompts.shutdown();
        }
        if (loginData != null) {
            loginData.clear();
        }
        if (flags != null) {
            flags.clearAll();
        }
        if (profiles != null) {
            profiles.clear();
        }
        if (teleports != null) {
            teleports.shutdown();
        }
        if (schedulers != null) {
            schedulers.shutdown();
        }
        if (worker != null) {
            drain(worker);
        }
        if (storage != null) {
            storage.close();
        }
    }

    public void reload() {
        configs.reloadAll();
        messages.reload();
        ConfigFile core = configs.get("config.yml");
        messages.apply(core.section("language"));
        economySetup.reload(core.section("economy"));
        confirmations.apply(core.section("confirmations").getInt("seconds", 0));
        audit.apply(core.section("staff-log"));
        updates.apply(core.section("updates"));
        teleports.apply(TeleportSettings.read(configs.get(TELEPORT_CONFIG).section("teleport")));
        modules.forEach(ChorusModule::reload);
    }

    /** One module, for a change that should not touch the rest of the server. */
    public boolean reload(String module) {
        ChorusModule found = byName.get(module.toLowerCase(Locale.ROOT));
        if (found == null) {
            return false;
        }
        configs.get(found.configPath()).reload();
        configs.reload(Messages::owns);
        messages.reload();
        found.reload();
        return true;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public ConfigFiles configs() {
        return configs;
    }

    public LoginData loginData() {
        return loginData;
    }

    public Messages messages() {
        return messages;
    }

    public CommandSupport support() {
        return support;
    }

    public Schedulers schedulers() {
        return schedulers;
    }

    public Confirmations confirmations() {
        return confirmations;
    }

    /** The blocks modules have claimed, so nothing else rewrites them. */
    public ReservedBlocks reserved() {
        return reserved;
    }

    public Economy economy() {
        return economySetup == null ? new NoEconomy() : economySetup.economy();
    }

    /** The built-in ledger, or null on a server whose money belongs to another plugin. */
    public @Nullable Balances balances() {
        return economySetup == null ? null : economySetup.balances();
    }

    /** The modules that actually started, in the order they did, for /chorus status. */
    public List<String> enabledModules() {
        List<String> names = new ArrayList<>(modules.size());
        modules.forEach(module -> names.add(0, module.name()));
        return names;
    }

    public List<String> switchedOffModules() {
        return List.copyOf(modulesOff);
    }

    public List<Registered> registeredCommands() {
        return List.copyOf(commands);
    }

    public Storage storage() {
        return storage;
    }

    public PlayerFlagService flags() {
        return flags;
    }

    public PlayerProfiles profiles() {
        return profiles;
    }

    public AuditLog audit() {
        return audit;
    }

    public InventoryBackups backups() {
        return backups;
    }

    public ChatPrompts prompts() {
        return prompts;
    }

    public UpdateCheck updates() {
        return updates;
    }

    /** Runs tasks where the server allows them, dropping them once the plugin is gone. */
    public Executor mainThread() {
        return mainThread;
    }

    /** Runs blocking work, mainly SQL, off the server thread. */
    public Executor worker() {
        return worker;
    }

    public void provide(HomeApi api) {
        services.provide(api);
    }

    public void provide(WarpApi api) {
        services.provide(api);
    }

    public void provide(SpawnApi api) {
        services.provide(api);
    }

    public void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    public <T extends ChorusCommand> T register(T command) {
        PluginCommand target = getCommand(command.name());
        if (target == null) {
            throw new IllegalStateException("Command '" + command.name() + "' is missing from plugin.yml");
        }
        target.setExecutor(command);
        target.setTabCompleter(command);
        commands.add(new Registered(installing, command));
        return command;
    }

    /**
     * Starts a module unless its config says otherwise. A module that is switched off still
     * answers for its commands, so a player gets a straight answer instead of Bukkit's
     * usage line.
     */
    private void install(ChorusModule module) {
        if (!isEnabled()) {
            // An earlier module already brought the plugin down.
            return;
        }

        if (!configs.get(module.configPath()).data().getBoolean("enabled", true)) {
            installing = module.name();
            module.commandNames().forEach(name -> {
                register(new DisabledCommand(support, name));
                // A module switched off still answers its commands, so its names stay claimed.
                switchedOff.add(name);
            });
            installing = CORE;
            modulesOff.add(module.name());
            getLogger().info("Module '" + module.name() + "' is switched off in " + module.configPath());
            return;
        }

        try {
            installing = module.name();
            module.enable();
            modules.push(module);
            byName.put(module.name().toLowerCase(Locale.ROOT), module);
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Module '" + module.name() + "' failed to start", exception);
            getServer().getPluginManager().disablePlugin(this);
        } finally {
            installing = CORE;
        }
    }

    private List<String> commandNames() {
        List<String> names = new ArrayList<>(commands.size());
        commands.forEach(registered -> names.add(registered.command().name()));
        return names;
    }

    private void startMetrics(ConfigFile core) {
        if (!core.data().getBoolean("metrics", true)) {
            return;
        }
        try {
            metrics = new Metrics(this, BSTATS_ID);
            metrics.addCustomChart(new SimplePie("storage",
                    () -> storage.dialect().name().toLowerCase(Locale.ROOT)));
            metrics.addCustomChart(new SimplePie("economy",
                    () -> economySetup.mode().name().toLowerCase(Locale.ROOT)));
            metrics.addCustomChart(new SimplePie("scheduling", schedulers::describe));
            metrics.addCustomChart(new SingleLineChart("modules", modules::size));
        } catch (LinkageError | RuntimeException exception) {
            getLogger().log(Level.FINE, "Metrics could not be started", exception);
        }
    }

    private void hookPlaceholders(StaffModule staff, PlayersModule players) {
        placeholders = new PlaceholderValues(services, players.afk(), staff.vanish());
        if (!getServer().getPluginManager().isPluginEnabled(PlaceholderValues.PLUGIN)) {
            return;
        }
        try {
            new ChorusExpansion(services, placeholders).register();
            getLogger().info("Registered the %chorus_...% placeholders with PlaceholderAPI.");
        } catch (LinkageError | RuntimeException exception) {
            getLogger().log(Level.WARNING, "PlaceholderAPI is installed but the expansion "
                    + "could not be registered", exception);
        }
    }

    /** @return whether the plugin is still standing. */
    private boolean open(Tables tables, String what) {
        try {
            tables.create();
            return true;
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "The " + what + " table could not be created", exception);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void drain(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                getLogger().warning("Some database writes were still running and had to be dropped");
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }


    /** A command and the module that owns it, which is what /commands groups by. */
    public record Registered(String module, ChorusCommand command) {
    }

    @FunctionalInterface
    private interface Tables {
        void create() throws SQLException;
    }

}
