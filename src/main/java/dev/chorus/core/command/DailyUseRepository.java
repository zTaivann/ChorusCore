package dev.chorus.core.command;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** How many times each command was used in its current day, by owner. */
public interface DailyUseRepository {

    /** @param dayStart when the day this count belongs to began, in epoch milliseconds */
    record Row(UUID owner, String timer, long dayStart, int uses) {
    }

    void createTables() throws SQLException;

    /** Every count for a day that began at or after {@code dayStart}. */
    List<Row> findSince(long dayStart) throws SQLException;

    void save(Row row) throws SQLException;

    /** @return how many were dropped. */
    int deleteBefore(long dayStart) throws SQLException;
}
