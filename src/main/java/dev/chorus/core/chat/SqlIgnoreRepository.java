package dev.chorus.core.chat;

import dev.chorus.core.storage.Schema;
import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SqlIgnoreRepository {

    private static final String SELECT = "SELECT ignored FROM chorus_ignores WHERE owner = ?";

    private static final String DELETE = "DELETE FROM chorus_ignores WHERE owner = ? AND ignored = ?";

    private final Storage storage;
    private final List<String> steps;
    private final String insert;

    public SqlIgnoreRepository(Storage storage) {
        this.storage = storage;
        this.steps = List.of(createTable(storage.dialect()));
        this.insert = insert(storage.dialect());
    }

    public void createTables() throws SQLException {
        Schema.apply(storage, "ignores", steps);
    }

    public Set<UUID> findFor(UUID owner) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, owner.toString());

            try (ResultSet rows = statement.executeQuery()) {
                Set<UUID> ignored = new HashSet<>();
                while (rows.next()) {
                    ignored.add(UUID.fromString(rows.getString("ignored")));
                }
                return ignored;
            }
        }
    }

    public void add(UUID owner, UUID ignored) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, owner.toString());
            statement.setString(2, ignored.toString());
            statement.executeUpdate();
        }
    }

    public void remove(UUID owner, UUID ignored) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, owner.toString());
            statement.setString(2, ignored.toString());
            statement.executeUpdate();
        }
    }

    /** Ignoring somebody twice is the same as ignoring them once. */
    private static String insert(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> "INSERT OR IGNORE INTO chorus_ignores (owner, ignored) VALUES (?, ?)";
            case MYSQL -> "INSERT IGNORE INTO chorus_ignores (owner, ignored) VALUES (?, ?)";
        };
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_ignores (
                        owner   TEXT NOT NULL,
                        ignored TEXT NOT NULL,
                        PRIMARY KEY (owner, ignored)
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_ignores (
                        owner   CHAR(36) NOT NULL,
                        ignored CHAR(36) NOT NULL,
                        PRIMARY KEY (owner, ignored)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }
}
