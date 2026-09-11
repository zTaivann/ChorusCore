package dev.chorus.core.kits;

import dev.chorus.core.config.ConfigFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes kit changes back into kits.yml.
 *
 * <p>Every change saves the file and then asks the module to read it again, so what a kit
 * does in game and what the file says are never two different things. Kits are edited a
 * handful of times in a server's life, so paying for a reload each time is nothing next to
 * the confusion of the two drifting apart.
 */
public final class KitEditor {

    private static final String ROOT = "kits.definitions.";

    private final ConfigFile config;
    private final Runnable reload;

    KitEditor(ConfigFile config, Runnable reload) {
        this.config = config;
        this.reload = reload;
    }

    /** What happened to the items, so the command can say when something was flattened. */
    public record Result(boolean saved, int count, int simplified) {

        static Result failed() {
            return new Result(false, 0, 0);
        }
    }

    public boolean create(String name, Player from) {
        ConfigurationSection block = config.data().createSection(ROOT + name);
        block.set("display", "<white>" + capitalise(name));
        block.set("icon", "CHEST");
        block.set("cooldown-seconds", 0);
        block.set("one-time", false);
        block.set("max-claims", 0);
        block.set("price", 0.0);
        block.set("items", KitWriter.describe(from.getInventory().getStorageContents()));
        return commit();
    }

    public boolean delete(String name) {
        config.data().set(ROOT + name, null);
        return commit();
    }

    public Result setItems(String name, Player from) {
        return setItems(name, from.getInventory().getStorageContents());
    }

    /** The same, from a screen the items were laid out in rather than from a rucksack. */
    public Result setItems(String name, ItemStack[] contents) {
        List<Map<String, Object>> written = KitWriter.describe(contents);

        int simplified = 0;
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir() && KitWriter.losesDetail(item)) {
                simplified++;
            }
        }

        config.data().set(ROOT + name + ".items", written);
        return commit() ? new Result(true, written.size(), simplified) : Result.failed();
    }

    /**
     * The icon as a whole item, written in the same form the contents take.
     *
     * <p>A kit shown as a named, enchanted sword reads better than one shown as a plain
     * one, and there was no reason for the icon to understand less than the items do.
     */
    public boolean setIcon(String name, ItemStack item) {
        config.data().set(ROOT + name + ".icon", KitWriter.describe(item));
        return commit();
    }

    /** An empty value takes the setting out again, leaving the kit on the default. */
    public boolean set(String name, String setting, String value) {
        String path = ROOT + name + "." + pathOf(setting);
        config.data().set(path, value.isEmpty() ? null : parse(setting, value));
        return commit();
    }

    private static Object parse(String setting, String value) {
        return switch (setting) {
            case "cooldown", "maxclaims" -> (int) Double.parseDouble(value.replace(',', '.'));
            case "price" -> Double.parseDouble(value.replace(',', '.'));
            case "onetime", "autoarmor", "clearinventory" -> Boolean.parseBoolean(value);
            case "lore" -> List.of(value.split("\\|"));
            default -> value;
        };
    }

    private static String pathOf(String setting) {
        return switch (setting) {
            case "cooldown" -> "cooldown-seconds";
            case "maxclaims" -> "max-claims";
            case "onetime" -> "one-time";
            case "autoarmor" -> "auto-armor";
            case "clearinventory" -> "clear-inventory";
            default -> setting;
        };
    }

    private boolean commit() {
        if (!config.save()) {
            return false;
        }
        reload.run();
        return true;
    }

    private static String capitalise(String name) {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
