package dev.chorus.core.kits.menu;

import dev.chorus.core.kits.KitEditor;
import dev.chorus.core.kits.KitEditor.Rule;
import dev.chorus.core.kits.KitEditor.RuleList;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.menu.ChatPrompts;
import dev.chorus.core.menu.Menu;
import dev.chorus.core.menu.MenuItems;
import dev.chorus.core.rules.Action;
import dev.chorus.core.rules.Requirement;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/** The claim actions, the fail actions and the requirements, one line to an item. */
public final class KitRulesMenu {

    private static final int MIN_ROWS = 2;
    private static final int MAX_ROWS = 6;

    /** Where the hint sits on an otherwise empty screen. */
    private static final int EMPTY_SLOT = 4;

    private static final Map<Action.Kind, Material> ACTION_ICONS =
            new EnumMap<>(Action.Kind.class);
    private static final Map<Requirement.Kind, Material> REQUIREMENT_ICONS =
            new EnumMap<>(Requirement.Kind.class);

    static {
        ACTION_ICONS.put(Action.Kind.MESSAGE, Material.PAPER);
        ACTION_ICONS.put(Action.Kind.BROADCAST, Material.BELL);
        ACTION_ICONS.put(Action.Kind.ACTIONBAR, Material.ITEM_FRAME);
        ACTION_ICONS.put(Action.Kind.TITLE, Material.PAINTING);
        ACTION_ICONS.put(Action.Kind.SOUND, Material.NOTE_BLOCK);
        ACTION_ICONS.put(Action.Kind.CONSOLE, Material.COMMAND_BLOCK);
        ACTION_ICONS.put(Action.Kind.PLAYER, Material.PLAYER_HEAD);
        ACTION_ICONS.put(Action.Kind.CLOSE, Material.OAK_DOOR);

        REQUIREMENT_ICONS.put(Requirement.Kind.PERMISSION, Material.NAME_TAG);
        REQUIREMENT_ICONS.put(Requirement.Kind.PLACEHOLDER, Material.COMPARATOR);
        REQUIREMENT_ICONS.put(Requirement.Kind.MONEY, Material.GOLD_INGOT);
        REQUIREMENT_ICONS.put(Requirement.Kind.PLAYTIME, Material.CLOCK);
        REQUIREMENT_ICONS.put(Requirement.Kind.KIT, Material.CHEST);
    }

    private final KitEditor editor;
    private final Messages messages;
    private final ChatPrompts prompts;
    private final KitMenuSettings settings;
    private final BiConsumer<Player, String> back;

    public KitRulesMenu(KitEditor editor, Messages messages, ChatPrompts prompts,
                        KitMenuSettings settings, BiConsumer<Player, String> back) {
        this.editor = editor;
        this.messages = messages;
        this.prompts = prompts;
        this.settings = settings;
        this.back = back;
    }

    public void open(Player player, String kit, RuleList list) {
        List<Rule> rules = editor.rules(kit, list);
        int rows = rows(rules.size());
        Menu menu = new Menu(player.getServer(),
                messages.render(titleKey(list), "kit", KitEditor.titled(kit)), rows);

        int room = menu.size() - 9;
        for (int slot = 0; slot < Math.min(rules.size(), room); slot++) {
            int index = slot;
            Rule rule = rules.get(slot);
            menu.setPerClick(slot, entry(list, rule, slot),
                    (clicker, click) -> clicked(clicker, kit, list, index, rule, click));
        }

        if (rules.isEmpty()) {
            menu.set(EMPTY_SLOT, MenuItems.of(settings.disabled(),
                    messages.render("menu.editor.rule-empty"),
                    messages.renderLines("menu.editor.rule-empty-lore")));
        }

        menu.set(menu.size() - 9, MenuItems.of(settings.back(),
                        messages.render("menu.editor.rule-back"),
                        messages.renderLines("menu.editor.rule-back-lore",
                                "kit", KitEditor.titled(kit))),
                clicker -> back.accept(clicker, kit));

        menu.set(menu.size() - 5, MenuItems.of(settings.add(),
                        messages.render("menu.editor.rule-add"),
                        messages.renderLines(addLoreKey(list))),
                clicker -> askFor(clicker, kit, list, -1, null));

        menu.fill(0, menu.size(), MenuItems.filler(settings.filler()));
        menu.open(player);
    }

