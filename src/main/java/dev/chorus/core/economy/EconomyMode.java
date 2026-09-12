package dev.chorus.core.economy;

import java.util.Locale;

/** Where the money comes from. */
public enum EconomyMode {

    /** The built-in ledger, unless another economy plugin has registered with Vault. */
    AUTO,

    /** The built-in ledger, whatever else is installed. */
    SELF,

    /** Another plugin's economy, through Vault. Prices are ignored if there is none. */
    VAULT,

    /** No economy at all, so every price is ignored. */
    OFF;

    public static EconomyMode parse(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "self", "built-in", "builtin", "internal" -> SELF;
            case "vault", "external" -> VAULT;
            case "off", "none", "false", "disabled" -> OFF;
            default -> AUTO;
        };
    }
}
