package dev.chorus.core.location;

import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SqlLocationRepository implements LocationRepository {

    private static final String SELECT_BY_CATEGORY = """
            SELECT name, world_id, world_name, x, y, z, yaw, pitch, created_at
            FROM chorus_locations
            WHERE category = ?""";

    private static final String DELETE = "DELETE FROM chorus_locations WHERE category = ? AND name = ?";

    private final Storage storage;
    private final String createTable;
    private final String upsert;

    public SqlLocationRepository(Storage storage) {
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
    public List<NamedLocation> findAll(String category) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_CATEGORY)) {
            statement.setString(1, category);

            try (ResultSet rows = statement.executeQuery()) {
                List<NamedLocation> locations = new ArrayList<>();
                while (rows.next()) {
                    locations.add(read(rows));
                }
                return locations;
            }
        }
    }

    @Override
    public void save(String category, NamedLocation location) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, category);
            statement.setString(2, location.name());
            statement.setString(3, location.worldId().toString());
            statement.setString(4, location.worldName());
            statement.setDouble(5, location.x());
            statement.setDouble(6, location.y());
            statement.setDouble(7, location.z());
            statement.setFloat(8, location.yaw());
            statement.setFloat(9, location.pitch());
            statement.setLong(10, location.createdAt());
            statement.executeUpdate();
        }
    }

    @Override
    public boolean delete(String category, String name) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, category);
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        }
    }

    private static NamedLocation read(ResultSet rows) throws SQLException {
        return new NamedLocation(
                rows.getString("name"),
                UUID.fromString(rows.getString("world_id")),
                rows.getString("world_name"),
                rows.getDouble("x"),
                rows.getDouble("y"),
                rows.getDouble("z"),
                rows.getFloat("yaw"),
                rows.getFloat("pitch"),
                rows.getLong("created_at"));
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_locations (
                        category   TEXT    NOT NULL,
                        name       TEXT    NOT NULL,
                        world_id   TEXT    NOT NULL,
                        world_name TEXT    NOT NULL,
                        x          REAL    NOT NULL,
                        y          REAL    NOT NULL,
                        z          REAL    NOT NULL,
                        yaw        REAL    NOT NULL,
                        pitch      REAL    NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (category, name)
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_locations (
                        category   VARCHAR(16) NOT NULL,
                        name       VARCHAR(32) NOT NULL,
                        world_id   CHAR(36)    NOT NULL,
                        world_name VARCHAR(64) NOT NULL,
                        x          DOUBLE      NOT NULL,
                        y          DOUBLE      NOT NULL,
                        z          DOUBLE      NOT NULL,
                        yaw        FLOAT       NOT NULL,
                        pitch      FLOAT       NOT NULL,
                        created_at BIGINT      NOT NULL,
                        PRIMARY KEY (category, name)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }

    /** Moving a warp keeps its original creation time, so created_at is never updated. */
    private static String upsert(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    INSERT INTO chorus_locations
                        (category, name, world_id, world_name, x, y, z, yaw, pitch, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (category, name) DO UPDATE SET
                        world_id   = excluded.world_id,
                        world_name = excluded.world_name,
                        x          = excluded.x,
                        y          = excluded.y,
                        z          = excluded.z,
                        yaw        = excluded.yaw,
                        pitch      = excluded.pitch""";
            case MYSQL -> """
                    INSERT INTO chorus_locations
                        (category, name, world_id, world_name, x, y, z, yaw, pitch, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        world_id   = VALUES(world_id),
                        world_name = VALUES(world_name),
                        x          = VALUES(x),
                        y          = VALUES(y),
                        z          = VALUES(z),
                        yaw        = VALUES(yaw),
                        pitch      = VALUES(pitch)""";
        };
    }
}
