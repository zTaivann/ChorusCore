package dev.chorus.core.feedback;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** A burst of particles at a position, sent to everyone who can see it. */
public record ParticleCue(@Nullable Particle particle, int count, double spread, double height,
                          double speed) {

    public static final ParticleCue NONE = new ParticleCue(null, 0, 0, 0, 0);

    private static final Map<String, String> RENAMES = Map.ofEntries(
            Map.entry("DUST", "REDSTONE"),
            Map.entry("REDSTONE", "DUST"),
            Map.entry("SMOKE", "SMOKE_NORMAL"),
            Map.entry("SMOKE_NORMAL", "SMOKE"),
            Map.entry("LARGE_SMOKE", "SMOKE_LARGE"),
            Map.entry("SMOKE_LARGE", "LARGE_SMOKE"),
            Map.entry("HAPPY_VILLAGER", "VILLAGER_HAPPY"),
            Map.entry("VILLAGER_HAPPY", "HAPPY_VILLAGER"),
            Map.entry("ANGRY_VILLAGER", "VILLAGER_ANGRY"),
            Map.entry("VILLAGER_ANGRY", "ANGRY_VILLAGER"),
            Map.entry("ENCHANT", "ENCHANTMENT_TABLE"),
            Map.entry("ENCHANTMENT_TABLE", "ENCHANT"),
            Map.entry("FIREWORK", "FIREWORKS_SPARK"),
            Map.entry("FIREWORKS_SPARK", "FIREWORK"),
            Map.entry("WITCH", "SPELL_WITCH"),
            Map.entry("SPELL_WITCH", "WITCH"),
            Map.entry("TOTEM_OF_UNDYING", "TOTEM"),
            Map.entry("TOTEM", "TOTEM_OF_UNDYING"),
            Map.entry("INSTANT_EFFECT", "SPELL_INSTANT"),
            Map.entry("SPELL_INSTANT", "INSTANT_EFFECT"),
            Map.entry("ENTITY_EFFECT", "SPELL_MOB"),
            Map.entry("SPELL_MOB", "ENTITY_EFFECT"),
            Map.entry("EFFECT", "SPELL"),
            Map.entry("SPELL", "EFFECT"),
            Map.entry("SPLASH", "WATER_SPLASH"),
            Map.entry("WATER_SPLASH", "SPLASH"),
            Map.entry("BUBBLE", "WATER_BUBBLE"),
            Map.entry("WATER_BUBBLE", "BUBBLE"));

    /**
     * Anything the block leaves out is taken from {@code base}, which is the defaults block.
     * {@code onUnknown} receives names this server has under neither spelling.
     */
    public static ParticleCue read(ConfigurationSection parent, ParticleCue base,
                                   Consumer<String> onUnknown) {
        ConfigurationSection particle = parent.getConfigurationSection("particle");
        if (particle == null) {
            return base;
        }

        String name = particle.getString("name");
        return new ParticleCue(
                name == null ? base.particle() : resolve(name, onUnknown),
                Math.max(0, particle.getInt("count", base.count())),
                Math.max(0, particle.getDouble("spread", base.spread())),
                Math.max(0, particle.getDouble("height", base.height())),
                Math.max(0, particle.getDouble("speed", base.speed())));
    }

    public void show(Location where) {
        World world = where.getWorld();
        if (particle == null || count <= 0 || world == null) {
            return;
        }
        // Lift the burst to chest height so it wraps the player instead of pooling at their feet.
        world.spawnParticle(particle, where.clone().add(0, height / 2, 0),
                count, spread, height / 2, spread, speed);
    }

    private static @Nullable Particle resolve(String name, Consumer<String> onUnknown) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String wanted = name.trim().toUpperCase(Locale.ROOT);
        Particle found = byName(wanted);
        if (found == null) {
            String otherSpelling = RENAMES.get(wanted);
            if (otherSpelling != null) {
                found = byName(otherSpelling);
            }
        }
        if (found == null) {
            onUnknown.accept(name);
        }
        return found;
    }

    private static @Nullable Particle byName(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException unknownOnThisVersion) {
            return null;
        }
    }
}
