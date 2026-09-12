package dev.chorus.core.utility;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.teleport.TeleportService;
import dev.chorus.core.utility.command.BottomCommand;
import dev.chorus.core.utility.command.BreakCommand;
import dev.chorus.core.utility.command.BurnCommand;
import dev.chorus.core.utility.command.CompassCommand;
import dev.chorus.core.utility.command.DepthCommand;
import dev.chorus.core.utility.command.DisposalCommand;
import dev.chorus.core.utility.command.FeedCommand;
import dev.chorus.core.utility.command.FlyCommand;
import dev.chorus.core.utility.command.GodCommand;
import dev.chorus.core.utility.command.HealCommand;
import dev.chorus.core.utility.command.JumpCommand;
import dev.chorus.core.utility.command.MenuCommand;
import dev.chorus.core.utility.command.MirrorCommand;
import dev.chorus.core.utility.command.NearCommand;
import dev.chorus.core.utility.command.PingCommand;
import dev.chorus.core.utility.command.PositionCommand;
import dev.chorus.core.utility.command.RepairCommand;
import dev.chorus.core.utility.command.RestCommand;
import dev.chorus.core.utility.command.SpeedCommand;
import dev.chorus.core.utility.command.SuicideCommand;
import dev.chorus.core.utility.command.TopCommand;
import dev.chorus.core.utility.command.TpsCommand;
import dev.chorus.core.utility.signs.ServiceSignListener;

import java.util.ArrayList;
import java.util.List;

public final class UtilityModule implements ChorusModule {

    private static final String CONFIG = "modules/utility.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final TeleportService teleports;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private UtilityService utility;
    private MirrorService mirrors;
    private ServiceSignListener serviceSigns;

    public UtilityModule(ChorusPlugin plugin, CommandSupport support, TeleportService teleports) {
        this.plugin = plugin;
        this.support = support;
        this.teleports = teleports;
    }

    @Override
    public String name() {
        return "utility";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        List<String> names = new ArrayList<>(List.of("heal", "feed", "fly", "god", "ping", "fix",
                "trash", "speed", "top", "near", "invsee", "ecsee", "tps", "getpos", "suicide",
                "burn", "jump", "bottom", "break", "depth", "compass", "rest"));
        for (PortableMenu menu : PortableMenu.values()) {
            names.add(menu.command());
        }
        return names;
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        utility = new UtilityService(readSettings());
        mirrors = new MirrorService(plugin, plugin.messages(), utility, plugin.schedulers());
        plugin.register(mirrors);

        serviceSigns = new ServiceSignListener(plugin.messages(), plugin.economy());
        serviceSigns.apply(config.section("utility").getBoolean("signs", true));
        plugin.register(serviceSigns);
        plugin.reserved().register(serviceSigns::isServiceSign);

        commands.add(plugin.register(new HealCommand(support, utility)));
        commands.add(plugin.register(new FeedCommand(support, utility)));
        commands.add(plugin.register(new FlyCommand(support)));
        commands.add(plugin.register(new GodCommand(support)));
        commands.add(plugin.register(new PingCommand(support)));
        commands.add(plugin.register(new RepairCommand(support, utility)));
        commands.add(plugin.register(new DisposalCommand(support, utility)));
        commands.add(plugin.register(new SpeedCommand(support, utility)));
        commands.add(plugin.register(new TopCommand(support, utility, teleports)));
        commands.add(plugin.register(new JumpCommand(support, teleports)));
        commands.add(plugin.register(new BottomCommand(support, teleports)));
        commands.add(plugin.register(new BreakCommand(support)));
        commands.add(plugin.register(new DepthCommand(support)));
        commands.add(plugin.register(new CompassCommand(support)));
        commands.add(plugin.register(new RestCommand(support)));
        commands.add(plugin.register(new NearCommand(support, utility)));
        commands.add(plugin.register(new TpsCommand(support)));
        commands.add(plugin.register(new PositionCommand(support)));
        commands.add(plugin.register(new SuicideCommand(support)));
        commands.add(plugin.register(new BurnCommand(support)));
        commands.add(plugin.register(new MirrorCommand(support, mirrors,
                InventoryMirror.Kind.INVENTORY, "invsee")));
        commands.add(plugin.register(new MirrorCommand(support, mirrors,
                InventoryMirror.Kind.ENDER_CHEST, "ecsee")));
        for (PortableMenu menu : PortableMenu.values()) {
            commands.add(plugin.register(new MenuCommand(support, menu)));
        }

        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    @Override
    public void disable() {
        if (mirrors != null) {
            mirrors.shutdown();
        }
    }

    @Override
    public void reload() {
        if (utility == null) {
            return;
        }
        utility.apply(readSettings());
        serviceSigns.apply(config.section("utility").getBoolean("signs", true));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    private UtilitySettings readSettings() {
        return UtilitySettings.read(config.section("utility"),
                name -> plugin.getLogger().warning(
                        "This server has no material called '" + name + "', configured in " + CONFIG));
    }
}
