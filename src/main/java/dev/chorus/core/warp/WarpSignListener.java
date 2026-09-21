package dev.chorus.core.warp;

import dev.chorus.core.command.CommandRules;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.location.Names;
import dev.chorus.core.locale.Messages;
import dev.chorus.core.teleport.TeleportService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/** Signs that read {@code [Warp]} on the first line and a warp name on the second. */
public final class WarpSignListener implements org.bukkit.event.Listener {

    private static final String CREATE_PERMISSION = "chorus.warp.sign.create";
    private static final String USE_PERMISSION = "chorus.warp.sign.use";
    private static final String HEADER = "[warp]";
    private static final String COMMAND = "warp";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final WarpService warps;
    private final WarpDetailsService details;
    private final TeleportService teleports;
    private final Messages messages;
    private final CommandSupport support;

    private volatile CommandRules rules = CommandRules.FREE;

    WarpSignListener(WarpService warps, WarpDetailsService details, TeleportService teleports,
                     Messages messages, CommandSupport support) {
        this.warps = warps;
        this.details = details;
        this.teleports = teleports;
        this.messages = messages;
        this.support = support;
    }

    /** Kept in step with the /warp command block, since a sign is just another way to run it. */
    void apply(CommandRules updated) {
        this.rules = updated;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        String header = plain(event.line(0));
        if (!header.equalsIgnoreCase(HEADER)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(CREATE_PERMISSION)) {
            event.setCancelled(true);
            messages.send(player, "error.no-permission");
            return;
        }

        String warp = Names.normalise(plain(event.line(1)));
        if (warps.find(warp).isEmpty()) {
            event.setCancelled(true);
            messages.send(player, "warp.unknown", "warp", warp);
            return;
        }
        messages.send(player, "warp.sign-created", "warp", warp);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        String warp = warpOn(event.getClickedBlock());
        if (warp == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(USE_PERMISSION)) {
            messages.send(player, "error.no-permission");
            return;
        }

        Optional<NamedLocation> found = warps.find(warp);
        if (found.isEmpty()) {
            messages.send(player, "warp.unknown", "warp", warp);
            return;
        }
        if (!warps.canUse(player, warp)) {
            messages.send(player, "warp.locked", "warp", warp);
            return;
        }

        Location destination = found.get().toLocation();
        if (destination == null) {
            messages.send(player, "warp.world-missing", "world", found.get().worldName());
            return;
        }

        String cooldownKey = COMMAND + ":" + warp;
        CommandRules against = details.of(warp).over(rules);
        if (!support.guard().allow(player, cooldownKey, against)) {
            return;
        }

        teleports.teleport(player, destination, against, COMMAND, () -> {
            support.guard().charge(player, cooldownKey, against);
            against.feedback().play(player);
            details.countUse(warp);
            messages.send(player, "warp.teleported", "warp", warp);
        });
    }

    /** Signs grew a back side in 1.20. The front is read, and the newer method is not on 1.18. */
    private @Nullable String warpOn(Block block) {
        if (!(block.getState() instanceof Sign sign)) {
            return null;
        }
        if (!plain(sign.line(0)).equalsIgnoreCase(HEADER)) {
            return null;
        }
        String warp = Names.normalise(plain(sign.line(1)));
        return warp.isEmpty() ? null : warp;
    }

    /**
     * Signs hold styled text, and a warp name is a plain word. Reading it back as plain text
     * means a coloured sign still points at the same warp it did before it was coloured.
     */
    private static String plain(Component line) {
        return PLAIN.serialize(line).trim().toLowerCase(Locale.ROOT);
    }
}
