package dev.chorus.core.backup;

import dev.chorus.core.storage.TempStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataBackupTest {

    @TempDir
    Path data;

    private TempStorage storage;
    private Path backups;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        storage = TempStorage.open();
        try (Connection connection = storage.connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample (name TEXT)");
            statement.execute("INSERT INTO sample VALUES ('kept')");
        }
        Files.writeString(data.resolve("config.yml"), "startup-banner: true");
        Files.createDirectories(data.resolve("messages"));
        Files.writeString(data.resolve("messages/core.yml"), "core: {}");
        // The live database and its WAL must not be zipped raw; the snapshot replaces them.
        Files.writeString(data.resolve("chorus.db"), "live");
        Files.writeString(data.resolve("chorus.db-wal"), "live");
        backups = data.resolve("backups");
    }

    @AfterEach
    void close() {
        storage.close();
    }

    private DataBackup backup(int keepDays) {
        return new DataBackup(data, backups, keepDays, storage, "chorus.db", Runnable::run);
    }

    private static Set<String> entries(Path zip) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            return file.stream().map(ZipEntry::getName).collect(Collectors.toSet());
        }
    }

    @Test
    void theZipHoldsConfigsMessagesAndADatabaseThatOpens() throws Exception {
        DataBackup.Result result = backup(14).run();

        assertTrue(result.database());
        assertEquals(Set.of("config.yml", "messages/core.yml", "chorus.db"), entries(result.file()));

        Path restored = data.resolve("restored.db");
        try (ZipFile zip = new ZipFile(result.file().toFile())) {
            Files.copy(zip.getInputStream(zip.getEntry("chorus.db")), restored);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + restored);
             ResultSet rows = connection.createStatement().executeQuery("SELECT name FROM sample")) {
            assertTrue(rows.next());
            assertEquals("kept", rows.getString(1));
        }
    }

    @Test
    void earlierBackupsAreNeverPackedIntoTheNextOne() throws Exception {
        backup(14).run();
        // Seconds in the name are the only thing telling two runs apart.
        Thread.sleep(1100);
        DataBackup.Result second = backup(14).run();

        assertFalse(entries(second.file()).stream().anyMatch(name -> name.startsWith("backups")));
    }

    @Test
    void onlyBackupsOlderThanKeepDaysAreDeleted() throws Exception {
        Files.createDirectories(backups);
        Path old = Files.writeString(backups.resolve("chorus-2020-01-01_00-00-00.zip"), "x");
        Path recent = Files.writeString(backups.resolve("chorus-2099-01-01_00-00-00.zip"), "x");
        Path other = Files.writeString(backups.resolve("notes.zip"), "x");
        FileTime ancient = FileTime.from(Instant.now().minus(30, ChronoUnit.DAYS));
        Files.setLastModifiedTime(old, ancient);
        Files.setLastModifiedTime(other, ancient);

        DataBackup.Result result = backup(14).run();

        assertEquals(1, result.removed());
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(recent));
        assertTrue(Files.exists(other));
        assertTrue(Files.exists(result.file()));
    }

    @Test
    void zeroKeepDaysKeepsEverything() throws Exception {
        Files.createDirectories(backups);
        Path old = Files.writeString(backups.resolve("chorus-2020-01-01_00-00-00.zip"), "x");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(400, ChronoUnit.DAYS)));

        assertEquals(0, backup(0).run().removed());
        assertTrue(Files.exists(old));
    }

    @Test
    void noSnapshotOrPartialFileIsLeftBehind() throws Exception {
        backup(14).run();

        try (var files = Files.list(backups)) {
            assertTrue(files.allMatch(file -> file.getFileName().toString().endsWith(".zip")));
        }
    }
}
