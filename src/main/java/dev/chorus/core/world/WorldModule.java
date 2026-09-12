package dev.chorus.core.world;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.teleport.TeleportService;
import dev.chorus.core.world.command.SpawnMobCommand;
import dev.chorus.core.world.command.SweepCommand;
import dev.chorus.core.world.command.TimeCommand;
import dev.chorus.core.world.command.WeatherCommand;
import dev.chorus.core.world.command.WorldCommand;

import java.util.ArrayList;
import java.util.List;

/** The world itself: its clock, its weather, what is standing in it and how to get there. */
public final class WorldModule implements ChorusModule {

    private static final String CONFIG = "modules/world.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final TeleportService teleports;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;

    public WorldModule(ChorusPlugin plugin, CommandSupport support, TeleportService teleports) {
        this.plugin = plugin;
        this.support = support;
        this.teleports = teleports;
    }

    @Override
    public String name() {
        return "world";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("world", "time", "weather", "spawnmob", "sweep");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);

        commands.add(plugin.register(new WorldCommand(support, teleports,
                () -> config.section("world").getBoolean("per-world-permission", false))));
        commands.add(plugin.register(new TimeCommand(support)));
        commands.add(plugin.register(new WeatherCommand(support)));
        commands.add(plugin.register(new SpawnMobCommand(support,
                () -> config.section("world").getInt("spawnmob-limit", 20))));
        commands.add(plugin.register(new SweepCommand(support, plugin.confirmations())));

        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    @Override
    public void disable() {
    }

    @Override
    public void reload() {
        if (config == null) {
            return;
        }
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }
}
