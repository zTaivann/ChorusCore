package dev.chorus.core.players;

import dev.chorus.core.storage.Schema;
import dev.chorus.core.storage.SqlDialect;
import dev.chorus.core.storage.Storage;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SqlPlayerProfileRepository implements PlayerProfileRepository {

    private static final String SELECT = "SELECT * FROM chorus_players WHERE player = ?";
    private static final String SELECT_BY_NAME =
            "SELECT * FROM chorus_players WHERE lower_name = ? OR lower_nickname = ? LIMIT 1";
    private static final String SELECT_SHARING =
            "SELECT name FROM chorus_players WHERE address = ? AND address <> '' AND player <> ? "
                    + "ORDER BY last_seen DESC LIMIT 20";
    private static final String SELECT_NAMES =
            "SELECT name FROM chorus_players WHERE lower_name LIKE ? ORDER BY last_seen DESC LIMIT ?";
    private static final String UPDATE_LEFT =
            "UPDATE chorus_players SET last_seen = ?, world = ?, x = ?, y = ?, z = ?, yaw = ?, "
                    + "pitch = ? WHERE player = ?";
    private static final String UPDATE_NICKNAME =
            "UPDATE chorus_players SET nickname = ?, lower_nickname = ? WHERE player = ?";

    private final Storage storage;
    private final List<String> steps;
    private final String seen;

    public SqlPlayerProfileRepository(Storage storage) {
        this.storage = storage;
        this.steps = steps(storage.dialect());
        this.seen = seen(storage.dialect());
    }

    @Override
    public void createTables() throws SQLException {
        Schema.apply(storage, "players", steps);
    }

    @Override
    public void seen(UUID player, String name, String address, long when) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(seen)) {
            statement.setString(1, player.toString());
            statement.setString(2, name);
            statement.setString(3, name.toLowerCase(Locale.ROOT));
            statement.setString(4, address);
            statement.setLong(5, when);
            statement.setLong(6, when);
            statement.executeUpdate();
        }
    }

    @Override
    public void left(UUID player, long when, String world, double x, double y, double z,
                     float yaw, float pitch) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_LEFT)) {
            statement.setLong(1, when);
            statement.setString(2, world);
            statement.setDouble(3, x);
            statement.setDouble(4, y);
            statement.setDouble(5, z);
            statement.setFloat(6, yaw);
            statement.setFloat(7, pitch);
            statement.setString(8, player.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void nickname(UUID player, @Nullable String nickname) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_NICKNAME)) {
            statement.setString(1, nickname == null ? "" : nickname);
            statement.setString(2, nickname == null ? "" : plain(nickname));
            statement.setString(3, player.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public @Nullable PlayerProfile find(UUID player) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, player.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows) : null;
            }
        }
    }

    @Override
    public @Nullable PlayerProfile findByName(String name) throws SQLException {
        String lower = name.toLowerCase(Locale.ROOT);
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_NAME)) {
            statement.setString(1, lower);
            statement.setString(2, lower);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows) : null;
            }
        }
    }

    @Override
    public List<String> sharing(String address, UUID except) throws SQLException {
        if (address.isEmpty()) {
            return List.of();
        }
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SHARING)) {
            statement.setString(1, address);
            statement.setString(2, except.toString());
            try (ResultSet rows = statement.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (rows.next()) {
                    names.add(rows.getString("name"));
                }
                return names;
            }
        }
    }

    @Override
    public List<String> namesLike(String prefix, int limit) throws SQLException {
        try (Connection connection = storage.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_NAMES)) {
            statement.setString(1, prefix.toLowerCase(Locale.ROOT) + "%");
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (rows.next()) {
                    names.add(rows.getString("name"));
                }
                return names;
            }
        }
    }

    /** A nickname is matched on its letters, so colour codes never hide one from /realname. */
    static String plain(String nickname) {
        return nickname.replaceAll("(?i)[&§][0-9a-fk-or]", "").toLowerCase(Locale.ROOT);
    }

    private static PlayerProfile read(ResultSet rows) throws SQLException {
        String nickname = rows.getString("nickname");
        return new PlayerProfile(
                UUID.fromString(rows.getString("player")),
                rows.getString("name"),
                nickname == null || nickname.isEmpty() ? null : nickname,
                rows.getLong("first_seen"),
                rows.getLong("last_seen"),
                rows.getString("address"),
                rows.getString("world"),
                rows.getDouble("x"), rows.getDouble("y"), rows.getDouble("z"),
                rows.getFloat("yaw"), rows.getFloat("pitch"));
    }

    private static String seen(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> """
                    INSERT INTO chorus_players (player, name, lower_name, address, first_seen, last_seen)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (player) DO UPDATE SET name = excluded.name,
                        lower_name = excluded.lower_name, address = excluded.address,
                        last_seen = excluded.last_seen""";
            case MYSQL -> """
                    INSERT INTO chorus_players (player, name, lower_name, address, first_seen, last_seen)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE name = VALUES(name), lower_name = VALUES(lower_name),
                        address = VALUES(address), last_seen = VALUES(last_seen)""";
        };
    }

    private static List<String> steps(SqlDialect dialect) {
        return switch (dialect) {
            case SQLITE -> List.of("""
                    CREATE TABLE IF NOT EXISTS chorus_players (
                        player         TEXT    NOT NULL PRIMARY KEY,
                        name           TEXT    NOT NULL,
                        lower_name     TEXT    NOT NULL,
                        nickname       TEXT    NOT NULL DEFAULT '',
                        lower_nickname TEXT    NOT NULL DEFAULT '',
                        address        TEXT    NOT NULL DEFAULT '',
                        first_seen     INTEGER NOT NULL,
                        last_seen      INTEGER NOT NULL,
                        world          TEXT    NOT NULL DEFAULT '',
                        x              REAL    NOT NULL DEFAULT 0,
                        y              REAL    NOT NULL DEFAULT 0,
                        z              REAL    NOT NULL DEFAULT 0,
                        yaw            REAL    NOT NULL DEFAULT 0,
                        pitch          REAL    NOT NULL DEFAULT 0
                    )""",
                    "CREATE INDEX IF NOT EXISTS chorus_players_name ON chorus_players (lower_name)",
                    "CREATE INDEX IF NOT EXISTS chorus_players_address ON chorus_players (address)");
            case MYSQL -> List.of("""
                    CREATE TABLE IF NOT EXISTS chorus_players (
                        player         CHAR(36)     NOT NULL PRIMARY KEY,
                        name           VARCHAR(32)  NOT NULL,
                        lower_name     VARCHAR(32)  NOT NULL,
                        nickname       VARCHAR(64)  NOT NULL DEFAULT '',
                        lower_nickname VARCHAR(64)  NOT NULL DEFAULT '',
                        address        VARCHAR(64)  NOT NULL DEFAULT '',
                        first_seen     BIGINT       NOT NULL,
                        last_seen      BIGINT       NOT NULL,
                        world          VARCHAR(64)  NOT NULL DEFAULT '',
                        x              DOUBLE       NOT NULL DEFAULT 0,
                        y              DOUBLE       NOT NULL DEFAULT 0,
                        z              DOUBLE       NOT NULL DEFAULT 0,
                        yaw            FLOAT        NOT NULL DEFAULT 0,
                        pitch          FLOAT        NOT NULL DEFAULT 0,
                        INDEX chorus_players_name (lower_name),
                        INDEX chorus_players_address (address)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4""");
        };
    }
}
