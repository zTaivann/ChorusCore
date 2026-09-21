package dev.chorus.core.economy;

import dev.chorus.core.storage.Schema;
import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class SqlPaymentRepository implements PaymentRepository {

    private static final String INSERT = """
            INSERT INTO chorus_payments (payer, payer_name, payee, payee_name, amount, paid_at)
            VALUES (?, ?, ?, ?, ?, ?)""";

    private static final String SELECT_ALL = """
            SELECT payer, payer_name, payee, payee_name, amount, paid_at
            FROM chorus_payments
            ORDER BY paid_at DESC
            LIMIT ?""";

    private static final String SELECT_FOR_PLAYER = """
            SELECT payer, payer_name, payee, payee_name, amount, paid_at
            FROM chorus_payments
            WHERE payer = ? OR payee = ?
            ORDER BY paid_at DESC
            LIMIT ?""";

    private static final String DELETE_OLD = "DELETE FROM chorus_payments WHERE paid_at < ?";

    private final Storage storage;
    private final List<String> steps;

    SqlPaymentRepository(Storage storage) {
        this.storage = storage;
        this.steps = List.of(createTable(storage.dialect()), createIndex());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "payments", steps);
    }

    @Override
    public void record(Payment payment) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, payment.payer().toString());
            statement.setString(2, payment.payerName());
            statement.setString(3, payment.payee().toString());
            statement.setString(4, payment.payeeName());
            statement.setDouble(5, payment.amount());
            statement.setLong(6, payment.paidAt());
            statement.executeUpdate();
        }
    }

    @Override
    public List<Payment> findRecent(@Nullable UUID player, int limit) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(
                     player == null ? SELECT_ALL : SELECT_FOR_PLAYER)) {
            if (player == null) {
                statement.setInt(1, limit);
            } else {
                statement.setString(1, player.toString());
                statement.setString(2, player.toString());
                statement.setInt(3, limit);
            }

            try (ResultSet rows = statement.executeQuery()) {
                List<Payment> payments = new ArrayList<>();
                while (rows.next()) {
                    payments.add(new Payment(
                            UUID.fromString(rows.getString("payer")),
                            rows.getString("payer_name"),
                            UUID.fromString(rows.getString("payee")),
                            rows.getString("payee_name"),
                            rows.getDouble("amount"),
                            rows.getLong("paid_at")));
                }
                return payments;
            }
        }
    }

    @Override
    public int deleteBefore(long cutoff) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE_OLD)) {
            statement.setLong(1, cutoff);
            return statement.executeUpdate();
        }
    }

    private static String createTable(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS chorus_payments (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        payer      TEXT    NOT NULL,
                        payer_name TEXT    NOT NULL,
                        payee      TEXT    NOT NULL,
                        payee_name TEXT    NOT NULL,
                        amount     REAL    NOT NULL,
                        paid_at    INTEGER NOT NULL
                    )""";
            case MYSQL -> """
                    CREATE TABLE IF NOT EXISTS chorus_payments (
                        id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        payer      CHAR(36)    NOT NULL,
                        payer_name VARCHAR(16) NOT NULL,
                        payee      CHAR(36)    NOT NULL,
                        payee_name VARCHAR(16) NOT NULL,
                        amount     DOUBLE      NOT NULL,
                        paid_at    BIGINT      NOT NULL
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""";
        };
    }

    /** Every read of this table is "newest first". */
    private static String createIndex() {
        return "CREATE INDEX chorus_payments_paid_at ON chorus_payments (paid_at)";
    }
}
