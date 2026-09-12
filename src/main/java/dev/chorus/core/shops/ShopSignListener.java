package dev.chorus.core.shops;

import dev.chorus.core.command.CommandRules;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.locale.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Signs that trade with the server: {@code [Buy]} hands items over for money, {@code [Sell]}
 * takes them back.
 *
 * <p>Stock is not tracked. These are the server's own shops, which is what the two words on
 * the first line have meant since the day somebody first wrote them on a sign.
 */
public final class ShopSignListener implements Listener {

    private static final String CREATE_PERMISSION = "chorus.shops.create";
    private static final String USE_PERMISSION = "chorus.shops.use";

    private final ShopService shops;
    private final WorthTable worth;
    private final Messages messages;

    private volatile boolean enabled = true;
    private volatile CommandRules feedback = CommandRules.FREE;

    public ShopSignListener(ShopService shops, WorthTable worth, Messages messages) {
        this.shops = shops;
        this.worth = worth;
        this.messages = messages;
    }

    public void apply(boolean signsEnabled, CommandRules rules) {
        this.enabled = signsEnabled;
        this.feedback = rules;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!enabled) {
            return;
        }
        String header = header(event.line(0));
        if (header == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(CREATE_PERMISSION)) {
            event.setCancelled(true);
            messages.send(player, "error.no-permission");
            return;
        }

        ShopSign shop = ShopSign.read(event.line(0), event.line(1), event.line(2), event.line(3));
        if (shop == null) {
            event.setCancelled(true);
            messages.send(player, "shops.sign-invalid");
            return;
        }
        if (shop.usesWorthTable() && worth.of(shop.material()) <= 0) {
            event.setCancelled(true);
            messages.send(player, "shops.no-worth",
                    "item", shop.material().name().toLowerCase(Locale.ROOT));
            return;
        }
        messages.send(player, "shops.sign-created",
                "amount", String.valueOf(shop.amount()),
                "item", shop.material().name().toLowerCase(Locale.ROOT));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        ShopSign shop = shopOn(event.getClickedBlock());
        if (shop == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(USE_PERMISSION)) {
            messages.send(player, "error.no-permission");
            return;
        }

        Economy economy = shops.economy();
        if (!economy.enabled()) {
            messages.send(player, "economy.unavailable");
            return;
        }
        if (shop.buying()) {
            buy(player, shop, economy);
        } else {
            sell(player, shop, economy);
        }
    }

    private void buy(Player player, ShopSign shop, Economy economy) {
        String item = shop.material().name().toLowerCase(Locale.ROOT);
        if (!economy.has(player, shop.price())) {
            messages.send(player, "economy.insufficient",
                    "price", economy.format(shop.price()),
                    "balance", economy.format(economy.balance(player)));
            return;
        }
        if (ShopService.room(player, shop.material(), shop.amount()) < shop.amount()) {
            messages.send(player, "shops.no-room", "item", item);
            return;
        }
        if (!economy.withdraw(player, shop.price())) {
            messages.send(player, "economy.transfer-failed");
            return;
        }

        int refused = ShopService.give(player, shop.material(), shop.amount());
        if (refused > 0) {
            // The inventory filled up between the check and the handover.
            economy.deposit(player, shop.price());
            messages.send(player, "shops.no-room", "item", item);
            return;
        }

        feedback.feedback().play(player);
        messages.send(player, "shops.bought",
                "amount", String.valueOf(shop.amount()),
                "item", item,
                "price", economy.format(shop.price()));
    }

    private void sell(Player player, ShopSign shop, Economy economy) {
        String item = shop.material().name().toLowerCase(Locale.ROOT);
        double price = shop.usesWorthTable()
                ? worth.of(shop.material()) * shop.amount()
                : shop.price();
        if (price <= 0) {
            messages.send(player, "shops.no-worth", "item", item);
            return;
        }
        if (ShopService.count(player, shop.material()) < shop.amount()) {
            messages.send(player, "shops.not-enough",
                    "amount", String.valueOf(shop.amount()), "item", item);
            return;
        }

        int taken = ShopService.take(player, shop.material(), shop.amount());
        if (taken < shop.amount()) {
            // Put back whatever did come out; nothing has been paid for yet.
            ShopService.give(player, shop.material(), taken);
            messages.send(player, "shops.not-enough",
                    "amount", String.valueOf(shop.amount()), "item", item);
            return;
        }
        if (!economy.deposit(player, price)) {
            ShopService.give(player, shop.material(), taken);
            messages.send(player, "economy.transfer-failed");
            return;
        }

        feedback.feedback().play(player);
        messages.send(player, "shops.sold",
                "amount", String.valueOf(shop.amount()),
                "item", item,
                "price", economy.format(price));
    }

    /**
     * Signs grew a back side in 1.20, which is why reading a line without saying which side
     * is deprecated on newer servers. It still reads the front, which is the one people write
     * on, and the method that replaced it does not exist on 1.18.
     */
    private static @Nullable ShopSign shopOn(Block block) {
        if (!(block.getState() instanceof Sign sign)) {
            return null;
        }
        return ShopSign.read(sign.line(0), sign.line(1), sign.line(2), sign.line(3));
    }

    private static @Nullable String header(Component line) {
        String text = PlainTextComponentSerializer.plainText()
                .serialize(line).trim().toLowerCase(Locale.ROOT);
        return text.equals("[buy]") || text.equals("[sell]") ? text : null;
    }
}
