package dev.chorus.core.location;

import java.sql.SQLException;
import java.util.List;

/**
 * Blocking persistence for server-wide positions. The category keeps unrelated sets apart,
 * so warps and the spawn point share one table without ever colliding on a name.
 */
public interface LocationRepository {

    void createTables() throws SQLException;

    List<NamedLocation> findAll(String category) throws SQLException;

    void save(String category, NamedLocation location) throws SQLException;

    boolean delete(String category, String name) throws SQLException;
}
