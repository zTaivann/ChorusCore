package dev.chorus.core.kits.menu;

import dev.chorus.core.command.Durations;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.kits.Kit;
import dev.chorus.core.kits.KitEditor;
import dev.chorus.core.kits.KitService;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.platform.Schedulers;
import dev.chorus.core.menu.ChatPrompts;
import dev.chorus.core.menu.PaletteMenu;
import dev.chorus.core.menu.Menu;
import dev.chorus.core.menu.MenuItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The kit editor as a screen.
 *
 * <p>Nothing here sends anybody away to type a command. A button holding a number is nudged
 * up and down with the mouse; a button holding text asks for it in chat and takes the answer
 * without it ever becoming a message; the icon is set by dropping an item into a slot. That
 * is the point of a screen, and a menu that ends every second click with "now go and type
 * this" has only moved the typing somewhere else.
 *
 * <p>The commands still exist and still do everything this does. Some people would rather
 * type, and taking that away to make a screen look complete would be a poor trade.
 */
public final class KitEditMenu {

    private static final int ROWS = 5;
    private static final int HEADER_SLOT = 4;
    private static final int REQUIREMENTS_SLOT = 10;
    private static final int CLAIM_ACTIONS_SLOT = 12;
    private static final int FAIL_ACTIONS_SLOT = 14;
    private static final int PLACEHOLDERS_SLOT = 16;
    private static final int ICON_SLOT = 19;
    private static final int ITEMS_SLOT = 21;
    private static final int PREVIEW_SLOT = 23;
    private static final int LORE_SLOT = 25;
    private static final int BACK_SLOT = 36;
    private static final int DELETE_SLOT = 44;

    /** The list of kits: four rows of entries over a row of navigation. */
    private static final int PER_PAGE = 36;
    private static final int PREVIOUS_SLOT = 38;
    private static final int NEW_KIT_SLOT = 40;
    private static final int NEXT_SLOT = 42;
    private static final int CLOSE_SLOT = 44;

    /** The settings band, left to right across the fourth row. */
    private static final int[] SETTING_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    /** A minute a click, a day with shift. Both by the mouse alone. */
    private static final int COOLDOWN_STEP = 60;
    private static final int COOLDOWN_LEAP = 86400;

    private final Plugin plugin;
    private final Schedulers schedulers;
    private final KitService kits;
    private final KitEditor editor;
    private final Messages messages;
    private final Economy economy;
    private final ChatPrompts prompts;
    private final KitMenuSettings settings;
    private final KitRulesMenu rules;

    public KitEditMenu(Plugin plugin, Schedulers schedulers, KitService kits, KitEditor editor,
                       Messages messages, Economy economy, ChatPrompts prompts,
                       KitMenuSettings settings) {
        this.plugin = plugin;
        this.schedulers = schedulers;
        this.kits = kits;
        this.editor = editor;
        this.messages = messages;
        this.economy = economy;
        this.prompts = prompts;
        this.settings = settings;
        this.rules = new KitRulesMenu(editor, messages, prompts, settings, this::open);
    }

    /** The list of kits, which is where the editor starts. */
    public void openList(Player player) {
        openList(player, 0);
    }

