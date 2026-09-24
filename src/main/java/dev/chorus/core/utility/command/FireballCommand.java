package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Numbers;
import dev.chorus.core.command.PlayerCommand;
import dev.chorus.core.utility.UtilityService;
import dev.chorus.core.utility.UtilitySettings;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.WitherSkull;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code /fireball [kind] [speed]}: throws something from where you are looking. */
public final class FireballCommand extends PlayerCommand {

    private static final Map<String, Class<? extends Projectile>> KINDS = kinds();
    private static final double DEFAULT_SPEED = 2;
    private static final double MAX_SPEED = 10;

    private final UtilityService utility;

    public FireballCommand(CommandSupport support, UtilityService utility) {
        super(support, "fireball", "chorus.utility.fireball");
        this.utility = utility;
    }

    @Override
    protected void execute(Player player, String[] args) {
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "fireball";
        Class<? extends Projectile> kind = KINDS.get(name);
        if (kind == null) {
            messages.send(player, "utility.fireball-usage",
                    "kinds", String.join(", ", KINDS.keySet()));
            return;
        }

        double speed = args.length > 1 ? speed(args[1]) : DEFAULT_SPEED;
        if (speed <= 0) {
            messages.send(player, "utility.fireball-speed", "max", String.valueOf((int) MAX_SPEED));
            return;
        }
        if (!ready(player)) {
            return;
        }

        Vector heading = player.getEyeLocation().getDirection().multiply(speed);
        Projectile shot = player.launchProjectile(kind, heading);
        if (shot instanceof Explosive explosive) {
            UtilitySettings.Fireball settings = utility.settings().fireball();
            explosive.setYield(settings.blast());
            explosive.setIsIncendiary(settings.incendiary());
        }

        settle(player);
        messages.send(player, "utility.fireball", "kind", name);
    }

    private static double speed(String raw) {
        double value = Numbers.decimal(raw.replace(',', '.'), -1);
        return value > 0 ? Math.min(MAX_SPEED, value) : -1;
    }

    private static Map<String, Class<? extends Projectile>> kinds() {
        Map<String, Class<? extends Projectile>> kinds = new LinkedHashMap<>();
        kinds.put("fireball", LargeFireball.class);
        kinds.put("small", SmallFireball.class);
        kinds.put("dragon", DragonFireball.class);
        kinds.put("skull", WitherSkull.class);
        kinds.put("arrow", Arrow.class);
        kinds.put("snowball", Snowball.class);
        kinds.put("egg", Egg.class);
        kinds.put("pearl", EnderPearl.class);
        // Not Map.copyOf: the order they are written in is the order the usage line lists them.
        return Collections.unmodifiableMap(kinds);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return startingWith(args[0], KINDS.keySet());
        }
        return args.length == 2 ? startingWith(args[1], List.of("1", "2", "4")) : List.of();
    }
}
