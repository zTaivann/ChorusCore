package dev.chorus.core.warp.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.location.Names;
import dev.chorus.core.warp.WarpDetails;
import dev.chorus.core.warp.WarpDetailsService;
import dev.chorus.core.warp.WarpService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Everything known about one warp, including the settings only staff ever see. */
public final class WarpInfoCommand extends ChorusCommand {

    private static final String STAFF_PERMISSION = "chorus.warp.info.full";

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final WarpService warps;
    private final WarpDetailsService details;
    private final Economy economy;

    public WarpInfoCommand(CommandSupport support, WarpService warps, WarpDetailsService details,
                           Economy economy) {
        super(support, "warpinfo", "chorus.warp.info");
        this.warps = warps;
        this.details = details;
        this.economy = economy;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "warp.info-usage");
            return;
        }

        String key = Names.normalise(args[0]);
        Optional<NamedLocation> found = warps.find(key);
        if (found.isEmpty()) {
            messages.send(sender, "warp.unknown", "warp", key);
            return;
        }
        if (!warps.canUse(sender, key) && !sender.hasPermission(STAFF_PERMISSION)) {
            messages.send(sender, "warp.locked", "warp", key);
            return;
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        NamedLocation warp = found.get();
        WarpDetails detail = details.of(key);

        messages.send(sender, "warp.info-header", "warp", warp.name());
        if (detail.description() != null) {
            messages.send(sender, "warp.info-description", "description", detail.description());
        }
        messages.send(sender, "warp.info-world",
                "world", warp.worldName(),
                "x", String.valueOf((int) warp.x()),
                "y", String.valueOf((int) warp.y()),
                "z", String.valueOf((int) warp.z()));
        if (detail.section() != null) {
            messages.send(sender, "warp.info-section", "section", detail.section());
        }
        messages.send(sender, "warp.info-uses", "uses", String.valueOf(detail.uses()));
        messages.send(sender, "warp.info-created",
                "date", DATE.format(Instant.ofEpochMilli(warp.createdAt())));

        if (detail.price() > 0) {
            messages.send(sender, "warp.info-price", "price", economy.format(detail.price()));
        }
        if (detail.cooldownSeconds() > 0) {
            messages.send(sender, "warp.info-cooldown",
                    "time", Durations.format(TimeUnit.SECONDS.toMillis(detail.cooldownSeconds())));
        }
        if (detail.permission() != null && sender.hasPermission(STAFF_PERMISSION)) {
            messages.send(sender, "warp.info-permission", "permission",
                    detail.permission().isEmpty() ? "-" : detail.permission());
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return startingWith(args[0],
                warps.visibleTo(sender).stream().map(NamedLocation::name).toList());
    }
}
