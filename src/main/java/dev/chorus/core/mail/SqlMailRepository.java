package dev.chorus.core.mail;

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
import java.util.concurrent.TimeUnit;

public final class SqlMailRepository implements MailRepository {

    private static final String INSERT =
            "INSERT INTO chorus_mail (recipient, sender, body, sent_at, seen) VALUES (?, ?, ?, ?, 0)";
    private static final String SELECT_INBOX =
            "SELECT * FROM chorus_mail WHERE recipient = ? ORDER BY sent_at DESC";
    private static final String COUNT_UNREAD =
            "SELECT COUNT(*) FROM chorus_mail WHERE recipient = ? AND seen = 0";
    private static final String MARK_READ =
            "UPDATE chorus_mail SET seen = 1 WHERE recipient = ? AND seen = 0";
    private static final String DELETE_ALL = "DELETE FROM chorus_mail WHERE recipient = ?";
    private static final String DELETE_ONE = "DELETE FROM chorus_mail WHERE recipient = ? AND id = ?";
    private static final String DELETE_OLD = "DELETE FROM chorus_mail WHERE sent_at < ?";

    private final Storage storage;
    private final List<String> steps;

    public SqlMailRepository(Storage storage) {
        this.storage = storage;
        this.steps = steps(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "mail", steps);
    }

    @Override
    public void send(UUID recipient, String sender, String body, long sentAt) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, recipient.toString());
            statement.setString(2, sender);
            statement.setString(3, body);
            statement.setLong(4, sentAt);
            statement.executeUpdate();
        }
    }

    @Override
    public List<Mail> inbox(UUID recipient) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_INBOX)) {
            statement.setString(1, recipient.toString());
            try (ResultSet rows = statement.executeQuery()) {
                List<Mail> letters = new ArrayList<>();
                while (rows.next()) {
                    letters.add(new Mail(rows.getLong("id"), recipient, rows.getString("sender"),
                            rows.getString("body"), rows.getLong("sent_at"),
                            rows.getInt("seen") == 1));
                }
                return letters;
            }
        }
    }

    @Override
    public int unread(UUID recipient) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(COUNT_UNREAD)) {
            statement.setString(1, recipient.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    @Override
    public void markRead(UUID recipient) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(MARK_READ)) {
            statement.setString(1, recipient.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public int clear(UUID recipient) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE_ALL)) {
            statement.setString(1, recipient.toString());
            return statement.executeUpdate();
        }
    }

    @Override
    public boolean clear(UUID recipient, long id) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE_ONE)) {
            statement.setString(1, recipient.toString());
            statement.setLong(2, id);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public void prune(int keepDays) throws SQLException {
        if (keepDays <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepDays);
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE_OLD)) {
            statement.setLong(1, cutoff);
            statement.executeUpdate();
        }
    }

    private static List<String> steps(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> List.of("""
                    CREATE TABLE IF NOT EXISTS chorus_mail (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        recipient TEXT    NOT NULL,
                        sender    TEXT    NOT NULL,
                        body      TEXT    NOT NULL,
                        sent_at   INTEGER NOT NULL,
                        seen      INTEGER NOT NULL DEFAULT 0
                    )""",
                    "CREATE INDEX IF NOT EXISTS chorus_mail_inbox ON chorus_mail (recipient, sent_at)");
            case MYSQL -> List.of("""
                    CREATE TABLE IF NOT EXISTS chorus_mail (
                        id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        recipient CHAR(36)     NOT NULL,
                        sender    VARCHAR(64)  NOT NULL,
                        body      VARCHAR(512) NOT NULL,
                        sent_at   BIGINT       NOT NULL,
                        seen      TINYINT      NOT NULL DEFAULT 0,
                        INDEX chorus_mail_inbox (recipient, sent_at)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""");
        };
    }
}
