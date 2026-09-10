package dev.chorus.core.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The point of the version table is that a server upgrading from an older release ends up
 * with the same columns as one installing for the first time, so both halves of that are
 * checked here against a real database.
 */
class SchemaTest {

    private static final String CREATE = """
            CREATE TABLE IF NOT EXISTS chorus_test (
                id   TEXT NOT NULL PRIMARY KEY,
                name TEXT
            )""";

    private static final String ADD_COLUMN = "ALTER TABLE chorus_test ADD COLUMN icon TEXT";

    private TempStorage storage;

    @BeforeEach
    void open() throws IOException {
        storage = TempStorage.open();
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void anOlderInstallGainsTheColumnsItIsMissing() throws SQLException {
        Schema.apply(storage, "test", List.of(CREATE));
        assertEquals(1, version("test"));
        assertEquals(List.of("id", "name"), columns());

        Schema.apply(storage, "test", List.of(CREATE, ADD_COLUMN));
        assertEquals(2, version("test"));
        assertEquals(List.of("id", "name", "icon"), columns());
    }

    @Test
    void aFreshInstallRunsEveryStepInOrder() throws SQLException {
        Schema.apply(storage, "test", List.of(CREATE, ADD_COLUMN));
        assertEquals(List.of("id", "name", "icon"), columns());
        assertEquals(2, version("test"));
    }

    @Test
    void applyingTwiceChangesNothing() throws SQLException {
        Schema.apply(storage, "test", List.of(CREATE, ADD_COLUMN));
        // A second ADD COLUMN would fail outright, so this passing is the proof that the
        // recorded version is what decides which steps run.
        Schema.apply(storage, "test", List.of(CREATE, ADD_COLUMN));
        assertEquals(2, version("test"));
    }

    @Test
    void componentsAreCountedSeparately() throws SQLException {
        Schema.apply(storage, "test", List.of(CREATE, ADD_COLUMN));
        assertEquals(0, version("other"));
    }

    @Test
    void aFailedStepLeavesTheVersionAlone() throws SQLException {
        Schema.apply(storage, "test", List.of(CREATE));
        assertThrows(SQLException.class,
                () -> Schema.apply(storage, "test", List.of(CREATE, "ALTER TABLE nope ADD COLUMN x TEXT")));
        assertEquals(1, version("test"), "a half-finished upgrade must not be recorded as done");
    }

    private int version(String component) throws SQLException {
        try (Connection connection = storage.connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT version FROM chorus_schema_version WHERE component = '" + component + "'")) {
            return rows.next() ? rows.getInt("version") : 0;
        }
    }

    private List<String> columns() throws SQLException {
        try (Connection connection = storage.connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM chorus_test")) {
            List<String> names = new ArrayList<>();
            for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                names.add(rows.getMetaData().getColumnName(column));
            }
            return names;
        }
    }
}
