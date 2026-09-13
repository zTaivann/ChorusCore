package dev.chorus.core.utility.powertool;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PowertoolRepository {

    void createTables() throws SQLException;

    /** Every binding one player has, by the name of the material it is on. */
    Map<String, List<String>> findAll(UUID player) throws SQLException;

    void save(UUID player, String material, List<String> commands) throws SQLException;

    void delete(UUID player, String material) throws SQLException;

    void deleteAll(UUID player) throws SQLException;
}
