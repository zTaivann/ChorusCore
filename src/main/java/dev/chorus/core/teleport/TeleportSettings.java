package dev.chorus.core.teleport;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Rules that hold for every teleport. The wait itself is per command and lives with that
 * command's other rules, since /home and /warp rarely want the same one.
 */
public record TeleportSettings(boolean cancelOnMove, boolean cancelOnDamage,
                               boolean rememberPreviousLocation, int historySize,
                               boolean warmupCountdown, boolean safeLanding,
                               int safeLandingRadius, int invulnerableSeconds) {

    private static final int MAX_HISTORY = 20;
    private static final int MAX_SEARCH = 16;
    private static final int MAX_INVULNERABLE = 30;

    public static TeleportSettings read(ConfigurationSection teleport) {
        return new TeleportSettings(
                teleport.getBoolean("cancel-on-move", true),
                teleport.getBoolean("cancel-on-damage", true),
                teleport.getBoolean("remember-previous-location", true),
                clamp(teleport.getInt("history-size", 5), 1, MAX_HISTORY),
                teleport.getBoolean("warmup-countdown", true),
                teleport.getBoolean("safe-landing", true),
                clamp(teleport.getInt("safe-landing-radius", 5), 1, MAX_SEARCH),
                clamp(teleport.getInt("invulnerable-seconds", 3), 0, MAX_INVULNERABLE));
    }

    private static int clamp(int value, int lowest, int highest) {
        return Math.max(lowest, Math.min(highest, value));
    }
}
