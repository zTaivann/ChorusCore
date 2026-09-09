package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.items.Enchantments;
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

    private static final String UNSAFE_PERMISSION = "chorus.items.enchant.unsafe";
    private static final int MAX_UNSAFE_LEVEL = 255;

    public EnchantCommand(CommandSupport support) {
        super(support, "enchant", "chorus.items.enchant");
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

        int level = args.length > 1 ? parse(args[1]) : enchantment.getMaxLevel();
        int ceiling = player.hasPermission(UNSAFE_PERMISSION)
                ? MAX_UNSAFE_LEVEL
                : enchantment.getMaxLevel();
        if (level < 0 || level > ceiling) {
            messages.send(player, "items.enchant-range", "max", String.valueOf(ceiling));
            return;
        }
        if (!ready(player)) {
            return;
        }

        String name = key(enchantment);
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

    private static int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], Enchantments.known());
        }
        if (args.length != 2) {
            return List.of();
        }
        Enchantment enchantment = Enchantments.byName(args[0]);
        if (enchantment == null) {
            return List.of();
        }
        List<String> levels = new ArrayList<>();
        for (int level = 1; level <= enchantment.getMaxLevel(); level++) {
            levels.add(String.valueOf(level));
        }
        return startingWith(args[1], levels);
    }
}
