package dev.chorus.core.feedback;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * A sound played to one player.
 *
 * <p>The key is handed to Bukkit as a plain string rather than through the {@code Sound}
 * enum on purpose: that type is an enum on 1.18 and an interface on 26, so any code that
 * named a constant would fail to link on half of the supported versions. A string the
 * client does not know simply plays nothing.
 */
public record SoundCue(String key, float volume, float pitch) {

    public static final SoundCue NONE = new SoundCue("", 1, 1);

    /** Anything the block leaves out is taken from {@code base}, which is the defaults block. */
    public static SoundCue read(ConfigurationSection parent, SoundCue base) {
        ConfigurationSection sound = parent.getConfigurationSection("sound");
        if (sound == null) {
            return base;
        }
        return new SoundCue(
                sound.getString("key", base.key()).trim(),
                (float) clamp(sound.getDouble("volume", base.volume()), 0, 10),
                (float) clamp(sound.getDouble("pitch", base.pitch()), 0.5, 2));
    }

    public void play(Player player, Location where) {
        if (key.isEmpty() || volume <= 0) {
            return;
        }
        player.playSound(where, key, volume, pitch);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
