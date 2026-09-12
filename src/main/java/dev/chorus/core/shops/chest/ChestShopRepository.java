package dev.chorus.core.shops.chest;

import java.sql.SQLException;
import java.util.List;

public interface ChestShopRepository {

    void createTables() throws SQLException;

    /** Every shop, for the service to hold in memory. */
    List<ChestShop> all() throws SQLException;

    /** @return the shop as it was saved, with the row it was given. */
    ChestShop save(ChestShop shop) throws SQLException;

    void update(ChestShop shop) throws SQLException;

    void delete(long id) throws SQLException;
}
