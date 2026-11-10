package dev.chorus.core.players.command;

import dev.chorus.core.command.CommandSupport;
import org.bukkit.WeatherType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Like {@link PlayerTimeCommand} for weather. Clients can only be shown clear or rain. */
public final class PlayerWeatherCommand extends PersonalViewCommand {

    public PlayerWeatherCommand(CommandSupport support) {
        super(support, "pweather", "chorus.players.pweather");
    }

    @Override
    protected String usageKey() {
        return "players.pweather-usage";
    }

    @Override
    protected List<String> values() {
        return List.of("clear", "rain", "reset");
    }

    @Override
    protected void apply(CommandSender sender, Player target, String value) {
        switch (value) {
            case "reset" -> {
                if (!ready(sender)) {
                    return;
                }
                target.resetPlayerWeather();
                settle(sender);
                report(sender, target, "players.pweather-reset",
                        "players.pweather-reset-other", value);
            }
            case "clear", "rain" -> {
                if (!ready(sender)) {
                    return;
                }
                target.setPlayerWeather(value.equals("rain") ? WeatherType.DOWNFALL : WeatherType.CLEAR);
                settle(sender);
                report(sender, target, "players.pweather-set",
                        "players.pweather-set-other", value);
            }
            default -> messages.send(sender, "players.pweather-unknown", "value", value);
        }
    }
}
