package dev.chorus.core.command;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which worlds a command works in.
 *
 * <p>One list does both jobs. A name on its own is the only place the command works; a name
 * with {@code !} in front is the one place it does not:
 *
 * <pre>
 * worlds: [ ]                        everywhere, which is the default
 * worlds: [ world, world_nether ]    only those two
 * worlds: [ '!event' ]               everywhere except the event world
 * </pre>
 *
 * <p>Names are compared without case, since a world folder and the name typed in a config
 * rarely agree on it.
 */
public record WorldRule(Set<String> allowed, Set<String> denied) {

    public static final WorldRule EVERYWHERE = new WorldRule(Set.of(), Set.of());

    private static final String NOT = "!";

    public boolean allows(String world) {
        if (allowed.isEmpty() && denied.isEmpty()) {
            return true;
        }
        String name = world.toLowerCase(Locale.ROOT);
        if (denied.contains(name)) {
            return false;
        }
        return allowed.isEmpty() || allowed.contains(name);
    }

    /** True when this rule lets the command run somewhere but not everywhere. */
    public boolean restricted() {
        return !allowed.isEmpty() || !denied.isEmpty();
    }

    /** The block's own list, or the one it inherits when it does not write one down. */
    public static WorldRule read(ConfigurationSection block, WorldRule base) {
        if (!block.contains("worlds")) {
            return base;
        }

        Set<String> allowed = new HashSet<>();
        Set<String> denied = new HashSet<>();
        for (String entry : block.getStringList("worlds")) {
            String name = entry.trim().toLowerCase(Locale.ROOT);
            if (name.startsWith(NOT)) {
                denied.add(name.substring(NOT.length()).trim());
            } else if (!name.isEmpty()) {
                allowed.add(name);
            }
        }
        return new WorldRule(Set.copyOf(allowed), Set.copyOf(denied));
    }

    /** For the checks, which read the list without a server to ask about worlds. */
    public static WorldRule of(List<String> entries) {
        ConfigurationSection block = new MemoryConfiguration();
        block.set("worlds", entries);
        return read(block, EVERYWHERE);
    }
}
