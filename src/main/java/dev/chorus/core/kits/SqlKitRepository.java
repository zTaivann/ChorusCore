package dev.chorus.core.kits;

import dev.chorus.core.storage.Schema;
import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SqlKitRepository implements KitRepository {

    private static final String SELECT =
            "SELECT kit, used_at, times FROM chorus_kit_uses WHERE owner = ?";

    private static final String DELETE = "DELETE FROM chorus_kit_uses WHERE owner = ? AND kit = ?";

    private final Storage storage;
    private final List<String> steps;
    private final String upsert;

    SqlKitRepository(Storage storage) {
        this.storage = storage;
        this.steps = List.of(createTable(storage.dialect()), addTimes(storage.dialect()));
        this.upsert = upsert(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "kits", steps);
    }

    @Override
    public Map<String, Use> findUses(UUID owner) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, owner.toString());

            try (ResultSet rows = statement.executeQuery()) {
                Map<String, Use> uses = new HashMap<>();
                while (rows.next()) {
                    uses.put(rows.getString("kit"),
                            new Use(rows.getLong("used_at"), rows.getInt("times")));
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

    /**
     * The count arrived after the first release. It defaults to one rather than zero, so a
     * row written before it existed reads as what it was: somebody who had taken the kit,
     * at least once.
     */
    private static String addTimes(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> "ALTER TABLE chorus_kit_uses ADD COLUMN times INTEGER NOT NULL DEFAULT 1";
            case MYSQL -> "ALTER TABLE chorus_kit_uses ADD COLUMN times INT NOT NULL DEFAULT 1";
        };
    }

    /** Counted in the database rather than read and written back, so two claims never race. */
    private static String upsert(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    INSERT INTO chorus_kit_uses (owner, kit, used_at, times) VALUES (?, ?, ?, 1)
                    ON CONFLICT (owner, kit) DO UPDATE SET
                        used_at = excluded.used_at,
                        times   = chorus_kit_uses.times + 1""";
            case MYSQL -> """
                    INSERT INTO chorus_kit_uses (owner, kit, used_at, times) VALUES (?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE
                        used_at = VALUES(used_at),
                        times   = times + 1""";
        };
    }
}
