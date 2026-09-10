package dev.chorus.core.audit;

import org.jetbrains.annotations.Nullable;

/**
 * One thing a member of staff did.
 *
 * <p>Names rather than ids, because a log is read by a person months later and a row of
 * UUIDs answers nothing. The subject is whoever it was done to, and is absent for the
 * actions that are not aimed at anybody.
 */
public record AuditEntry(String actor, String action, @Nullable String subject,
                         @Nullable String detail, long at) {
}
