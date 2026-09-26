package dev.chorus.core.kits;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.kits.command.KitCommand;
import dev.chorus.core.kits.command.KitEditCommand;
import dev.chorus.core.kits.command.KitListCommand;
import dev.chorus.core.kits.command.KitResetCommand;
import dev.chorus.core.kits.menu.KitEditMenu;
import dev.chorus.core.kits.menu.KitMenuSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class KitsModule implements ChorusModule {

    private static final String CONFIG = "modules/kits.yml";

    private final ChorusPlugin plugin;
    private final CommandSupport support;
    private final List<ChorusCommand> commands = new ArrayList<>();

    private ConfigFile config;
    private KitService kits;
    private volatile KitSettings settings;

    public KitsModule(ChorusPlugin plugin, CommandSupport support) {
        this.plugin = plugin;
        this.support = support;
    }

    @Override
    public String name() {
        return "kits";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    @Override
    public List<String> commandNames() {
        return List.of("kit", "kits", "kitedit", "kitreset");
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);

        KitRepository repository = new SqlKitRepository(plugin.storage());
        try {
            repository.createTables();
        } catch (SQLException exception) {
            throw new IllegalStateException("The kit history table could not be created", exception);
        }

        kits = new KitService(repository, plugin.backups(), plugin.messages(),
                plugin.economy(), plugin.worker(), plugin.mainThread(), plugin.schedulers());
        load();

        plugin.loginData().add("kit history", kits, true);
        support.guard().claimedKits(kits::hasClaimed);
        plugin.register(new FirstJoinKitListener(kits, plugin.messages(), plugin.getLogger()));
        commands.add(plugin.register(new KitCommand(support, kits, plugin.getLogger())));
        commands.add(plugin.register(new KitListCommand(support, kits, () -> settings)));
        commands.add(plugin.register(new KitResetCommand(support, kits)));
        KitEditor editor = new KitEditor(config, this::load);
        KitEditMenu menu = new KitEditMenu(plugin, plugin.schedulers(), kits, editor,
                plugin.messages(), plugin.economy(), plugin.prompts(),
                KitMenuSettings.read(config.section("kits.editor"), this::warn));
        commands.add(plugin.register(
                new KitEditCommand(support, kits, editor, menu, plugin.economy())));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    @Override
    public void disable() {
        support.guard().claimedKits(null);
        if (kits != null) {
            kits.clear();
        }
    }

    @Override
    public void reload() {
        if (kits == null) {
            return;
        }
        load();
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());
    }

    /** Reads the kits out of the config already in memory, never off the disk. */
    private void load() {
        ConfigurationSection root = config.section("kits");
        settings = KitSettings.read(root, this::warn);

        ConfigurationSection definitions = root.getConfigurationSection("definitions");
        Map<String, Kit> loaded = KitReader.read(
                definitions != null ? definitions : new MemoryConfiguration(), this::warn);
        kits.apply(loaded, settings.firstJoinKit());

        if (!settings.firstJoinKit().isEmpty() && !loaded.containsKey(settings.firstJoinKit())) {
            warn("first-join names a kit called '" + settings.firstJoinKit() + "' that does not exist");
        }
    }

    private void warn(String problem) {
        plugin.getLogger().warning(CONFIG + ": " + problem);
    }
}
