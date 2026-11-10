package dev.chorus.core.command;

import dev.chorus.commons.platform.Schedulers;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.stafflog.StaffLog;

/** What every command needs. */
public record CommandSupport(Messages messages, ActionGuard guard, Schedulers schedulers,
                             StaffLog staffLog) {
}
