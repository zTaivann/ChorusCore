package dev.chorus.core.items;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Enchantments looked up by namespaced key rather than by field name.
 *
 * <p>The constants on {@link Enchantment} were renamed more than once between 1.18 and 26,
 * but {@code minecraft:protection} has meant the same thing throughout. The list below holds
 * every vanilla id from either end of that range, including the ones that were renamed and
 * the ones that were added later; whatever the running server does not know is simply left
 * out of {@link #known()}.
 */
public final class Enchantments {

    private static final List<String> VANILLA = List.of(
            "aqua_affinity", "bane_of_arthropods", "binding_curse", "blast_protection",
            "breach", "channeling", "density", "depth_strider", "efficiency", "feather_falling",
            "fire_aspect", "fire_protection", "flame", "fortune", "frost_walker", "impaling",
            "infinity", "knockback", "looting", "loyalty", "luck_of_the_sea", "lure", "mending",
            "multishot", "piercing", "power", "projectile_protection", "protection", "punch",
            "quick_charge", "respiration", "riptide", "sharpness", "silk_touch", "smite",
            "soul_speed", "sweeping", "sweeping_edge", "swift_sneak", "thorns", "unbreaking",
            "vanishing_curse", "wind_burst");

    /** Filled on first use: the set a running server has cannot change while it is up. */
    private static volatile List<String> known;

    private Enchantments() {
    }

    public static @Nullable Enchantment byName(String name) {
        String cleaned = name.toLowerCase(Locale.ROOT).replace(' ', '_');
        int colon = cleaned.indexOf(':');
        if (colon >= 0) {
            cleaned = cleaned.substring(colon + 1);
        }
        return Enchantment.getByKey(NamespacedKey.minecraft(cleaned));
    }

    /** The vanilla ids this server actually has, for tab completion. */
    public static List<String> known() {
        List<String> cached = known;
        if (cached == null) {
            cached = VANILLA.stream().filter(name -> byName(name) != null).toList();
            known = cached;
        }
        return cached;
    }
}
