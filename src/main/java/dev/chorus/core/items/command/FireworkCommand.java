package dev.chorus.core.items.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.items.ItemAttributes;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /firework}: builds the rocket in your hand.
 *
 * <pre>
 *   /firework shape:star colour:red,blue fade:white trail twinkle
 *   /firework power 2
 *   /firework clear
 *   /firework fire 5
 * </pre>
 */
public final class FireworkCommand extends HeldItemCommand {

    private static final int MAX_POWER = 3;
    private static final int MAX_FIRE = 16;
    private static final List<String> WORDS =
            List.of("shape:", "colour:", "fade:", "trail", "twinkle", "power", "clear", "fire");

    public FireworkCommand(CommandSupport support) {
        super(support, "firework", "chorus.items.firework");
    }

    @Override
    protected void execute(Player player, String[] args) {
        ItemStack item = held(player);
        if (item == null) {
            return;
        }
        if (item.getType() != Material.FIREWORK_ROCKET
                || !(item.getItemMeta() instanceof FireworkMeta meta)) {
            messages.send(player, "items.firework-not-a-rocket");
            return;
        }
        if (args.length == 0) {
            messages.send(player, "items.firework-usage");
            return;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        switch (first) {
            case "clear" -> clear(player, item, meta);
            case "power" -> power(player, item, meta, args);
            case "fire" -> fire(player, item, args);
            default -> burst(player, item, meta, args);
        }
    }

    private void clear(Player player, ItemStack item, FireworkMeta meta) {
        if (!ready(player)) {
            return;
        }
        meta.clearEffects();
        meta.setPower(1);
        item.setItemMeta(meta);

        settle(player);
        messages.send(player, "items.firework-cleared");
    }

    private void power(Player player, ItemStack item, FireworkMeta meta, String[] args) {
        if (args.length < 2) {
            messages.send(player, "items.firework-usage");
            return;
        }
        int power;
        try {
            power = Integer.parseInt(args[1]);
        } catch (NumberFormatException notANumber) {
            messages.send(player, "items.firework-usage");
            return;
        }
        if (power < 0 || power > MAX_POWER) {
            messages.send(player, "items.firework-power-range", "max", String.valueOf(MAX_POWER));
            return;
        }
        if (!ready(player)) {
            return;
        }

        meta.setPower(power);
        item.setItemMeta(meta);
        settle(player);
        messages.send(player, "items.firework-power", "power", String.valueOf(power));
    }

    /** Launches copies of the held rocket without using it up. */
    private void fire(Player player, ItemStack item, String[] args) {
        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Math.max(1, Math.min(MAX_FIRE, Integer.parseInt(args[1])));
            } catch (NumberFormatException notANumber) {
                messages.send(player, "items.firework-usage");
                return;
            }
        }
        if (!ready(player)) {
            return;
        }

        for (int launched = 0; launched < amount; launched++) {
            Firework rocket = player.getWorld().spawn(player.getLocation(), Firework.class);
            if (item.getItemMeta() instanceof FireworkMeta template) {
                rocket.setFireworkMeta(template);
            }
        }

        settle(player);
        messages.send(player, "items.firework-fired", "count", String.valueOf(amount));
    }

    private void burst(Player player, ItemStack item, FireworkMeta meta, String[] args) {
        FireworkEffect.Builder builder = FireworkEffect.builder();
        List<Color> colours = new ArrayList<>();
        List<Color> fades = new ArrayList<>();
        boolean understood = false;

        for (String word : args) {
            int colon = word.indexOf(':');
            String key = (colon < 0 ? word : word.substring(0, colon)).toLowerCase(Locale.ROOT);
            String value = colon < 0 ? "" : word.substring(colon + 1);

            switch (key) {
                case "shape", "type" -> {
                    FireworkEffect.Type shape = shape(value);
                    if (shape == null) {
                        messages.send(player, "items.firework-shape-unknown", "shape", value);
                        return;
                    }
                    builder.with(shape);
                    understood = true;
                }
                case "colour", "color", "c" -> understood |= colours(player, value, colours);
                case "fade", "f" -> understood |= colours(player, value, fades);
                case "trail" -> {
                    builder.trail(true);
                    understood = true;
                }
                case "twinkle", "flicker" -> {
                    builder.flicker(true);
                    understood = true;
                }
                default -> messages.send(player, "items.firework-ignored", "word", word);
            }
        }

        if (!understood || colours.isEmpty()) {
            messages.send(player, "items.firework-needs-colour");
            return;
        }
        if (!ready(player)) {
            return;
        }

        builder.withColor(colours);
        if (!fades.isEmpty()) {
            builder.withFade(fades);
        }
        meta.addEffect(builder.build());
        item.setItemMeta(meta);

        settle(player);
        messages.send(player, "items.firework-added",
                "count", String.valueOf(meta.getEffectsSize()));
    }

    /** @return whether anything was actually read out of the word. */
    private boolean colours(Player player, String value, List<Color> into) {
        boolean any = false;
        for (String part : value.split(",")) {
            Color colour = colour(part);
            if (colour == null) {
                messages.send(player, "items.firework-colour-unknown", "colour", part);
                continue;
            }
            into.add(colour);
            any = true;
        }
        return any;
    }

    private static @Nullable Color colour(String value) {
        String name = value.trim();
        if (name.isEmpty()) {
            return null;
        }
        try {
            return DyeColor.valueOf(name.toUpperCase(Locale.ROOT).replace(' ', '_'))
                    .getFireworkColor();
        } catch (IllegalArgumentException notADye) {
            return ItemAttributes.parseColour(name);
        }
    }

    private static @Nullable FireworkEffect.Type shape(String value) {
        try {
            return FireworkEffect.Type.valueOf(value.toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? startingWith(args[0], WORDS) : List.of();
    }
}
