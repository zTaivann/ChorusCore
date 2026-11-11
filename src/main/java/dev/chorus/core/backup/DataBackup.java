package dev.chorus.core.backup;

import dev.chorus.commons.storage.SqlDialect;
import dev.chorus.commons.storage.Storage;
import dev.chorus.core.storage.Queries;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** One zip with the plugin folder and, on SQLite, a consistent copy of the database. */
public final class DataBackup {

    /** What a backup produced. {@code database} is false when the data lives on a remote server. */
    public record Result(Path file, long bytes, boolean database, int removed) {
    }

    private static final String PREFIX = "chorus-";
    private static final String SUFFIX = ".zip";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path dataFolder;
    private final Path folder;
    private final int keepDays;
    private final Storage storage;
    private final String databaseFile;
    private final Executor worker;

    /**
     * @param folder   where zips are written; may be inside {@code dataFolder}
     * @param keepDays zips older than this are deleted after a backup; 0 keeps them all
     */
    public DataBackup(Path dataFolder, Path folder, int keepDays, Storage storage,
                      String databaseFile, Executor worker) {
        this.dataFolder = dataFolder;
        this.folder = folder;
        this.keepDays = keepDays;
        this.storage = storage;
        this.databaseFile = databaseFile;
        this.worker = worker;
    }

    /**
     * Blocks while it zips, so call it from a thread that may wait, never the server thread. IO
     * failures surface as {@link UncheckedIOException} so the call fits {@link Queries.Call}.
     */
    public Result run() throws SQLException {
        try {
            Files.createDirectories(folder);
            Path snapshot = folder.resolve("snapshot.tmp");
            Files.deleteIfExists(snapshot);
            boolean database = hasLocalDatabase();
            if (database) {
                Queries.await(() -> copyDatabase(snapshot), worker);
            }

            Path zip = folder.resolve(PREFIX + STAMP.format(LocalDateTime.now()) + SUFFIX);
            Path partial = folder.resolve(zip.getFileName() + ".part");
            try {
                pack(partial, database ? snapshot : null);
                Files.move(partial, zip);
            } finally {
                Files.deleteIfExists(snapshot);
                Files.deleteIfExists(partial);
            }
            return new Result(zip, Files.size(zip), database, rotate(zip));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private boolean hasLocalDatabase() {
        return storage.dialect() == SqlDialect.SQLITE;
    }

    /** VACUUM INTO is the one way to copy a live WAL database without tearing it. */
    private Void copyDatabase(Path target) throws SQLException {
        try (Connection connection = storage.connection(); Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
        }
        return null;
    }

    private void pack(Path zip, @Nullable Path snapshot) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip));
             Stream<Path> files = Files.walk(dataFolder)) {
            if (snapshot != null) {
                add(out, databaseFile, snapshot);
            }
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                if (!file.startsWith(folder) && !(snapshot != null && isLiveDatabase(file))) {
                    add(out, dataFolder.relativize(file).toString().replace('\\', '/'), file);
                }
            }
        }
    }

    /** The database file and its WAL and shared-memory companions are replaced by the snapshot. */
    private boolean isLiveDatabase(Path file) {
        String name = file.getFileName().toString();
        return name.equals(databaseFile) || name.equals(databaseFile + "-wal") || name.equals(databaseFile + "-shm");
    }

    private static void add(ZipOutputStream out, String name, Path file) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        Files.copy(file, out);
        out.closeEntry();
    }

    private int rotate(Path keep) throws IOException {
        if (keepDays <= 0) {
            return 0;
        }
        long oldest = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(keepDays);
        int removed = 0;
        try (Stream<Path> zips = Files.list(folder)) {
            for (Path zip : (Iterable<Path>) zips.filter(DataBackup::isBackup)::iterator) {
                if (!zip.equals(keep) && Files.getLastModifiedTime(zip).toMillis() < oldest) {
                    Files.delete(zip);
                    removed++;
                }
            }
        }
        return removed;
    }

    private static boolean isBackup(Path file) {
        String name = file.getFileName().toString();
        return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
    }
}
