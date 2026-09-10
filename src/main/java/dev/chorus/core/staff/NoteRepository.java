package dev.chorus.core.staff;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

interface NoteRepository {

    void createTables() throws SQLException;

    void add(StaffNote note) throws SQLException;

    /** Oldest first, so a player's history reads in the order it happened. */
    List<StaffNote> findFor(UUID subject) throws SQLException;

    int clear(UUID subject) throws SQLException;
}
