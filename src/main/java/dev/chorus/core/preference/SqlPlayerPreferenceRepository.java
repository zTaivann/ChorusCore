package dev.chorus.core.preference;

import dev.chorus.commons.storage.SqlDialect;
import dev.chorus.commons.storage.Storage;
import dev.chorus.core.storage.Schema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A row exists only while the preference is set, so there is nothing to store for the default. */
public final class SqlPlayerPreferenceRepository implements PlayerPreferenceRepository {

    private static final String SELECT = "SELECT flag FROM chorus_player_flags WHERE player = ?";
    private static final String DELETE =
            "DELETE FROM chorus_player_flags WHERE player = ? AND flag = ?";

    private final Storage storage;
    private final List<String> steps;
    private final String insert;

    public SqlPlayerPreferenceRepository(Storage storage) {
        this.storage = storage;
        this.steps = List.of(createTable(storage.dialect()));
        this.insert = insert(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "player-flags", steps);
    }

    @Override
    public Set<String> findSet(UUID player) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, player.toString());

            try (ResultSet rows = statement.executeQuery()) {
                Set<String> preferences = new HashSet<>();
                while (rows.next()) {
                    preferences.add(rows.getString("flag"));
                }
                return preferences;
            }
        }
    }

    @Override
    public void set(UUID player, String preference) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, player.toString());
            statement.setString(2, preference);
            statement.executeUpdate();
        }
    }

    @Override
    public void clear(UUID player, String preference) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, player.toString());
            statement.setString(2, preference);
            statement.executeUpdate();
        }
    }

    private static String insert(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> "INSERT OR IGNORE INTO chorus_player_flags (player, flag) VALUES (?, ?)";
            case MYSQL -> "INSERT IGNORE INTO chorus_player_flags (player, flag) VALUES (?, ?)";
        };
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_player_flags (
                        player TEXT NOT NULL,
                        flag   TEXT NOT NULL,
                        PRIMARY KEY (player, flag)
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_player_flags (
                        player CHAR(36)   NOT NULL,
                        flag   VARCHAR(32) NOT NULL,
                        PRIMARY KEY (player, flag)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }
}
