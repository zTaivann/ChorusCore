package dev.chorus.core.shops;

import dev.chorus.core.command.PermissionValue;
import dev.chorus.core.config.ConfigFile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.permissions.Permissible;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * What each material is worth, for {@code /sell} and for a {@code [Sell]} sign with no price
 * written on it. Read from worth.yml: one item each under {@code worth}, several at one price
 * under {@code groups}.
 */
public final class WorthTable {

    public static final String FILE = "worth.yml";

    private static final String ITEMS = "worth";
    private static final String GROUPS = "groups";

    private final ConfigFile file;

    private volatile Map<Material, Double> prices = Map.of();
    private volatile double multiplier = 1.0;
    private volatile Map<Material, Double> itemMultipliers = Map.of();
    private volatile List<PermissionValue> permissionMultipliers = List.of();

    public WorthTable(ConfigFile file) {
        this.file = file;
    }

    public void reload(Consumer<String> onProblem) {
        ConfigurationSection data = file.data();
        prices = read(data, onProblem);
        multiplier = Math.max(0, data.getDouble("sell-multiplier", 1.0));
        itemMultipliers = itemMultipliers(data, onProblem);
        permissionMultipliers = permissionMultipliers(data, onProblem);
    }

    /** The price of every item that can be sold, from the worth and groups sections. */
    static Map<Material, Double> read(ConfigurationSection data, Consumer<String> onProblem) {
        Map<Material, Double> read = new EnumMap<>(Material.class);
        Set<Material> unsellable = EnumSet.noneOf(Material.class);

        ConfigurationSection items = data.getConfigurationSection(ITEMS);
        if (items != null) {
            for (String key : items.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    onProblem.accept("This server has no material called '" + key
                            + "', priced in " + FILE);
                    continue;
                }
                double price = items.getDouble(key, 0);
                if (price > 0) {
                    read.put(material, price);
                } else {
                    unsellable.add(material);
                }
            }
        }

        ConfigurationSection groups = data.getConfigurationSection(GROUPS);
        if (groups != null) {
            for (String name : groups.getKeys(false)) {
                ConfigurationSection group = groups.getConfigurationSection(name);
                double price = group == null ? 0 : group.getDouble("price", 0);
                if (price <= 0) {
                    onProblem.accept("The group '" + name + "' in " + FILE + " has no price");
                    continue;
                }
                for (String pattern : group.getStringList("items")) {
                    List<Material> matched = matching(pattern);
                    if (matched.isEmpty()) {
                        onProblem.accept("The group '" + name + "' in " + FILE + " names '"
                                + pattern + "', which is no item on this server");
                    }
                    for (Material material : matched) {
                        if (!unsellable.contains(material)) {
                            read.putIfAbsent(material, price);
                        }
                    }
                }
            }
        }

        return Map.copyOf(read);
    }

    /**
     * {@code item-multipliers}, by item. A group's name stands for every item in it, and an
     * item named on its own wins over its group.
     */
    static Map<Material, Double> itemMultipliers(ConfigurationSection data,
                                                 Consumer<String> onProblem) {
        ConfigurationSection written = data.getConfigurationSection("item-multipliers");
        if (written == null) {
            return Map.of();
        }
        ConfigurationSection groups = data.getConfigurationSection(GROUPS);
        Map<Material, Double> byGroup = new EnumMap<>(Material.class);
        Map<Material, Double> byItem = new EnumMap<>(Material.class);
        for (String key : written.getKeys(false)) {
            double factor = written.getDouble(key, -1);
            if (factor < 0) {
                onProblem.accept("The multiplier for '" + key + "' in " + FILE
                        + " is not a number of 0 or more");
                continue;
            }
            ConfigurationSection group = groups == null ? null : groups.getConfigurationSection(key);
            if (group != null) {
                for (String pattern : group.getStringList("items")) {
                    matching(pattern).forEach(material -> byGroup.putIfAbsent(material, factor));
                }
                continue;
            }
            Material material = Material.matchMaterial(key);
            if (material == null) {
                onProblem.accept(FILE + " has a multiplier for '" + key
                        + "', which is neither an item nor a group");
                continue;
            }
            byItem.put(material, factor);
        }
        byGroup.putAll(byItem);
        return Map.copyOf(byGroup);
    }

    /** {@code permission-multipliers}: the biggest one the seller holds counts. */
    static List<PermissionValue> permissionMultipliers(ConfigurationSection data,
                                                       Consumer<String> onProblem) {
        List<PermissionValue> multipliers = new ArrayList<>();
        List<PermissionValue> entries = PermissionValue.read(
                data.getList("permission-multipliers", List.of()),
                line -> onProblem.accept(FILE + " has a permission multiplier it cannot read: '"
                        + line + "'. Write it as 'permission: multiplier', like 'group.vip: 1.25'."));
        for (PermissionValue entry : entries) {
            if (entry.value() < 0) {
                onProblem.accept(FILE + " gives " + entry.permission() + " a multiplier below 0");
                continue;
            }
            multipliers.add(entry);
        }
        return List.copyOf(multipliers);
    }

    /**
     * The items a name stands for. A {@code *} stands for any part of the name, so
     * {@code *_log} is every log.
     */
    static List<Material> matching(String pattern) {
        String wanted = pattern.trim().toLowerCase(Locale.ROOT);
        if (!wanted.contains("*")) {
            Material material = Material.matchMaterial(wanted);
            return material == null || !material.isItem() ? List.of() : List.of(material);
        }
        Pattern shape = Pattern.compile(String.join(".*",
                Arrays.stream(wanted.split("\\*", -1)).map(Pattern::quote).toList()));
        List<Material> matched = new ArrayList<>();
        for (Material material : Material.values()) {
            String name = material.name();
            if (!name.startsWith("LEGACY_") && material.isItem()
                    && shape.matcher(name.toLowerCase(Locale.ROOT)).matches()) {
                matched.add(material);
            }
        }
        return matched;
    }

    /** What the server pays this seller for one, or zero for an item that cannot be sold. */
    public double of(Permissible seller, Material material) {
        Double price = prices.get(material);
        if (price == null) {
            return 0;
        }
        return price * multiplier * itemMultipliers.getOrDefault(material, 1.0)
                * PermissionValue.best(seller, permissionMultipliers, 1.0);
    }

    public boolean sellable(Material material) {
        return prices.containsKey(material);
    }

    public int size() {
        return prices.size();
    }

    /**
     * Writes a new price into the file, so /setworth survives a restart. Zero or less means
     * it cannot be sold, even when a group names it.
     */
    public boolean set(Material material, double price) {
        String key = material.name().toLowerCase(Locale.ROOT);
        Map<Material, Double> changed = new EnumMap<>(Material.class);
        changed.putAll(prices);
        if (price <= 0) {
            changed.remove(material);
            file.data().set(ITEMS + "." + key, 0.0);
        } else {
            changed.put(material, price);
            file.data().set(ITEMS + "." + key, price);
        }
        prices = Map.copyOf(changed);
        return file.save();
    }
}
