package dev.chorus.core.warp;

import java.sql.SQLException;
import java.util.List;

interface WarpDetailsRepository {

    void createTables() throws SQLException;

    List<WarpDetails> findAll() throws SQLException;

    void save(WarpDetails details) throws SQLException;

    void delete(String warp) throws SQLException;
}
