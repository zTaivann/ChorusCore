package dev.chorus.core.items.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /itemdb [item]}: what the thing in your hand is actually called.
 *
 * <p>The answer to "what do I type in the config for this". Without an argument it reads the
 * held item, which is how it is used almost every time.
 */
public final class ItemDbCommand extends ChorusCommand {

    private static final int MAX_MATCHES = 10;

    public ItemDbCommand(CommandSupport support) {
        super(support, "itemdb", "chorus.items.itemdb");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        ItemStack held = null;
        Material material;

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "items.itemdb-usage");
                return;
            }
            held = player.getInventory().getItemInMainHand();
            material = held.getType();
            if (material.isAir()) {
                messages.send(sender, "items.itemdb-empty");
                return;
            }
        } else {
            material = Material.matchMaterial(args[0]);
            if (material == null) {
                search(sender, args[0]);
                return;
            }
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        String name = material.name().toLowerCase(Locale.ROOT);
        messages.send(sender, "items.itemdb-header", "item", name);
        messages.send(sender, "items.itemdb-kind",
                "kind", kind(material),
                "stack", String.valueOf(material.getMaxStackSize()));

        if (material.getMaxDurability() > 0) {
            int max = material.getMaxDurability();
            int left = held != null && held.getItemMeta() instanceof Damageable damageable
                    ? max - damageable.getDamage()
                    : max;
            messages.send(sender, "items.itemdb-durability",
                    "left", String.valueOf(left), "max", String.valueOf(max));
        }

        int recipes = sender.getServer().getRecipesFor(new ItemStack(material)).size();
        if (recipes > 0) {
            messages.send(sender, "items.itemdb-recipes",
                    "count", String.valueOf(recipes), "item", name);
        }
    }

    /** Nothing matched exactly, so offer whatever contains what they typed. */
    private void search(CommandSender sender, String typed) {
        List<String> found = matching(typed, MAX_MATCHES);
        if (found.isEmpty()) {
            messages.send(sender, "items.itemdb-unknown", "item", typed);
            return;
        }
        settle(sender);
        messages.send(sender, "items.itemdb-matches",
                "count", String.valueOf(found.size()),
                "items", String.join(", ", found));
    }

    private static List<String> matching(String typed, int limit) {
        String wanted = typed.toLowerCase(Locale.ROOT).replace(' ', '_');
        List<String> found = new ArrayList<>();
        for (Material material : Material.values()) {
            if (found.size() >= limit) {
                break;
            }
            if (!material.isLegacy() && material.name().toLowerCase(Locale.ROOT).contains(wanted)) {
                found.add(material.name().toLowerCase(Locale.ROOT));
            }
        }
        return found;
    }

    private static String kind(Material material) {
        if (material.isBlock() && material.isItem()) {
            return "block and item";
        }
        return material.isBlock() ? "block" : "item";
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || args[0].length() < 2) {
            return List.of();
        }
        return matching(args[0], MAX_MATCHES);
    }
}
