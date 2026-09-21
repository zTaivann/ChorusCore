package dev.chorus.core.audit;

import org.jetbrains.annotations.Nullable;

/** One thing a member of staff did. */
public record AuditEntry(String actor, String action, @Nullable String subject,
                         @Nullable String detail, long at) {
}
