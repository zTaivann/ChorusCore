package dev.chorus.core.staff;

import java.util.UUID;

/** A line staff left about a player, for whoever deals with them next. */
public record StaffNote(UUID subject, String subjectName, String author, String text, long written) {
}
