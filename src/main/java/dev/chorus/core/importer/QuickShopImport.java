package dev.chorus.core.importer;

import dev.chorus.core.backup.InventoryCodec;
import dev.chorus.core.shops.chest.ChestShop;
import dev.chorus.core.shops.chest.ChestShopRepository;
import dev.chorus.core.shops.chest.SqlChestShopRepository;
import dev.chorus.core.storage.Storage;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a server's QuickShop-Hikari shops into Chorus. */
public final class QuickShopImport {

    /** QuickShop writes 0 for a shop that sells to players and 1 for one that buys. */
    private static final int SELLING = 0;

    /** How many table names fit in a chat line before it stops being readable. */
    private static final int MAX_NAMES = 8;

    /** The table every version of the newer shape has, which is what names the prefix. */
    private static final String MAP_TABLE = "shop_map";

    /** H2 counts its own catalogue as tables; nothing in there is a shop. */
    private static final String SYSTEM_SCHEMA = "INFORMATION_SCHEMA";

    /** Enough of a value to recognise it in a chat line. */
    private static final int SNIPPET = 40;

    private static final Pattern OWNER_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern OWNER_ID_PLAIN = Pattern.compile("\\b[0-9a-fA-F]{32}\\b");

    private final Storage storage;
    private final Path folder;

    public QuickShopImport(Storage storage, Path folder) {
        this.storage = storage;
        this.folder = folder;
    }

    /** The QuickShop folder next to this plugin's own, or null when there is none. */
    public static @Nullable Path folderIn(Path plugins) {
        return QuickShopDatabase.folderIn(plugins);
    }

    /** One QuickShop row, in the only nine fields that mean anything here. */
    private record Row(String world, int x, int y, int z, String owner, String item,
                       double price, int type, boolean unlimited) {
    }

    /** Everything the reading pass writes into or reads from, so the loop stays one line. */
    private record Into(ShopImportReport report, ChestShopRepository shops,
                        Map<String, ChestShop> existing, Map<UUID, String> names,
                        boolean dryRun, boolean overwrite) {
    }

    /**
     * Blocking. Belongs on a worker thread.
     *
     * @param dryRun    true to read everything and write nothing
     * @param overwrite true to replace a Chorus shop standing on the same block
     */
    public ShopImportReport run(boolean dryRun, boolean overwrite) throws SQLException {
        ShopImportReport report = new ShopImportReport();

        QuickShopDatabase.Settings settings = QuickShopDatabase.read(folder);
        if (settings == null) {
            report.problem("No QuickShop database in " + folder.getFileName());
            return report;
        }
        report.from(settings.kind());

        ChestShopRepository shops = new SqlChestShopRepository(storage);
        shops.createTables();

        Map<String, ChestShop> existing = new HashMap<>();
        for (ChestShop shop : shops.all()) {
            existing.put(shop.key(), shop);
        }
        Map<UUID, String> names = knownNames();

        Into into = new Into(report, shops, existing, names, dryRun, overwrite);
        try (Connection connection = QuickShopDatabase.open(settings)) {
            Set<String> tables = tablesIn(connection);
            String prefix = prefixIn(tables, settings.prefix());
            if (prefix == null) {
                explain(report, settings, tables);
                return report;
            }

            // Both shapes are tried, newest first. A server may still carry either.
            String modern = attempt(connection, modernQuery(prefix), "item", into);
            if (modern == null) {
                return report;
            }
            String legacy = attempt(connection, legacyQuery(prefix), "itemConfig", into);
            if (legacy == null) {
                return report;
            }

            explain(report, settings, tables);
            report.problem(modern);
            report.problem(legacy);
        } catch (SQLException unreachable) {
            report.problem(settings.kind() + " could not be opened: " + reasonFor(unreachable));
        }
        return report;
    }

