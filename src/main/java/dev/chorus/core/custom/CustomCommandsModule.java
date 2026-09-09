package dev.chorus.core.custom;

import dev.chorus.core.ChorusModule;
import dev.chorus.core.ChorusPlugin;
import dev.chorus.core.config.ConfigFile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Commands defined entirely in the config, for the /discord and /rules of this world.
 *
 * <p>They are registered into the server's command map by hand, since plugin.yml cannot
 * describe something the admin has not invented yet. They are taken back out on shutdown so
 * a plugin reload does not leave two of each behind.
 */
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
     * Adding or removing a command needs a restart, the same as aliases do, because the
     * client is told the command list once when it connects.
     */
    @Override
    public void reload() {
        plugin.getLogger().info("New or renamed custom commands need a server restart.");
    }

    private void register() {
        ConfigurationSection custom = config.section("custom");
        CommandMap map = plugin.getServer().getCommandMap();
        String fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);

        for (String name : custom.getKeys(false)) {
            ConfigurationSection block = custom.getConfigurationSection(name);
            if (block == null) {
                continue;
            }

            CustomDefinition definition = CustomDefinition.read(block, name.toLowerCase(Locale.ROOT));
            if (definition.doesNothing()) {
                plugin.getLogger().warning(CONFIG + ": /" + definition.name()
                        + " has no messages and runs no commands, so it was skipped.");
                continue;
            }

            CustomCommand command = new CustomCommand(definition, plugin.messages(),
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
}
