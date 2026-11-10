package dev.chorus.core.config;

import dev.chorus.core.command.ChargeOn;
import dev.chorus.core.command.CooldownScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The lookup the config readers share: exact names, and null rather than an exception. */
class EnumsTest {

    @Test
    void anExactNameIsFoundAndAnythingElseIsNot() {
        for (CooldownScope scope : CooldownScope.values()) {
            assertEquals(scope, Enums.named(CooldownScope.values(), scope.name()));
            assertEquals(scope, CooldownScope.of(" " + scope.name().toLowerCase() + " "));
        }
        for (ChargeOn moment : ChargeOn.values()) {
            assertEquals(moment, ChargeOn.of(moment.name().toLowerCase()));
        }
        assertNull(Enums.named(CooldownScope.values(), "nothing"));
        assertNull(ChargeOn.of("nothing"));
    }
}
