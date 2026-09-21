package dev.chorus.core.shops.chest;

import dev.chorus.core.economy.Economy;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.menu.ChatPrompts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Making, using and protecting a chest shop. */
public final class ChestShopListener implements Listener {

    private static final String CREATE_PERMISSION = "chorus.shops.chest.create";
    private static final String USE_PERMISSION = "chorus.shops.chest.use";
    private static final String ADMIN_PERMISSION = "chorus.shops.chest.admin";
    private static final String UNLIMITED_PERMISSION = "chorus.shops.chest.unlimited";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final BlockFace[] AROUND =
            {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final ChestShops shops;
    private final ChestShopDisplays displays;
    private final Messages messages;
    private final Economy economy;
    private final ChatPrompts prompts;

    private volatile ChestShopSettings settings;

    public ChestShopListener(ChestShops shops, ChestShopDisplays displays, Messages messages,
                             Economy economy, ChatPrompts prompts, ChestShopSettings settings) {
        this.shops = shops;
        this.displays = displays;
        this.messages = messages;
        this.economy = economy;
        this.prompts = prompts;
        this.settings = settings;
    }

    public void apply(ChestShopSettings updated) {
        this.settings = updated;
    }

    // ── Making one ────────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!settings.enabled() || !plain(event.line(0)).equals(ChestShopSign.HEADER)) {
            return;
        }

        Player player = event.getPlayer();
        Block container = ShopContainers.holderOf(event.getBlock());
        if (container == null || !ShopContainers.isShopContainer(container)) {
            event.setCancelled(true);
            messages.send(player, "shops.chest-needs-container");
            return;
        }
        if (!player.hasPermission(CREATE_PERMISSION)) {
            event.setCancelled(true);
            messages.send(player, "error.no-permission");
            return;
        }
        if (shops.at(container) != null) {
            event.setCancelled(true);
            messages.send(player, "shops.chest-already");
            return;
        }
        if (!economy.enabled()) {
            event.setCancelled(true);
            messages.send(player, "economy.unavailable");
            return;
        }

        ItemStack template = player.getInventory().getItemInMainHand().clone();
        if (template.getType().isAir()) {
            event.setCancelled(true);
            messages.send(player, "shops.chest-hold-item");
            return;
        }
        template.setAmount(1);

        int limit = settings.limitFor(player);
        if (!player.hasPermission(ADMIN_PERMISSION) && shops.ownedBy(player.getUniqueId()) >= limit) {
            event.setCancelled(true);
            messages.send(player, "shops.chest-limit", "limit", String.valueOf(limit));
            return;
        }

        String mode = plain(event.line(1));
        boolean selling = !mode.equals("buy") && !mode.equals("buying");
        boolean unlimited = mode.equals("admin") && player.hasPermission(UNLIMITED_PERMISSION);

        double price = price(plain(event.line(3)));
        if (price >= 0) {
            open(player, container, template, price, selling, unlimited);
            return;
        }

