package dev.chorus.core.home;

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

final class SqlHomeRepository implements HomeRepository {

    private static final String SELECT_BY_OWNER = """
            SELECT name, world_id, world_name, x, y, z, yaw, pitch, created_at
            FROM chorus_homes
            WHERE owner = ?""";

    private static final String DELETE = "DELETE FROM chorus_homes WHERE owner = ? AND name = ?";

    private final Storage storage;
    private final String createTable;
    private final String upsert;

    SqlHomeRepository(Storage storage) {
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
    public List<Home> findByOwner(UUID owner) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_OWNER)) {
            statement.setString(1, owner.toString());

            try (ResultSet rows = statement.executeQuery()) {
                List<Home> homes = new ArrayList<>();
                while (rows.next()) {
                    homes.add(read(owner, rows));
                }
                return homes;
            }
        }
    }

    @Override
    public void save(Home home) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, home.owner().toString());
            statement.setString(2, home.name());
            statement.setString(3, home.worldId().toString());
            statement.setString(4, home.worldName());
            statement.setDouble(5, home.x());
            statement.setDouble(6, home.y());
            statement.setDouble(7, home.z());
            statement.setFloat(8, home.yaw());
            statement.setFloat(9, home.pitch());
            statement.setLong(10, home.createdAt());
            statement.executeUpdate();
        }
    }

    @Override
    public boolean delete(UUID owner, String name) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, owner.toString());
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        }
    }

    private static Home read(UUID owner, ResultSet rows) throws SQLException {
        return new Home(
                owner,
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
                    CREATE TABLE IF NOT EXISTS chorus_homes (
                        owner      TEXT    NOT NULL,
                        name       TEXT    NOT NULL,
                        world_id   TEXT    NOT NULL,
                        world_name TEXT    NOT NULL,
                        x          REAL    NOT NULL,
                        y          REAL    NOT NULL,
                        z          REAL    NOT NULL,
                        yaw        REAL    NOT NULL,
                        pitch      REAL    NOT NULL,
                        created_at INTEGER NOT NULL,
                        PRIMARY KEY (owner, name)
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_homes (
                        owner      CHAR(36)    NOT NULL,
                        name       VARCHAR(32) NOT NULL,
                        world_id   CHAR(36)    NOT NULL,
                        world_name VARCHAR(64) NOT NULL,
                        x          DOUBLE      NOT NULL,
                        y          DOUBLE      NOT NULL,
                        z          DOUBLE      NOT NULL,
                        yaw        FLOAT       NOT NULL,
                        pitch      FLOAT       NOT NULL,
                        created_at BIGINT      NOT NULL,
                        PRIMARY KEY (owner, name)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }

    /** Overwriting a home keeps its original creation time, so created_at is never updated. */
    private static String upsert(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    INSERT INTO chorus_homes
                        (owner, name, world_id, world_name, x, y, z, yaw, pitch, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (owner, name) DO UPDATE SET
                        world_id   = excluded.world_id,
                        world_name = excluded.world_name,
                        x          = excluded.x,
                        y          = excluded.y,
                        z          = excluded.z,
                        yaw        = excluded.yaw,
                        pitch      = excluded.pitch""";
            case MYSQL -> """
                    INSERT INTO chorus_homes
                        (owner, name, world_id, world_name, x, y, z, yaw, pitch, created_at)
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
