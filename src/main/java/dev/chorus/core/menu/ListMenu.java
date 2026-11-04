package dev.chorus.core.menu;

import dev.chorus.core.locale.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** A paginated menu of clickable entries, used by /homes and /warps. */
public final class ListMenu {

    /**
     * One entry of the menu, with a full item as its icon.
     */
    public record Entry(ItemStack icon, Component display, List<Component> lore,
                        Consumer<Player> action) {

        /** For the callers that only have a material to show. */
        public Entry(Material icon, Component display, List<Component> lore,
                     Consumer<Player> action) {
            this(new ItemStack(icon), display, lore, action);
        }
    }

    private ListMenu() {
    }

    public static void open(Player viewer, Messages messages, MenuSettings settings,
                            String titleKey, List<Entry> entries, int page) {
        open(viewer, messages, settings, titleKey, entries, page, null);
    }

    /**
     * The same with a way back, for a menu that was opened from another one.
     *
     * @param back        what the back button does, or null for a menu that is the first one.
     * @param titleValues anything else the title names, beyond the page it is on.
     */
    public static void open(Player viewer, Messages messages, MenuSettings settings,
                            String titleKey, List<Entry> entries, int page,
                            @Nullable Consumer<Player> back, String... titleValues) {
        open(viewer, messages, settings, titleKey, entries, page, back, null, titleValues);
    }

    /**
     * The same with one button of the menu's own, such as the filter on the backup list.
     *
     * @param control what goes in the free spot on the navigation row, or null.
     */
    public static void open(Player viewer, Messages messages, MenuSettings settings,
                            String titleKey, List<Entry> entries, int page,
                            @Nullable Consumer<Player> back, @Nullable Entry control,
                            String... titleValues) {
        int perPage = settings.perPage();
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        int current = Math.min(Math.max(0, page), pages - 1);

        String[] values = new String[titleValues.length + 4];
        values[0] = "page";
        values[1] = String.valueOf(current + 1);
        values[2] = "pages";
        values[3] = String.valueOf(pages);
        System.arraycopy(titleValues, 0, values, 4, titleValues.length);

        Menu menu = new Menu(viewer.getServer(),
                messages.render(viewer, titleKey, values), settings.rows());

        int first = current * perPage;
        for (int index = 0; index < perPage && first + index < entries.size(); index++) {
            Entry entry = entries.get(first + index);
            menu.set(index, MenuItems.of(entry.icon(), entry.display(), entry.lore()),
                    entry.action());
        }

        if (current > 0) {
            menu.set(settings.previousSlot(), button(viewer, messages, settings.previousPage(),
                            "menu.buttons.previous", "menu.buttons.previous-lore", current),
                    clicker -> open(clicker, messages, settings, titleKey, entries,
                            current - 1, back, control, titleValues));
        }
        if (current < pages - 1) {
            menu.set(settings.nextSlot(), button(viewer, messages, settings.nextPage(),
                            "menu.buttons.next", "menu.buttons.next-lore", current + 2),
                    clicker -> open(clicker, messages, settings, titleKey, entries,
                            current + 1, back, control, titleValues));
        }
        if (back != null) {
            menu.set(settings.backSlot(), MenuItems.of(settings.previousPage(),
                            messages.render(viewer, "menu.buttons.back"),
                            List.of(messages.render(viewer, "menu.buttons.back-lore"))),
                    back);
        }
        if (control != null) {
            menu.set(settings.controlSlot(),
                    MenuItems.of(control.icon(), control.display(), control.lore()),
                    control.action());
        }
        menu.set(settings.closeSlot(), MenuItems.of(settings.close(),
                        messages.render(viewer, "menu.buttons.close"),
                        List.of(messages.render(viewer, "menu.buttons.close-lore"))),
                Player::closeInventory);

        menu.fill(0, settings.previousSlot(), MenuItems.filler(settings.filler()));
        menu.fill(settings.previousSlot(), settings.size(),
                MenuItems.filler(settings.navigationFiller()));
        menu.open(viewer);
    }

    private static org.bukkit.inventory.ItemStack button(Player viewer, Messages messages,
                                                         Material material, String nameKey,
                                                         String loreKey, int page) {
        return MenuItems.of(material, messages.render(viewer, nameKey),
                List.of(messages.render(viewer, loreKey, "page", String.valueOf(page))));
    }
}
