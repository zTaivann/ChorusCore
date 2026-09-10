package dev.chorus.core;

import dev.chorus.core.api.ChorusApi;
import dev.chorus.core.api.HomeApi;
import dev.chorus.core.api.SpawnApi;
import dev.chorus.core.api.WarpApi;
import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.audit.SqlAuditRepository;
import dev.chorus.core.chat.ChatModule;
import dev.chorus.core.command.ActionGuard;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandAliases;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Cooldowns;
import dev.chorus.core.command.DisabledCommand;
import dev.chorus.core.command.RootCommand;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.config.ConfigFiles;
import dev.chorus.core.custom.CustomCommandsModule;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.flags.PlayerFlagListener;
import dev.chorus.core.flags.PlayerFlagService;
import dev.chorus.core.flags.SqlPlayerFlagRepository;
import dev.chorus.core.economy.EconomyModule;
import dev.chorus.core.economy.NoEconomy;
import dev.chorus.core.economy.VaultEconomy;
import dev.chorus.core.home.HomeModule;
import dev.chorus.core.items.ItemsModule;
import dev.chorus.core.kits.KitsModule;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.menu.MenuListener;
import dev.chorus.core.papi.ChorusExpansion;
import dev.chorus.core.players.PlayersModule;
import dev.chorus.core.request.TeleportRequestModule;
import dev.chorus.core.spawn.SpawnModule;
import dev.chorus.core.staff.StaffModule;
import dev.chorus.core.storage.SqlStorage;
import dev.chorus.core.storage.Storage;
import dev.chorus.core.storage.StorageOptions;
import dev.chorus.core.teleport.TeleportService;
import dev.chorus.core.teleport.TeleportSettings;
import dev.chorus.core.utility.UtilityModule;
import dev.chorus.core.warp.WarpModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class ChorusPlugin extends JavaPlugin {

    public static final String TELEPORT_CONFIG = "modules/teleport.yml";

    private static final long COOLDOWN_SWEEP_TICKS = 20L * 60 * 5;

    private final Deque<ChorusModule> modules = new ArrayDeque<>();
    private final List<String> registeredCommands = new ArrayList<>();
    private final Cooldowns cooldowns = new Cooldowns();

    private ConfigFiles configs;
    private Executor mainThread;
    private ExecutorService worker;
    private Messages messages;
    private Economy economy;
    private Storage storage;
    private TeleportService teleports;
    private PlayerFlagService flags;
    private AuditLog audit;
    private CommandSupport support;
    private ChorusServices services;

    @Override
    public void onEnable() {
        configs = new ConfigFiles(this);
        ConfigFile core = configs.get("config.yml");

        mainThread = task -> {
            if (isEnabled()) {
                getServer().getScheduler().runTask(this, task);
            }
        };
        worker = Executors.newFixedThreadPool(2, storageThreadFactory());
        messages = Messages.load(this, configs.get("messages.yml"));

        economy = core.section("economy").getBoolean("enabled", true)
                ? new VaultEconomy(getServer(), getLogger())
                : new NoEconomy();
        if (economy instanceof Listener listener) {
            register(listener);
        }
        support = new CommandSupport(messages, new ActionGuard(messages, cooldowns, economy));

        try {
            storage = SqlStorage.open(this, StorageOptions.read(core.data()));
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "The database could not be opened, disabling the plugin", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SqlPlayerFlagRepository flagStore = new SqlPlayerFlagRepository(storage);
        try {
            flagStore.createTables();
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "The player settings table could not be created", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        flags = new PlayerFlagService(flagStore, worker, getLogger());
        register(new PlayerFlagListener(flags, messages, getLogger()));

        SqlAuditRepository auditStore = new SqlAuditRepository(storage);
        try {
            auditStore.createTables();
        } catch (SQLException exception) {
            getLogger().log(Level.SEVERE, "The staff log table could not be created", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        audit = new AuditLog(auditStore, worker, mainThread, getLogger());
        audit.apply(core.section("staff-log"));
        audit.prune();

        teleports = new TeleportService(this, messages, mainThread,
                TeleportSettings.read(configs.get(TELEPORT_CONFIG).section("teleport")));
        register(teleports);
        register(new MenuListener());
        register(new RootCommand(this, support));

        services = new ChorusServices(getDescription().getVersion(), economy, teleports, messages);

        install(new HomeModule(this, support, teleports));
        install(new WarpModule(this, support, teleports));
        install(new SpawnModule(this, support, teleports));
        install(new TeleportRequestModule(this, support, teleports, flags));
        install(new UtilityModule(this, support, teleports));
        install(new EconomyModule(this, support));
        install(new ChatModule(this, support));
        install(new ItemsModule(this, support));

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

        CommandAliases.apply(this, configs.get("aliases.yml"), registeredCommands);
        getServer().getScheduler().runTaskTimer(this,
                () -> cooldowns.sweep(System.currentTimeMillis()),
                COOLDOWN_SWEEP_TICKS, COOLDOWN_SWEEP_TICKS);
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

        cooldowns.clear();
        if (flags != null) {
            flags.clearAll();
        }
        if (teleports != null) {
            teleports.shutdown();
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
        economy.refresh();
        audit.apply(configs.get("config.yml").section("staff-log"));
        teleports.apply(TeleportSettings.read(configs.get(TELEPORT_CONFIG).section("teleport")));
        modules.forEach(ChorusModule::reload);
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public ConfigFiles configs() {
        return configs;
    }

    public Messages messages() {
        return messages;
    }

    public CommandSupport support() {
        return support;
    }

    public Economy economy() {
        return economy;
    }

    public Storage storage() {
        return storage;
    }

    public PlayerFlagService flags() {
        return flags;
    }

    public AuditLog audit() {
        return audit;
    }

    /** Runs tasks on the server thread, dropping them once the plugin is gone. */
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
        registeredCommands.add(command.name());
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
            module.commandNames().forEach(name -> register(new DisabledCommand(support, name)));
            getLogger().info("Module '" + module.name() + "' is switched off in " + module.configPath());
            return;
        }

        try {
            module.enable();
            modules.push(module);
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Module '" + module.name() + "' failed to start", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void hookPlaceholders(StaffModule staff, PlayersModule players) {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            new ChorusExpansion(services, players.afk(), staff.vanish()).register();
            getLogger().info("Registered the %chorus_...% placeholders with PlaceholderAPI.");
        } catch (LinkageError | RuntimeException exception) {
            getLogger().log(Level.WARNING, "PlaceholderAPI is installed but the expansion "
                    + "could not be registered", exception);
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

    private static ThreadFactory storageThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "chorus-storage-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
