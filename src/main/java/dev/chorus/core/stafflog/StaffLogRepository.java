package dev.chorus.core.stafflog;

import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;

public interface StaffLogRepository {

    void createTables() throws SQLException;

    void record(StaffLogEntry entry) throws SQLException;

    /** Newest first. A null name means everything, whoever did it or had it done to them. */
    List<StaffLogEntry> findRecent(@Nullable String name, int limit) throws SQLException;

    int deleteBefore(long cutoff) throws SQLException;
}
