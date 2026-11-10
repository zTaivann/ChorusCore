package dev.chorus.core.config;

import org.jetbrains.annotations.Nullable;

/** Reading an enum constant out of a config value without the exception valueOf throws. */
public final class Enums {

    private Enums() {
    }

    /** The constant whose name is exactly {@code name}, or null. */
    public static <E extends Enum<E>> @Nullable E named(E[] values, String name) {
        for (E value : values) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return null;
    }
}
