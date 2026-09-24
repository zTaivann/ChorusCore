package dev.chorus.core.backup;

import dev.chorus.core.command.Durations;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.menu.ListMenu;
import dev.chorus.core.menu.Menu;
import dev.chorus.core.menu.MenuItems;
import dev.chorus.core.menu.MenuSettings;
import dev.chorus.core.platform.Schedulers;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The backups as screens: the list, one of them laid out, and its ender chest. */
public final class BackupMenu {

    private static final String RESTORE_PERMISSION = "chorus.items.restore";

    /** The layout of a player's inventory, which is the order the slots are stored in. */
    private static final int STORAGE = 36;
    private static final int OFFHAND = 40;
    private static final int VIEW_ROWS = 6;
    private static final int ENDER_ROWS = 4;

    private static final Map<BackupReason, Material> ICONS = new EnumMap<>(BackupReason.class);

    static {
        ICONS.put(BackupReason.DEATH, Material.SKELETON_SKULL);
        ICONS.put(BackupReason.JOIN, Material.LIME_BANNER);
        ICONS.put(BackupReason.WORLD, Material.END_PORTAL_FRAME);
        ICONS.put(BackupReason.QUIT, Material.OAK_DOOR);
        ICONS.put(BackupReason.CLEAR, Material.BUCKET);
        ICONS.put(BackupReason.KIT, Material.CHEST);
        ICONS.put(BackupReason.RESTORE, Material.CLOCK);
        ICONS.put(BackupReason.MANUAL, Material.PAPER);
    }

    private final Messages messages;
    private final InventoryBackups backups;
    private final MenuSettings settings;
    private final Schedulers schedulers;

    public BackupMenu(Messages messages, InventoryBackups backups, MenuSettings settings,
                      Schedulers schedulers) {
        this.messages = messages;
        this.backups = backups;
        this.settings = settings;
        this.schedulers = schedulers;
    }

    /** Everything saved for one player, newest first. */
    public void openList(Player viewer, Player subject, List<InventorySnapshot> found) {
        openList(viewer, subject, found, null);
    }

    /** The same list showing only one kind of copy. */
    public void openList(Player viewer, Player subject, List<InventorySnapshot> found,
                         @Nullable BackupReason only) {
        List<ListMenu.Entry> entries = new ArrayList<>(found.size());
        for (InventorySnapshot snapshot : found) {
            BackupReason reason = BackupReason.of(first(snapshot.reason()));
            if (only != null && reason != only) {
                continue;
            }
            entries.add(new ListMenu.Entry(
                    ICONS.getOrDefault(reason, Material.PAPER),
                    messages.render("menu.backups.entry",
                            "reason", named(reason, detail(snapshot.reason())),
                            "ago", ago(snapshot)),
                    lore(snapshot),
                    clicker -> reopen(clicker, subject, snapshot.id())));
        }

        ListMenu.open(viewer, messages, settings, "menu.backups.title", entries, 0, null,
                filterButton(subject, found, only), "player", subject.getName());
    }

    /** Cycles to the next reason that has anything under it, then back to showing them all. */
    private ListMenu.Entry filterButton(Player subject, List<InventorySnapshot> found,
                                        @Nullable BackupReason only) {
        BackupReason next = nextWith(found, only);
        return new ListMenu.Entry(
                only == null ? Material.HOPPER : ICONS.getOrDefault(only, Material.PAPER),
                messages.render("menu.backups.filter",
                        "filter", only == null
                                ? messages.plain("menu.backups.filter-all")
                                : named(only, "")),
                messages.renderLines("menu.backups.filter-lore",
                        "next", next == null
                                ? messages.plain("menu.backups.filter-all")
                                : named(next, "")),
                clicker -> openList(clicker, subject, found, next));
    }

    private static @Nullable BackupReason nextWith(List<InventorySnapshot> found,
                                                   @Nullable BackupReason from) {
        List<BackupReason> present = new ArrayList<>();
        for (InventorySnapshot snapshot : found) {
            BackupReason reason = BackupReason.of(first(snapshot.reason()));
            if (!present.contains(reason)) {
                present.add(reason);
            }
        }
        if (present.isEmpty()) {
            return null;
        }
        int place = from == null ? -1 : present.indexOf(from);
        return place + 1 >= present.size() ? null : present.get(place + 1);
    }

