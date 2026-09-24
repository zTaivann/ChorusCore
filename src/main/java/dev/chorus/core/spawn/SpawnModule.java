package dev.chorus.core.spawn;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.location.LocationRepository;
import dev.chorus.core.location.LocationService;
import dev.chorus.core.location.SqlLocationRepository;
import dev.chorus.core.spawn.command.SetSpawnCommand;
import dev.chorus.core.spawn.command.SpawnCommand;
import dev.chorus.core.teleport.TeleportService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SpawnModule implements ChorusModule {

    private static final String CONFIG = "modules/spawn.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final TeleportService teleports;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private SpawnService spawn;

    public SpawnModule(ChorusPlugin plugin, CommandSupport support, TeleportService teleports) {
        this.plugin = plugin;
        this.support = support;
        this.teleports = teleports;
    }

    @Override
    public String name() {
        return "spawn";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("spawn", "setspawn");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);

        LocationRepository repository = new SqlLocationRepository(plugin.storage());
        LocationService locations = new LocationService(SpawnService.CATEGORY, repository,
                plugin.worker(), plugin.mainThread());
        try {
            repository.createTables();
            locations.loadAll();
        } catch (SQLException exception) {
            throw new IllegalStateException("The spawn points could not be loaded", exception);
        }

        spawn = new SpawnService(locations, SpawnSettings.read(config.section("spawn")));
        plugin.provide(spawn);
        plugin.register(new SpawnListener(spawn));

        commands.add(plugin.register(new SpawnCommand(support, spawn, teleports)));
        commands.add(plugin.register(new SetSpawnCommand(support, spawn, plugin.getLogger())));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    @Override
    public void reload() {
        if (spawn == null) {
            return;
        }
        spawn.apply(SpawnSettings.read(config.section("spawn")));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }
}
