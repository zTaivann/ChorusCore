package dev.chorus.core.world;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The mobs a command may spawn: living ones the server can spawn at all. */
public final class Mobs {

    /** Their names as players type them, worked out once rather than on every key. */
    public static final List<String> NAMES = Arrays.stream(EntityType.values())
            .filter(Mobs::spawnable)
            .map(type -> type.name().toLowerCase(Locale.ROOT))
            .toList();

    private Mobs() {
    }

    /** The mob a player meant, or null when it is not one or may not be spawned. */
    public static @Nullable EntityType parse(String raw) {
        try {
            EntityType type = EntityType.valueOf(raw.toUpperCase(Locale.ROOT).replace(' ', '_'));
            return spawnable(type) ? type : null;
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static boolean spawnable(EntityType type) {
        return type.isSpawnable() && type.isAlive();
    }
}
