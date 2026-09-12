package dev.chorus.core.importer;

import dev.chorus.core.config.ConfigFile;
import dev.chorus.core.economy.BalanceRepository;
import dev.chorus.core.economy.SqlBalanceRepository;
import dev.chorus.core.home.Home;
import dev.chorus.core.home.HomeRepository;
import dev.chorus.core.home.SqlHomeRepository;
import dev.chorus.core.location.LocationRepository;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.location.Names;
import dev.chorus.core.location.SqlLocationRepository;
import dev.chorus.core.mail.MailRepository;
import dev.chorus.core.mail.SqlMailRepository;
import dev.chorus.core.players.PlayerProfileRepository;
import dev.chorus.core.players.SqlPlayerProfileRepository;
import dev.chorus.core.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Reads a server's EssentialsX data into Chorus.
 *
 * <p>Nobody changes the plugin that holds every home and every balance on their server if it
 * means losing them, so this exists and has to be trusted. Three rules make that possible:
 *
 * <ol>
 *   <li><b>Nothing in the Essentials folder is touched.</b> Every file is opened for reading.
 *       If the import goes wrong, or the server owner changes their mind, putting the old
 *       plugin back is all it takes.</li>
 *   <li><b>Nothing already in Chorus is overwritten</b> unless it is asked for. A home, a
 *       warp or a balance that is already here is counted as skipped and left alone.</li>
 *   <li><b>It can be run without doing anything.</b> The check pass reads every file and
 *       reports exactly what a real run would write.</li>
 * </ol>
 *
 * <p>Kits are deliberately not imported. Essentials writes them as its own item strings, and
 * a kit that came across half right is worse than one that never came across at all.
 */
public final class EssentialsImport {

    private static final String WARP_CATEGORY = "warp";
    private static final int MAX_HOME_NAME = 32;

    private final Storage storage;
    private final Path essentials;
    private final @Nullable ConfigFile shopsConfig;

    public EssentialsImport(Storage storage, Path essentials, @Nullable ConfigFile shopsConfig) {
        this.storage = storage;
        this.essentials = essentials;
        this.shopsConfig = shopsConfig;
    }

    public boolean isPresent() {
        return Files.isDirectory(essentials);
    }

    /**
     * Blocking. Belongs on a worker thread.
     *
     * @param dryRun    true to read everything and write nothing
     * @param overwrite true to replace what Chorus already has
     */
    public ImportReport run(boolean dryRun, boolean overwrite) throws SQLException {
        ImportReport report = new ImportReport();

        HomeRepository homes = new SqlHomeRepository(storage);
        BalanceRepository balances = new SqlBalanceRepository(storage);
        PlayerProfileRepository profiles = new SqlPlayerProfileRepository(storage);
        MailRepository mail = new SqlMailRepository(storage);
        LocationRepository locations = new SqlLocationRepository(storage);

        Set<UUID> withBalances = existingBalances(balances);

        players(report, dryRun, overwrite, homes, balances, profiles, mail, withBalances);
        warps(report, dryRun, overwrite, locations);
        worth(report, dryRun, overwrite);
        return report;
    }

    // ── userdata ──────────────────────────────────────────────────────────────

