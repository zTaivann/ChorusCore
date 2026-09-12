package dev.chorus.core.shops.chest;

import dev.chorus.core.storage.Schema;
import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SqlChestShopRepository implements ChestShopRepository {

    private static final String SELECT_ALL = "SELECT * FROM chorus_chest_shops";
    private static final String INSERT = """
            INSERT INTO chorus_chest_shops
                (owner, owner_name, world, x, y, z, item, price, selling, unlimited, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
    private static final String UPDATE = """
            UPDATE chorus_chest_shops SET owner_name = ?, item = ?, price = ?, selling = ?,
                unlimited = ? WHERE id = ?""";
    private static final String DELETE = "DELETE FROM chorus_chest_shops WHERE id = ?";

    private final Storage storage;
    private final List<String> steps;

    public SqlChestShopRepository(Storage storage) {
        this.storage = storage;
        this.steps = steps(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "chest-shops", steps);
    }

    @Override
    public List<ChestShop> all() throws SQLException {
        try (Connection connection = storage.connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(SELECT_ALL)) {
            List<ChestShop> shops = new ArrayList<>();
            while (rows.next()) {
                shops.add(read(rows));
            }
            return shops;
        }
    }

    @Override
    public ChestShop save(ChestShop shop) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, shop.owner().toString());
            statement.setString(2, shop.ownerName());
            statement.setString(3, shop.world());
            statement.setInt(4, shop.x());
            statement.setInt(5, shop.y());
            statement.setInt(6, shop.z());
            statement.setString(7, shop.item());
            statement.setDouble(8, shop.price());
            statement.setInt(9, shop.selling() ? 1 : 0);
            statement.setInt(10, shop.unlimited() ? 1 : 0);
            statement.setLong(11, shop.createdAt());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : 0;
                return new ChestShop(id, shop.owner(), shop.ownerName(), shop.world(),
                        shop.x(), shop.y(), shop.z(), shop.item(), shop.price(),
                        shop.selling(), shop.unlimited(), shop.createdAt());
            }
        }
    }

    @Override
    public void update(ChestShop shop) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, shop.ownerName());
            statement.setString(2, shop.item());
            statement.setDouble(3, shop.price());
            statement.setInt(4, shop.selling() ? 1 : 0);
            statement.setInt(5, shop.unlimited() ? 1 : 0);
            statement.setLong(6, shop.id());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(long id) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private static ChestShop read(ResultSet rows) throws SQLException {
        return new ChestShop(
                rows.getLong("id"),
                UUID.fromString(rows.getString("owner")),
                rows.getString("owner_name"),
                rows.getString("world"),
                rows.getInt("x"), rows.getInt("y"), rows.getInt("z"),
                rows.getString("item"),
                rows.getDouble("price"),
                rows.getInt("selling") == 1,
                rows.getInt("unlimited") == 1,
                rows.getLong("created_at"));
    }

    private static List<String> steps(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> List.of("""
                    CREATE TABLE IF NOT EXISTS chorus_chest_shops (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner      TEXT    NOT NULL,
                        owner_name TEXT    NOT NULL,
                        world      TEXT    NOT NULL,
                        x          INTEGER NOT NULL,
                        y          INTEGER NOT NULL,
                        z          INTEGER NOT NULL,
                        item       TEXT    NOT NULL,
                        price      REAL    NOT NULL,
                        selling    INTEGER NOT NULL DEFAULT 1,
                        unlimited  INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )""",
                    "CREATE UNIQUE INDEX IF NOT EXISTS chorus_chest_shops_block"
                            + " ON chorus_chest_shops (world, x, y, z)",
                    "CREATE INDEX IF NOT EXISTS chorus_chest_shops_owner"
                            + " ON chorus_chest_shops (owner)");
            case MYSQL -> List.of("""
                    CREATE TABLE IF NOT EXISTS chorus_chest_shops (
                        id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        owner      CHAR(36)     NOT NULL,
                        owner_name VARCHAR(32)  NOT NULL,
                        world      VARCHAR(64)  NOT NULL,
                        x          INT          NOT NULL,
                        y          INT          NOT NULL,
                        z          INT          NOT NULL,
                        item       MEDIUMTEXT   NOT NULL,
                        price      DOUBLE       NOT NULL,
                        selling    TINYINT      NOT NULL DEFAULT 1,
                        unlimited  TINYINT      NOT NULL DEFAULT 0,
                        created_at BIGINT       NOT NULL,
                        UNIQUE KEY chorus_chest_shops_block (world, x, y, z),
                        INDEX chorus_chest_shops_owner (owner)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""");
        };
    }
}
