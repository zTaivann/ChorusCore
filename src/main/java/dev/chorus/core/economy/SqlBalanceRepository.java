package dev.chorus.core.economy;

import dev.chorus.core.storage.Schema;
import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SqlBalanceRepository implements BalanceRepository {

    private static final String SELECT_ALL = "SELECT player, name, balance FROM chorus_balances";
    private static final String DELETE = "DELETE FROM chorus_balances WHERE player = ?";

    private final Storage storage;
    private final List<String> steps;
    private final String save;

    public SqlBalanceRepository(Storage storage) {
        this.storage = storage;
        this.steps = steps(storage.dialect());
        this.save = save(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "balances", steps);
    }

    @Override
    public List<Account> all() throws SQLException {
        try (Connection connection = storage.connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(SELECT_ALL)) {
            List<Account> accounts = new ArrayList<>();
            while (rows.next()) {
                accounts.add(new Account(UUID.fromString(rows.getString("player")),
                        rows.getString("name"), rows.getDouble("balance")));
            }
            return accounts;
        }
    }

    @Override
    public void save(UUID player, String name, double balance) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(save)) {
            statement.setString(1, player.toString());
            statement.setString(2, name);
            statement.setDouble(3, balance);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(UUID player) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, player.toString());
            statement.executeUpdate();
        }
    }

    private static String save(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    INSERT INTO chorus_balances (player, name, balance) VALUES (?, ?, ?)
                    ON CONFLICT (player) DO UPDATE SET name = excluded.name,
                        balance = excluded.balance""";
            case MYSQL -> """
                    INSERT INTO chorus_balances (player, name, balance) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE name = VALUES(name), balance = VALUES(balance)""";
        };
    }

    private static List<String> steps(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> List.of("""
                    CREATE TABLE IF NOT EXISTS chorus_balances (
                        player  TEXT NOT NULL PRIMARY KEY,
                        name    TEXT NOT NULL,
                        balance REAL NOT NULL DEFAULT 0
                    )""",
                    "CREATE INDEX IF NOT EXISTS chorus_balances_top ON chorus_balances (balance)");
            case MYSQL -> List.of("""
                    CREATE TABLE IF NOT EXISTS chorus_balances (
                        player  CHAR(36)    NOT NULL PRIMARY KEY,
                        name    VARCHAR(32) NOT NULL,
                        balance DOUBLE      NOT NULL DEFAULT 0,
                        INDEX chorus_balances_top (balance)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""");
        };
    }
}
