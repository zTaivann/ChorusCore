package dev.chorus.core.command;

import dev.chorus.core.audit.AuditLog;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.Schedulers;

/** What every command needs, bundled so constructors stay readable. */
public record CommandSupport(Messages messages, ActionGuard guard, Schedulers schedulers,
                             AuditLog audit) {
}
