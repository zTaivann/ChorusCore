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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

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
        return List.of("kit", "kits", "kitedit");
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

        kits = new KitService(repository, plugin.worker(), plugin.mainThread());
        load();

        plugin.register(new KitDataListener(kits, plugin.messages(), plugin.getLogger()));
        commands.add(plugin.register(new KitCommand(support, kits, plugin.getLogger())));
        commands.add(plugin.register(new KitListCommand(support, kits, () -> settings)));
        commands.add(plugin.register(new KitEditCommand(support, kits, new KitEditor(config, this::reloadKits), plugin.economy())));
        CommandRules.applyAll(config.section("commands"), commands, plugin.getLogger());

        loadPlayersAlreadyOnline();
    }

    @Override
    public void disable() {
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

    /** Re-reads the file after /kitedit has written to it. */
    private void reloadKits() {
        config.reload();
        load();
    }

    private void load() {
        ConfigurationSection root = config.section("kits");
        settings = KitSettings.read(root, this::warn);

        ConfigurationSection definitions = root.getConfigurationSection("definitions");
        Map<String, Kit> loaded = KitReader.read(
                definitions != null ? definitions : new MemoryConfiguration(),
                plugin.messages(), this::warn);
        kits.apply(loaded, settings.firstJoinKit());

        if (!settings.firstJoinKit().isEmpty() && !loaded.containsKey(settings.firstJoinKit())) {
            warn("first-join names a kit called '" + settings.firstJoinKit() + "' that does not exist");
        }
    }

    private void warn(String problem) {
        plugin.getLogger().warning(CONFIG + ": " + problem);
    }

    /** Covers the case of the plugin being enabled on a server that is already running. */
    private void loadPlayersAlreadyOnline() {
        List<UUID> online = plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList();
        if (online.isEmpty()) {
            return;
        }

        plugin.worker().execute(() -> {
            for (UUID playerId : online) {
                try {
                    kits.load(playerId);
                } catch (SQLException exception) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Could not load the kit history of " + playerId, exception);
                }
            }
        });
    }
}