    /**
     * One page of it.
     *
     * <p>Paged rather than cut off at whatever fits: a server with forty kits had the last
     * four silently missing from the screen, and there was no way to reach them except by
     * typing the name of a kit the screen would not show you.
     */
    private void openList(Player player, int page) {
        List<Kit> all = kits.all();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.min(Math.max(0, page), pages - 1);

        Menu menu = new Menu(player.getServer(), messages.render("menu.editor.list-title",
                "page", String.valueOf(current + 1),
                "pages", String.valueOf(pages)), ROWS);

        int first = current * PER_PAGE;
        for (int slot = 0; slot < PER_PAGE && first + slot < all.size(); slot++) {
            Kit kit = all.get(first + slot);
            menu.set(slot, MenuItems.of(kit.icon(),
                            messages.render("menu.editor.list-entry",
                                    "kit", KitEditor.titled(kit.name())),
                            messages.renderLines("menu.editor.list-entry-lore",
                                    "items", String.valueOf(kit.items().size()))),
                    clicker -> open(clicker, kit.name()));
        }

        if (current > 0) {
            menu.set(PREVIOUS_SLOT,
                    button(settings.back(), "menu.buttons.previous",
                            "menu.buttons.previous-lore", "page", String.valueOf(current)),
                    clicker -> openList(clicker, current - 1));
        }
        if (current < pages - 1) {
            menu.set(NEXT_SLOT,
                    button(settings.back(), "menu.buttons.next",
                            "menu.buttons.next-lore", "page", String.valueOf(current + 2)),
                    clicker -> openList(clicker, current + 1));
        }

        menu.set(NEW_KIT_SLOT, button(settings.newKit(), "menu.editor.new", "menu.editor.new-lore"),
                this::askNewKit);
        menu.set(CLOSE_SLOT, button(settings.close(), "menu.buttons.close", "menu.buttons.close-lore"),
                Player::closeInventory);
        menu.fill(0, menu.size(), MenuItems.filler(settings.filler()));
        menu.open(player);
    }

    /** One kit, with every setting on a button. */
    public void open(Player player, String name) {
        Kit kit = kits.find(name).orElse(null);
        if (kit == null) {
            messages.send(player, "kits.edit-unknown", "kit", name);
            return;
        }

        Menu menu = new Menu(player.getServer(),
                messages.render("menu.editor.title", "kit", KitEditor.titled(kit.name())), ROWS);

        menu.set(HEADER_SLOT, MenuItems.of(kit.icon(),
                        messages.render("menu.editor.header", "kit", KitEditor.titled(kit.name())),
                        messages.renderLines("menu.editor.header-lore", "display", kit.display())),
                clicker -> ask(clicker, kit, "menu.editor.ask-display", "display"));

        lists(menu, kit);

        menu.set(ICON_SLOT, button(settings.icon(), "menu.editor.icon", "menu.editor.icon-lore",
                        "icon", kit.icon().getType().name().toLowerCase(Locale.ROOT)),
                clicker -> openIcon(clicker, kit));

        menu.set(ITEMS_SLOT, button(settings.items(), "menu.editor.items", "menu.editor.items-lore",
                        "count", String.valueOf(kit.items().size())),
                clicker -> openItems(clicker, kit));

        menu.set(PREVIEW_SLOT,
                button(settings.preview(), "menu.editor.preview", "menu.editor.preview-lore"),
                clicker -> preview(clicker, kit));

        menu.set(LORE_SLOT, button(settings.lore(), "menu.editor.lore", "menu.editor.lore-lore",
                        "value", kit.lore().isEmpty()
                                ? messages.plain("kits.word-none")
                                : String.valueOf(kit.lore().size())),
                clicker -> ask(clicker, kit, "menu.editor.ask-lore", "lore"));

        settings(menu, kit);

        menu.set(BACK_SLOT, button(settings.back(), "menu.editor.back", "menu.editor.back-lore"),
                this::openList);
        menu.setPerClick(DELETE_SLOT,
                button(settings.delete(), "menu.editor.delete", "menu.editor.delete-lore"),
                (clicker, click) -> {
                    // Only on a shift click. A delete button one stray click away from a kit
                    // somebody spent an afternoon on is not a button, it is a trap.
                    if (click.isShiftClick()) {
                        editor.delete(kit.name());
                        messages.send(clicker, "kits.edit-deleted", "kit", kit.name());
                        openList(clicker);
                    } else {
                        messages.send(clicker, "menu.editor.delete-confirm", "kit", kit.name());
                    }
                });

        menu.fill(0, menu.size(), MenuItems.filler(settings.filler()));
        menu.open(player);
    }

