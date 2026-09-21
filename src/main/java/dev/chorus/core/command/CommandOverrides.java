package dev.chorus.core.command;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** Sends a name taken from the server's own commands to the command that took it. */
public final class CommandOverrides implements Listener {

    /** Taken name to the command of ours that answers to it, both already in lower case. */
    private final Map<String, String> redirects;

    public CommandOverrides(Map<String, String> redirects) {
        this.redirects = redirects;
    }

    public boolean isEmpty() {
        return redirects.isEmpty();
    }

    /**
     * Last of all, so everything else sees the line the player actually typed. A plugin that
     * logs commands should log {@code /clear}, and one that blocks it should still block it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String rewritten = rewrite(event.getMessage().startsWith("/")
                ? event.getMessage().substring(1)
                : event.getMessage());
        if (rewritten != null) {
            event.setMessage("/" + rewritten);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        String rewritten = rewrite(event.getCommand());
        if (rewritten != null) {
            event.setCommand(rewritten);
        }
    }

    /** @return the line with its first word swapped, or null when there is nothing to swap. */
    private @Nullable String rewrite(String line) {
        int space = line.indexOf(' ');
        String label = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);

        // Only the bare name. Somebody who typed minecraft:clear asked for that one.
        String ours = redirects.get(label);
        if (ours == null || ours.equals(label)) {
            return null;
        }
        return space < 0 ? ours : ours + line.substring(space);
    }
}
