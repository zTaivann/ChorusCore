package dev.chorus.core.storage;

import java.util.Locale;

public enum SqlDialect {

    SQLITE("org.sqlite.JDBC"),

    /** Speaks to both MySQL and MariaDB through the MariaDB driver. */
    MYSQL("org.mariadb.jdbc.Driver");

    private final String driverClass;

    SqlDialect(String driverClass) {
        this.driverClass = driverClass;
    }

    public String driverClass() {
        return driverClass;
    }

    public static SqlDialect parse(String value) {
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalised) {
            case "SQLITE", "LOCAL", "FILE" -> SQLITE;
            case "MYSQL", "MARIADB", "REMOTE" -> MYSQL;
            default -> throw new IllegalArgumentException(
                    "Unknown storage type '" + value + "', expected sqlite or mysql");
        };
    }
}
