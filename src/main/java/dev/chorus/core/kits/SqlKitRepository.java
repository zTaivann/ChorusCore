package dev.chorus.core.kits;

import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class SqlKitRepository implements KitRepository {

    private static final String SELECT = "SELECT kit, used_at FROM chorus_kit_uses WHERE owner = ?";
    private static final String DELETE = "DELETE FROM chorus_kit_uses WHERE owner = ? AND kit = ?";

    private final Storage storage;
    private final String createTable;
    private final String upsert;

    SqlKitRepository(Storage storage) {
        this.storage = storage;
        this.createTable = createTable(storage.dialect());
        this.upsert = upsert(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        try (Connection connection = storage.connection();
             Statement statement = connection.createStatement()) {
            statement.execute(createTable);
        }
    }

    @Override
    public Map<String, Long> findUses(UUID owner) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, owner.toString());

            try (ResultSet rows = statement.executeQuery()) {
                Map<String, Long> uses = new HashMap<>();
                while (rows.next()) {
                    uses.put(rows.getString("kit"), rows.getLong("used_at"));
                }
                return uses;
            }
        }
    }

    @Override
    public void markUsed(UUID owner, String kit, long when) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, owner.toString());
            statement.setString(2, kit);
            statement.setLong(3, when);
            statement.executeUpdate();
        }
    }

    @Override
    public boolean clear(UUID owner, String kit) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, owner.toString());
            statement.setString(2, kit);
            return statement.executeUpdate() > 0;
        }
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_kit_uses (
                        owner   TEXT    NOT NULL,
                        kit     TEXT    NOT NULL,
                        used_at INTEGER NOT NULL,
                        PRIMARY KEY (owner, kit)
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_kit_uses (
                        owner   CHAR(36)    NOT NULL,
                        kit     VARCHAR(32) NOT NULL,
                        used_at BIGINT      NOT NULL,
                        PRIMARY KEY (owner, kit)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }

    private static String upsert(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    INSERT INTO chorus_kit_uses (owner, kit, used_at) VALUES (?, ?, ?)
                    ON CONFLICT (owner, kit) DO UPDATE SET used_at = excluded.used_at""";
            case MYSQL -> """
                    INSERT INTO chorus_kit_uses (owner, kit, used_at) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE used_at = VALUES(used_at)""";
        };
    }
}
