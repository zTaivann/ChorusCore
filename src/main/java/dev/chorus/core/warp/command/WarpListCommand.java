package dev.chorus.core.warp.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.location.NamedLocation;
import dev.chorus.core.menu.ListMenu;
import dev.chorus.core.warp.WarpDetails;
import dev.chorus.core.warp.WarpDetailsService;
import dev.chorus.core.warp.WarpService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WarpListCommand extends ChorusCommand {

    /** Warps with a section come first, in section order; the loose ones follow. */
    private static final Comparator<NamedLocation> BY_NAME =
            Comparator.comparing(NamedLocation::name, String.CASE_INSENSITIVE_ORDER);

    private final WarpService warps;
    private final WarpDetailsService details;
    private final Supplier<String> order;

    public WarpListCommand(CommandSupport support, WarpService warps, WarpDetailsService details,
                           Supplier<String> order) {
        super(support, "warps", "chorus.warp.list");
        this.warps = warps;
        this.details = details;
        this.order = order;
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        List<NamedLocation> visible = new ArrayList<>(warps.visibleTo(sender));
        if (visible.isEmpty()) {
            messages.send(sender, "warp.none");
            return;
        }
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        visible.sort(Comparator
                .comparing((NamedLocation warp) -> section(warp), Comparator.nullsLast(String::compareTo))
                .thenComparing(within()));

        if (args.length > 0) {
            openSection(sender, visible, args[0]);
            return;
        }

        // The console has no screen to open, so it always gets the written list.
        if (warps.settings().menu().enabled() && sender instanceof Player player) {
            openMenu(player, visible);
        } else {
            sendList(sender, visible);
        }
    }

    /** Whichever order the config asks for inside a section. */
    private Comparator<NamedLocation> within() {
        return switch (order.get().toLowerCase(Locale.ROOT)) {
            case "uses", "popular" -> {
                Comparator<NamedLocation> byUses = Comparator.comparingLong(
                        (NamedLocation warp) -> details.of(warp.name()).uses());
                yield byUses.reversed().thenComparing(BY_NAME);
            }
            // The order they came out of the database, which is the order they were made.
            case "none", "created" -> (left, right) -> 0;
            default -> BY_NAME;
        };
    }

    /** Straight into one section, for a server whose warps are sorted into many. */
    private void openSection(CommandSender sender, List<NamedLocation> visible, String wanted) {
        List<NamedLocation> inside = new ArrayList<>();
        for (NamedLocation warp : visible) {
            String section = section(warp);
            if (section != null && section.equalsIgnoreCase(wanted)) {
                inside.add(warp);
            }
        }
        if (inside.isEmpty()) {
            messages.send(sender, "warp.section-unknown", "section", wanted);
            return;
        }
        if (warps.settings().menu().enabled() && sender instanceof Player player) {
            openGrid(player, inside, "menu.warps.section-title", null,
                    "section", section(inside.get(0)));
        } else {
            sendList(sender, inside);
        }
    }

    /** The grid, or a screen of sections when the warps are grouped into any. */
    private void openMenu(Player player, List<NamedLocation> visible) {
        Map<String, List<NamedLocation>> sections = group(visible);
        if (sections.size() < 2) {
            openGrid(player, visible, "menu.warps.title", null);
            return;
        }

        List<ListMenu.Entry> entries = new ArrayList<>(sections.size());
        for (Map.Entry<String, List<NamedLocation>> group : sections.entrySet()) {
            List<NamedLocation> inside = group.getValue();
            String name = group.getKey().isEmpty()
                    ? messages.plain("menu.warps.section-loose")
                    : group.getKey();
            entries.add(new ListMenu.Entry(
                    icon(details.of(inside.get(0).name())),
                    messages.render("menu.warps.section", "section", name),
                    messages.renderLines("menu.warps.section-lore",
                            "count", String.valueOf(inside.size())),
                    clicker -> openGrid(clicker, inside, "menu.warps.section-title",
                            back -> openMenu(back, visible), "section", name)));
        }
        ListMenu.open(player, messages, warps.settings().menu(), "menu.warps.title", entries, 0);
    }

    private void openGrid(Player player, List<NamedLocation> group, String titleKey,
                          @Nullable Consumer<Player> back, String... titleValues) {
        List<ListMenu.Entry> entries = new ArrayList<>(group.size());
        for (NamedLocation warp : group) {
            WarpDetails detail = details.of(warp.name());
            entries.add(new ListMenu.Entry(
                    icon(detail),
                    messages.render("menu.warps.entry", "warp", warp.name()),
                    lore(warp, detail),
                    clicker -> {
                        clicker.closeInventory();
                        clicker.performCommand("warp " + warp.name());
                    }));
        }
        ListMenu.open(player, messages, warps.settings().menu(), titleKey, entries, 0,
                back, titleValues);
    }

    /** By section, in the order they were sorted into. Loose warps share the empty name. */
    private Map<String, List<NamedLocation>> group(List<NamedLocation> visible) {
        Map<String, List<NamedLocation>> sections = new LinkedHashMap<>();
        for (NamedLocation warp : visible) {
            String section = section(warp);
            sections.computeIfAbsent(section == null ? "" : section,
                    key -> new ArrayList<>()).add(warp);
        }
        return sections;
    }

    private List<Component> lore(NamedLocation warp, WarpDetails detail) {
        List<Component> lore = new ArrayList<>(6);
        if (detail.description() != null) {
            lore.add(messages.render("menu.warps.lore-description",
                    "description", detail.description()));
        }
        lore.add(messages.render("menu.warps.lore-world", "world", warp.worldName()));
        lore.add(messages.render("menu.warps.lore-location",
                "x", round(warp.x()), "y", round(warp.y()), "z", round(warp.z())));
        if (detail.uses() > 0) {
            lore.add(messages.render("menu.warps.lore-uses", "uses", String.valueOf(detail.uses())));
        }
        lore.add(messages.render("menu.warps.lore-divider"));
        lore.add(messages.render("menu.warps.lore-action"));
        return lore;
    }

    private void sendList(CommandSender sender, List<NamedLocation> visible) {
        messages.send(sender, "warp.list.header", "count", String.valueOf(visible.size()));

        Map<String, List<NamedLocation>> sections = group(visible);
        boolean grouped = sections.size() > 1 || !sections.containsKey("");
        for (Map.Entry<String, List<NamedLocation>> group : sections.entrySet()) {
            if (grouped && !group.getKey().isEmpty()) {
                messages.send(sender, "warp.list.section", "section", group.getKey());
            }
            sender.sendMessage(Component.join(
                    JoinConfiguration.separator(messages.render("warp.list.separator")),
                    line(group.getValue())));
        }
    }

    private List<Component> line(List<NamedLocation> group) {
        List<Component> entries = new ArrayList<>(group.size());
        for (NamedLocation warp : group) {
            entries.add(messages.render("warp.list.entry", "warp", warp.name())
                    .clickEvent(ClickEvent.runCommand("/warp " + warp.name()))
                    .hoverEvent(HoverEvent.showText(hover(warp))));
        }
        return entries;
    }

    private Component hover(NamedLocation warp) {
        WarpDetails detail = details.of(warp.name());
        if (detail.description() != null) {
            return messages.render("warp.list.hover-description",
                    "warp", warp.name(),
                    "description", detail.description(),
                    "world", warp.worldName());
        }
        return messages.render("warp.list.hover",
                "warp", warp.name(),
                "world", warp.worldName(),
                "x", round(warp.x()),
                "y", round(warp.y()),
                "z", round(warp.z()));
    }

    private String section(NamedLocation warp) {
        return details.of(warp.name()).section();
    }

    /** The warp's own icon when it has one, and the one from the config when it does not. */
    private Material icon(WarpDetails detail) {
        if (detail.icon() == null) {
            return warps.settings().menu().icon();
        }
        Material own = Material.matchMaterial(detail.icon());
        return own != null && own.isItem() ? own : warps.settings().menu().icon();
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