    /**
     * The three lists, each on a screen of its own.
     *
     * <p>A kit can hold a dozen actions and half as many requirements, and putting them on
     * this screen would leave no room for anything else and no way to say which line was
     * being changed. Each one opens where its lines are one to an item.
     */
    private void lists(Menu menu, Kit kit) {
        menu.set(REQUIREMENTS_SLOT, button(settings.requirements(), "menu.editor.requirements",
                        "menu.editor.requirements-lore",
                        "count", String.valueOf(kit.requirements().size())),
                clicker -> rules.open(clicker, kit.name(), KitEditor.RuleList.REQUIREMENTS));

        menu.set(CLAIM_ACTIONS_SLOT, button(settings.claimActions(), "menu.editor.claimactions",
                        "menu.editor.claimactions-lore",
                        "count", String.valueOf(kit.claimActions().size())),
                clicker -> rules.open(clicker, kit.name(), KitEditor.RuleList.CLAIM_ACTIONS));

        menu.set(FAIL_ACTIONS_SLOT, button(settings.failActions(), "menu.editor.failactions",
                        "menu.editor.failactions-lore",
                        "count", String.valueOf(kit.failActions().size())),
                clicker -> rules.open(clicker, kit.name(), KitEditor.RuleList.FAIL_ACTIONS));

        menu.set(PLACEHOLDERS_SLOT, toggle(kit.placeholders(), "menu.editor.placeholders",
                        "menu.editor.placeholders-lore", word(kit.placeholders())),
                clicker -> set(clicker, kit, "placeholders", String.valueOf(!kit.placeholders())));
    }

    private void settings(Menu menu, Kit kit) {
        menu.setPerClick(SETTING_SLOTS[0],
                button(settings.cooldown(), "menu.editor.cooldown", "menu.editor.cooldown-lore",
                        "value", cooldownOf(kit)),
                (clicker, click) -> number(clicker, kit, click, "cooldown", "menu.editor.ask-cooldown",
                        kit.cooldownSeconds(), click.isShiftClick() ? COOLDOWN_LEAP : COOLDOWN_STEP));

        menu.setPerClick(SETTING_SLOTS[1],
                button(settings.maxClaims(), "menu.editor.maxclaims", "menu.editor.maxclaims-lore",
                        "value", claimsOf(kit)),
                (clicker, click) -> number(clicker, kit, click, "maxclaims",
                        "menu.editor.ask-maxclaims", kit.maxClaims(), click.isShiftClick() ? 10 : 1));

        menu.setPerClick(SETTING_SLOTS[2],
                button(settings.price(), "menu.editor.price", "menu.editor.price-lore",
                        "value", priceOf(kit)),
                (clicker, click) -> number(clicker, kit, click, "price", "menu.editor.ask-price",
                        (int) kit.price(), click.isShiftClick() ? 100 : 10));

        menu.set(SETTING_SLOTS[3], toggle(kit.oneTime(), "menu.editor.onetime",
                        "menu.editor.onetime-lore", word(kit.oneTime())),
                clicker -> set(clicker, kit, "onetime", String.valueOf(!kit.oneTime())));

        menu.set(SETTING_SLOTS[4], toggle(kit.autoArmor(), "menu.editor.autoarmor",
                        "menu.editor.autoarmor-lore", word(kit.autoArmor())),
                clicker -> set(clicker, kit, "autoarmor", String.valueOf(!kit.autoArmor())));

        menu.set(SETTING_SLOTS[5], toggle(kit.clearInventory(), "menu.editor.clearinventory",
                        "menu.editor.clearinventory-lore", word(kit.clearInventory())),
                clicker -> set(clicker, kit, "clearinventory", String.valueOf(!kit.clearInventory())));

        menu.set(SETTING_SLOTS[6],
                button(settings.permission(), "menu.editor.permission", "menu.editor.permission-lore",
                        "value", kit.permission().isEmpty()
                                ? messages.plain("kits.word-everyone")
                                : kit.permission()),
                clicker -> ask(clicker, kit, "menu.editor.ask-permission", "permission"));
    }

