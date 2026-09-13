package dev.chorus.core.utility.powertool;

import dev.chorus.core.storage.Schema;
import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One row per item a player has bound something to.
 *
 * <p>The commands on an item are stored as one field with a newline between them. A command
 * cannot hold a newline, so nothing has to be escaped, and the alternative — a row for each
 * line with an order column — would be three times the code for a list that is never longer
 * than a handful.
 */
public final class SqlPowertoolRepository implements PowertoolRepository {

    private static final String SEPARATOR = "\n";
    private static final String SELECT =
            "SELECT material, commands FROM chorus_powertools WHERE player = ?";
    private static final String DELETE =
            "DELETE FROM chorus_powertools WHERE player = ? AND material = ?";
    private static final String DELETE_ALL = "DELETE FROM chorus_powertools WHERE player = ?";

    private final Storage storage;
    private final List<String> steps;
    private final String upsert;

    public SqlPowertoolRepository(Storage storage) {
        this.storage = storage;
        this.steps = List.of(createTable(storage.dialect()));
        this.upsert = upsert(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "powertools", steps);
    }

    @Override
    public Map<String, List<String>> findAll(UUID player) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, player.toString());

            try (ResultSet rows = statement.executeQuery()) {
                Map<String, List<String>> bound = new HashMap<>();
                while (rows.next()) {
                    List<String> commands = split(rows.getString("commands"));
                    if (!commands.isEmpty()) {
                        bound.put(rows.getString("material"), commands);
                    }
                }
                return bound;
            }
        }
    }

    @Override
    public void save(UUID player, String material, List<String> commands) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, player.toString());
            statement.setString(2, material);
            statement.setString(3, String.join(SEPARATOR, commands));
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(UUID player, String material) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, player.toString());
            statement.setString(2, material);
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteAll(UUID player) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            statement.setString(1, player.toString());
            statement.executeUpdate();
        }
    }

    /** Blank lines dropped, so a row left half written by an older version reads as empty. */
    private static List<String> split(String stored) {
        List<String> commands = new ArrayList<>();
        for (String command : stored.split(SEPARATOR)) {
            if (!command.isBlank()) {
                commands.add(command);
            }
        }
        return List.copyOf(commands);
    }

    private static String upsert(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    INSERT INTO chorus_powertools (player, material, commands) VALUES (?, ?, ?)
                    ON CONFLICT (player, material) DO UPDATE SET commands = excluded.commands""";
            case MYSQL -> """
                    INSERT INTO chorus_powertools (player, material, commands) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE commands = VALUES(commands)""";
        };
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_powertools (
                        player   TEXT NOT NULL,
                        material TEXT NOT NULL,
                        commands TEXT NOT NULL,
                        PRIMARY KEY (player, material)
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_powertools (
                        player   CHAR(36)     NOT NULL,
                        material VARCHAR(64)  NOT NULL,
                        commands VARCHAR(512) NOT NULL,
                        PRIMARY KEY (player, material)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }
}
