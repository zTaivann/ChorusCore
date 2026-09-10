package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.PlayerCommand;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** {@code /skull [player]}, or your own head when no name is given. */
public final class SkullCommand extends PlayerCommand {

    private static final String OTHERS_PERMISSION = "chorus.items.skull.others";

    public SkullCommand(CommandSupport support) {
        super(support, "skull", "chorus.items.skull");
    }

    @Override
    protected void execute(Player player, String[] args) {
        OfflinePlayer owner = player;
        if (args.length > 0 && !args[0].equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission(OTHERS_PERMISSION)) {
                messages.send(player, "error.no-permission");
                return;
            }
            owner = known(player, args[0]);
            if (owner == null) {
                return;
            }
        }

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (!(skull.getItemMeta() instanceof SkullMeta meta)) {
            messages.send(player, "items.skull-unavailable");
            return;
        }
        if (!ready(player)) {
            return;
        }

        meta.setOwningPlayer(owner);
        skull.setItemMeta(meta);

        String name = owner.getName() == null ? args[0] : owner.getName();
        for (ItemStack leftover : player.getInventory().addItem(skull).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        settle(player);
        messages.send(player, "items.skull", "player", name);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(OTHERS_PERMISSION)) {
            return List.of();
        }
        return onlineNames(sender, args[0], true);
    }
}
