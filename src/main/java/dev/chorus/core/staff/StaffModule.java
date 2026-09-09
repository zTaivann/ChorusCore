package dev.chorus.core.staff;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.staff.command.GameModeCommand;
import dev.chorus.core.staff.command.TeleportHereCommand;
import dev.chorus.core.staff.command.TeleportToCommand;
import dev.chorus.core.staff.command.TpAllCommand;
import dev.chorus.core.staff.command.TpPosCommand;
import dev.chorus.core.staff.command.VanishCommand;
import dev.chorus.core.teleport.TeleportService;
import org.bukkit.GameMode;

import java.util.ArrayList;
import java.util.List;

public final class StaffModule implements ChorusModule {

    private static final String CONFIG = "modules/staff.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final TeleportService teleports;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private VanishService vanish;

    public StaffModule(ChorusPlugin plugin, CommandSupport support, TeleportService teleports) {
        this.plugin = plugin;
        this.support = support;
        this.teleports = teleports;
    }

    @Override
    public String name() {
        return "staff";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("tp", "tphere", "tppos", "tpall", "vanish",
                "gamemode", "gmc", "gms", "gma", "gmsp");
    }

    public VanishService vanish() {
        return vanish;
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        vanish = new VanishService(plugin);
        plugin.register(vanish);

        commands.add(plugin.register(new TeleportToCommand(support, teleports)));
        commands.add(plugin.register(new TeleportHereCommand(support, teleports)));
        commands.add(plugin.register(new TpPosCommand(support, teleports)));
        commands.add(plugin.register(new TpAllCommand(support, teleports)));
        commands.add(plugin.register(new VanishCommand(support, vanish)));
        commands.add(plugin.register(new GameModeCommand(support, "gamemode", null)));
        commands.add(plugin.register(new GameModeCommand(support, "gmc", GameMode.CREATIVE)));
        commands.add(plugin.register(new GameModeCommand(support, "gms", GameMode.SURVIVAL)));
        commands.add(plugin.register(new GameModeCommand(support, "gma", GameMode.ADVENTURE)));
        commands.add(plugin.register(new GameModeCommand(support, "gmsp", GameMode.SPECTATOR)));

        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    @Override
    public void disable() {
        if (vanish != null) {
            vanish.shutdown();
        }
    }

    @Override
    public void reload() {
        if (config != null) {
            CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
        }
    }
}
