package dev.chorus.core.items;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.items.command.ClearInventoryCommand;
import dev.chorus.core.items.command.CondenseCommand;
import dev.chorus.core.items.command.EnchantCommand;
import dev.chorus.core.items.command.GlowCommand;
import dev.chorus.core.items.command.HatCommand;
import dev.chorus.core.items.command.ItemNameCommand;
import dev.chorus.core.items.command.LoreCommand;
import dev.chorus.core.items.command.MoreCommand;
import dev.chorus.core.items.command.SkullCommand;
import dev.chorus.core.items.command.StackCommand;
import dev.chorus.core.items.command.UnbreakableCommand;

import java.util.ArrayList;
import java.util.List;

public final class ItemsModule implements ChorusModule {

    private static final String CONFIG = "modules/items.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private ItemService items;

    public ItemsModule(ChorusPlugin plugin, CommandSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    @Override
    public String name() {
        return "items";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("hat", "condense", "clearinventory", "itemname", "lore", "more", "skull",
                "unbreakable", "glow", "enchant", "stack");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        items = new ItemService(readSettings());

        commands.add(plugin.register(new HatCommand(support)));
        commands.add(plugin.register(new CondenseCommand(support, items)));
        commands.add(plugin.register(new ClearInventoryCommand(support)));
        commands.add(plugin.register(new ItemNameCommand(support, items)));
        commands.add(plugin.register(new LoreCommand(support, items)));
        commands.add(plugin.register(new MoreCommand(support)));
        commands.add(plugin.register(new SkullCommand(support)));
        commands.add(plugin.register(new UnbreakableCommand(support)));
        commands.add(plugin.register(new GlowCommand(support)));
        commands.add(plugin.register(new EnchantCommand(support)));
        commands.add(plugin.register(new StackCommand(support)));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    @Override
    public void disable() {
    }

    @Override
    public void reload() {
        if (items == null) {
            return;
        }
        items.apply(readSettings());
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    private ItemSettings readSettings() {
        return ItemSettings.read(config.section("items"),
                name -> plugin.getLogger().warning(
                        "This server has no material called '" + name + "', configured in " + CONFIG));
    }
}
