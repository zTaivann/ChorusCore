package dev.chorus.core.utility;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.teleport.TeleportService;
import dev.chorus.core.text.MotdListener;
import dev.chorus.core.text.ServerText;
import dev.chorus.core.text.command.TextCommand;
import dev.chorus.core.utility.command.BottomCommand;
import dev.chorus.core.utility.command.BreakCommand;
import dev.chorus.core.utility.command.BurnCommand;
import dev.chorus.core.utility.command.CompassCommand;
import dev.chorus.core.utility.command.DepthCommand;
import dev.chorus.core.utility.command.DisposalCommand;
import dev.chorus.core.utility.command.ExtinguishCommand;
import dev.chorus.core.utility.command.FeedCommand;
import dev.chorus.core.utility.command.FireballCommand;
import dev.chorus.core.utility.command.FlyCommand;
import dev.chorus.core.utility.command.GodCommand;
import dev.chorus.core.utility.command.HealCommand;
import dev.chorus.core.utility.command.JumpCommand;
import dev.chorus.core.utility.command.MenuCommand;
import dev.chorus.core.utility.command.MirrorCommand;
import dev.chorus.core.utility.command.NearCommand;
import dev.chorus.core.utility.command.PingCommand;
import dev.chorus.core.utility.command.PositionCommand;
import dev.chorus.core.utility.command.PowertoolCommand;
import dev.chorus.core.utility.command.PowertoolToggleCommand;
import dev.chorus.core.utility.command.RepairCommand;
import dev.chorus.core.utility.command.RestCommand;
import dev.chorus.core.utility.command.SmiteCommand;
import dev.chorus.core.utility.command.SpeedCommand;
import dev.chorus.core.utility.command.SuicideCommand;
import dev.chorus.core.utility.command.TopCommand;
import dev.chorus.core.utility.command.TpsCommand;
import dev.chorus.core.utility.powertool.PowertoolListener;
import dev.chorus.core.utility.powertool.Powertools;
import dev.chorus.core.utility.powertool.SqlPowertoolRepository;
import dev.chorus.core.utility.signs.ServiceSignListener;

import java.sql.SQLException;
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
    private Powertools powertools;
    private ServerText motdText;
    private ServerText infoText;
    private MotdListener motdOnJoin;

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
                "burn", "jump", "bottom", "break", "depth", "compass", "rest", "ext", "smite",
                "fireball", "powertool", "powertooltoggle", "motd", "info"));
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

        SqlPowertoolRepository powertoolStore = new SqlPowertoolRepository(plugin.storage());
        try {
            powertoolStore.createTables();
        } catch (SQLException exception) {
            throw new IllegalStateException("The powertools table could not be created", exception);
        }
        powertools = new Powertools(powertoolStore, plugin.worker(), plugin.getLogger());
        plugin.register(new PowertoolListener(powertools, plugin.flags(), plugin.messages(),
                plugin.getLogger()));

        motdText = new ServerText(plugin, "motd.txt");
        infoText = new ServerText(plugin, "info.txt");
        motdText.reload();
        infoText.reload();

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
        commands.add(plugin.register(new ExtinguishCommand(support)));
        commands.add(plugin.register(new SmiteCommand(support, plugin.schedulers())));
        commands.add(plugin.register(new FireballCommand(support, utility)));
        commands.add(plugin.register(new PowertoolCommand(support, powertools)));
        commands.add(plugin.register(new PowertoolToggleCommand(support, plugin.flags())));
        commands.add(plugin.register(new NearCommand(support, utility)));

        TextCommand motd = plugin.register(new TextCommand(support, "motd", motdText));
        commands.add(motd);
        commands.add(plugin.register(new TextCommand(support, "info", infoText)));
        motdOnJoin = new MotdListener(motd);
        motdOnJoin.apply(utility.settings().motd().showOnJoin());
        plugin.register(motdOnJoin);
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
        if (powertools != null) {
            powertools.clearAll();
        }
    }

    @Override
    public void reload() {
        if (utility == null) {
            return;
        }
        utility.apply(readSettings());
        serviceSigns.apply(config.section("utility").getBoolean("signs", true));
        motdOnJoin.apply(utility.settings().motd().showOnJoin());
        motdText.reload();
        infoText.reload();
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    private UtilitySettings readSettings() {
        return UtilitySettings.read(config.section("utility"),
                name -> plugin.getLogger().warning(
                        "This server has no material called '" + name + "', configured in " + CONFIG));
    }
}
