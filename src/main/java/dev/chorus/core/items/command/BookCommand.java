package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /book [author|title]}: reopens a signed book, or renames one.
 *
 * <p>A signed book cannot be edited in game. Turning it back into a writable one is the only
 * way to fix a typo on page four without writing the whole thing again, and signing it puts
 * it back exactly as it was with the same title and author.
 */
public final class BookCommand extends HeldItemCommand {

    private static final String AUTHOR_PERMISSION = "chorus.items.book.author";
    private static final List<String> ACTIONS = List.of("author", "title");
    private static final int MAX_TITLE = 32;

    public BookCommand(CommandSupport support) {
        super(support, "book", "chorus.items.book");
    }

    @Override
    protected void execute(Player player, String[] args) {
        ItemStack item = held(player);
        if (item == null) {
            return;
        }

        if (args.length == 0) {
            unseal(player, item);
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action) || args.length < 2) {
            messages.send(player, "items.book-usage");
            return;
        }
        if (!player.hasPermission(AUTHOR_PERMISSION)) {
            messages.send(player, "error.no-permission");
            return;
        }
        if (item.getType() != Material.WRITTEN_BOOK
                || !(item.getItemMeta() instanceof BookMeta meta)) {
            messages.send(player, "items.book-not-signed");
            return;
        }
        if (!ready(player)) {
            return;
        }

        String text = trim(String.join(" ", List.of(args).subList(1, args.length)));
        if (action.equals("author")) {
            meta.setAuthor(text);
        } else {
            meta.setTitle(text);
        }
        item.setItemMeta(meta);

        settle(player);
        messages.send(player, action.equals("author") ? "items.book-author" : "items.book-title",
                "text", text);
    }

    /**
     * Signed to writable and back again.
     *
     * <p>The title and author are carried across on the way out and restored on the way in,
     * so a book that goes round the loop comes back the same book.
     */
    private void unseal(Player player, ItemStack item) {
        if (!(item.getItemMeta() instanceof BookMeta meta)) {
            messages.send(player, "items.book-not-a-book");
            return;
        }
        if (!ready(player)) {
            return;
        }

        if (item.getType() == Material.WRITTEN_BOOK) {
            item.setType(Material.WRITABLE_BOOK);
            item.setItemMeta(meta);
            settle(player);
            messages.send(player, "items.book-unsealed");
            return;
        }
        if (item.getType() != Material.WRITABLE_BOOK) {
            messages.send(player, "items.book-not-a-book");
            return;
        }

        item.setType(Material.WRITTEN_BOOK);
        BookMeta signed = (BookMeta) item.getItemMeta();
        if (signed != null) {
            signed.pages(meta.pages());
            if (meta.getTitle() == null || meta.getTitle().isEmpty()) {
                signed.setTitle(player.getName() + "'s book");
            } else {
                signed.setTitle(meta.getTitle());
            }
            signed.setAuthor(meta.getAuthor() == null ? player.getName() : meta.getAuthor());
            item.setItemMeta(signed);
        }

        settle(player);
        messages.send(player, "items.book-signed");
    }

    private static String trim(String text) {
        String cleaned = text.replace('_', ' ').trim();
        return cleaned.length() > MAX_TITLE ? cleaned.substring(0, MAX_TITLE) : cleaned;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(AUTHOR_PERMISSION)) {
            return List.of();
        }
        return startingWith(args[0], ACTIONS);
    }
}
