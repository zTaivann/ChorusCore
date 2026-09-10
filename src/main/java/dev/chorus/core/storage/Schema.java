package dev.chorus.core.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Brings each part of the database up to the shape this version of the plugin expects.
 *
 * <p>{@code CREATE TABLE IF NOT EXISTS} is enough to make a table appear, but it will not add
 * a column to one that is already there, so it cannot carry a server from an older release to
 * a newer one on its own. Instead every table is written as an ordered list of steps — the
 * first creates it, the rest change it — and the number of steps already run is recorded in
 * {@code chorus_schema_version}. A server upgrading from an earlier release runs only the
 * steps it has not seen, and a fresh one runs all of them in order.
 *
 * <p>Steps are therefore append-only. Editing one that has already shipped would leave the
 * two kinds of server with different tables and no way to tell them apart.
 */
public final class Schema {

    private static final String SELECT =
            "SELECT version FROM chorus_schema_version WHERE component = ?";

    private Schema() {
    }

    /**
     * Runs whichever of {@code steps} this database has not run yet, in order, and records
     * how far it got. The whole upgrade is one transaction: a step that fails leaves the
     * database exactly as it was, rather than half changed with nothing to say so.
     */
    public static void apply(Storage storage, String component, List<String> steps)
            throws SQLException {
        try (Connection connection = storage.connection()) {
            createRegistry(connection, storage.dialect());

            int applied = versionOf(connection, component);
            if (applied >= steps.size()) {
                return;
            }

            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (int step = applied; step < steps.size(); step++) {
                    statement.execute(steps.get(step));
                }
                record(connection, storage.dialect(), component, steps.size());
                connection.commit();
            } catch (SQLException failed) {
                connection.rollback();
                throw failed;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private static void createRegistry(Connection connection, SqlDialect dialect)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(switch (dialect) {
                case SQLITE -> """
                        CREATE TABLE IF NOT EXISTS chorus_schema_version (
                            component TEXT    NOT NULL PRIMARY KEY,
                            version   INTEGER NOT NULL
                        )""";
                case MYSQL -> """
                        CREATE TABLE IF NOT EXISTS chorus_schema_version (
                            component VARCHAR(32) NOT NULL PRIMARY KEY,
                            version   INT         NOT NULL
                        ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
            });
        }
    }

    private static int versionOf(Connection connection, String component) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, component);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt("version") : 0;
            }
        }
    }

    private static void record(Connection connection, SqlDialect dialect, String component,
                               int version) throws SQLException {
        String upsert = switch (dialect) {
            case SQLITE -> """
                    INSERT INTO chorus_schema_version (component, version) VALUES (?, ?)
                    ON CONFLICT (component) DO UPDATE SET version = excluded.version""";
            case MYSQL -> """
                    INSERT INTO chorus_schema_version (component, version) VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE version = VALUES(version)""";
        };
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, component);
            statement.setInt(2, version);
            statement.executeUpdate();
        }
    }
}
