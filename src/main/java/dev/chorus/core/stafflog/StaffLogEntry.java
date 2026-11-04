package dev.chorus.core.stafflog;

import org.jetbrains.annotations.Nullable;

/** One thing a member of staff did. */
public record StaffLogEntry(String actor, String action, @Nullable String subject,
                         @Nullable String detail, long at) {
}
