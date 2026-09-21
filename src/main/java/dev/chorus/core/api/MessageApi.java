package dev.chorus.core.api;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * The plugin's messages folder. Placeholders are given in pairs, without the percent signs:
 * {@code send(player, "home.created", "home", name)}.
 */
public interface MessageApi {

    void send(CommandSender target, String key, String... placeholders);

    Component render(String key, String... placeholders);

    /** The prefix on its own, for a line an addon builds itself. */
    Component prefix();
}