    /**
     * A number button: nudged with the mouse, or typed when the number is a long way off.
     *
     * <p>Left raises, right lowers, shift makes the step a big one, and dropping (Q) asks for
     * the exact figure. Q rather than the middle button because the middle button only
     * reaches the server in creative mode, which would leave the one gesture that sets a
     * cooldown of 86400 working for half the people who need it.
     *
     * <p>Nothing goes below zero: a negative cooldown is not something anybody wants and
     * would only have to be undone.
     */
    private void number(Player player, Kit kit, ClickType click, String setting, String question,
                        int current, int by) {
        if (asksForIt(click)) {
            ask(player, kit, question, setting);
            return;
        }
        set(player, kit, setting, String.valueOf(Math.max(0, click.isRightClick()
                ? current - by
                : current + by)));
    }

    private static boolean asksForIt(ClickType click) {
        return click == ClickType.DROP || click == ClickType.CONTROL_DROP
                || click == ClickType.MIDDLE;
    }

    /**
     * Asks in chat and comes straight back to the kit.
     *
     * <p>Reopening is what makes it feel like one screen rather than a detour: whatever was
     * typed is already saved and already on the button by the time it is seen again.
     */
    private void ask(Player player, Kit kit, String question, String setting) {
        prompts.ask(player, messages.render(question, "kit", kit.name()),
                typed -> {
                    // "none" is how a text setting is emptied, since an empty chat line is
                    // not something a player can send.
                    String value = typed.equalsIgnoreCase("none") ? "" : typed;
                    if (!editor.set(kit.name(), setting, value)) {
                        messages.send(player, "error.storage");
                    }
                    open(player, kit.name());
                },
                () -> open(player, kit.name()));
    }

    private void askNewKit(Player player) {
        prompts.ask(player, messages.render("menu.editor.ask-name"),
                typed -> {
                    String name = ChatPrompts.normalise(typed);
                    if (!name.matches("[a-z0-9_-]{1,32}")) {
                        messages.send(player, "kits.edit-bad-name");
                        openList(player);
                        return;
                    }
                    if (kits.find(name).isPresent()) {
                        messages.send(player, "kits.edit-exists", "kit", name);
                        openList(player);
                        return;
                    }
                    if (!editor.create(name, player)) {
                        messages.send(player, "error.storage");
                        openList(player);
                        return;
                    }
                    messages.send(player, "kits.edit-created", "kit", name);
                    open(player, name);
                },
                () -> openList(player));
    }

    private void set(Player player, Kit kit, String setting, String value) {
        if (editor.set(kit.name(), setting, value)) {
            // Reopened rather than repainted: the file has just been read again, so the kit
            // held here is already out of date and every button on it with it.
            open(player, kit.name());
            return;
        }
        messages.send(player, "error.storage");
    }

    /**
     * One slot for the icon, and one slot that means one.
     *
     * <p>Click an item in your inventory and it takes the slot, whatever was already on it. Having
     * to take the old icon off before the new one would go on was a step that existed for no
     * reason other than that the same screen also lays out sixty items at once; here the
     * slot is full by definition, so filling it is replacing it.
     *
     * <p>The whole item is kept rather than its kind alone, so a kit shown as a named,
     * enchanted sword stays that sword.
     */
    private void openIcon(Player player, Kit kit) {
        PaletteMenu slot = new PaletteMenu(player.getServer(),
                messages.render("menu.editor.icon-title", "kit", KitEditor.titled(kit.name())), 1, 1,
                (closer, contents) -> {
                    ItemStack chosen = firstOf(contents);
                    if (chosen == null) {
                        later(closer, () -> open(closer, kit.name()));
                        return;
                    }
                    if (editor.setIcon(kit.name(), chosen)) {
                        messages.send(closer, "menu.editor.icon-set", "kit", kit.name(),
                                "icon", chosen.getType().name().toLowerCase(Locale.ROOT));
                    } else {
                        messages.send(closer, "error.storage");
                    }
                    later(closer, () -> open(closer, kit.name()));
                });

        slot.fill(new ItemStack[]{kit.icon().clone()});
        slot.surround(MenuItems.filler(settings.filler()));
        slot.open(player);
    }

