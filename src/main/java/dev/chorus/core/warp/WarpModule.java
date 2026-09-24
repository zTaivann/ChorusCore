package dev.chorus.core.warp;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.location.LocationRepository;
import dev.chorus.core.location.LocationService;
import dev.chorus.core.location.SqlLocationRepository;
import dev.chorus.core.teleport.TeleportService;
import dev.chorus.core.warp.command.DelWarpCommand;
import dev.chorus.core.warp.command.SetWarpCommand;
import dev.chorus.core.warp.command.WarpCommand;
import dev.chorus.core.warp.command.WarpInfoCommand;
import dev.chorus.core.warp.command.WarpListCommand;
import dev.chorus.core.warp.command.WarpSetCommand;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class WarpModule implements ChorusModule {

    private static final String CONFIG = "modules/warps.yml";
    private static final String CATEGORY = "warp";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final TeleportService teleports;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private WarpService warps;
    private WarpDetailsService details;
    private WarpSignListener signs;

    public WarpModule(ChorusPlugin plugin, CommandSupport support, TeleportService teleports) {
        this.plugin = plugin;
        this.support = support;
        this.teleports = teleports;
    }

    @Override
    public String name() {
        return "warps";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("warp", "warps", "setwarp", "delwarp", "warpinfo", "warpset");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);

        LocationRepository repository = new SqlLocationRepository(plugin.storage());
        LocationService locations = new LocationService(CATEGORY, repository,
                plugin.worker(), plugin.mainThread());
        WarpDetailsRepository detailStore = new SqlWarpDetailsRepository(plugin.storage());
        try {
            repository.createTables();
            locations.loadAll();
            detailStore.createTables();
        } catch (SQLException exception) {
            throw new IllegalStateException("The warps could not be loaded", exception);
        }

        Logger logger = plugin.getLogger();
        details = new WarpDetailsService(detailStore, plugin.worker(), logger);
        try {
            details.load();
        } catch (SQLException exception) {
            throw new IllegalStateException("The warp settings could not be loaded", exception);
        }

        warps = new WarpService(locations, details, readSettings());
        plugin.provide(warps);

        commands.add(plugin.register(new WarpCommand(support, warps, details, teleports)));
        commands.add(plugin.register(new WarpListCommand(support, warps, details,
                () -> config.section("warps").getString("sort", "name"))));
        commands.add(plugin.register(new SetWarpCommand(support, warps, logger)));
        commands.add(plugin.register(
                new DelWarpCommand(support, warps, details, plugin.confirmations(), logger)));
        commands.add(plugin.register(new WarpInfoCommand(support, warps, details, plugin.economy())));
        commands.add(plugin.register(new WarpSetCommand(support, warps, details)));
        CommandRules.applyAll(config.section("commands"), commands, logger);

        if (config.section("warps").getBoolean("signs", true)) {
            signs = new WarpSignListener(warps, details, teleports, plugin.messages(), support);
            plugin.register(signs);
            signs.apply(CommandRules.read(config.section("commands"), "warp", logger));
        }
    }

    @Override
    public void reload() {
        if (warps == null) {
            return;
        }
        warps.apply(readSettings());
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
        if (signs != null) {
            signs.apply(CommandRules.read(config.section("commands"), "warp", plugin.getLogger()));
        }
    }

    private WarpSettings readSettings() {
        return WarpSettings.read(config.section("warps"),
                name -> plugin.getLogger().warning(
                        "This server has no material called '" + name + "', configured in " + CONFIG));
    }
}
