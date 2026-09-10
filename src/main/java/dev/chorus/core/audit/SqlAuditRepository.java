package dev.chorus.core.audit;

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

public final class SqlAuditRepository implements AuditRepository {

    private static final String INSERT = """
            INSERT INTO chorus_staff_log (actor, action, subject, detail, done_at)
            VALUES (?, ?, ?, ?, ?)""";

    private static final String SELECT_ALL = """
            SELECT actor, action, subject, detail, done_at
            FROM chorus_staff_log
            ORDER BY done_at DESC
            LIMIT ?""";

    private static final String SELECT_FOR_NAME = """
            SELECT actor, action, subject, detail, done_at
            FROM chorus_staff_log
            WHERE actor = ? OR subject = ?
            ORDER BY done_at DESC
            LIMIT ?""";

    private static final String DELETE_OLD = "DELETE FROM chorus_staff_log WHERE done_at < ?";

    private final Storage storage;
    private final List<String> steps;

    public SqlAuditRepository(Storage storage) {
        this.storage = storage;
        this.steps = List.of(createTable(storage.dialect()),
                "CREATE INDEX chorus_staff_log_done_at ON chorus_staff_log (done_at)");
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "staff-log", steps);
    }

    @Override
    public void record(AuditEntry entry) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, entry.actor());
            statement.setString(2, entry.action());
            statement.setString(3, entry.subject());
            statement.setString(4, entry.detail());
            statement.setLong(5, entry.at());
            statement.executeUpdate();
        }
    }

    @Override
    public List<AuditEntry> findRecent(@Nullable String name, int limit) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(
                     name == null ? SELECT_ALL : SELECT_FOR_NAME)) {
            if (name == null) {
                statement.setInt(1, limit);
            } else {
                statement.setString(1, name);
                statement.setString(2, name);
                statement.setInt(3, limit);
            }

            try (ResultSet rows = statement.executeQuery()) {
                List<AuditEntry> entries = new ArrayList<>();
                while (rows.next()) {
                    entries.add(new AuditEntry(
                            rows.getString("actor"),
                            rows.getString("action"),
                            rows.getString("subject"),
                            rows.getString("detail"),
                            rows.getLong("done_at")));
                }
                return entries;
            }
        }
    }

    @Override
    public int deleteBefore(long cutoff) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE_OLD)) {
            statement.setLong(1, cutoff);
            return statement.executeUpdate();
        }
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_staff_log (
                        id      INTEGER PRIMARY KEY AUTOINCREMENT,
                        actor   TEXT    NOT NULL,
                        action  TEXT    NOT NULL,
                        subject TEXT,
                        detail  TEXT,
                        done_at INTEGER NOT NULL
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_staff_log (
                        id      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        actor   VARCHAR(32)  NOT NULL,
                        action  VARCHAR(32)  NOT NULL,
                        subject VARCHAR(32),
                        detail  VARCHAR(255),
                        done_at BIGINT       NOT NULL
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }
}
