package dev.chorus.core.economy;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository {

    void createTables() throws SQLException;

    void record(Payment payment) throws SQLException;

    /** The most recent first. A null player means everyone. */
    List<Payment> findRecent(UUID player, int limit) throws SQLException;

    /** Drops everything older than the given moment, and says how many rows went. */
    int deleteBefore(long cutoff) throws SQLException;
}