    /** Read again on the way in, so a screen left open never shows something already gone. */
    private void reopen(Player viewer, Player subject, long id) {
        backups.find(id).whenComplete((snapshot, failure) -> {
            if (failure != null || snapshot == null) {
                messages.send(viewer, "items.restore-gone");
                return;
            }
            schedulers.withEntity(viewer, () -> openInventory(viewer, subject, snapshot));
        });
    }

    /** One backup, laid out the way the player was carrying it. */
    public void openInventory(Player viewer, Player subject, InventorySnapshot snapshot) {
        ItemStack[] contents = InventoryCodec.decode(snapshot.contents());
        if (contents == null) {
            messages.send(viewer, "items.restore-unreadable");
            return;
        }

        Menu menu = new Menu(viewer.getServer(), messages.render("menu.backups.view-title",
                "player", subject.getName(), "ago", ago(snapshot)), VIEW_ROWS);
        for (int slot = 0; slot < Math.min(contents.length, OFFHAND + 1); slot++) {
            menu.set(slot, contents[slot]);
        }

        int nav = menu.size() - 9;
        menu.set(nav, button(settings.previousPage(), "menu.buttons.back", "menu.buttons.back-lore"),
                clicker -> back(clicker, subject));
        if (snapshot.hasEnderChest()) {
            menu.set(nav + 2, button(Material.ENDER_CHEST,
                            "menu.backups.ender", "menu.backups.ender-lore"),
                    clicker -> openEnderChest(clicker, subject, snapshot));
        }
        restoreButtons(menu, nav, viewer, subject, snapshot);
        menu.set(nav + 8, button(settings.close(), "menu.buttons.close", "menu.buttons.close-lore"),
                Player::closeInventory);

        menu.fill(STORAGE + 5, nav, MenuItems.filler(settings.navigationFiller()));
        menu.fill(nav, menu.size(), MenuItems.filler(settings.navigationFiller()));
        menu.open(viewer);
    }

    /** The ender chest from the same backup. */
    public void openEnderChest(Player viewer, Player subject, InventorySnapshot snapshot) {
        ItemStack[] contents = InventoryCodec.decode(snapshot.enderChest());
        if (contents == null) {
            messages.send(viewer, "items.restore-unreadable");
            return;
        }

        Menu menu = new Menu(viewer.getServer(), messages.render("menu.backups.ender-title",
                "player", subject.getName(), "ago", ago(snapshot)), ENDER_ROWS);
        for (int slot = 0; slot < Math.min(contents.length, menu.size() - 9); slot++) {
            menu.set(slot, contents[slot]);
        }

        int nav = menu.size() - 9;
        menu.set(nav, button(settings.previousPage(), "menu.buttons.back", "menu.buttons.back-lore"),
                clicker -> openInventory(clicker, subject, snapshot));
        if (viewer.hasPermission(RESTORE_PERMISSION)) {
            menu.set(nav + 4, button(Material.ENDER_CHEST,
                            "menu.backups.restore-ender", "menu.backups.restore-ender-lore"),
                    clicker -> restore(clicker, subject, snapshot,
                            EnumSet.of(InventoryBackups.Part.ENDER_CHEST)));
        }
        menu.set(nav + 8, button(settings.close(), "menu.buttons.close", "menu.buttons.close-lore"),
                Player::closeInventory);

        menu.fill(nav, menu.size(), MenuItems.filler(settings.navigationFiller()));
        menu.open(viewer);
    }

    /** The two ways to put a backup back, for anybody allowed to. */
    private void restoreButtons(Menu menu, int nav, Player viewer, Player subject,
                                InventorySnapshot snapshot) {
        if (!viewer.hasPermission(RESTORE_PERMISSION)) {
            return;
        }
        menu.set(nav + 4, button(Material.LIME_DYE,
                        "menu.backups.restore", "menu.backups.restore-lore"),
                clicker -> restore(clicker, subject, snapshot,
                        EnumSet.of(InventoryBackups.Part.INVENTORY)));
        menu.set(nav + 5, button(Material.CHEST,
                        "menu.backups.hand-over", "menu.backups.hand-over-lore"),
                clicker -> handOver(clicker, subject, snapshot));
        menu.set(nav + 6, button(Material.EMERALD,
                        "menu.backups.restore-all", "menu.backups.restore-all-lore"),
                clicker -> restore(clicker, subject, snapshot,
                        EnumSet.allOf(InventoryBackups.Part.class)));
    }

