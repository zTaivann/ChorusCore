package dev.chorus.core.players;

import dev.chorus.core.storage.Storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Deletes everything belonging to players who have not been seen for a long time.
 *
 * <p>A core plugin keeps a row for every player who ever joined, and on a server that has
 * been up for years most of those rows belong to somebody who logged in once. Nothing else
 * removes them.
 *
 * <p>Three rules keep it safe to run on a live server:
 *
 * <ul>
 *   <li>It can be asked what it would do without doing it, which is the version worth
 *       running first.</li>
 *   <li>Anybody who owns a chest shop is left alone in full. Deleting their row would leave
 *       a chest and a sign standing in the world with nothing behind them.</li>
 *   <li>The whole run is one transaction, so a failure halfway leaves the database exactly
 *       as it was rather than with half a player deleted.</li>
 * </ul>
 *
 * <p>The staff log is deliberately not touched. It is a record of what staff did, not data
 * belonging to the player it was done to.
 */
public final class PlayerPurge {

    /** How many players go into one statement. Long enough to be quick, short of any limit. */
    private static final int CHUNK = 200;

    private static final String STALE =
            "SELECT player FROM chorus_players WHERE last_seen > 0 AND last_seen < ?";
    private static final String SHOP_OWNERS = "SELECT DISTINCT owner FROM chorus_chest_shops";

    /**
     * A table and the columns in it that hold a player.
     *
     * <p>chorus_players is last: if the run fails after some of the others, the profile is
     * still there and the same player is found again next time.
     */
    private record Target(String table, List<String> columns) {
    }

    private static final List<Target> TARGETS = List.of(
            new Target("chorus_homes", List.of("owner")),
            new Target("chorus_balances", List.of("player")),
            new Target("chorus_player_flags", List.of("player")),
            new Target("chorus_powertools", List.of("player")),
            new Target("chorus_ignores", List.of("owner", "ignored")),
            new Target("chorus_mail", List.of("recipient")),
            new Target("chorus_inventory_backups", List.of("owner")),
            new Target("chorus_pending_restores", List.of("owner")),
            new Target("chorus_kit_uses", List.of("owner")),
            new Target("chorus_payments", List.of("payer", "payee")),
            new Target("chorus_notes", List.of("subject")),
            new Target("chorus_players", List.of("player")));

    private final Storage storage;

    public PlayerPurge(Storage storage) {
        this.storage = storage;
    }

    /**
     * @param players  how many were removed, or would be.
     * @param rows     how many rows that came to across every table.
     * @param shopKeep how many were left alone because they own a chest shop.
     */
    public record Report(int players, int rows, int shopKeep) {

        public boolean foundAnything() {
            return players > 0 || shopKeep > 0;
        }
    }

    /**
     * @param before  nobody last seen after this is touched.
     * @param exclude players to leave alone whatever their date, which is everybody online.
     * @param live    false reads and counts without deleting anything.
     */
    public Report run(long before, Set<UUID> exclude, boolean live) throws SQLException {
        try (Connection connection = storage.connection()) {
            Set<String> tables = existingTables(connection);
            if (!tables.contains("chorus_players")) {
                return new Report(0, 0, 0);
            }

            List<String> stale = stale(connection, before, exclude);
            Set<String> keep = tables.contains("chorus_chest_shops")
                    ? shopOwners(connection)
                    : Set.of();

            int shopKeep = 0;
            List<String> going = new ArrayList<>(stale.size());
            for (String player : stale) {
                if (keep.contains(player)) {
                    shopKeep++;
                } else {
                    going.add(player);
                }
            }
            if (going.isEmpty()) {
                return new Report(0, 0, shopKeep);
            }

            int rows = live
                    ? delete(connection, tables, going)
                    : count(connection, tables, going);
            return new Report(going.size(), rows, shopKeep);
        }
    }

    private int delete(Connection connection, Set<String> tables, List<String> players)
            throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int rows = 0;
            for (Target target : TARGETS) {
                if (tables.contains(target.table())) {
                    rows += apply(connection, "DELETE FROM " + target.table(), target, players);
                }
            }
            connection.commit();
            return rows;
        } catch (SQLException failed) {
            connection.rollback();
            throw failed;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private int count(Connection connection, Set<String> tables, List<String> players)
            throws SQLException {
        int rows = 0;
        for (Target target : TARGETS) {
            if (tables.contains(target.table())) {
                rows += apply(connection, "SELECT COUNT(*) FROM " + target.table(), target, players);
            }
        }
        return rows;
    }

    /**
     * Runs one statement over every player in chunks.
     *
     * <p>The table and column names come from the list above and never from anything typed,
     * so the only thing that reaches the database as a value is the player id, bound.
     */
    private int apply(Connection connection, String head, Target target, List<String> players)
            throws SQLException {
        int total = 0;
        for (int from = 0; from < players.size(); from += CHUNK) {
            List<String> chunk = players.subList(from, Math.min(players.size(), from + CHUNK));
            String sql = head + " WHERE " + where(target, chunk.size());

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                for (int column = 0; column < target.columns().size(); column++) {
                    for (String player : chunk) {
                        statement.setString(index++, player);
                    }
                }

                if (head.startsWith("SELECT")) {
                    try (ResultSet rows = statement.executeQuery()) {
                        total += rows.next() ? rows.getInt(1) : 0;
                    }
                } else {
                    total += statement.executeUpdate();
                }
            }
        }
        return total;
    }

    private static String where(Target target, int size) {
        String placeholders = "?, ".repeat(size - 1) + "?";
        StringBuilder clause = new StringBuilder();
        for (String column : target.columns()) {
            if (!clause.isEmpty()) {
                clause.append(" OR ");
            }
            clause.append(column).append(" IN (").append(placeholders).append(')');
        }
        return clause.toString();
    }

    private static List<String> stale(Connection connection, long before, Set<UUID> exclude)
            throws SQLException {
        Set<String> skip = new HashSet<>();
        exclude.forEach(player -> skip.add(player.toString()));

        try (PreparedStatement statement = connection.prepareStatement(STALE)) {
            statement.setLong(1, before);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> stale = new ArrayList<>();
                while (rows.next()) {
                    String player = rows.getString("player");
                    if (!skip.contains(player)) {
                        stale.add(player);
                    }
                }
                return stale;
            }
        }
    }

    private static Set<String> shopOwners(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SHOP_OWNERS);
             ResultSet rows = statement.executeQuery()) {
            Set<String> owners = new HashSet<>();
            while (rows.next()) {
                owners.add(rows.getString("owner"));
            }
            return owners;
        }
    }

    /**
     * The tables that are actually there.
     *
     * <p>A module that has never been switched on has never made its table, and asking the
     * database about one that does not exist is an error rather than an empty answer.
     */
    private static Set<String> existingTables(Connection connection) throws SQLException {
        Set<String> found = new HashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet rows = metadata.getTables(connection.getCatalog(), null, "chorus_%", null)) {
            while (rows.next()) {
                found.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return found;
    }
}
