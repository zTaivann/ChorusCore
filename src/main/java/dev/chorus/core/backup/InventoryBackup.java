package dev.chorus.core.backup;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Everything a player was carrying at one moment.
 *
 * @param id       the row /restore refers to
 * @param reason   a {@link BackupReason} name
 * @param actor    who triggered it: the player, staff or the server
 * @param contents the inventory, encoded
 * @param cause    what killed them, or null
 * @param killer   the player who killed them, or null
 */
public record InventoryBackup(long id, UUID owner, long takenAt, String reason, String actor,
                                String contents, String enderChest, int level, float experience,
                                double health, int food, String world, int x, int y, int z,
                                @Nullable String cause, @Nullable String killer) {

    /** Whether the ender chest has anything in it. */
    public boolean hasEnderChest() {
        return !enderChest.isEmpty();
    }
}
