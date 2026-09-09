package dev.chorus.core.home;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Blocking persistence for homes. Every method talks to the database on the calling
 * thread; scheduling is {@link HomeService}'s job.
 */
public interface HomeRepository {

    void createTables() throws SQLException;

    List<Home> findByOwner(UUID owner) throws SQLException;

    void save(Home home) throws SQLException;

    boolean delete(UUID owner, String name) throws SQLException;
}
