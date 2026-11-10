package dev.chorus.core.economy;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * How amounts are written and how far a balance may go.
 *
 * @param thousands  separator between digit groups, or {@code ""}
 * @param decimal    decimal separator
 * @param shortUnits letters for thousands, millions and so on, empty for full amounts
 */
public record Currency(String symbol, boolean suffix, int decimals, String thousands,
                       String decimal, List<String> shortUnits, String singular, String plural,
                       double startingBalance, double minimum, double maximum) {

    private static final int MAX_DECIMALS = 4;
    private static final List<String> UNITS = List.of("K", "M", "B", "T");

    public static Currency read(ConfigurationSection economy) {
        ConfigurationSection currency = economy.getConfigurationSection("currency");
        int decimals = currency == null ? 2 : currency.getInt("decimals", 2);
        boolean shortForm = currency != null && currency.getBoolean("short-form", false);
        return new Currency(
                currency == null ? "$" : currency.getString("symbol", "$"),
                currency != null && currency.getBoolean("suffix", false),
                Math.max(0, Math.min(MAX_DECIMALS, decimals)),
                currency == null ? "," : currency.getString("thousands-separator", ","),
                separator(currency == null ? "." : currency.getString("decimal-separator", ".")),
                shortForm ? units(currency.getStringList("short-units")) : List.of(),
                currency == null ? "coin" : currency.getString("singular", "coin"),
                currency == null ? "coins" : currency.getString("plural", "coins"),
                Math.max(0, economy.getDouble("starting-balance", 0)),
                economy.getDouble("minimum-balance", 0),
                Math.max(0, economy.getDouble("maximum-balance", 1_000_000_000_000d)));
    }

    public String format(double amount) {
        double rounded = round(amount);
        String written = shortUnits.isEmpty() || Math.abs(rounded) < 1000
                ? full(rounded)
                : shortened(rounded);
        return suffix ? written + symbol : symbol + written;
    }

    /** The word for an amount, such as "5 coins". */
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

    private String full(double amount) {
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append('.').append("0".repeat(decimals));
        }
        DecimalFormat format = new DecimalFormat(pattern.toString(), symbols());
        format.setGroupingUsed(!thousands.isEmpty());
        return format.format(amount);
    }

    /** 1.5K, 2.25M: two decimals at most, and none that are only zeros. */
    private String shortened(double amount) {
        DecimalFormat format = new DecimalFormat("0.##", symbols());
        format.setRoundingMode(RoundingMode.HALF_UP);
        int unit = 0;
        double scaled = amount;
        while (unit < shortUnits.size() && Math.abs(scaled) >= 1000) {
            scaled /= 1000;
            unit++;
        }
        // 999,999 rounds up to 1000K, which reads better as 1M.
        double shown = BigDecimal.valueOf(scaled).setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (unit < shortUnits.size() && Math.abs(shown) >= 1000) {
            scaled /= 1000;
            unit++;
        }
        return format.format(scaled) + shortUnits.get(unit - 1);
    }

    private DecimalFormatSymbols symbols() {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        if (!thousands.isEmpty()) {
            symbols.setGroupingSeparator(thousands.charAt(0));
        }
        symbols.setDecimalSeparator(decimal.charAt(0));
        return symbols;
    }

    private static String separator(String written) {
        return written.isEmpty() ? "." : written;
    }

    private static List<String> units(List<String> written) {
        return written.isEmpty() ? UNITS : List.copyOf(written);
    }
}
