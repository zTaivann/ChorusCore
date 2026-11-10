package dev.chorus.core.kits;

import dev.chorus.commons.command.Durations;
import dev.chorus.core.economy.Economy;
import dev.chorus.core.locale.Messages;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

/** A kit's settings as the editor shows them, in the reader's language. */
public record KitWords(Messages messages, Economy economy) {

    public String cooldown(Player player, Kit kit) {
        return kit.cooldownSeconds() == 0
                ? messages.plain(player, "kits.word-none")
                : Durations.format(TimeUnit.SECONDS.toMillis(kit.cooldownSeconds()));
    }

    public String claims(Player player, Kit kit) {
        return kit.maxClaims() == 0
                ? messages.plain(player, "kits.word-unlimited")
                : String.valueOf(kit.maxClaims());
    }

    public String price(Player player, Kit kit) {
        return kit.price() <= 0 ? messages.plain(player, "kits.word-free") : economy.format(kit.price());
    }

    public String word(Player player, boolean value) {
        return messages.plain(player, value ? "kits.word-true" : "kits.word-false");
    }
}
