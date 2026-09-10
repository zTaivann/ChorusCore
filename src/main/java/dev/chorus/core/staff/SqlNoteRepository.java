package dev.chorus.core.staff;

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

final class SqlNoteRepository implements NoteRepository {

    private static final String INSERT = """
            INSERT INTO chorus_notes (subject, subject_name, author, note, written_at)
            VALUES (?, ?, ?, ?, ?)""";

    private static final String SELECT = """
            SELECT subject_name, author, note, written_at
            FROM chorus_notes
            WHERE subject = ?
            ORDER BY written_at ASC""";

    private static final String DELETE = "DELETE FROM chorus_notes WHERE subject = ?";

    private final Storage storage;
    private final List<String> steps;

    SqlNoteRepository(Storage storage) {
        this.storage = storage;
        this.steps = List.of(createTable(storage.dialect()),
                "CREATE INDEX chorus_notes_subject ON chorus_notes (subject)");
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "notes", steps);
    }

    @Override
    public void add(StaffNote note) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, note.subject().toString());
            statement.setString(2, note.subjectName());
            statement.setString(3, note.author());
            statement.setString(4, note.text());
            statement.setLong(5, note.written());
            statement.executeUpdate();
        }
    }

    @Override
    public List<StaffNote> findFor(UUID subject) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, subject.toString());

            try (ResultSet rows = statement.executeQuery()) {
                List<StaffNote> notes = new ArrayList<>();
                while (rows.next()) {
                    notes.add(new StaffNote(subject,
                            rows.getString("subject_name"),
                            rows.getString("author"),
                            rows.getString("note"),
                            rows.getLong("written_at")));
                }
                return notes;
            }
        }
    }

    @Override
    public int clear(UUID subject) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, subject.toString());
            return statement.executeUpdate();
        }
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_notes (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        subject      TEXT    NOT NULL,
                        subject_name TEXT    NOT NULL,
                        author       TEXT    NOT NULL,
                        note         TEXT    NOT NULL,
                        written_at   INTEGER NOT NULL
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_notes (
                        id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        subject      CHAR(36)     NOT NULL,
                        subject_name VARCHAR(16)  NOT NULL,
                        author       VARCHAR(32)  NOT NULL,
                        note         VARCHAR(255) NOT NULL,
                        written_at   BIGINT       NOT NULL
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }
}
