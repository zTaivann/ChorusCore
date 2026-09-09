package dev.chorus.core.command;

import dev.chorus.core.config.ConfigFile;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Puts the aliases from aliases.yml into the server's command map.
 *
 * <p>plugin.yml deliberately declares none, so this file is the only place aliases come
 * from and there is never a stale one left behind by an edit. They are applied while the
 * plugin enables, which is why changing them asks for a restart rather than a reload.
 */
public final class CommandAliases {

    private CommandAliases() {
    }

    public static void apply(JavaPlugin plugin, ConfigFile file, List<String> commands) {
        CommandMap map = plugin.getServer().getCommandMap();
        String fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);

        for (String name : commands) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                continue;
            }

            List<String> aliases = clean(file.data().getStringList(name), name);
            if (aliases.isEmpty()) {
                continue;
            }

            // Bukkit drops aliases that are already taken by editing this list in place,
            // so it has to be one we own and can modify.
            command.setAliases(aliases);
            map.register(fallbackPrefix, command);
        }
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
