package dev.chorus.core.home;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.home.command.DelHomeCommand;
import dev.chorus.core.home.command.HomeCommand;
import dev.chorus.core.home.command.HomeIconCommand;
import dev.chorus.core.home.command.HomeListCommand;
import dev.chorus.core.home.command.RenameHomeCommand;
import dev.chorus.core.home.command.SetHomeCommand;
import dev.chorus.core.teleport.TeleportService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class HomeModule implements ChorusModule {

    private static final String CONFIG = "modules/homes.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final TeleportService teleports;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private HomeService homes;

    public HomeModule(ChorusPlugin plugin, CommandSupport support, TeleportService teleports) {
        this.plugin = plugin;
        this.support = support;
        this.teleports = teleports;
    }

    @Override
    public String name() {
        return "homes";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("home", "sethome", "delhome", "homes", "renamehome", "homeicon");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);

        HomeRepository repository = new SqlHomeRepository(plugin.storage());
        try {
            repository.createTables();
        } catch (SQLException exception) {
            throw new IllegalStateException("The homes table could not be created", exception);
        }

        homes = new HomeService(repository, plugin.worker(), plugin.mainThread(),
                readSettings());
        plugin.provide(homes);

        plugin.loginData().add("homes", homes, true);

        Logger logger = plugin.getLogger();

        commands.add(plugin.register(new HomeCommand(support, homes, teleports)));
        commands.add(plugin.register(
                new SetHomeCommand(support, homes, plugin.confirmations(), logger)));
        commands.add(plugin.register(
                new DelHomeCommand(support, homes, plugin.confirmations(), logger)));
        commands.add(plugin.register(new HomeListCommand(support, homes)));
        commands.add(plugin.register(new RenameHomeCommand(support, homes, logger)));
        commands.add(plugin.register(new HomeIconCommand(support, homes, logger)));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    @Override
    public void disable() {
        if (homes != null) {
            homes.clear();
        }
    }

    @Override
    public void reload() {
        if (homes == null) {
            return;
        }
        homes.apply(readSettings());
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }


    private HomeSettings readSettings() {
        return HomeSettings.read(config.section("homes"),
                name -> plugin.getLogger().warning(
                        "This server has no material called '" + name + "', configured in " + CONFIG));
    }
}
