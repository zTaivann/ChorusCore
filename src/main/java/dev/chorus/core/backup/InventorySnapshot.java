package dev.chorus.core.backup;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Everything a player was carrying at one moment.
 *
 * @param id          the row, which is what /restore names
 * @param owner       whose it was
 * @param takenAt     when
 * @param reason      what was about to happen to it, as a {@link BackupReason} name
 * @param actor       who set that off: the player, a staff member, or the server
 * @param contents    the inventory, encoded
 * @param enderChest  the ender chest, encoded
 * @param level       their experience level
 * @param health      how much of their health was left
 * @param food        how much of their hunger bar was left
 * @param experience  how far into the next level, from 0 to 1
 * @param world       where they were
 * @param cause       what killed them, for a death, or null
 * @param killer      who killed them, for a death by a player, or null
 */
public record InventorySnapshot(long id, UUID owner, long takenAt, String reason, String actor,
                                String contents, String enderChest, int level, float experience,
                                double health, int food, String world, int x, int y, int z,
                                @Nullable String cause, @Nullable String killer) {

    /** Whether there is anything in the ender chest worth opening a screen for. */
    public boolean hasEnderChest() {
        return !enderChest.isEmpty();
    }
}
