package dev.chorus.core.economy;

import java.util.UUID;

/** One transfer, as it happened. */
public record Payment(UUID payer, String payerName, UUID payee, String payeeName,
                      double amount, long paidAt) {
}
