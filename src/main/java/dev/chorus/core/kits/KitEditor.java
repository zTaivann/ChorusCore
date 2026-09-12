package dev.chorus.core.kits;

import dev.chorus.core.config.ConfigFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        block.set("display", "<white>" + titled(name));
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

    /**
     * The three lists a kit keeps beyond its items, by the name the file gives them.
     *
     * <p>All three hold one line each of {@code type: value}, so one set of operations
     * serves them and the screen that edits them does not have to care which it is on.
     */
    public enum RuleList {

        CLAIM_ACTIONS("claim-actions"),
        FAIL_ACTIONS("fail-actions"),
        REQUIREMENTS("requirements");

        private final String path;

        RuleList(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }

        /** Only a requirement may refuse, so only a requirement has a line to refuse with. */
        public boolean denies() {
            return this == REQUIREMENTS;
        }
    }

    /** One line of such a list, exactly as the file holds it. */
    public record Rule(String line, @Nullable String deny) {

        public static Rule of(String line) {
            return new Rule(line, null);
        }
    }

    /**
     * What a list holds, unparsed.
     *
     * <p>Read from the file rather than from the loaded kit on purpose. A line this plugin
     * does not understand is dropped on load, and editing a list by position in a version of
     * it that has already lost a line is how a typo somewhere above quietly deletes the line
     * below. Here the positions are the file's own.
     */
    public List<Rule> rules(String name, RuleList list) {
        // Asked for without a default, so this is the file's own answer. The copy bundled in
        // the jar backs this config, and reading through it would hand back the lines that
        // ship with the example kits as though the server had written them.
        return read(config.data().get(ROOT + name + "." + list.path(), null));
    }

    static List<Rule> read(@Nullable Object stored) {
        if (!(stored instanceof List<?> written)) {
            return List.of();
        }

        List<Rule> rules = new ArrayList<>(written.size());
        for (Object entry : written) {
            Map<?, ?> block = KitReader.asBlock(entry);
            if (block == null) {
                if (entry != null) {
                    rules.add(Rule.of(String.valueOf(entry)));
                }
                continue;
            }
            Object condition = block.get("condition");
            if (condition != null) {
                Object deny = block.get("deny");
                rules.add(new Rule(String.valueOf(condition),
                        deny == null ? null : String.valueOf(deny)));
            }
        }
        return rules;
    }

    public boolean addRule(String name, RuleList list, Rule rule) {
        List<Rule> rules = new ArrayList<>(rules(name, list));
        rules.add(rule);
        return write(name, list, rules);
    }

    public boolean setRule(String name, RuleList list, int index, Rule rule) {
        List<Rule> rules = new ArrayList<>(rules(name, list));
        if (index < 0 || index >= rules.size()) {
            return false;
        }
        rules.set(index, rule);
        return write(name, list, rules);
    }

    public boolean removeRule(String name, RuleList list, int index) {
        List<Rule> rules = new ArrayList<>(rules(name, list));
        if (index < 0 || index >= rules.size()) {
            return false;
        }
        rules.remove(index);
        return write(name, list, rules);
    }

    /**
     * Back into the file in the plainest shape that still says everything.
     *
     * <p>A requirement with nothing to say when it refuses is written as the bare line it
     * was, not as a block with an empty half. Somebody opening the file afterwards should
     * not be able to tell which lines were typed and which were clicked.
     *
     * <p>An emptied list is written as an empty list rather than taken out. Taking it out
     * would leave the bundled copy of the file showing through, and the last line somebody
     * removed from one of the example kits would be back by the next reload.
     */
    private boolean write(String name, RuleList list, List<Rule> rules) {
        config.data().set(ROOT + name + "." + list.path(), describe(rules));
        return commit();
    }

    static List<Object> describe(List<Rule> rules) {
        List<Object> written = new ArrayList<>(rules.size());
        for (Rule rule : rules) {
            if (rule.deny() == null || rule.deny().isBlank()) {
                written.add(rule.line());
                continue;
            }
            Map<String, Object> block = new LinkedHashMap<>(2);
            block.put("condition", rule.line());
            block.put("deny", rule.deny());
            written.add(block);
        }
        return written;
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
            case "onetime", "autoarmor", "clearinventory", "placeholders" ->
                    Boolean.parseBoolean(value);
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

    /**
     * The name with a capital on the front, for a heading.
     *
     * <p>A kit is keyed by its lower-case name and always will be, because that is what
     * somebody types. A screen headed "starter" reads like a mistake, though, so the two
     * part company at the point where the name is drawn rather than used.
     */
    public static String titled(String name) {
        return name.isEmpty()
                ? name
                : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
