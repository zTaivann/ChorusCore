package dev.chorus.core.utility.command;

import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.TargetedCommand;
import dev.chorus.core.utility.UtilitySettings;
import dev.chorus.core.utility.UtilityService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public final class HealCommand extends TargetedCommand {

    private final UtilityService utility;

    public HealCommand(CommandSupport support, UtilityService utility) {
        super(support, "heal", "chorus.utility.heal");
        this.utility = utility;
    }

    @Override
    protected void execute(CommandSender sender, Player target) {
        if (!ready(sender)) {
            return;
        }

        UtilitySettings settings = utility.settings();
        UtilitySettings.Heal heal = settings.heal();

        // getMaxHealth() is deprecated in favour of the max health attribute, but that
        // attribute was renamed between 1.18 and 26 and this method was not, so it is the
        // only way to spell this that works across the whole supported range.
        target.setHealth(target.getMaxHealth());

        if (heal.extinguish()) {
            target.setFireTicks(0);
        }
        if (heal.clearEffects()) {
            for (PotionEffect effect : target.getActivePotionEffects()) {
                target.removePotionEffect(effect.getType());
            }
        }
        if (heal.restoreFood()) {
            target.setFoodLevel(20);
            target.setSaturation(settings.feed().saturation());
            target.setExhaustion(0);
        }

        settle(sender);
        announce(sender, target, "utility.healed", "utility.healed-other", "utility.heal-received");
    }
}