    /** @return null when it worked, or why it did not. */
    private @Nullable String attempt(Connection connection, String sql, String itemColumn,
                                     Into into) {
        try {
            read(connection, sql, itemColumn, into);
            return null;
        } catch (SQLException refused) {
            return reasonFor(refused);
        }
    }

    /** Says what was actually in there, since nothing in it could be read. */
    private static void explain(ShopImportReport report, QuickShopDatabase.Settings settings,
                                Set<String> tables) {
        if (tables.isEmpty()) {
            report.problem("Opened the " + settings.kind() + " database but found nothing in it. "
                    + "If QuickShop is still running, stop it and try again.");
            return;
        }
        report.problem("Nothing in there looks like a QuickShop shop table. It holds: "
                + names(tables));
    }

    /** Where the shop is, which record it points at, and the record itself. */
    private static String modernQuery(String prefix) {
        return "SELECT m.world, m.x, m.y, m.z, d.owner, d.item, d.price, d.type, d.unlimited"
                + " FROM " + prefix + "shop_map m"
                + " JOIN " + prefix + "shops s ON s.id = m.shop"
                + " JOIN " + prefix + "data d ON d.id = s.data";
    }

    /** The older shape, where one row is the whole shop. */
    private static String legacyQuery(String prefix) {
        return "SELECT world, x, y, z, owner, itemConfig, price, type, unlimited FROM "
                + prefix + "shops";
    }