    /**
     * The item editor.
     *
     * <p>Starts holding what the kit holds and works the same way: click to copy on, click to
     * take off. Whatever is on it when the screen closes becomes the kit, and nothing has
     * left the player's inventory to get there.
     */
    private void openItems(Player player, Kit kit) {
        PaletteMenu items = new PaletteMenu(player.getServer(),
                messages.render("menu.editor.items-title", "kit", KitEditor.titled(kit.name())),
                settings.itemRows(),
                (closer, contents) -> {
                    KitEditor.Result result = editor.setItems(kit.name(), contents);
                    if (!result.saved()) {
                        messages.send(closer, "error.storage");
                        return;
                    }
                    messages.send(closer, "kits.edit-items",
                            "kit", kit.name(), "count", String.valueOf(result.count()));
                    if (result.simplified() > 0) {
                        messages.send(closer, "kits.edit-items-simplified",
                                "count", String.valueOf(result.simplified()));
                    }
                    later(closer, () -> open(closer, kit.name()));
                });

        items.fill(kit.contents().toArray(new ItemStack[0]));
        items.open(player);
    }

    /** Read-only, so a kit can be looked at the way a player would see it. */
    private void preview(Player player, Kit kit) {
        List<ItemStack> contents = kit.contents();
        int rows = Math.max(1, Math.min(6, (contents.size() + 8) / 9));
        Menu menu = new Menu(player.getServer(),
                messages.render("menu.editor.preview-title", "kit", KitEditor.titled(kit.name())), rows);

        for (int slot = 0; slot < Math.min(contents.size(), menu.size()); slot++) {
            menu.set(slot, contents.get(slot));
        }
        menu.open(player);
    }

    /**
     * Opens a screen on the next tick rather than now.
     *
     * <p>Only ever called from a close handler, and it has to be. Opening an inventory while
     * the server is in the middle of closing one makes it close the one still on screen,
     * which fires another close, which opens again: a loop that ends when the server does.
     * A tick later the close is finished and there is nothing left to fall back into.
     */
    private void later(Player player, Runnable open) {
        if (!plugin.isEnabled()) {
            // A close during shutdown. The change is already saved; there is nobody left
            // to show it to, and asking a stopping server to schedule anything throws.
            return;
        }
        schedulers.entity(player, () -> {
            if (player.isOnline()) {
                open.run();
            }
        });
    }

    private static @Nullable ItemStack firstOf(ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                return item.clone();
            }
        }
        return null;
    }

    private ItemStack button(Material material, String nameKey, String loreKey,
                             String... placeholders) {
        return MenuItems.of(material, messages.render(nameKey),
                messages.renderLines(loreKey, placeholders));
    }

    /** Green for on, grey for off, so the state reads before the text does. */
    private ItemStack toggle(boolean on, String nameKey, String loreKey, String value) {
        return MenuItems.of(on ? settings.enabled() : settings.disabled(),
                messages.render(nameKey), messages.renderLines(loreKey, "value", value));
    }

    private String cooldownOf(Kit kit) {
        return kit.cooldownSeconds() == 0
                ? messages.plain("kits.word-none")
                : Durations.format(TimeUnit.SECONDS.toMillis(kit.cooldownSeconds()));
    }

    private String claimsOf(Kit kit) {
        return kit.maxClaims() == 0
                ? messages.plain("kits.word-unlimited")
                : String.valueOf(kit.maxClaims());
    }

    private String priceOf(Kit kit) {
        return kit.price() <= 0 ? messages.plain("kits.word-free") : economy.format(kit.price());
    }

    private String word(boolean value) {
        return messages.plain(value ? "kits.word-true" : "kits.word-false");
    }
}
