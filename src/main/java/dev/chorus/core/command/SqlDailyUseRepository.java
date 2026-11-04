package dev.chorus.core.command;

import dev.chorus.core.storage.Schema;
import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One row per owner and timer, overwritten when a new day starts. */
public final class SqlDailyUseRepository implements DailyUseRepository {

    private static final String SELECT =
            "SELECT owner, timer, day_start, uses FROM chorus_command_uses WHERE day_start >= ?";
    private static final String DELETE_OLD = "DELETE FROM chorus_command_uses WHERE day_start < ?";

    private final Storage storage;
    private final List<String> steps;
    private final String upsert;

    public SqlDailyUseRepository(Storage storage) {
        this.storage = storage;
        this.steps = List.of(createTable(storage.dialect()));
        this.upsert = upsert(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "command-uses", steps);
    }

    @Override
    public List<Row> findSince(long dayStart) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setLong(1, dayStart);
            try (ResultSet rows = statement.executeQuery()) {
                List<Row> found = new ArrayList<>();
                while (rows.next()) {
                    found.add(new Row(UUID.fromString(rows.getString("owner")),
                            rows.getString("timer"), rows.getLong("day_start"), rows.getInt("uses")));
                }
                return found;
            }
        }
    }

    @Override
    public void save(Row row) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setString(1, row.owner().toString());
            statement.setString(2, row.timer());
            statement.setLong(3, row.dayStart());
            statement.setInt(4, row.uses());
            statement.executeUpdate();
        }
    }

    @Override
    public int deleteBefore(long dayStart) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE_OLD)) {
            statement.setLong(1, dayStart);
            return statement.executeUpdate();
        }
    }

    private static String upsert(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    INSERT INTO chorus_command_uses (owner, timer, day_start, uses) VALUES (?, ?, ?, ?)
                    ON CONFLICT (owner, timer) DO UPDATE SET day_start = excluded.day_start,
                        uses = excluded.uses""";
            case MYSQL -> """
                    INSERT INTO chorus_command_uses (owner, timer, day_start, uses) VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE day_start = VALUES(day_start), uses = VALUES(uses)""";
        };
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_command_uses (
                        owner     TEXT    NOT NULL,
                        timer     TEXT    NOT NULL,
                        day_start INTEGER NOT NULL,
                        uses      INTEGER NOT NULL,
                        PRIMARY KEY (owner, timer)
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_command_uses (
                        owner     CHAR(36)     NOT NULL,
                        timer     VARCHAR(128) NOT NULL,
                        day_start BIGINT       NOT NULL,
                        uses      INT          NOT NULL,
                        PRIMARY KEY (owner, timer)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }
}
