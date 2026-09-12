package dev.chorus.core.economy;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface BalanceRepository {

    void createTables() throws SQLException;

    /** Every account, for the ledger to hold in memory. */
    List<Account> all() throws SQLException;

    void save(UUID player, String name, double balance) throws SQLException;

    void delete(UUID player) throws SQLException;

    record Account(UUID player, String name, double balance) {
    }
}
