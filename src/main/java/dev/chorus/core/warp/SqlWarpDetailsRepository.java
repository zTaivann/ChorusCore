package dev.chorus.core.warp;

import dev.chorus.core.storage.Schema;
import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

final class SqlWarpDetailsRepository implements WarpDetailsRepository {

    private static final String SELECT = """
            SELECT warp, icon, permission, price, cooldown_seconds, description, section, uses
            FROM chorus_warp_details""";

    private static final String DELETE = "DELETE FROM chorus_warp_details WHERE warp = ?";

    private final Storage storage;
    private final List<String> steps;
    private final String upsert;

    SqlWarpDetailsRepository(Storage storage) {
        this.storage = storage;
        this.steps = List.of(createTable(storage.dialect()));
        this.upsert = upsert(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "warp-details", steps);
    }

    @Override
    public List<WarpDetails> findAll() throws SQLException {
        try (Connection connection = storage.connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(SELECT)) {
            List<WarpDetails> details = new ArrayList<>();
            while (rows.next()) {
                details.add(new WarpDetails(
                        rows.getString("warp"),
                        rows.getString("icon"),
                        rows.getString("permission"),
                        rows.getDouble("price"),
                        rows.getInt("cooldown_seconds"),
                        rows.getString("description"),
                        rows.getString("section"),
                        rows.getLong("uses")));
            }
            return details;
        }
    }

    @Override
    public void save(WarpDetails details) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, details.warp());
            statement.setString(2, details.icon());
            statement.setString(3, details.permission());
            statement.setDouble(4, details.price());
            statement.setInt(5, details.cooldownSeconds());
            statement.setString(6, details.description());
            statement.setString(7, details.section());
            statement.setLong(8, details.uses());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(String warp) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, warp);
            statement.executeUpdate();
        }
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_warp_details (
                        warp             TEXT    NOT NULL PRIMARY KEY,
                        icon             TEXT,
                        permission       TEXT,
                        price            REAL    NOT NULL,
                        cooldown_seconds INTEGER NOT NULL,
                        description      TEXT,
                        section          TEXT,
                        uses             INTEGER NOT NULL
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_warp_details (
                        warp             VARCHAR(32)  NOT NULL PRIMARY KEY,
                        icon             VARCHAR(64),
                        permission       VARCHAR(128),
                        price            DOUBLE       NOT NULL,
                        cooldown_seconds INT          NOT NULL,
                        description      VARCHAR(128),
                        section          VARCHAR(32),
                        uses             BIGINT       NOT NULL
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }

    private static String upsert(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    INSERT INTO chorus_warp_details
                        (warp, icon, permission, price, cooldown_seconds, description, section, uses)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (warp) DO UPDATE SET
                        icon             = excluded.icon,
                        permission       = excluded.permission,
                        price            = excluded.price,
                        cooldown_seconds = excluded.cooldown_seconds,
                        description      = excluded.description,
                        section          = excluded.section,
                        uses             = excluded.uses""";
            case MYSQL -> """
                    INSERT INTO chorus_warp_details
                        (warp, icon, permission, price, cooldown_seconds, description, section, uses)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        icon             = VALUES(icon),
                        permission       = VALUES(permission),
                        price            = VALUES(price),
                        cooldown_seconds = VALUES(cooldown_seconds),
                        description      = VALUES(description),
                        section          = VALUES(section),
                        uses             = VALUES(uses)""";
        };
    }
}
