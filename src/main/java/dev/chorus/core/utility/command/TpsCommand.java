package dev.chorus.core.utility.command;

import dev.chorus.core.command.ChorusCommand;
import dev.chorus.core.command.CommandSupport;
import dev.chorus.core.command.Durations;
import org.bukkit.command.CommandSender;

import java.lang.management.ManagementFactory;
import java.util.Locale;

/** How the server is doing: ticks, tick time, uptime and memory. */
public final class TpsCommand extends ChorusCommand {

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    public TpsCommand(CommandSupport support) {
        super(support, "tps", "chorus.utility.tps");
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        if (!ready(sender)) {
            return;
        }
        settle(sender);

        double[] rates = sender.getServer().getTPS();
        messages.send(sender, "utility.tps-header");
        messages.send(sender, "utility.tps-rates",
                "one", format(rates, 0),
                "five", format(rates, 1),
                "fifteen", format(rates, 2));
        messages.send(sender, "utility.tps-tick",
                "millis", String.format(Locale.ROOT, "%.2f", sender.getServer().getAverageTickTime()));
        messages.send(sender, "utility.tps-uptime",
                "time", Durations.format(ManagementFactory.getRuntimeMXBean().getUptime()));

        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEGABYTE;
        messages.send(sender, "utility.tps-memory",
                "used", String.valueOf(used),
                "max", String.valueOf(runtime.maxMemory() / BYTES_PER_MEGABYTE));
    }

    /**
     * A server that has just caught up reports a little over twenty, which reads as a fault
     * rather than the good news it is, so the figure is capped.
     */
    private static String format(double[] rates, int index) {
        if (index >= rates.length) {
            return "?";
        }
        return String.format(Locale.ROOT, "%.2f", Math.min(20.0, rates[index]));
    }
}
