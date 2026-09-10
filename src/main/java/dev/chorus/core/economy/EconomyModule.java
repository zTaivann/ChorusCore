package dev.chorus.core.economy;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.economy.command.BalanceCommand;
import dev.chorus.core.economy.command.BalanceTopCommand;
import dev.chorus.core.economy.command.EcoCommand;
import dev.chorus.core.economy.command.PayCommand;
import dev.chorus.core.economy.command.PayLogCommand;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class EconomyModule implements ChorusModule {

    private static final String CONFIG = "modules/economy.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private EconomyService service;
    private PaymentLog log;

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
        return List.of("pay", "balance", "baltop", "eco", "paylog");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        EconomySettings settings = readSettings();
        service = new EconomyService(settings);

        PaymentRepository payments = new SqlPaymentRepository(plugin.storage());
        try {
            payments.createTables();
        } catch (SQLException exception) {
            throw new IllegalStateException("The payment log table could not be created", exception);
        }
        log = new PaymentLog(payments, plugin.worker(), plugin.mainThread(), plugin.getLogger(),
                settings);
        log.prune();

        Economy economy = plugin.economy();
        commands.add(plugin.register(new PayCommand(support, economy, service, log, plugin.getLogger())));
        commands.add(plugin.register(new BalanceCommand(support, economy)));
        commands.add(plugin.register(new BalanceTopCommand(support, economy, service)));
        commands.add(plugin.register(new EcoCommand(support, economy, plugin.audit())));
        commands.add(plugin.register(new PayLogCommand(support, log, economy)));
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
        EconomySettings settings = readSettings();
        service.apply(settings);
        log.apply(settings);
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    private EconomySettings readSettings() {
        return EconomySettings.read(config.section("economy"));
    }
}
