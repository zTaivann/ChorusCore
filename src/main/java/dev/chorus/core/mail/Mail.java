package dev.chorus.core.mail;

import java.util.UUID;

/**
 * One piece of mail.
 *
 * @param id     the row /mail clear refers to
 * @param sender the writer's name, so a deleted account still reads sensibly
 * @param read   whether the recipient has seen it
 */
public record Mail(long id, UUID recipient, String sender, String body, long sentAt, boolean read) {
}
