package dev.chorus.core.audit;

import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;

public interface AuditRepository {

    void createTables() throws SQLException;

    void record(AuditEntry entry) throws SQLException;

    /** Newest first. A null name means everything, whoever did it or had it done to them. */
    List<AuditEntry> findRecent(@Nullable String name, int limit) throws SQLException;

    int deleteBefore(long cutoff) throws SQLException;
}
