package dev.chorus.core.command;

import dev.chorus.core.locale.Messages;

/** The two things every command needs, bundled so constructors stay readable. */
public record CommandSupport(Messages messages, ActionGuard guard) {
}
