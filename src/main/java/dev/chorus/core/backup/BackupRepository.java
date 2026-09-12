package dev.chorus.core.backup;

import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface BackupRepository {

    void createTables() throws SQLException;

    void save(InventorySnapshot snapshot) throws SQLException;

    /** Newest first, which is the order somebody asking for one wants them in. */
    List<InventorySnapshot> findFor(UUID owner, int limit) throws SQLException;

    /** One by its row, for a screen that was opened before somebody clicked restore. */
    @Nullable
    InventorySnapshot find(long id) throws SQLException;

    /** Anything older than the cutoff, and anything past the limit a player may keep. */
    void prune(long before, int keepPerPlayer) throws SQLException;

    /** Leaves a restore waiting for a player who is not here to receive it. */
    void queue(UUID owner, long snapshot, String parts, String actor) throws SQLException;

    /** Takes whatever was waiting for this player, clearing it at the same time. */
    @Nullable
    Waiting takeWaiting(UUID owner) throws SQLException;

    /** A restore somebody set up while its owner was offline. */
    record Waiting(long snapshot, String parts, String actor) {
    }
}
