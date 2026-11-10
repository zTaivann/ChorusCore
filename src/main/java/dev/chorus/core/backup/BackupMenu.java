package dev.chorus.core.backup;

import dev.chorus.commons.command.Durations;
import dev.chorus.commons.menu.Menu;
import dev.chorus.commons.menu.MenuSettings;
import dev.chorus.commons.platform.Schedulers;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.menu.ListMenu;
import dev.chorus.core.menu.MenuItems;
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

/** The backup menus: the list, one backup laid out, and its ender chest. */
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
    public void openList(Player viewer, Player subject, List<InventoryBackup> found) {
        openList(viewer, subject, found, null);
    }

    /** The same list showing only one kind of backup. */
    public void openList(Player viewer, Player subject, List<InventoryBackup> found,
                         @Nullable BackupReason only) {
        List<ListMenu.Entry> entries = new ArrayList<>(found.size());
        for (InventoryBackup backup : found) {
            BackupReason reason = BackupReason.of(backup.reason());
            if (only != null && reason != only) {
                continue;
            }
            entries.add(new ListMenu.Entry(
                    ICONS.getOrDefault(reason, Material.PAPER),
                    messages.render(viewer, "menu.backups.entry",
                            "reason", BackupReason.describe(backup.reason(), messages, viewer),
                            "ago", ago(backup)),
                    lore(viewer, backup),
                    clicker -> reopen(clicker, subject, backup.id())));
        }

        ListMenu.open(viewer, messages, settings, "menu.backups.title", entries, 0, null,
                filterButton(viewer, subject, found, only), "player", subject.getName());
    }

    /** Cycles to the next reason that has anything under it, then back to showing them all. */
    private ListMenu.Entry filterButton(Player viewer, Player subject,
                                        List<InventoryBackup> found,
                                        @Nullable BackupReason only) {
        BackupReason next = nextWith(found, only);
        return new ListMenu.Entry(
                only == null ? Material.HOPPER : ICONS.getOrDefault(only, Material.PAPER),
                messages.render(viewer, "menu.backups.filter",
                        "filter", only == null
                                ? messages.plain(viewer, "menu.backups.filter-all")
                                : messages.plain(viewer, only.messageKey())),
                messages.renderLines(viewer, "menu.backups.filter-lore",
                        "next", next == null
                                ? messages.plain(viewer, "menu.backups.filter-all")
                                : messages.plain(viewer, next.messageKey())),
                clicker -> openList(clicker, subject, found, next));
    }

    private static @Nullable BackupReason nextWith(List<InventoryBackup> found,
                                                   @Nullable BackupReason from) {
        List<BackupReason> present = new ArrayList<>();
        for (InventoryBackup backup : found) {
            BackupReason reason = BackupReason.of(backup.reason());
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

    /** Read again each time it opens. */
    private void reopen(Player viewer, Player subject, long id) {
        backups.find(id).whenComplete((backup, failure) -> {
            if (failure != null || backup == null) {
                messages.send(viewer, "items.restore-gone");
                return;
            }
            schedulers.withEntity(viewer, () -> openInventory(viewer, subject, backup));
        });
    }

    /** One backup, laid out the way the player was carrying it. */
    public void openInventory(Player viewer, Player subject, InventoryBackup backup) {
        ItemStack[] contents = InventoryCodec.decode(backup.contents());
        if (contents == null) {
            messages.send(viewer, "items.restore-unreadable");
            return;
        }

        Menu menu = new Menu(viewer.getServer(), messages.render(viewer, "menu.backups.view-title",
                "player", subject.getName(), "ago", ago(backup)), VIEW_ROWS);
        for (int slot = 0; slot < Math.min(contents.length, OFFHAND + 1); slot++) {
            menu.set(slot, contents[slot]);
        }

        int nav = menu.size() - 9;
        menu.set(nav, button(viewer, settings.previousPage(), "menu.buttons.back", "menu.buttons.back-lore"),
                clicker -> back(clicker, subject));
        if (backup.hasEnderChest()) {
            menu.set(nav + 2, button(viewer, Material.ENDER_CHEST,
                            "menu.backups.ender", "menu.backups.ender-lore"),
                    clicker -> openEnderChest(clicker, subject, backup));
        }
        restoreButtons(menu, nav, viewer, subject, backup);
        menu.set(nav + 8, button(viewer, settings.close(), "menu.buttons.close", "menu.buttons.close-lore"),
                Player::closeInventory);

        menu.fill(STORAGE + 5, nav, MenuItems.filler(settings.navigationFiller()));
        menu.fill(nav, menu.size(), MenuItems.filler(settings.navigationFiller()));
        menu.open(viewer);
    }

    /** The ender chest from the same backup. */
    public void openEnderChest(Player viewer, Player subject, InventoryBackup backup) {
        ItemStack[] contents = InventoryCodec.decode(backup.enderChest());
        if (contents == null) {
            messages.send(viewer, "items.restore-unreadable");
            return;
        }

        Menu menu = new Menu(viewer.getServer(), messages.render(viewer, "menu.backups.ender-title",
                "player", subject.getName(), "ago", ago(backup)), ENDER_ROWS);
        for (int slot = 0; slot < Math.min(contents.length, menu.size() - 9); slot++) {
            menu.set(slot, contents[slot]);
        }

        int nav = menu.size() - 9;
        menu.set(nav, button(viewer, settings.previousPage(), "menu.buttons.back", "menu.buttons.back-lore"),
                clicker -> openInventory(clicker, subject, backup));
        if (viewer.hasPermission(RESTORE_PERMISSION)) {
            menu.set(nav + 4, button(viewer, Material.ENDER_CHEST,
                            "menu.backups.restore-ender", "menu.backups.restore-ender-lore"),
                    clicker -> restore(clicker, subject, backup,
                            EnumSet.of(InventoryBackups.Part.ENDER_CHEST)));
        }
        menu.set(nav + 8, button(viewer, settings.close(), "menu.buttons.close", "menu.buttons.close-lore"),
                Player::closeInventory);

        menu.fill(nav, menu.size(), MenuItems.filler(settings.navigationFiller()));
        menu.open(viewer);
    }

    /** The two ways to put a backup back, for anybody allowed to. */
    private void restoreButtons(Menu menu, int nav, Player viewer, Player subject,
                                InventoryBackup backup) {
        if (!viewer.hasPermission(RESTORE_PERMISSION)) {
            return;
        }
        menu.set(nav + 4, button(viewer, Material.LIME_DYE,
                        "menu.backups.restore", "menu.backups.restore-lore"),
                clicker -> restore(clicker, subject, backup,
                        EnumSet.of(InventoryBackups.Part.INVENTORY)));
        menu.set(nav + 5, button(viewer, Material.CHEST,
                        "menu.backups.hand-over", "menu.backups.hand-over-lore"),
                clicker -> handOver(clicker, subject, backup));
        menu.set(nav + 6, button(viewer, Material.EMERALD,
                        "menu.backups.restore-all", "menu.backups.restore-all-lore"),
                clicker -> restore(clicker, subject, backup,
                        EnumSet.allOf(InventoryBackups.Part.class)));
    }

    /** Gives the saved items back on top of whatever they are carrying now. */
    private void handOver(Player viewer, Player subject, InventoryBackup backup) {
        viewer.closeInventory();
        if (!subject.isOnline()) {
            messages.send(viewer, "error.player-not-found", "player", subject.getName());
            return;
        }

        schedulers.entity(subject, () -> {
            int handed = backups.deliver(subject, backup, true);
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

    private void restore(Player viewer, Player subject, InventoryBackup backup,
                         Set<InventoryBackups.Part> parts) {
        viewer.closeInventory();
        if (!subject.isOnline()) {
            messages.send(viewer, "error.player-not-found", "player", subject.getName());
            return;
        }

        schedulers.entity(subject, () -> {
            Set<InventoryBackups.Part> done =
                    backups.restore(subject, backup, parts, viewer.getName());
            if (done.isEmpty()) {
                messages.send(viewer, "items.restore-unreadable");
                return;
            }

            messages.send(viewer, "items.restored",
                    "player", subject.getName(), "ago", ago(backup));
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

    private List<Component> lore(Player viewer, InventoryBackup backup) {
        String key = backup.killer() != null
                ? "menu.backups.entry-killed-lore"
                : backup.cause() != null
                        ? "menu.backups.entry-death-lore"
                        : "menu.backups.entry-lore";

        return messages.renderLines(viewer, key,
                "killer", backup.killer() == null ? "" : backup.killer(),
                "cause", backup.cause() == null ? "" : backup.cause(),
                "actor", backup.actor(),
                "world", backup.world(),
                "x", String.valueOf(backup.x()),
                "y", String.valueOf(backup.y()),
                "z", String.valueOf(backup.z()),
                "level", String.valueOf(backup.level()),
                "health", String.valueOf(Math.round(backup.health())),
                "food", String.valueOf(backup.food()));
    }

    private ItemStack button(Player viewer, Material material, String nameKey, String loreKey) {
        return MenuItems.of(material, messages.render(viewer, nameKey),
                messages.renderLines(viewer, loreKey));
    }

    private static String ago(InventoryBackup backup) {
        return Durations.format(System.currentTimeMillis() - backup.takenAt());
    }
}