    /** As many rows as the lines need, and never the same screen twice for the same list. */
    private static int rows(int count) {
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, (count + 8) / 9 + 1));
    }

    private void clicked(Player player, String kit, RuleList list, int index, Rule rule,
                         ClickType click) {
        if (click.isShiftClick()) {
            if (editor.removeRule(kit, list, index)) {
                messages.send(player, "menu.editor.rule-removed", "kit", kit);
            } else {
                messages.send(player, "error.storage");
            }
            open(player, kit, list);
            return;
        }
        if (click.isRightClick() && list.denies()) {
            askDeny(player, kit, list, index, rule);
            return;
        }
        askFor(player, kit, list, index, rule);
    }

    /**
     * Asks for a line, and puts it where it was asked for.
     *
     * @param index the line being rewritten, or -1 to add one
     * @param rule  that line as it stands, so it can be read before it is replaced
     */
    private void askFor(Player player, String kit, RuleList list, int index, @Nullable Rule rule) {
        if (rule != null) {
            messages.send(player, "menu.editor.rule-current", "line", rule.line());
        }
        prompts.ask(player, messages.render(askKey(list), "kit", kit),
                typed -> {
                    String line = typed.trim();
                    if (!understood(list, line)) {
                        messages.send(player, badKey(list));
                        open(player, kit, list);
                        return;
                    }
                    Rule written = new Rule(line, rule == null ? null : rule.deny());
                    boolean saved = index < 0
                            ? editor.addRule(kit, list, written)
                            : editor.setRule(kit, list, index, written);
                    messages.send(player, saved ? "menu.editor.rule-saved" : "error.storage",
                            "kit", kit);
                    open(player, kit, list);
                },
                () -> open(player, kit, list));
    }

    private void askDeny(Player player, String kit, RuleList list, int index, Rule rule) {
        if (rule.deny() != null) {
            messages.send(player, "menu.editor.rule-current", "line", rule.deny());
        }
        prompts.ask(player, messages.render("menu.editor.ask-deny", "kit", kit),
                typed -> {
                    // "none" puts it back on the general refusal in the messages folder.
                    String deny = typed.equalsIgnoreCase("none") ? null : typed;
                    boolean saved = editor.setRule(kit, list, index, new Rule(rule.line(), deny));
                    messages.send(player, saved ? "menu.editor.rule-saved" : "error.storage",
                            "kit", kit);
                    open(player, kit, list);
                },
                () -> open(player, kit, list));
    }

    private static boolean understood(RuleList list, String line) {
        return list.denies()
                ? Requirement.of(line, null) != null
                : Action.of(line) != null;
    }

    private ItemStack entry(RuleList list, Rule rule, int index) {
        Known known = known(list, rule);
        String number = String.valueOf(index + 1);

        if (known == null) {
            return MenuItems.of(Material.BARRIER,
                    messages.render("menu.editor.rule-unknown", "index", number),
                    messages.renderLines("menu.editor.rule-unknown-lore", "line", rule.line()));
        }

        return MenuItems.of(known.material(),
                messages.render("menu.editor.rule-entry", "index", number, "kind", known.name()),
                list.denies()
                        ? messages.renderLines("menu.editor.requirement-entry-lore",
                                "line", rule.line(),
                                "deny", rule.deny() == null
                                        ? messages.plain("menu.editor.rule-deny-default")
                                        : rule.deny())
                        : messages.renderLines("menu.editor.rule-entry-lore", "line", rule.line()));
    }

    /** What the line turned out to be, or null when it turned out to be nothing. */
    private record Known(String name, Material material) {
    }

    private static @Nullable Known known(RuleList list, Rule rule) {
        if (list.denies()) {
            Requirement requirement = Requirement.of(rule.line(), rule.deny());
            return requirement == null ? null
                    : new Known(word(requirement.kind().name()),
                            REQUIREMENT_ICONS.get(requirement.kind()));
        }
        Action action = Action.of(rule.line());
        return action == null ? null
                : new Known(word(action.kind().name()), ACTION_ICONS.get(action.kind()));
    }

    private static String word(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String titleKey(RuleList list) {
        return switch (list) {
            case CLAIM_ACTIONS -> "menu.editor.claimactions-title";
            case FAIL_ACTIONS -> "menu.editor.failactions-title";
            case REQUIREMENTS -> "menu.editor.requirements-title";
        };
    }

    private static String askKey(RuleList list) {
        return switch (list) {
            case CLAIM_ACTIONS -> "menu.editor.ask-claimaction";
            case FAIL_ACTIONS -> "menu.editor.ask-failaction";
            case REQUIREMENTS -> "menu.editor.ask-requirement";
        };
    }

    private static String addLoreKey(RuleList list) {
        return list.denies()
                ? "menu.editor.requirement-add-lore"
                : "menu.editor.action-add-lore";
    }

    private static String badKey(RuleList list) {
        return list.denies() ? "menu.editor.bad-requirement" : "menu.editor.bad-action";
    }
}
