package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.items.Enchantments;
import dev.chorus.core.items.ItemRestrictions;
import dev.chorus.core.items.ItemService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** {@code /enchant <enchantment> [level]}, with {@code 0} to take one off again. */
public final class EnchantCommand extends HeldItemCommand {

    private final ItemService items;

    public EnchantCommand(CommandSupport support, ItemService items) {
        super(support, "enchant", "chorus.items.enchant");
        this.items = items;
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            messages.send(player, "items.enchant-usage");
            return;
        }

        ItemStack item = held(player);
        if (item == null) {
            return;
        }

        Enchantment enchantment = Enchantments.byName(args[0]);
        if (enchantment == null) {
            messages.send(player, "items.enchant-unknown", "enchantment", args[0]);
            return;
        }

        String name = key(enchantment);
        ItemRestrictions restrictions = items.settings().restrictions();
        if (!restrictions.mayEnchant(player, name)) {
            messages.send(player, "items.enchant-blocked", "enchantment", name);
            return;
        }

        int level = args.length > 1 ? Numbers.integer(args[1], -1) : enchantment.getMaxLevel();
        int ceiling = restrictions.highestLevel(player, enchantment);
        if (level < 0 || level > ceiling) {
            messages.send(player, "items.enchant-range", "max", String.valueOf(ceiling));
            return;
        }
        if (!ready(player)) {
            return;
        }

        if (level == 0) {
            item.removeEnchantment(enchantment);
            settle(player);
            messages.send(player, "items.enchant-removed", "enchantment", name);
            return;
        }

        // Ignoring the vanilla restrictions on purpose: the permission above is what decides
        // whether a level beyond the usual maximum is allowed, not the item in hand.
        item.addUnsafeEnchantment(enchantment, level);
        settle(player);
        messages.send(player, "items.enchant",
                "enchantment", name, "level", String.valueOf(level));
    }

    private static String key(Enchantment enchantment) {
        return enchantment.getKey().getKey();
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            ItemRestrictions restrictions = items.settings().restrictions();
            return startingWith(args[0], Enchantments.known().stream()
                    .filter(name -> restrictions.mayEnchant(sender, name))
                    .toList());
        }
        if (args.length != 2) {
            return List.of();
        }
        Enchantment enchantment = Enchantments.byName(args[0]);
        if (enchantment == null) {
            return List.of();
        }
        // Every vanilla level, and the ceiling on top when this sender may go past it. Two
        // hundred and fifty-five entries would be a wall of numbers rather than a suggestion.
        int ceiling = items.settings().restrictions().highestLevel(sender, enchantment);
        List<String> levels = new ArrayList<>();
        for (int level = 1; level <= enchantment.getMaxLevel(); level++) {
            levels.add(String.valueOf(level));
        }
        if (ceiling > enchantment.getMaxLevel()) {
            levels.add(String.valueOf(ceiling));
        }
        return startingWith(args[1], levels);
    }
}