    private void read(Connection connection, String sql, String itemColumn, Into into)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                take(new Row(
                        rows.getString("world"), rows.getInt("x"), rows.getInt("y"),
                        rows.getInt("z"), rows.getString("owner"), rows.getString(itemColumn),
                        rows.getDouble("price"), rows.getInt("type"),
                        rows.getBoolean("unlimited")), into);
            }
        }
    }

    private void take(Row row, Into into) throws SQLException {
        into.report().shop();
        String at = row.world() + " " + row.x() + "," + row.y() + "," + row.z();

        if (row.world() == null || row.world().isBlank()) {
            into.report().broken();
            into.report().problem("A shop row has no world on it");
            return;
        }

        UUID id = ownerOf(row.owner());
        if (id == null) {
            into.report().broken();
            into.report().problem("The owner of the shop at " + at
                    + " is not an id: " + snippet(row.owner()));
            return;
        }

        String[] why = new String[1];
        ItemStack stack = itemOf(row.item(), reason -> why[0] = reason);
        if (stack == null) {
            into.report().broken();
            into.report().problem("The item of the shop at " + at + " could not be read: "
                    + (why[0] == null ? snippet(row.item()) : why[0]));
            return;
        }

        String key = ChestShop.key(row.world(), row.x(), row.y(), row.z());
        ChestShop already = into.existing().get(key);
        if (already != null && !into.overwrite()) {
            into.report().skip();
            return;
        }

        ChestShop shop = new ChestShop(already == null ? 0 : already.id(), id,
                into.names().getOrDefault(id, id.toString()),
                row.world(), row.x(), row.y(), row.z(),
                InventoryCodec.encode(new ItemStack[] {stack}), Math.max(0, row.price()),
                row.type() == SELLING, row.unlimited(), System.currentTimeMillis());

        into.report().wrote();
        if (into.dryRun()) {
            return;
        }

        if (already == null) {
            into.existing().put(key, into.shops().save(shop));
        } else {
            into.shops().update(shop);
            into.existing().put(key, shop);
        }
    }

    /**
     * QuickShop writes the owner as a plain id on most rows, and on some as an id with
     * something in front of it. Anything that is not a readable id is left alone rather than
     * guessed at, since guessing means handing somebody else's shop to the wrong player.
     */
    private static @Nullable UUID ownerOf(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        // Written as a bare id, a prefixed one and as JSON over the versions.
        Matcher dashed = OWNER_ID.matcher(raw);
        if (dashed.find()) {
            return UUID.fromString(dashed.group());
        }

        Matcher plain = OWNER_ID_PLAIN.matcher(raw);
        if (plain.find()) {
            String hex = plain.group();
            return UUID.fromString(hex.substring(0, 8) + '-' + hex.substring(8, 12) + '-'
                    + hex.substring(12, 16) + '-' + hex.substring(16, 20) + '-'
                    + hex.substring(20));
        }
        return null;
    }

    /** QuickShop keeps the item as a piece of YAML with the item under the key "item". */
    private static @Nullable ItemStack itemOf(@Nullable String raw, Consumer<String> onProblem) {
        if (raw == null || raw.isBlank()) {
            onProblem.accept("there is nothing in the column");
            return null;
        }

        ItemStack item = fromBytes(raw);
        if (item == null) {
            item = fromYaml(raw);
        }
        if (item == null) {
            onProblem.accept("it is neither form QuickShop writes. It starts " + snippet(raw));
            return null;
        }
        if (item.getType().isAir()) {
            onProblem.accept("it is air");
            return null;
        }
        return item;
    }

    /** The form QuickShop writes now: the bytes Bukkit itself gives for an item, in base64. */
    private static @Nullable ItemStack fromBytes(String raw) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw.trim()));
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** The older form: a piece of YAML with the item under the key "item". */
    private static @Nullable ItemStack fromYaml(String raw) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(raw);
            return yaml.getItemStack("item");
        } catch (InvalidConfigurationException | RuntimeException unreadable) {
            return null;
        }
    }

    /** Enough of a value to recognise it, never enough to fill the screen. */
    private static String snippet(@Nullable String raw) {
        if (raw == null) {
            return "nothing";
        }
        String flat = raw.replace('\n', ' ').trim();
        return flat.length() <= SNIPPET ? flat : flat.substring(0, SNIPPET) + "...";
    }

    /** The names Chorus already knows, so the signs read as names rather than as ids. */
    private Map<UUID, String> knownNames() {
        Map<UUID, String> names = new HashMap<>();
        try (Connection connection = storage.connection();
             PreparedStatement statement =
                     connection.prepareStatement("SELECT player, name FROM chorus_players");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                try {
                    names.put(UUID.fromString(rows.getString("player")), rows.getString("name"));
                } catch (IllegalArgumentException skip) {
                    // A row nothing here wrote. The id is used instead.
                }
            }
        } catch (SQLException noProfiles) {
        }
        return names;
    }

    /** Every table the connection can see. */
    private static Set<String> tablesIn(Connection connection) throws SQLException {
        Set<String> found = new HashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet rows = metadata.getTables(null, null, "%", new String[] {"TABLE"})) {
            while (rows.next()) {
                // H2 counts its own information schema as tables.
                String schema = rows.getString("TABLE_SCHEM");
                if (schema != null && schema.equalsIgnoreCase(SYSTEM_SCHEMA)) {
                    continue;
                }
                found.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return found;
    }

    /**
     * The prefix the tables actually carry.
     *
     * @return null when nothing in there looks like QuickShop.
     */
    private static @Nullable String prefixIn(Set<String> tables, String configured) {
        for (String candidate : List.of(configured, "")) {
            if (tables.contains(candidate + "shop_map") || tables.contains(candidate + "shops")) {
                return candidate;
            }
        }
        for (String table : tables) {
            if (table.endsWith(MAP_TABLE)) {
                return table.substring(0, table.length() - MAP_TABLE.length());
            }
        }
        return null;
    }

    /** A handful of them, since a database can hold hundreds and a chat line cannot. */
    private static String names(Set<String> tables) {
        List<String> some = new ArrayList<>(new TreeSet<>(tables));
        if (some.size() <= MAX_NAMES) {
            return String.join(", ", some);
        }
        return String.join(", ", some.subList(0, MAX_NAMES))
                + " and " + (some.size() - MAX_NAMES) + " more";
    }

    /** The short version, since the whole message is a wall of JDBC. */
    private static String reasonFor(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
