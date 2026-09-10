package dev.chorus.core.economy;

import java.util.UUID;

/**
 * One transfer, as it happened.
 *
 * <p>Both names are stored alongside the ids. A log is read months later, often about someone
 * who has since left, and looking a name up then would mean either a web request or a blank.
 */
public record Payment(UUID payer, String payerName, UUID payee, String payeeName,
                      double amount, long paidAt) {
}
