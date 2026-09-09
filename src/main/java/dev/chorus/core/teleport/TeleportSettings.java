package dev.chorus.core.teleport;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Rules that hold for every teleport. The wait itself is per command and lives with that
 * command's other rules, since /home and /warp rarely want the same one.
 */
public record TeleportSettings(boolean cancelOnMove, boolean cancelOnDamage,
                               boolean rememberPreviousLocation) {

    public static TeleportSettings read(ConfigurationSection teleport) {
        return new TeleportSettings(
                teleport.getBoolean("cancel-on-move", true),
                teleport.getBoolean("cancel-on-damage", true),
                teleport.getBoolean("remember-previous-location", true));
    }
}
