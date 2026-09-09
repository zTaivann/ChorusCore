package dev.chorus.core.economy;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.economy.command.BalanceCommand;
import dev.chorus.core.economy.command.PayCommand;

import java.util.ArrayList;
import java.util.List;

public final class EconomyModule implements ChorusModule {

    private static final String CONFIG = "modules/economy.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private EconomyService service;

    public EconomyModule(ChorusPlugin plugin, CommandSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    @Override
    public String name() {
        return "economy";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("pay", "balance");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        service = new EconomyService(EconomySettings.read(config.section("economy")));

        Economy economy = plugin.economy();
        commands.add(plugin.register(new PayCommand(support, economy, service, plugin.getLogger())));
        commands.add(plugin.register(new BalanceCommand(support, economy)));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());

        if (!economy.enabled()) {
            plugin.getLogger().info("No economy is available, so /pay and /balance will say so. "
                    + "Install Vault plus an economy plugin, or check economy.enabled in config.yml.");
        }
    }

    @Override
    public void disable() {
    }

    @Override
    public void reload() {
        if (service == null) {
            return;
        }
        service.apply(EconomySettings.read(config.section("economy")));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }
}
