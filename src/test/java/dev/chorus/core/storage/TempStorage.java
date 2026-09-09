package dev.chorus.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/** A throwaway SQLite file, so the storage tests exercise real SQL rather than a mock. */
public final class TempStorage implements Storage {

    private final HikariDataSource pool;

    private TempStorage(HikariDataSource pool) {
        this.pool = pool;
    }

    public static TempStorage open() throws IOException {
        Path folder = Files.createTempDirectory("chorus-test");
        HikariConfig settings = new HikariConfig();
        settings.setPoolName("chorus-test");
        settings.setDriverClassName(SqlDialect.SQLITE.driverClass());
        settings.setJdbcUrl("jdbc:sqlite:" + folder.resolve("chorus.db").toAbsolutePath());
        settings.setConnectionInitSql("PRAGMA journal_mode = WAL");
        settings.setMaximumPoolSize(1);
        return new TempStorage(new HikariDataSource(settings));
    }

    @Override
    public Connection connection() throws SQLException {
        return pool.getConnection();
    }

    @Override
    public SqlDialect dialect() {
        return SqlDialect.SQLITE;
    }

    @Override
    public void close() {
        pool.close();
    }
}
