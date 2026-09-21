package dev.chorus.core.economy;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** How money is written down and how far it is allowed to go. */
public record Currency(String symbol, boolean suffix, int decimals, String singular, String plural,
                       double startingBalance, double minimum, double maximum) {

    private static final int MAX_DECIMALS = 4;

    public static Currency read(ConfigurationSection economy) {
        ConfigurationSection currency = economy.getConfigurationSection("currency");
        int decimals = currency == null ? 2 : currency.getInt("decimals", 2);
        return new Currency(
                currency == null ? "$" : currency.getString("symbol", "$"),
                currency != null && currency.getBoolean("suffix", false),
                Math.max(0, Math.min(MAX_DECIMALS, decimals)),
                currency == null ? "coin" : currency.getString("singular", "coin"),
                currency == null ? "coins" : currency.getString("plural", "coins"),
                Math.max(0, economy.getDouble("starting-balance", 0)),
                economy.getDouble("minimum-balance", 0),
                Math.max(0, economy.getDouble("maximum-balance", 1_000_000_000_000d)));
    }

    public String format(double amount) {
        DecimalFormat format = new DecimalFormat(pattern(), DecimalFormatSymbols.getInstance(Locale.ROOT));
        String written = format.format(round(amount));
        return suffix ? written + symbol : symbol + written;
    }

    /** The word that goes with an amount, for the lines that read "5 coins" rather than "$5". */
    public String name(double amount) {
        return Math.abs(amount - 1) < 1e-9 ? singular : plural;
    }

    public double round(double amount) {
        return BigDecimal.valueOf(amount).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    /** Keeps a balance inside the range the config allows. */
    public double clamp(double amount) {
        return Math.max(minimum, Math.min(maximum, round(amount)));
    }

    public boolean wouldOverflow(double amount) {
        return round(amount) > maximum;
    }

    private String pattern() {
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append('.');
            pattern.append("0".repeat(decimals));
        }
        return pattern.toString();
    }
}
