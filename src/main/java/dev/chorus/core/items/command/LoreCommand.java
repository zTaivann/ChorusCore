package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.items.ItemService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** {@code /lore add <text>}, {@code /lore set <line> <text>}, {@code /lore remove <line>}, {@code /lore clear}. */
public final class LoreCommand extends HeldItemCommand {

    private final ItemService items;

    public LoreCommand(CommandSupport support, ItemService items) {
        super(support, "lore", "chorus.items.lore");
        this.items = items;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "items.lore-usage");
            return;
        }

        ItemStack item = held(player);
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            messages.send(player, "items.not-editable");
            return;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        String action = args[0].toLowerCase(Locale.ROOT);
        boolean changed = switch (action) {
            case "add" -> add(player, lore, args);
            case "set" -> replace(player, lore, args);
            case "remove" -> remove(player, lore, args);
            case "clear" -> {
                lore.clear();
                yield true;
            }
            default -> {
                messages.send(player, "items.lore-usage");
                yield false;
            }
        };
        if (!changed || !ready(player)) {
            return;
        }

        meta.lore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
        settle(player);
        messages.send(player, action.equals("clear") ? "items.lore-cleared" : "items.lore-updated");
    }

    private boolean add(Player player, List<Component> lore, String[] args) {
        if (args.length < 2) {
            messages.send(player, "items.lore-usage");
            return false;
        }
        if (lore.size() >= items.settings().maxLoreLines()) {
            messages.send(player, "items.lore-full",
                    "max", String.valueOf(items.settings().maxLoreLines()));
            return false;
        }
        lore.add(items.text(player, join(args, 1)));
        return true;
    }

    private boolean replace(Player player, List<Component> lore, String[] args) {
        if (args.length < 3) {
            messages.send(player, "items.lore-usage");
            return false;
        }
        int line = lineNumber(player, lore, args[1]);
        if (line < 0) {
            return false;
        }
        lore.set(line, items.text(player, join(args, 2)));
        return true;
    }

    private boolean remove(Player player, List<Component> lore, String[] args) {
        if (args.length < 2) {
            messages.send(player, "items.lore-usage");
            return false;
        }
        int line = lineNumber(player, lore, args[1]);
        if (line < 0) {
            return false;
        }
        lore.remove(line);
        return true;
    }

    /** Lines are one-based for the player and zero-based here. Returns -1 when it is not a line. */
    private int lineNumber(Player player, List<Component> lore, String raw) {
        int line;
        try {
            line = Integer.parseInt(raw) - 1;
        } catch (NumberFormatException notANumber) {
            line = -1;
        }
        if (line < 0 || line >= lore.size()) {
            messages.send(player, "items.lore-no-line", "lines", String.valueOf(lore.size()));
            return -1;
        }
        return line;
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1
                ? startingWith(args[0], List.of("add", "set", "remove", "clear"))
                : List.of();
    }
}
