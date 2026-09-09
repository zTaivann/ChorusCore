package dev.chorus.core.utility;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * The vanilla screens a player can open without standing in front of the block.
 *
 * <p>A null location tells Paper to use the player's own position, and the flag forces the
 * screen open even though there is no such block there.
 */
public enum PortableMenu {

    CRAFTING("craft", player -> player.openWorkbench(null, true)),
    ANVIL("anvil", player -> player.openAnvil(null, true)),
    SMITHING("smithingtable", player -> player.openSmithingTable(null, true)),
    GRINDSTONE("grindstone", player -> player.openGrindstone(null, true)),
    STONECUTTER("stonecutter", player -> player.openStonecutter(null, true)),
    LOOM("loom", player -> player.openLoom(null, true)),
    CARTOGRAPHY("cartography", player -> player.openCartographyTable(null, true)),
    ENCHANTING("enchanting", player -> player.openEnchanting(null, true)),
    ENDERCHEST("enderchest", player -> player.openInventory(player.getEnderChest()));

    private final String command;
    private final Consumer<Player> opener;

    PortableMenu(String command, Consumer<Player> opener) {
        this.command = command;
        this.opener = opener;
    }

    public String command() {
        return command;
    }

    public void open(Player player) {
        opener.accept(player);
    }
}
