package dev.chorus.core.command;

import dev.chorus.core.config.ConfigFile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
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
 *
 * <p>The unregister below is the whole reason any of this works. Bukkit keeps two lists: the
 * aliases a command was given, and the ones it is actually answering to. {@code setAliases}
 * only refreshes the second while the command is unregistered, and Bukkit registers every
 * command in plugin.yml a moment before the plugin enables. Setting them without taking the
 * command back out first therefore changes a list nothing reads, quietly, with no error to
 * say so.
 */
public final class CommandAliases {

    private CommandAliases() {
    }

    public static void apply(JavaPlugin plugin, ConfigFile file, List<String> commands) {
        CommandMap map = plugin.getServer().getCommandMap();
        String fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);
        boolean changed = false;

        for (String name : commands) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                continue;
            }

            List<String> aliases = clean(file.data().getStringList(name), name);
            boolean taken = takeOverBuiltIn(map, name, command);
            if (aliases.isEmpty() && !taken) {
                continue;
            }

            command.unregister(map);
            // Bukkit drops an alias another command already owns by editing this list in
            // place, so it has to be one we own and can modify.
            command.setAliases(new ArrayList<>(aliases));
            map.register(fallbackPrefix, command);
            changed = true;
        }

        if (changed) {
            // Clients are told the command list once, on join. Without this the aliases work
            // when typed but do not turn up in tab completion until the next reconnect.
            plugin.getServer().getOnlinePlayers().forEach(Player::updateCommands);
        }
    }

    /**
     * Frees a name the server itself already answers to, such as {@code /tps} or
     * {@code /list}.
     *
     * <p>Bukkit will not let a plugin take a name that is already spoken for, which is right
     * when the holder is another plugin and wrong when it is a built-in the owner installed
     * this one to replace. So the name is only taken when nothing from a plugin holds it:
     * another plugin's command is never touched, whichever loaded first.
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
