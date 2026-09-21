package dev.chorus.core.backup;

import dev.chorus.core.storage.Schema;
import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;

import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SqlBackupRepository implements BackupRepository {

    private static final String COLUMNS =
            "id, owner, taken_at, reason, actor, contents, ender_chest,"
                    + " level, experience, health, food, world, x, y, z, cause, killer";

    private static final String SELECT = "SELECT " + COLUMNS
            + " FROM chorus_inventory_backups WHERE owner = ? ORDER BY taken_at DESC LIMIT ?";

    private static final String SELECT_ONE = "SELECT " + COLUMNS
            + " FROM chorus_inventory_backups WHERE id = ?";

    private static final String DELETE_OLD =
            "DELETE FROM chorus_inventory_backups WHERE taken_at < ?";

    /** Everything past the newest few for one player. */
    private static final String DELETE_SURPLUS = """
            DELETE FROM chorus_inventory_backups
            WHERE id IN (
                SELECT id FROM (
                    SELECT b.id AS id FROM chorus_inventory_backups b
                    WHERE (
                        SELECT COUNT(*) FROM chorus_inventory_backups newer
                        WHERE newer.owner = b.owner AND newer.taken_at > b.taken_at
                    ) >= ?
                ) AS surplus
            )""";

    private static final String INSERT = """
            INSERT INTO chorus_inventory_backups
                (owner, taken_at, reason, actor, contents, ender_chest,
                 level, experience, health, food, world, x, y, z, cause, killer)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private final Storage storage;
    private final List<String> steps;

    public SqlBackupRepository(Storage storage) {
        this.storage = storage;
        this.steps = steps(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "inventory-backups", steps);
    }

    @Override
    public void save(InventorySnapshot snapshot) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, snapshot.owner().toString());
            statement.setLong(2, snapshot.takenAt());
            statement.setString(3, snapshot.reason());
            statement.setString(4, snapshot.actor());
            statement.setString(5, snapshot.contents());
            statement.setString(6, snapshot.enderChest());
            statement.setInt(7, snapshot.level());
            statement.setFloat(8, snapshot.experience());
            statement.setDouble(9, snapshot.health());
            statement.setInt(10, snapshot.food());
            statement.setString(11, snapshot.world());
            statement.setInt(12, snapshot.x());
            statement.setInt(13, snapshot.y());
            statement.setInt(14, snapshot.z());
            statement.setString(15, snapshot.cause());
            statement.setString(16, snapshot.killer());
            statement.executeUpdate();
        }
    }

    @Override
    public List<InventorySnapshot> findFor(UUID owner, int limit) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, owner.toString());
            statement.setInt(2, limit);

            try (ResultSet rows = statement.executeQuery()) {
                List<InventorySnapshot> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(read(rows));
                }
                return found;
            }
        }
    }

    @Override
    public List<InventorySnapshot> recent(int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM chorus_inventory_backups"
                + " ORDER BY taken_at DESC LIMIT ?";
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);

            try (ResultSet rows = statement.executeQuery()) {
                List<InventorySnapshot> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(read(rows));
                }
                return found;
            }
        }
    }

    @Override
    public @Nullable InventorySnapshot find(long id) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
            statement.setLong(1, id);

            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows) : null;
            }
        }
    }

    @Override
    public void prune(long before, int keepPerPlayer) throws SQLException {
        try (Connection connection = storage.connection()) {
            if (before > 0) {
                try (PreparedStatement statement = connection.prepareStatement(DELETE_OLD)) {
                    statement.setLong(1, before);
                    statement.executeUpdate();
                }
            }
            if (keepPerPlayer > 0) {
                try (PreparedStatement statement = connection.prepareStatement(DELETE_SURPLUS)) {
                    statement.setInt(1, keepPerPlayer);
                    statement.executeUpdate();
                }
            }
        }
    }

    @Override
    public void queue(UUID owner, long snapshot, String parts, String actor) throws SQLException {
        String upsert = switch (storage.dialect()) {
            case SQLITE -> """
                    INSERT INTO chorus_pending_restores (owner, snapshot, parts, actor, queued)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (owner) DO UPDATE SET snapshot = excluded.snapshot,
                        parts = excluded.parts, actor = excluded.actor, queued = excluded.queued""";
            case MYSQL -> """
                    INSERT INTO chorus_pending_restores (owner, snapshot, parts, actor, queued)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE snapshot = VALUES(snapshot), parts = VALUES(parts),
                        actor = VALUES(actor), queued = VALUES(queued)""";
        };
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, owner.toString());
            statement.setLong(2, snapshot);
            statement.setString(3, parts);
            statement.setString(4, actor);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    @Override
    public @Nullable Waiting takeWaiting(UUID owner) throws SQLException {
        try (Connection connection = storage.connection()) {
            Waiting waiting;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT snapshot, parts, actor FROM chorus_pending_restores WHERE owner = ?")) {
                statement.setString(1, owner.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    waiting = rows.next()
                            ? new Waiting(rows.getLong("snapshot"), rows.getString("parts"),
                            rows.getString("actor"))
                            : null;
                }
            }
            if (waiting == null) {
                return null;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM chorus_pending_restores WHERE owner = ?")) {
                statement.setString(1, owner.toString());
                statement.executeUpdate();
            }
            return waiting;
        }
    }

    private static InventorySnapshot read(ResultSet rows) throws SQLException {
        return new InventorySnapshot(
                rows.getLong("id"),
                UUID.fromString(rows.getString("owner")),
                rows.getLong("taken_at"),
                rows.getString("reason"),
                rows.getString("actor"),
                rows.getString("contents"),
                text(rows.getString("ender_chest")),
                rows.getInt("level"),
                rows.getFloat("experience"),
                rows.getDouble("health"),
                rows.getInt("food"),
                text(rows.getString("world")),
                rows.getInt("x"),
                rows.getInt("y"),
                rows.getInt("z"),
                rows.getString("cause"),
                rows.getString("killer"));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    /** The table, then the columns that arrived after it. */
    private static List<String> steps(SqlDialect dialect) {
        String integer = dialect == SqlDialect.SQLITE ? "INTEGER" : "INT";
        String text = dialect == SqlDialect.SQLITE ? "TEXT" : "VARCHAR(64)";
        String blob = dialect == SqlDialect.SQLITE ? "TEXT" : "MEDIUMTEXT";

        return List.of(
                createTable(dialect),
                "CREATE INDEX chorus_inventory_backups_owner"
                        + " ON chorus_inventory_backups (owner, taken_at)",
                alter("ender_chest", blob + " NOT NULL DEFAULT ''"),
                alter("level", integer + " NOT NULL DEFAULT 0"),
                alter("experience", "FLOAT NOT NULL DEFAULT 0"),
                alter("health", "DOUBLE NOT NULL DEFAULT 20"),
                alter("food", integer + " NOT NULL DEFAULT 20"),
                alter("world", text + " NOT NULL DEFAULT ''"),
                alter("x", integer + " NOT NULL DEFAULT 0"),
                alter("y", integer + " NOT NULL DEFAULT 0"),
                alter("z", integer + " NOT NULL DEFAULT 0"),
                alter("cause", text + " NULL"),
                alter("killer", text + " NULL"),
                waitingTable(dialect));
    }

    private static String waitingTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_pending_restores (
                        owner    TEXT    NOT NULL PRIMARY KEY,
                        snapshot INTEGER NOT NULL,
                        parts    TEXT    NOT NULL,
                        actor    TEXT    NOT NULL,
                        queued   INTEGER NOT NULL
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_pending_restores (
                        owner    CHAR(36)    NOT NULL PRIMARY KEY,
                        snapshot BIGINT      NOT NULL,
                        parts    VARCHAR(64) NOT NULL,
                        actor    VARCHAR(64) NOT NULL,
                        queued   BIGINT      NOT NULL
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }

    private static String alter(String column, String type) {
        return "ALTER TABLE chorus_inventory_backups ADD COLUMN " + column + " " + type;
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_inventory_backups (
                        id       INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner    TEXT    NOT NULL,
                        taken_at INTEGER NOT NULL,
                        reason   TEXT    NOT NULL,
                        actor    TEXT    NOT NULL,
                        contents TEXT    NOT NULL
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_inventory_backups (
                        id       BIGINT      NOT NULL AUTO_INCREMENT,
                        owner    CHAR(36)    NOT NULL,
                        taken_at BIGINT      NOT NULL,
                        reason   VARCHAR(64) NOT NULL,
                        actor    VARCHAR(64) NOT NULL,
                        contents MEDIUMTEXT  NOT NULL,
                        PRIMARY KEY (id)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }
}
