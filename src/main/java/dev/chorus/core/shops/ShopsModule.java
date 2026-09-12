package dev.chorus.core.shops;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.shops.command.SellCommand;
import dev.chorus.core.shops.command.SetWorthCommand;
import dev.chorus.core.shops.command.WorthCommand;

import java.util.ArrayList;
import java.util.List;

/** Shops run by the server: the signs, and the prices behind them. */
public final class ShopsModule implements ChorusModule {

    private static final String CONFIG = "modules/shops.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private WorthTable worth;
    private ShopSignListener signs;

    public ShopsModule(ChorusPlugin plugin, CommandSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    @Override
    public String name() {
        return "shops";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("sell", "worth", "setworth");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        worth = new WorthTable(config);
        readWorth();

        ShopService service = new ShopService(plugin.economy());
        signs = new ShopSignListener(service, worth, plugin.messages());
        plugin.register(signs);

        commands.add(plugin.register(new SellCommand(support, worth, plugin.economy())));
        commands.add(plugin.register(new WorthCommand(support, worth, plugin.economy())));
        commands.add(plugin.register(new SetWorthCommand(support, worth, plugin.economy())));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
        applySigns();

        plugin.getLogger().info("Shops: " + worth.size() + " items have a price.");
    }

    @Override
    public void disable() {
    }

    @Override
    public void reload() {
        if (worth == null) {
            return;
        }
        readWorth();
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
        applySigns();
    }

    /** The signs borrow /sell's sound, since a sign is another way of running it. */
    private void applySigns() {
        signs.apply(config.section("shops").getBoolean("signs", true),
                CommandRules.read(config.section("commands"), "sell", plugin.getLogger()));
    }

    private void readWorth() {
        worth.reload(name -> plugin.getLogger().warning(
                "This server has no material called '" + name + "', priced in " + CONFIG));
    }
}
