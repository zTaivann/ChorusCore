package dev.chorus.core.utility.signs;

import dev.chorus.core.economy.Economy;
import dev.chorus.core.locale.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * The signs that do what a command does: {@code [Heal]}, {@code [Kit]}, {@code [Repair]} and
 * the rest.
 */
public final class ServiceSignListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int LAST_LINE = 3;

    private final Messages messages;
    private final Economy economy;

    private volatile boolean enabled = true;

    public ServiceSignListener(Messages messages, Economy economy) {
        this.messages = messages;
        this.economy = economy;
    }

    public void apply(boolean signsEnabled) {
        this.enabled = signsEnabled;
    }

    public boolean isServiceSign(Block block) {
        if (!(block.getState() instanceof Sign sign)) {
            return false;
        }
        return ServiceSign.of(plain(sign.line(0))) != null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!enabled) {
            return;
        }
        ServiceSign kind = ServiceSign.of(plain(event.line(0)));
        if (kind == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(kind.createPermission())) {
            event.setCancelled(true);
            messages.send(player, "error.no-permission");
            return;
        }
        if (kind == ServiceSign.FREE && item(plain(event.line(2))) == null) {
            event.setCancelled(true);
            messages.send(player, "utility.sign-free-item");
            return;
        }
        messages.send(player, "utility.sign-created", "kind", kind.header());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || !(event.getClickedBlock().getState() instanceof Sign sign)) {
            return;
        }
        ServiceSign kind = ServiceSign.of(plain(sign.line(0)));
        if (kind == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(kind.usePermission())) {
            messages.send(player, "error.no-permission");
            return;
        }

        double price = price(plain(sign.line(LAST_LINE)));
        if (price > 0 && economy.enabled()) {
            if (!economy.has(player, price)) {
                messages.send(player, "economy.insufficient",
                        "price", economy.format(price),
                        "balance", economy.format(economy.balance(player)));
                return;
            }
        }

        boolean done = kind == ServiceSign.FREE
                ? free(player, sign)
                : player.performCommand(line(kind, sign));

        // Only a command that actually ran costs anything.
        if (done && price > 0 && economy.enabled() && economy.withdraw(player, price)) {
            messages.send(player, "economy.charged", "price", economy.format(price));
        }
    }

    /** The command with whatever the middle lines said after it. */
    private static String line(ServiceSign kind, Sign sign) {
        StringBuilder command = new StringBuilder(kind.command());
        for (int row = 1; row < LAST_LINE; row++) {
            String text = plain(sign.line(row));
            if (!text.isEmpty()) {
                command.append(' ').append(text);
            }
        }
        return command.toString();
    }

    private boolean free(Player player, Sign sign) {
        Material material = item(plain(sign.line(2)));
        if (material == null) {
            messages.send(player, "utility.sign-broken");
            return false;
        }
        int amount = amount(plain(sign.line(1)), material.getMaxStackSize());

        ItemStack giving = new ItemStack(material, amount);
        Map<Integer, ItemStack> rejected = player.getInventory().addItem(giving);
        if (!rejected.isEmpty()) {
            rejected.values().forEach(left ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
        }

        messages.send(player, "utility.sign-free-given",
                "amount", String.valueOf(amount),
                "item", material.name().toLowerCase(Locale.ROOT));
        return true;
    }

    private static @Nullable Material item(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        Material material = Material.matchMaterial(raw.replace(' ', '_'));
        return material != null && material.isItem() && !material.isAir() ? material : null;
    }

    private static int amount(String raw, int fallback) {
        try {
            int typed = Integer.parseInt(raw);
            return typed <= 0 ? fallback : Math.min(typed, fallback);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /** Zero when the last line is not a price, which is how a free sign is written. */
    private static double price(String raw) {
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            double value = Double.parseDouble(raw.replace(',', '.').replace("$", ""));
            return Double.isFinite(value) && value > 0 ? value : 0;
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    private static String plain(Component line) {
        return PLAIN.serialize(line).trim().toLowerCase(Locale.ROOT);
    }
}
