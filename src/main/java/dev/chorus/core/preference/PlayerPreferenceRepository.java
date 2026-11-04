package dev.chorus.core.preference;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

public interface PlayerPreferenceRepository {

    void createTables() throws SQLException;

    Set<String> findSet(UUID player) throws SQLException;

    void set(UUID player, String preference) throws SQLException;

    void clear(UUID player, String preference) throws SQLException;
}
