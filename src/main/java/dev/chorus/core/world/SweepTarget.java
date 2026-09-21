package dev.chorus.core.world;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Monster;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WaterMob;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** What {@code /sweep} is being asked to clear out. */
public enum SweepTarget {

    DROPS("drops", entity -> entity instanceof Item),
    EXPERIENCE("experience", entity -> entity instanceof ExperienceOrb),
    ARROWS("arrows", entity -> entity instanceof AbstractArrow),
    BOATS("boats", entity -> entity instanceof Boat),
    MINECARTS("minecarts", entity -> entity instanceof Minecart),
    VEHICLES("vehicles", entity -> entity instanceof Boat || entity instanceof Minecart),
    MONSTERS("monsters", entity -> entity instanceof Monster),
    ANIMALS("animals", entity -> entity instanceof Animals && !tamed(entity)),
    AMBIENT("ambient", entity -> entity instanceof Ambient || entity instanceof WaterMob),
    TAMED("tamed", SweepTarget::tamed),
    NAMED("named", entity -> entity.customName() != null),
    VILLAGERS("villagers", entity -> entity instanceof NPC),
    ARMOUR_STANDS("armourstands", entity -> entity instanceof ArmorStand),
    FRAMES("frames", entity -> entity instanceof ItemFrame),
    PAINTINGS("paintings", entity -> entity instanceof Painting),

    /** Everything that can be spawned, which is not everything that can be built. */
    MOBS("mobs", entity -> (entity instanceof Monster || entity instanceof Animals
            || entity instanceof Ambient || entity instanceof WaterMob) && !tamed(entity)),

    EVERYTHING("all", entity -> !(entity instanceof ArmorStand) && !(entity instanceof Hanging)
            && !(entity instanceof NPC));

    private final String label;
    private final Predicate<Entity> matches;

    SweepTarget(String label, Predicate<Entity> matches) {
        this.label = label;
        this.matches = matches;
    }

    public String label() {
        return label;
    }

    /** A player is never swept, whichever target was asked for. */
    public boolean covers(Entity entity) {
        return !(entity instanceof Player) && matches.test(entity);
    }

    public static List<String> labels() {
        return java.util.Arrays.stream(values()).map(SweepTarget::label).toList();
    }

    /** The named target, or null when the word is meant to be an entity type instead. */
    public static SweepTarget of(String value) {
        String wanted = value.toLowerCase(Locale.ROOT);
        for (SweepTarget target : values()) {
            if (target.label.equals(wanted)) {
                return target;
            }
        }
        return null;
    }

    /** One kind of mob by name, for the times a server is drowning in exactly one thing. */
    public static Predicate<Entity> ofType(EntityType type) {
        return entity -> !(entity instanceof Player) && entity.getType() == type;
    }

    private static boolean tamed(Entity entity) {
        return entity instanceof Tameable tameable && tameable.isTamed();
    }
}