    /** Gives the saved items back on top of whatever they are carrying now. */
    private void handOver(Player viewer, Player subject, InventorySnapshot snapshot) {
        viewer.closeInventory();
        if (!subject.isOnline()) {
            messages.send(viewer, "error.player-not-found", "player", subject.getName());
            return;
        }

        schedulers.entity(subject, () -> {
            int handed = backups.deliver(subject, snapshot, true);
            if (handed < 0) {
                messages.send(viewer, "items.restore-unreadable");
                return;
            }
            messages.send(viewer, "items.handed-over",
                    "player", subject.getName(), "count", String.valueOf(handed));
            if (!viewer.equals(subject)) {
                messages.send(subject, "items.hand-received", "count", String.valueOf(handed));
            }
        });
    }

    private void restore(Player viewer, Player subject, InventorySnapshot snapshot,
                         Set<InventoryBackups.Part> parts) {
        viewer.closeInventory();
        if (!subject.isOnline()) {
            messages.send(viewer, "error.player-not-found", "player", subject.getName());
            return;
        }

        schedulers.entity(subject, () -> {
            Set<InventoryBackups.Part> done =
                    backups.restore(subject, snapshot, parts, viewer.getName());
            if (done.isEmpty()) {
                messages.send(viewer, "items.restore-unreadable");
                return;
            }

            messages.send(viewer, "items.restored",
                    "player", subject.getName(), "ago", ago(snapshot));
            if (!viewer.equals(subject)) {
                messages.send(subject, "items.restore-received");
            }
        });
    }

    private void back(Player viewer, Player subject) {
        backups.find(subject.getUniqueId()).whenComplete((found, failure) ->
                schedulers.withEntity(viewer, () -> {
                    if (failure != null || found.isEmpty()) {
                        viewer.closeInventory();
                        return;
                    }
                    openList(viewer, subject, found);
                }));
    }

    private List<Component> lore(InventorySnapshot snapshot) {
        String key = snapshot.killer() != null
                ? "menu.backups.entry-killed-lore"
                : snapshot.cause() != null
                        ? "menu.backups.entry-death-lore"
                        : "menu.backups.entry-lore";

        return messages.renderLines(key,
                "killer", snapshot.killer() == null ? "" : snapshot.killer(),
                "cause", snapshot.cause() == null ? "" : snapshot.cause(),
                "actor", snapshot.actor(),
                "world", snapshot.world(),
                "x", String.valueOf(snapshot.x()),
                "y", String.valueOf(snapshot.y()),
                "z", String.valueOf(snapshot.z()),
                "level", String.valueOf(snapshot.level()),
                "health", String.valueOf(Math.round(snapshot.health())),
                "food", String.valueOf(snapshot.food()));
    }

    private ItemStack button(Material material, String nameKey, String loreKey) {
        return MenuItems.of(material, messages.render(nameKey),
                messages.renderLines(loreKey));
    }

    private static String ago(InventorySnapshot snapshot) {
        return Durations.format(System.currentTimeMillis() - snapshot.takenAt());
    }

    /** The reason without whatever was appended to it, such as the kit's name. */
    private static String first(String reason) {
        int space = reason.indexOf(' ');
        return space < 0 ? reason : reason.substring(0, space);
    }

    /** And only what was appended: the kit, or the world they left. */
    private static String detail(String reason) {
        int space = reason.indexOf(' ');
        return space < 0 ? "" : reason.substring(space + 1);
    }

    /** What the reason is called on screen. */
    private String named(BackupReason reason, String detail) {
        String name = messages.plain(switch (reason) {
            case DEATH -> "menu.backups.reason-death";
            case JOIN -> "menu.backups.reason-join";
            case QUIT -> "menu.backups.reason-quit";
            case WORLD -> "menu.backups.reason-world";
            case CLEAR -> "menu.backups.reason-clear";
            case KIT -> "menu.backups.reason-kit";
            case RESTORE -> "menu.backups.reason-restore";
            case MANUAL -> "menu.backups.reason-manual";
        });
        return detail.isEmpty() ? name : name + " " + detail;
    }
}
