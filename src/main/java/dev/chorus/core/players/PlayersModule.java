package dev.chorus.core.players;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.players.command.AfkCommand;
import dev.chorus.core.players.command.ListCommand;
import dev.chorus.core.players.command.NickCommand;
import dev.chorus.core.players.command.PlayerTimeCommand;
import dev.chorus.core.players.command.PlayerWeatherCommand;
import dev.chorus.core.players.command.PlaytimeCommand;
import dev.chorus.core.players.command.RealNameCommand;
import dev.chorus.core.players.command.SeenCommand;
import dev.chorus.core.players.command.WhoisCommand;

import java.util.ArrayList;
import java.util.List;

public final class PlayersModule implements ChorusModule {

    private static final String CONFIG = "modules/players.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private AfkService afk;
    private GeoLookup geo;

    public PlayersModule(ChorusPlugin plugin, CommandSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    @Override
    public String name() {
        return "players";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("afk", "seen", "playtime", "whois", "list", "ptime", "pweather",
                "nick", "realname");
    }

    public AfkService afk() {
        return afk;
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        afk = new AfkService(plugin, plugin.messages(), plugin.schedulers(),
                PlayerSettings.read(config.section("players")));
        geo = new GeoLookup(plugin.worker());
        geo.apply(config.section("players"));
        plugin.register(afk);
        afk.start();

        commands.add(plugin.register(new AfkCommand(support, afk)));
        commands.add(plugin.register(new SeenCommand(support, afk, plugin.profiles())));
        commands.add(plugin.register(new PlaytimeCommand(support)));
        commands.add(plugin.register(new WhoisCommand(support, afk, geo)));
        commands.add(plugin.register(new ListCommand(support, afk)));
        commands.add(plugin.register(new PlayerTimeCommand(support)));
        commands.add(plugin.register(new PlayerWeatherCommand(support)));
        commands.add(plugin.register(new NickCommand(support, plugin.profiles(),
                () -> config.section("players").getInt("max-nick-length", 16))));
        commands.add(plugin.register(new RealNameCommand(support, plugin.profiles())));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    @Override
    public void disable() {
        if (afk != null) {
            afk.shutdown();
        }
        if (geo != null) {
            geo.clear();
        }
    }

    @Override
    public void reload() {
        if (afk == null) {
            return;
        }
        afk.apply(PlayerSettings.read(config.section("players")));
        geo.apply(config.section("players"));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }
}