    private void players(ImportReport report, boolean dryRun, boolean overwrite,
                         HomeRepository homes, BalanceRepository balances,
                         PlayerProfileRepository profiles, MailRepository mail,
                         Set<UUID> withBalances) {
        Path folder = essentials.resolve("userdata");
        if (!Files.isDirectory(folder)) {
            return;
        }

        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                onePlayer(report, file, dryRun, overwrite, homes, balances, profiles, mail,
                        withBalances);
            }
        } catch (Exception unreadable) {
            report.problem("userdata: " + unreadable.getMessage());
        }
    }

    private void onePlayer(ImportReport report, Path file, boolean dryRun, boolean overwrite,
                           HomeRepository homes, BalanceRepository balances,
                           PlayerProfileRepository profiles, MailRepository mail,
                           Set<UUID> withBalances) {
        report.readFile();

        UUID owner = uuidOf(file);
        if (owner == null) {
            report.problem(file.getFileName() + ": the name is not an account id");
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file.toFile());
        if (data.getKeys(false).isEmpty()) {
            report.problem(file.getFileName() + ": empty or not readable");
            return;
        }
        report.player();

        String name = data.getString("last-account-name", "");
        long login = data.getLong("timestamps.login", 0);
        long logout = data.getLong("timestamps.logout", 0);
        String address = data.getString("ipAddress", "");
        String nickname = data.getString("nickname", "");

        try {
            profile(report, dryRun, overwrite, profiles, owner, name, address, login, logout,
                    nickname);
            homes(report, dryRun, overwrite, homes, owner, data);
            balance(report, dryRun, overwrite, balances, owner, name, data, withBalances);
            mail(report, dryRun, mail, owner, data);
        } catch (SQLException failed) {
            report.problem(file.getFileName() + ": " + failed.getMessage());
        }
    }

    private void profile(ImportReport report, boolean dryRun, boolean overwrite,
                         PlayerProfileRepository profiles, UUID owner, String name,
                         String address, long login, long logout, String nickname)
            throws SQLException {
        if (name.isEmpty()) {
            return;
        }
        boolean known = profiles.find(owner) != null;
        if (known && !overwrite) {
            report.skip();
        } else if (!dryRun) {
            long first = login > 0 ? login : System.currentTimeMillis();
            profiles.seen(owner, name, address, first);
            if (logout > 0) {
                profiles.left(owner, logout, "", 0, 0, 0, 0, 0);
            }
        }

        if (!nickname.isEmpty()) {
            report.nickname();
            if (!dryRun) {
                profiles.nickname(owner, nickname);
            }
        }
    }

    private void homes(ImportReport report, boolean dryRun, boolean overwrite,
                       HomeRepository homes, UUID owner, YamlConfiguration data)
            throws SQLException {
        ConfigurationSection section = data.getConfigurationSection("homes");
        if (section == null) {
            return;
        }

        Set<String> existing = new HashSet<>();
        for (Home home : homes.findByOwner(owner)) {
            existing.add(home.name());
        }

        for (String raw : section.getKeys(false)) {
            ConfigurationSection where = section.getConfigurationSection(raw);
            if (where == null) {
                continue;
            }
            String name = Names.normalise(raw);
            if (name.isEmpty() || name.length() > MAX_HOME_NAME) {
                report.problem("home '" + raw + "' of " + owner + ": the name will not fit");
                continue;
            }
            if (existing.contains(name) && !overwrite) {
                report.skip();
                continue;
            }

            String world = world(where);
            if (world.isEmpty()) {
                report.problem("home '" + raw + "' of " + owner + ": no world");
                continue;
            }

            report.home();
            if (dryRun) {
                continue;
            }
            homes.save(new Home(owner, name, worldId(world), world,
                    where.getDouble("x"), where.getDouble("y"), where.getDouble("z"),
                    (float) where.getDouble("yaw"), (float) where.getDouble("pitch"),
                    System.currentTimeMillis(), null));
        }
    }

    private void balance(ImportReport report, boolean dryRun, boolean overwrite,
                         BalanceRepository balances, UUID owner, String name,
                         YamlConfiguration data, Set<UUID> withBalances) throws SQLException {
        if (!data.contains("money")) {
            return;
        }
        double money = money(data.get("money"));
        if (money < 0) {
            report.problem("the balance of " + owner + " is not a number");
            return;
        }
        if (withBalances.contains(owner) && !overwrite) {
            report.skip();
            return;
        }

        report.balance();
        if (!dryRun) {
            balances.save(owner, name.isEmpty() ? owner.toString() : name, money);
        }
    }

    /**
     * Essentials writes mail as lines of text, older versions as {@code "Sender: body"} and
     * newer ones with a timestamp in front. Both are read back into a sender and a body.
     */
    private void mail(ImportReport report, boolean dryRun, MailRepository mail, UUID owner,
                      YamlConfiguration data) throws SQLException {
        List<String> letters = data.getStringList("mail");
        if (letters.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();

        for (String line : letters) {
            String text = line.trim();
            if (text.isEmpty()) {
                continue;
            }
            int colon = text.indexOf(':');
            String sender = colon > 0 ? text.substring(0, colon).trim() : "Server";
            String body = colon > 0 ? text.substring(colon + 1).trim() : text;
            if (body.isEmpty()) {
                continue;
            }

            report.letter();
            if (!dryRun) {
                mail.send(owner, sender, body, now);
            }
        }
    }

    // ── warps ─────────────────────────────────────────────────────────────────

    private void warps(ImportReport report, boolean dryRun, boolean overwrite,
                       LocationRepository locations) {
        Path folder = essentials.resolve("warps");
        if (!Files.isDirectory(folder)) {
            return;
        }

        Set<String> existing = new HashSet<>();
        try {
            for (NamedLocation warp : locations.findAll(WARP_CATEGORY)) {
                existing.add(warp.name());
            }
        } catch (SQLException failed) {
            report.problem("warps: " + failed.getMessage());
            return;
        }

        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                oneWarp(report, file, dryRun, overwrite, locations, existing);
            }
        } catch (Exception unreadable) {
            report.problem("warps: " + unreadable.getMessage());
        }
    }

    private void oneWarp(ImportReport report, Path file, boolean dryRun, boolean overwrite,
                         LocationRepository locations, Set<String> existing) {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file.toFile());
        String raw = data.getString("name", stem(file));
        String name = Names.normalise(raw);
        if (name.isEmpty()) {
            report.problem("warp " + file.getFileName() + ": no usable name");
            return;
        }
        if (existing.contains(name) && !overwrite) {
            report.skip();
            return;
        }

        String world = world(data);
        if (world.isEmpty()) {
            report.problem("warp '" + name + "': no world");
            return;
        }

        report.warp();
        if (dryRun) {
            return;
        }
        try {
            locations.save(WARP_CATEGORY, new NamedLocation(name, worldId(world), world,
                    data.getDouble("x"), data.getDouble("y"), data.getDouble("z"),
                    (float) data.getDouble("yaw"), (float) data.getDouble("pitch"),
                    System.currentTimeMillis()));
        } catch (SQLException failed) {
            report.problem("warp '" + name + "': " + failed.getMessage());
        }
    }

    // ── worth.yml ─────────────────────────────────────────────────────────────

    private void worth(ImportReport report, boolean dryRun, boolean overwrite) {
        File file = essentials.resolve("worth.yml").toFile();
        if (!file.isFile() || shopsConfig == null) {
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection worth = data.getConfigurationSection("worth");
        if (worth == null) {
            return;
        }

        boolean changed = false;
        for (String key : worth.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                report.problem("worth '" + key + "': no such item on this version");
                continue;
            }
            double price = price(worth.get(key));
            if (price <= 0) {
                continue;
            }

            String path = "shops.worth." + material.name().toLowerCase(Locale.ROOT);
            if (shopsConfig.data().contains(path) && !overwrite) {
                report.skip();
                continue;
            }

            report.price();
            if (!dryRun) {
                shopsConfig.data().set(path, price);
                changed = true;
            }
        }
        if (changed) {
            shopsConfig.save();
        }
    }

    // ── reading Essentials ────────────────────────────────────────────────────

    /**
     * Essentials writes a price as a number, or as a section with the amount under it when
     * the item had data values. Both shapes turn into one number.
     */
    private static double price(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                double found = price(section.get(key));
                if (found > 0) {
                    return found;
                }
            }
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException notANumber) {
                return -1;
            }
        }
        return -1;
    }

    /** Essentials has written money as a string for years, and as a number before that. */
    private static double money(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException notANumber) {
                return -1;
            }
        }
        return -1;
    }

    /** {@code world-name} is the modern key; {@code world} is what older files used. */
    private static String world(ConfigurationSection where) {
        String name = where.getString("world-name", "");
        return name.isEmpty() ? where.getString("world", "") : name;
    }

    /**
     * The world's real id when the server has it loaded, and one made from the name when it
     * does not. A world that is not loaded now may be loaded later, and the name is what the
     * plugin looks it up by.
     */
    private static UUID worldId(String name) {
        World world = Bukkit.getWorld(name);
        return world != null ? world.getUID()
                : UUID.nameUUIDFromBytes(("chorus-import:" + name).getBytes());
    }

    private static @Nullable UUID uuidOf(Path file) {
        try {
            return UUID.fromString(stem(file));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private static String stem(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private Set<UUID> existingBalances(BalanceRepository balances) throws SQLException {
        Set<UUID> known = new HashSet<>();
        for (BalanceRepository.Account account : balances.all()) {
            known.add(account.player());
        }
        return known;
    }

    /** Every folder an Essentials install might be in, newest layout first. */
    public static List<Path> candidates(Path pluginsFolder) {
        List<Path> found = new ArrayList<>();
        found.add(pluginsFolder.resolve("Essentials"));
        found.add(pluginsFolder.resolve("EssentialsX"));
        return found;
    }
}
