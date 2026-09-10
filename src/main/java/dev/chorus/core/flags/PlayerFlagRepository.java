package dev.chorus.core.flags;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

public interface PlayerFlagRepository {

    void createTables() throws SQLException;

    Set<String> findSet(UUID player) throws SQLException;

    void set(UUID player, String flag) throws SQLException;

    void clear(UUID player, String flag) throws SQLException;
}
