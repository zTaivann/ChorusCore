package dev.chorus.core.storage;

import java.sql.Connection;
import java.sql.SQLException;

public interface Storage extends AutoCloseable {

    /** Borrows a pooled connection. Callers must close it, ideally with try-with-resources. */
    Connection connection() throws SQLException;

    SqlDialect dialect();

    @Override
    void close();
}
