package dev.chorus.core.location;

import java.sql.SQLException;
import java.util.List;

/**
 * Blocking persistence for server-wide positions. The category keeps sets such as warps and
 * spawn apart in one table.
 */
public interface LocationRepository {

    void createTables() throws SQLException;

    List<NamedLocation> findAll(String category) throws SQLException;

    void save(String category, NamedLocation location) throws SQLException;

    boolean delete(String category, String name) throws SQLException;
}
