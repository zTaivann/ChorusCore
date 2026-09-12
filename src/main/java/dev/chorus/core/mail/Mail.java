package dev.chorus.core.mail;

import java.util.UUID;

/**
 * One letter.
 *
 * @param id        the row, which is what /mail clear names
 * @param recipient who it is for
 * @param sender    who wrote it, by name, so a deleted account still reads sensibly
 * @param body      what it says
 * @param sentAt    when it was written
 * @param read      whether the recipient has seen it
 */
public record Mail(long id, UUID recipient, String sender, String body, long sentAt, boolean read) {
}
