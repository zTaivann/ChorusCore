package dev.chorus.core.kits;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/** When each player last took each kit. */
public interface KitRepository {

    void createTables() throws SQLException;

    /** How each kit stands for one player: when it was last taken, and how often. */
    record Use(long lastTaken, int times) {
    }

    Map<String, Use> findUses(UUID owner) throws SQLException;

    void markUsed(UUID owner, String kit, long when) throws SQLException;

    /** Used by /kitreset, and by an admin clearing a one-time kit. */
    boolean clear(UUID owner, String kit) throws SQLException;
}
