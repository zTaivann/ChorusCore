package dev.chorus.core.custom;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.config.ConfigFile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Commands defined entirely in the config, for the /discord and /rules of this world. */
public final class CustomCommandsModule implements ChorusModule {

    private static final String CONFIG = "modules/custom-commands.yml";

    private final ChorusPlugin plugin;
    private final List<CustomCommand> registered = new ArrayList<>();

    private ConfigFile config;

    public CustomCommandsModule(ChorusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "custom-commands";
    }

    @Override
    public String configPath() {
        return CONFIG;
    }

    /** None: everything this module owns is invented at runtime. */
    @Override
    public List<String> commandNames() {
        return List.of();
    }

    @Override
    public void enable() {
        config = plugin.configs().get(CONFIG);
        register();
    }

    @Override
    public void disable() {
        unregister();
    }

    /**
     * What an existing command says and does is read again. Adding, renaming or removing
     * one, or changing its aliases, needs a restart, the same as an alias does.
     */
    @Override
    public void reload() {
        Map<String, ConfigurationSection> blocks = blocks();
        for (CustomCommand command : registered) {
            ConfigurationSection block = blocks.remove(command.definition().name());
            if (block != null) {
                command.update(CustomDefinition.read(block, command.definition().name(), this::warn));
            }
        }
        if (!blocks.isEmpty()) {
            plugin.getLogger().info("New or renamed custom commands need a server restart.");
        }
    }

    private void register() {
        CommandMap map = plugin.getServer().getCommandMap();
        String fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);

        for (Map.Entry<String, ConfigurationSection> entry : blocks().entrySet()) {
            CustomDefinition definition = CustomDefinition.read(entry.getValue(), entry.getKey(), this::warn);
            if (definition.doesNothing()) {
                warn("/" + definition.name() + " has no messages, commands or on-success actions,"
                        + " so it was skipped.");
                continue;
            }

            CustomCommand command = new CustomCommand(definition, plugin.support(),
                    plugin.cooldowns(), plugin.getServer());
            map.register(fallbackPrefix, command);
            registered.add(command);
        }

        if (!registered.isEmpty()) {
            plugin.getLogger().info("Registered " + registered.size() + " custom commands.");
        }
    }

    private void unregister() {
        if (registered.isEmpty()) {
            return;
        }
        CommandMap map = plugin.getServer().getCommandMap();
        // Removing by identity clears the name, the aliases and the prefixed forms at once.
        for (Command command : registered) {
            map.getKnownCommands().values().removeIf(known -> known == command);
            command.unregister(map);
        }
        registered.clear();
    }

    /** Every command block in the file, by its name in lower case. */
    private Map<String, ConfigurationSection> blocks() {
        ConfigurationSection custom = config.section("custom");
        Map<String, ConfigurationSection> blocks = new LinkedHashMap<>();
        for (String name : custom.getKeys(false)) {
            ConfigurationSection block = custom.getConfigurationSection(name);
            if (block != null) {
                blocks.put(name.toLowerCase(Locale.ROOT), block);
            }
        }
        return blocks;
    }

    private void warn(String problem) {
        plugin.getLogger().warning(CONFIG + ": " + problem);
    }
}
