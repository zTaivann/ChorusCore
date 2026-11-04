package dev.chorus.core.command;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** When a command's price is taken. */
public enum ChargeOn {

    /** Once the command has worked, which for a teleport means on arrival. */
    SUCCESS,

    /** As soon as the command is accepted, before any warmup. */
    USE;

    /** Null when the text names neither. */
    public static @Nullable ChargeOn of(String text) {
        String wanted = text.trim().toUpperCase(Locale.ROOT);
        for (ChargeOn moment : values()) {
            if (moment.name().equals(wanted)) {
                return moment;
            }
        }
        return null;
    }
}
