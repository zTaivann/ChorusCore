package dev.chorus.core.importer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.stream.Stream;

/**
 * Finds the database QuickShop keeps its shops in, and opens it for reading.
 *
 * <p>QuickShop-Hikari writes one of three things depending on its own config: a MySQL or
 * MariaDB server, an H2 file, or — on older releases — an SQLite file. The first two of those
 * this plugin can read with the drivers it already has. H2 needs a driver nothing here ships,
 * so if the server has none the import says exactly that instead of failing with a stack
 * trace nobody can act on.
 *
 * <p>Everything is opened read-only. Nothing in the QuickShop folder is written, ever, which
 * is what makes it safe to try the check pass on a live server.
 */
final class QuickShopDatabase {

    private static final String DEFAULT_PREFIX = "qs_";
    private static final String H2_SUFFIX = ".mv.db";
    private static final String SQLITE_SUFFIX = ".db";

    /** Where the folder might be, newest name first. */
    private static final String[] FOLDERS = {
        "QuickShop-Hikari", "QuickShopHikari", "QuickShop"
    };

    private QuickShopDatabase() {
    }

    /**
     * @param kind what to tell the admin it is, when the driver for it is missing.
     */
    record Settings(String url, String user, String password, String prefix,
                    String kind, String driverClass) {
    }

    /** The first QuickShop folder that exists next to this plugin's own. */
    static @Nullable Path folderIn(Path plugins) {
        for (String name : FOLDERS) {
            Path candidate = plugins.resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Null when the folder holds no database this can even name. */
    static @Nullable Settings read(Path folder) {
        YamlConfiguration config =
                YamlConfiguration.loadConfiguration(folder.resolve("config.yml").toFile());
        ConfigurationSection database = config.getConfigurationSection("database");
        String prefix = database == null
                ? DEFAULT_PREFIX
                : database.getString("prefix", DEFAULT_PREFIX);

        if (database != null && database.getBoolean("mysql", false)) {
            String host = database.getString("host", "localhost");
            int port = database.getInt("port", 3306);
            String name = database.getString("database", "quickshop");
            return new Settings(
                    "jdbc:mariadb://" + host + ':' + port + '/' + name,
                    database.getString("user", ""),
                    database.getString("password", ""),
                    prefix, "MySQL", "org.mariadb.jdbc.Driver");
        }
        return local(folder, prefix);
    }

    static Connection open(Settings settings) throws SQLException {
        try {
            Class.forName(settings.driverClass());
        } catch (ClassNotFoundException missing) {
            throw new SQLException("No driver for " + settings.kind(), missing);
        }

        Connection connection = DriverManager.getConnection(
                settings.url(), settings.user(), settings.password());
        try {
            connection.setReadOnly(true);
        } catch (SQLException tooLate) {
            // SQLite wants to be told before it opens. Nothing here runs anything but a
            // SELECT either way, so this is a belt on top of braces.
        }
        return connection;
    }

    /** The file QuickShop keeps beside its config when it is not pointed at a server. */
    private static @Nullable Settings local(Path folder, String prefix) {
        Path h2 = firstEndingWith(folder, H2_SUFFIX);
        if (h2 != null) {
            String file = h2.toAbsolutePath().toString();
            String base = file.substring(0, file.length() - H2_SUFFIX.length());
            // No MODE here on purpose. H2 keeps the one it was created with, and naming a
            // different one on the way in changes how it reads its own table names.
            return new Settings(
                    "jdbc:h2:file:" + base + ";ACCESS_MODE_DATA=r;IFEXISTS=TRUE",
                    "", "", prefix, "H2", "org.h2.Driver");
        }

        Path sqlite = firstEndingWith(folder, SQLITE_SUFFIX);
        if (sqlite != null) {
            return new Settings("jdbc:sqlite:" + sqlite.toAbsolutePath(),
                    "", "", prefix, "SQLite", "org.sqlite.JDBC");
        }
        return null;
    }

    private static @Nullable Path firstEndingWith(Path folder, String suffix) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    // An H2 file ends in .db as well, so the plain search must not find one.
                    .filter(path -> suffix.equals(H2_SUFFIX)
                            || !path.getFileName().toString().endsWith(H2_SUFFIX))
                    .findFirst()
                    .orElse(null);
        } catch (IOException unreadable) {
            return null;
        }
    }
}
