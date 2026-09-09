package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.utility.UtilitySettings;
import dev.chorus.core.utility.UtilityService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RepairCommand extends PlayerCommand {

    private static final String ALL_PERMISSION = "chorus.utility.fix.all";

    private final UtilityService utility;

    public RepairCommand(CommandSupport support, UtilityService utility) {
        super(support, "fix", "chorus.utility.fix");
        this.utility = utility;
    }

    @Override
    protected void execute(Player player, String[] args) {
        UtilitySettings.Fix rules = utility.settings().fix();
        boolean everything = args.length > 0 && args[0].equalsIgnoreCase("all");

        if (everything && (!rules.allowAll() || !player.hasPermission(ALL_PERMISSION))) {
            messages.send(player, "utility.repair-all-denied");
            return;
        }
        if (everything) {
            repairEverything(player, rules);
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        if (held.getType().isAir()) {
            messages.send(player, "utility.repair-empty-hand");
            return;
        }
        if (rules.blacklist().contains(held.getType())) {
            messages.send(player, "utility.repair-blacklisted");
            return;
        }
        if (!ready(player)) {
            return;
        }
        if (!repair(held, rules)) {
            messages.send(player, "utility.repair-nothing");
            return;
        }

        inventory.setItemInMainHand(held);
        settle(player);
        messages.send(player, "utility.repaired");
    }

    private void repairEverything(Player player, UtilitySettings.Fix rules) {
        if (!ready(player)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        // Copies come back from getContents(), so each repaired item has to be written back.
        ItemStack[] contents = inventory.getContents();
        int repaired = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            if (repair(contents[slot], rules)) {
                inventory.setItem(slot, contents[slot]);
                repaired++;
            }
        }

        if (repaired == 0) {
            messages.send(player, "utility.repair-nothing");
            return;
        }
        settle(player);
        messages.send(player, "utility.repaired-all", "count", String.valueOf(repaired));
    }

    private static boolean repair(ItemStack item, UtilitySettings.Fix rules) {
        if (item == null || item.getType().getMaxDurability() <= 0
                || rules.blacklist().contains(item.getType())) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || damageable.getDamage() == 0) {
            return false;
        }
        damageable.setDamage(0);
        item.setItemMeta(meta);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !utility.settings().fix().allowAll()
                || !sender.hasPermission(ALL_PERMISSION)) {
            return List.of();
        }
        return startingWith(args[0], List.of("all"));
    }
}
