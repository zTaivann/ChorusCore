package dev.chorus.core.feedback;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** What a command looks and sounds like when it works. */
public record CommandFeedback(SoundCue sound, ParticleCue particle) {

    public static final CommandFeedback NONE = new CommandFeedback(SoundCue.NONE, ParticleCue.NONE);

    public static CommandFeedback read(ConfigurationSection command, CommandFeedback base,
                                       Consumer<String> onUnknownParticle) {
        return new CommandFeedback(
                SoundCue.read(command, base.sound()),
                ParticleCue.read(command, base.particle(), onUnknownParticle));
    }

    public void play(Player player) {
        Location at = player.getLocation();
        sound.play(player, at);
        particle.show(at);
    }

    /** Used for the puff of particles a player leaves behind when a teleport fires. */
    public void showAt(Location where) {
        particle.show(where);
    }
}
