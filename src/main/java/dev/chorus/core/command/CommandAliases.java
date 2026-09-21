package dev.chorus.core.command;

import dev.chorus.core.config.ConfigFile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Puts the aliases from aliases.yml into the server's command map. */
public final class CommandAliases {

    private CommandAliases() {
    }

    /**
     * @return the names taken from the server's own commands, each mapped to the command of
     *         ours that should answer to it.
     */
    public static Map<String, String> apply(JavaPlugin plugin, ConfigFile file,
                                            List<String> commands, Set<String> switchedOff) {
        CommandMap map = plugin.getServer().getCommandMap();
        String fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);
        Map<String, String> taken = new HashMap<>();
        boolean changed = false;

        for (String name : commands) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                continue;
            }

            List<String> aliases = clean(file.data().getStringList(name), name);

            boolean live = !switchedOff.contains(name);
            boolean freed = live && takeOverBuiltIn(map, name, command);
            if (live) {
                for (String alias : aliases) {
                    // Bukkit silently drops an alias another command already owns.
                    if (takeOverBuiltIn(map, alias, command)) {
                        taken.put(alias, name);
                        freed = true;
                    }
                }
            }
            if (aliases.isEmpty() && !freed) {
                continue;
            }

            command.unregister(map);
            command.setAliases(new ArrayList<>(aliases));
            map.register(fallbackPrefix, command);
            changed = true;
        }

        if (changed) {
            // Clients are told the command list on join, so it has to be sent again.
            plugin.getServer().getOnlinePlayers().forEach(Player::updateCommands);
        }
        return Map.copyOf(taken);
    }

    /**
     * Frees a name the server itself already answers to, such as {@code /tps} or
     * {@code /list}.
     *
     * @return whether anything had to be moved out of the way.
     */
    private static boolean takeOverBuiltIn(CommandMap map, String name, PluginCommand ours) {
        Command holder = map.getCommand(name);
        if (holder == null || holder.equals(ours) || holder instanceof PluginCommand) {
            return false;
        }
        map.getKnownCommands().remove(name);
        return true;
    }

    private static List<String> clean(List<String> configured, String command) {
        List<String> aliases = new ArrayList<>(configured.size());
        for (String raw : configured) {
            String alias = raw.trim().toLowerCase(Locale.ROOT);
            if (!alias.isEmpty() && !alias.equals(command) && !aliases.contains(alias)) {
                aliases.add(alias);
            }
        }
        return aliases;
    }
}
