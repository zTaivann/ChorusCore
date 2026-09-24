package dev.chorus.core.shops;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.shops.chest.ChestShopDisplays;
import dev.chorus.core.shops.chest.ChestShopListener;
import dev.chorus.core.shops.chest.ChestShopSettings;
import dev.chorus.core.shops.chest.ChestShops;
import dev.chorus.core.shops.chest.SqlChestShopRepository;
import dev.chorus.core.shops.command.SellCommand;
import dev.chorus.core.shops.command.SetWorthCommand;
import dev.chorus.core.shops.command.ShopCommand;
import dev.chorus.core.shops.command.WorthCommand;

import java.sql.SQLException;
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
    private ChestShops chestShops;
    private ChestShopListener chestSigns;
    private ChestShopDisplays displays;

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
        return List.of("sell", "worth", "setworth", "shop");
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
        SqlChestShopRepository store = new SqlChestShopRepository(plugin.storage());
        try {
            store.createTables();
        } catch (SQLException exception) {
            throw new IllegalStateException("The chest shop table could not be created", exception);
        }
        chestShops = new ChestShops(store, plugin.worker(), plugin.getLogger());
        try {
            chestShops.load();
        } catch (SQLException exception) {
            throw new IllegalStateException("The chest shops could not be read", exception);
        }

        displays = new ChestShopDisplays(plugin, chestShops, plugin.schedulers());
        displays.apply(config.section("shops").getBoolean("chest.display", true));

        chestSigns = new ChestShopListener(chestShops, displays, plugin.messages(),
                plugin.economy(), plugin.prompts(),
                ChestShopSettings.read(config.section("shops")));
        plugin.register(chestSigns);
        // The worlds are already loaded when a module starts, so chunk events are not enough.
        displays.showEverything();
        // So /editsign and anything else that rewrites a block leaves a shop alone.
        plugin.reserved().register(block -> chestShops.at(block) != null);
        commands.add(plugin.register(new ShopCommand(support, chestShops, displays,
                plugin.economy(), plugin.confirmations())));

        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
        applySigns();

        plugin.getLogger().info("Shops: " + worth.size() + " items have a price, "
                + chestShops.size() + " chest shops.");
    }

    @Override
    public void disable() {
        if (displays != null) {
            displays.forgetAll();
        }
        if (chestShops != null) {
            chestShops.clear();
        }
    }

    @Override
    public void reload() {
        if (worth == null) {
            return;
        }
        readWorth();
        chestSigns.apply(ChestShopSettings.read(config.section("shops")));
        displays.apply(config.section("shops").getBoolean("chest.display", true));
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
