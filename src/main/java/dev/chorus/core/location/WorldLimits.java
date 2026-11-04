package dev.chorus.core.location;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** A cap per world, from a {@code world-limits} block, on top of the overall one. */
public record WorldLimits(Map<String, Integer> limits) {

    public static final WorldLimits NONE = new WorldLimits(Map.of());

    public static WorldLimits read(@Nullable ConfigurationSection block) {
        if (block == null) {
            return NONE;
        }
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (String world : block.getKeys(false)) {
            limits.put(world.toLowerCase(Locale.ROOT), Math.max(0, block.getInt(world)));
        }
        return new WorldLimits(Map.copyOf(limits));
    }

    /** The cap for one world, or -1 when that world has none of its own. */
    public int in(String world) {
        return limits.getOrDefault(world.toLowerCase(Locale.ROOT), -1);
    }
}
