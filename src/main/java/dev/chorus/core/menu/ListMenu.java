package dev.chorus.core.menu;

import dev.chorus.core.locale.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * A paginated grid of clickable entries, used by /homes and /warps.
 *
 * <p>The bottom row is always navigation with its own filler, so the number of entries a
 * page holds never changes and the strip reads as a separate band.
 */
public final class ListMenu {

    public record Entry(Material icon, Component display, List<Component> lore,
                        Consumer<Player> action) {
    }

    private ListMenu() {
    }

    public static void open(Player viewer, Messages messages, MenuSettings settings,
                            String titleKey, List<Entry> entries, int page) {
        int perPage = settings.perPage();
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        int current = Math.min(Math.max(0, page), pages - 1);

        Menu menu = new Menu(viewer.getServer(), messages.render(titleKey,
                "page", String.valueOf(current + 1),
                "pages", String.valueOf(pages)), settings.rows());

        int first = current * perPage;
        for (int index = 0; index < perPage && first + index < entries.size(); index++) {
            Entry entry = entries.get(first + index);
            menu.set(index, MenuItems.of(entry.icon(), entry.display(), entry.lore()),
                    entry.action());
        }

        if (current > 0) {
            menu.set(settings.previousSlot(), button(messages, settings.previousPage(),
                            "menu.previous", "menu.previous-lore", current),
                    clicker -> open(clicker, messages, settings, titleKey, entries, current - 1));
        }
        if (current < pages - 1) {
            menu.set(settings.nextSlot(), button(messages, settings.nextPage(),
                            "menu.next", "menu.next-lore", current + 2),
                    clicker -> open(clicker, messages, settings, titleKey, entries, current + 1));
        }
        menu.set(settings.closeSlot(), MenuItems.of(settings.close(),
                        messages.render("menu.close"), List.of(messages.render("menu.close-lore"))),
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
