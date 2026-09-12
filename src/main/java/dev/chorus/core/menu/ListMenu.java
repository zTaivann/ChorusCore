package dev.chorus.core.menu;

import dev.chorus.core.locale.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * A paginated grid of clickable entries, used by /homes and /warps.
 *
 * <p>The bottom row is always navigation with its own filler, so the number of entries a
 * page holds never changes and the strip reads as a separate band.
 */
public final class ListMenu {

    /**
     * One line of the grid. The icon is a whole item rather than a bare material, so an
     * entry can carry whatever was chosen for it: a named sword stays a named sword.
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
     * The same with a way back, for a screen that was opened from another one.
     *
     * @param back        what the back button does, or null for a screen that is the first one.
     * @param titleValues anything else the title names, beyond the page it is on.
     */
    public static void open(Player viewer, Messages messages, MenuSettings settings,
                            String titleKey, List<Entry> entries, int page,
                            @Nullable Consumer<Player> back, String... titleValues) {
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
                messages.render(titleKey, values), settings.rows());

        int first = current * perPage;
        for (int index = 0; index < perPage && first + index < entries.size(); index++) {
            Entry entry = entries.get(first + index);
            menu.set(index, MenuItems.of(entry.icon(), entry.display(), entry.lore()),
                    entry.action());
        }

        if (current > 0) {
            menu.set(settings.previousSlot(), button(messages, settings.previousPage(),
                            "menu.buttons.previous", "menu.buttons.previous-lore", current),
                    clicker -> open(clicker, messages, settings, titleKey, entries,
                            current - 1, back, titleValues));
        }
        if (current < pages - 1) {
            menu.set(settings.nextSlot(), button(messages, settings.nextPage(),
                            "menu.buttons.next", "menu.buttons.next-lore", current + 2),
                    clicker -> open(clicker, messages, settings, titleKey, entries,
                            current + 1, back, titleValues));
        }
        if (back != null) {
            menu.set(settings.backSlot(), MenuItems.of(settings.previousPage(),
                            messages.render("menu.buttons.back"),
                            List.of(messages.render("menu.buttons.back-lore"))),
                    back);
        }
        menu.set(settings.closeSlot(), MenuItems.of(settings.close(),
                        messages.render("menu.buttons.close"),
                        List.of(messages.render("menu.buttons.close-lore"))),
                Player::closeInventory);

        menu.fill(0, settings.previousSlot(), MenuItems.filler(settings.filler()));
        menu.fill(settings.previousSlot(), settings.size(),
                MenuItems.filler(settings.navigationFiller()));
        menu.open(viewer);
    }

    private static org.bukkit.inventory.ItemStack button(Messages messages, Material material,
                                                         String nameKey, String loreKey, int page) {
        return MenuItems.of(material, messages.render(nameKey),
                List.of(messages.render(loreKey, "page", String.valueOf(page))));
    }
}
