package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /potion <effect> [level] [seconds]}: writes effects onto the potion in your hand. */
public final class PotionCommand extends HeldItemCommand {

    private static final String UNSAFE_PERMISSION = "chorus.items.potion.unsafe";
    private static final int SAFE_LEVEL = 2;
    private static final int MAX_LEVEL = 127;
    private static final int MAX_SECONDS = 60 * 60;
    private static final int DEFAULT_SECONDS = 30;
    private static final int TICKS_PER_SECOND = 20;

    public PotionCommand(CommandSupport support) {
        super(support, "potion", "chorus.items.potion");
    }

    @Override
    protected void execute(Player player, String[] args) {
        ItemStack item = held(player);
        if (item == null) {
            return;
        }
        if (!isPotion(item.getType()) || !(item.getItemMeta() instanceof PotionMeta meta)) {
            messages.send(player, "items.potion-not-a-potion");
            return;
        }
        if (args.length == 0) {
            messages.send(player, "items.potion-usage");
            return;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("clear")) {
            clear(player, item, meta);
            return;
        }
        if (first.equals("list")) {
            list(player, meta);
            return;
        }

        PotionEffectType type = effect(first);
        if (type == null) {
            messages.send(player, "items.potion-unknown", "effect", args[0]);
            return;
        }

        int level = args.length > 1 ? Numbers.integer(args[1], 0) : 1;
        int seconds = args.length > 2 ? Numbers.integer(args[2], 0) : DEFAULT_SECONDS;
        if (level < 1 || seconds < 1) {
            messages.send(player, "items.potion-usage");
            return;
        }
        if (level > SAFE_LEVEL && !player.hasPermission(UNSAFE_PERMISSION)) {
            messages.send(player, "items.potion-unsafe", "max", String.valueOf(SAFE_LEVEL));
            return;
        }
        if (!ready(player)) {
            return;
        }

        level = Math.min(level, MAX_LEVEL);
        seconds = Math.min(seconds, MAX_SECONDS);
        meta.addCustomEffect(
                new PotionEffect(type, seconds * TICKS_PER_SECOND, level - 1), true);
        item.setItemMeta(meta);

        settle(player);
        messages.send(player, "items.potion-added",
                "effect", name(type),
                "level", String.valueOf(level),
                "seconds", String.valueOf(seconds));
    }

    private void clear(Player player, ItemStack item, PotionMeta meta) {
        if (!ready(player)) {
            return;
        }
        meta.clearCustomEffects();
        item.setItemMeta(meta);

        settle(player);
        messages.send(player, "items.potion-cleared");
    }

    private void list(Player player, PotionMeta meta) {
        List<PotionEffect> effects = meta.getCustomEffects();
        if (effects.isEmpty()) {
            messages.send(player, "items.potion-none");
            return;
        }

        messages.send(player, "items.potion-list-header", "count", String.valueOf(effects.size()));
        for (PotionEffect effect : effects) {
            messages.send(player, "items.potion-list-entry",
                    "effect", name(effect.getType()),
                    "level", String.valueOf(effect.getAmplifier() + 1),
                    "seconds", String.valueOf(effect.getDuration() / TICKS_PER_SECOND));
        }
    }

    private static boolean isPotion(Material material) {
        return material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION
                || material == Material.TIPPED_ARROW;
    }

    /** The effects the server actually has. */
    private static @Nullable PotionEffectType effect(String raw) {
        String wanted = raw.toUpperCase(Locale.ROOT).replace(' ', '_');
        for (PotionEffectType type : PotionEffectType.values()) {
            if (type != null && type.getName().equalsIgnoreCase(wanted)) {
                return type;
            }
        }
        return null;
    }

    private static String name(PotionEffectType type) {
        return type.getName().toLowerCase(Locale.ROOT);
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>(List.of("clear", "list"));
        for (PotionEffectType type : PotionEffectType.values()) {
            if (type != null) {
                names.add(name(type));
            }
        }
        return startingWith(args[0], names);
    }
}
