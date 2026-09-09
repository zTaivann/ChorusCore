package dev.chorus.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

public final class SqlStorage implements Storage {

    private final HikariDataSource pool;
    private final SqlDialect dialect;

    private SqlStorage(HikariDataSource pool, SqlDialect dialect) {
        this.pool = pool;
        this.dialect = dialect;
    }

    public static SqlStorage open(Plugin plugin, StorageOptions options) throws SQLException {
        SqlDialect dialect = options.dialect();

        HikariConfig settings = new HikariConfig();
        settings.setPoolName("chorus-" + dialect.name().toLowerCase(Locale.ROOT));
        settings.setDriverClassName(dialect.driverClass());
        switch (dialect) {
            case SQLITE -> configureSqlite(plugin, options, settings);
            case MYSQL -> configureRemote(options.remote(), settings);
        }

        HikariDataSource pool = new HikariDataSource(settings);
        try (Connection connection = pool.getConnection()) {
            if (!connection.isValid(5)) {
                throw new SQLException("The database rejected the first connection");
            }
        } catch (SQLException exception) {
            pool.close();
            throw exception;
        }
        return new SqlStorage(pool, dialect);
    }

    @Override
    public Connection connection() throws SQLException {
        return pool.getConnection();
    }

    @Override
    public SqlDialect dialect() {
        return dialect;
    }

    @Override
    public void close() {
        pool.close();
    }

    private static void configureSqlite(Plugin plugin, StorageOptions options, HikariConfig settings) {
        File folder = plugin.getDataFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IllegalStateException("Could not create " + folder);
        }

        settings.setJdbcUrl("jdbc:sqlite:" + new File(folder, options.file()).getAbsolutePath());
        // Write-ahead logging survives in the file itself, so running it per connection is free.
        settings.setConnectionInitSql("PRAGMA journal_mode = WAL");
        // SQLite serialises writers anyway; a second connection would only produce lock errors.
        settings.setMaximumPoolSize(1);
    }

    private static void configureRemote(StorageOptions.Remote remote, HikariConfig settings) {
        StringJoiner query = new StringJoiner("&");
        for (Map.Entry<String, String> property : remote.properties().entrySet()) {
            query.add(property.getKey() + "=" + property.getValue());
        }

        String url = "jdbc:mariadb://" + remote.host() + ":" + remote.port() + "/" + remote.database();
        settings.setJdbcUrl(query.length() == 0 ? url : url + "?" + query);
        settings.setUsername(remote.username());
        settings.setPassword(remote.password());
        settings.setMaximumPoolSize(remote.poolSize());
        settings.setMinimumIdle(Math.min(2, remote.poolSize()));
        // Stay below the eight hour wait_timeout most hosts ship with.
        settings.setMaxLifetime(TimeUnit.MINUTES.toMillis(25));
        settings.setKeepaliveTime(TimeUnit.MINUTES.toMillis(5));
    }
}