        // No price on the sign, so it is asked for in chat.
        Block sign = event.getBlock();
        messages.send(player, "shops.chest-ask-price",
                "item", ChestShopSign.itemName(template));
        prompts.ask(player, messages.render("shops.chest-ask-price-prompt"),
                typed -> {
                    double typedPrice = price(typed.trim());
                    if (typedPrice < 0) {
                        messages.send(player, "shops.chest-bad-price");
                        takeDown(sign);
                        return;
                    }
                    if (shops.at(container) != null || !ShopContainers.isShopContainer(container)) {
                        messages.send(player, "shops.chest-gone");
                        takeDown(sign);
                        return;
                    }
                    open(player, container, template, typedPrice, selling, unlimited);
                },
                () -> {
                    messages.send(player, "shops.chest-cancelled");
                    takeDown(sign);
                });
    }

    private void open(Player player, Block container, ItemStack template, double price,
                      boolean selling, boolean unlimited) {
        if (price > settings.maxPrice()) {
            messages.send(player, "shops.chest-price-too-high",
                    "max", economy.format(settings.maxPrice()));
            return;
        }
        double cost = settings.creationCost();
        if (cost > 0 && !player.hasPermission(ADMIN_PERMISSION)) {
            if (!economy.has(player, cost)) {
                messages.send(player, "economy.insufficient",
                        "price", economy.format(cost),
                        "balance", economy.format(economy.balance(player)));
                return;
            }
            economy.withdraw(player, cost);
        }

        ChestShop shop = shops.create(player, container, template, price, selling, unlimited);
        ChestShopSign.refresh(shop, container, messages, economy);
        displays.show(shop);

        messages.send(player, selling ? "shops.chest-created-selling" : "shops.chest-created-buying",
                "item", ChestShopSign.itemName(template),
                "price", economy.format(price));
    }

    private static void takeDown(Block sign) {
        if (sign.getState() instanceof Sign) {
            sign.breakNaturally();
        }
    }

    // ── Using one ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!settings.enabled() || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        ChestShop shop = shops.at(clicked);
        if (shop == null) {
            return;
        }

        Player player = event.getPlayer();
        boolean owner = shop.isOwner(player.getUniqueId()) || player.hasPermission(ADMIN_PERMISSION);
        boolean onSign = clicked.getState() instanceof Sign;

        if (owner && !onSign) {
            // Their own chest. Opening it is how stock gets in and out.
            return;
        }
        event.setCancelled(true);

        if (owner) {
            info(player, shop, true);
            return;
        }
        if (!player.hasPermission(USE_PERMISSION)) {
            messages.send(player, "error.no-permission");
            return;
        }
        if (!economy.enabled()) {
            messages.send(player, "economy.unavailable");
            return;
        }
        askAmount(player, shop);
    }

    private void info(Player player, ChestShop shop, boolean owner) {
        ItemStack template = shop.template();
        Block container = containerOf(shop);
        if (template == null || container == null) {
            messages.send(player, "shops.chest-broken");
            return;
        }
        Inventory stock = ShopContainers.inventoryOf(container);

        messages.send(player, "shops.chest-info-header");
        messages.send(player, "shops.chest-info-owner", "player", shop.ownerName());
        messages.send(player, "shops.chest-info-item", "item", ChestShopSign.itemName(template));
        messages.send(player, "shops.chest-info-price",
                "price", economy.format(shop.price()),
                "item", ChestShopSign.itemName(template));
        messages.send(player, shop.selling() ? "shops.chest-info-stock" : "shops.chest-info-room",
                "amount", amountWord(shop, stock, template));
        if (owner) {
            messages.send(player, "shops.chest-info-yours");
        }
    }

    private void askAmount(Player player, ChestShop shop) {
        ItemStack template = shop.template();
        Block container = containerOf(shop);
        if (template == null || container == null) {
            messages.send(player, "shops.chest-broken");
            return;
        }
        Inventory stock = ShopContainers.inventoryOf(container);
        if (stock == null) {
            messages.send(player, "shops.chest-broken");
            return;
        }

        int most = most(player, shop, stock, template);
        if (most <= 0) {
            messages.send(player, shop.selling() ? "shops.chest-empty" : "shops.chest-full");
            return;
        }

        info(player, shop, false);
        messages.send(player, shop.selling() ? "shops.chest-ask-buy" : "shops.chest-ask-sell",
                "amount", String.valueOf(most));

        prompts.ask(player, messages.render("shops.chest-ask-amount"),
                typed -> finish(player, shop, template, typed.trim()),
                () -> messages.send(player, "shops.chest-cancelled"));
    }

    /** The trade itself, with everything measured again. */
    private void finish(Player player, ChestShop shop, ItemStack template, String typed) {
        if (!player.isOnline()) {
            return;
        }
        Block container = containerOf(shop);
        ChestShop current = container == null ? null : shops.exactlyAt(container);
        if (current == null || current.id() != shop.id()
                || !ShopContainers.isShopContainer(container)) {
            messages.send(player, "shops.chest-gone");
            return;
        }
        if (!withinReach(player, container)) {
            messages.send(player, "shops.chest-too-far");
            return;
        }
        Inventory stock = ShopContainers.inventoryOf(container);
        if (stock == null) {
            messages.send(player, "shops.chest-broken");
            return;
        }

        int most = most(player, current, stock, template);
        int wanted = amount(typed, most);
        if (wanted <= 0) {
            messages.send(player, "shops.chest-bad-amount");
            return;
        }
        wanted = Math.min(wanted, most);

        OfflinePlayer owner = player.getServer().getOfflinePlayer(current.owner());
        ChestShopTrade.Trade trade = current.selling()
                ? ChestShopTrade.buy(player, current, stock, template, wanted, economy, owner)
                : ChestShopTrade.sell(player, current, stock, template, wanted, economy, owner);

        report(player, current, template, trade);
        if (trade.result() == ChestShopTrade.Result.DONE) {
            ChestShopSign.refresh(current, container, messages, economy);
            tellOwner(player, current, template, trade);
        }
    }

    private void report(Player player, ChestShop shop, ItemStack template,
                        ChestShopTrade.Trade trade) {
        String item = ChestShopSign.itemName(template);
        switch (trade.result()) {
            case DONE -> messages.send(player,
                    shop.selling() ? "shops.chest-bought" : "shops.chest-sold",
                    "amount", String.valueOf(trade.amount()),
                    "item", item,
                    "price", economy.format(trade.money()));
            case NO_STOCK -> messages.send(player, "shops.chest-empty");
            case NO_SPACE -> messages.send(player, "shops.no-room", "item", item);
            case NO_MONEY -> messages.send(player, "economy.insufficient",
                    "price", economy.format(trade.money()),
                    "balance", economy.format(economy.balance(player)));
            case NOTHING_CARRIED -> messages.send(player, "shops.none-carried", "item", item);
            case SHOP_FULL -> messages.send(player, "shops.chest-full");
            case SHOP_BROKE -> messages.send(player, "shops.chest-owner-broke",
                    "player", shop.ownerName());
            case FAILED -> messages.send(player, "economy.transfer-failed");
        }
    }

    private void tellOwner(Player buyer, ChestShop shop, ItemStack template,
                           ChestShopTrade.Trade trade) {
        Player owner = buyer.getServer().getPlayer(shop.owner());
        if (owner == null || owner.equals(buyer)) {
            return;
        }
        messages.send(owner, shop.selling() ? "shops.chest-owner-sold" : "shops.chest-owner-bought",
                "player", buyer.getName(),
                "amount", String.valueOf(trade.amount()),
                "item", ChestShopSign.itemName(template),
                "price", economy.format(trade.money()));
    }

    /** The most this player could trade right now: stock, room and money all considered. */
    private int most(Player player, ChestShop shop, Inventory stock, ItemStack template) {
        int stackLimit = template.getMaxStackSize() * 36;
        if (shop.selling()) {
            int available = ChestShopTrade.stockOf(shop, stock, template);
            int room = ChestShopTrade.space(player.getInventory(), template, stackLimit);
            int affordable = shop.price() <= 0
                    ? stackLimit
                    : (int) Math.floor(economy.balance(player) / shop.price());
            return Math.max(0, Math.min(Math.min(available, room), Math.min(affordable, stackLimit)));
        }

        int carrying = ChestShopTrade.count(player.getInventory(), template);
        int room = ChestShopTrade.roomIn(shop, stock, template, stackLimit);
        int payable = shop.unlimited() || shop.price() <= 0
                ? stackLimit
                : (int) Math.floor(economy.balance(
                        player.getServer().getOfflinePlayer(shop.owner())) / shop.price());
        return Math.max(0, Math.min(Math.min(carrying, room), Math.min(payable, stackLimit)));
    }

    private String amountWord(ChestShop shop, @Nullable Inventory stock, ItemStack template) {
        if (shop.unlimited() || stock == null) {
            return messages.plain("shops.chest-unlimited");
        }
        int count = shop.selling()
                ? ChestShopTrade.count(stock, template)
                : ChestShopTrade.space(stock, template, Integer.MAX_VALUE);
        return String.valueOf(count);
    }

    // ── Keeping it in one piece ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ChestShop shop = shops.at(event.getBlock());
        if (shop == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!shop.isOwner(player.getUniqueId()) && !player.hasPermission(ADMIN_PERMISSION)) {
            event.setCancelled(true);
            messages.send(player, "shops.chest-protected", "player", shop.ownerName());
            return;
        }

        // Breaking any part of it takes the whole shop down.
        displays.hide(shop.key());
        shops.remove(shop);
        messages.send(player, "shops.chest-removed");
    }

    /** A second chest next to somebody else's shop would share its stock. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Material placed = event.getBlockPlaced().getType();
        if (placed != Material.CHEST && placed != Material.TRAPPED_CHEST) {
            return;
        }
        for (BlockFace face : AROUND) {
            Block neighbour = event.getBlockPlaced().getRelative(face);
            if (neighbour.getType() != placed) {
                continue;
            }
            ChestShop shop = shops.at(neighbour);
            if (shop != null && !shop.isOwner(event.getPlayer().getUniqueId())
                    && !event.getPlayer().hasPermission(ADMIN_PERMISSION)) {
                event.setCancelled(true);
                messages.send(event.getPlayer(), "shops.chest-protected",
                        "player", shop.ownerName());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(shops::isShopBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(shops::isShopBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyShop(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyShop(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /** A hopper under a shop would empty it without anybody paying for anything. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent event) {
        if (!settings.protectHoppers()) {
            return;
        }
        if (isShopInventory(event.getSource()) || isShopInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    /** The owner has just been rearranging their stock, so the sign is out of date. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Location where = event.getInventory().getLocation();
        if (where == null) {
            return;
        }
        ChestShop shop = shops.at(where.getBlock());
        if (shop != null) {
            ChestShopSign.refresh(shop, where.getBlock(), messages, economy);
        }
    }

    // ── The floating item ─────────────────────────────────────────────────────

    /** Two shops selling the same thing side by side must not merge into one item. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        if (displays.isDisplay(event.getEntity()) || displays.isDisplay(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (displays.isDisplay(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickup(PlayerAttemptPickupItemEvent event) {
        if (displays.isDisplay(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (displays.isDisplay(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (displays.isDisplay(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Never saved to disk, so they have to be put back when a chunk comes round again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        displays.showIn(event.getChunk());

        // Rewriting the signs puts back one another plugin or a rollback changed.
        for (ChestShop shop : displays.in(event.getChunk())) {
            Location where = shop.location();
            if (where != null) {
                ChestShopSign.refresh(shop, where.getBlock(), messages, economy);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        displays.forgetIn(event.getChunk());
    }

    private boolean anyShop(List<Block> blocks) {
        for (Block block : blocks) {
            if (shops.isShopBlock(block)) {
                return true;
            }
        }
        return false;
    }

    private boolean isShopInventory(Inventory inventory) {
        Location where = inventory.getLocation();
        return where != null && shops.at(where.getBlock()) != null;
    }

    // ── Odds and ends ─────────────────────────────────────────────────────────

    private @Nullable Block containerOf(ChestShop shop) {
        Location where = shop.location();
        return where == null ? null : where.getBlock();
    }

    private boolean withinReach(Player player, Block container) {
        if (!player.getWorld().equals(container.getWorld())) {
            return false;
        }
        int reach = settings.reach();
        return player.getLocation().distanceSquared(container.getLocation()) <= (double) reach * reach;
    }

    /** {@code all} means as many as possible; anything else has to be a positive number. */
    private static int amount(String typed, int most) {
        if (typed.equalsIgnoreCase("all") || typed.equals("*")) {
            return most;
        }
        try {
            return Integer.parseInt(typed);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    /** Negative when the line is not a price, which is how "ask me" is said. */
    private static double price(String raw) {
        if (raw.isEmpty()) {
            return -1;
        }
        try {
            double value = Double.parseDouble(raw.replace(',', '.').replace("$", ""));
            return Double.isFinite(value) && value >= 0
                    ? Math.round(value * 100.0) / 100.0
                    : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private static String plain(Component line) {
        return PLAIN.serialize(line).trim().toLowerCase(Locale.ROOT);
    }
}
