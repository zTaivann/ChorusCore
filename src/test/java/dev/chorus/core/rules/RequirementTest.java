package dev.chorus.core.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequirementTest {

    @Test
    void balanceIsReadUnderItsEarlierNameToo() {
        assertEquals(Requirement.Kind.BALANCE, Requirement.of("balance: 500", null).kind());
        assertEquals(Requirement.Kind.BALANCE, Requirement.of("money: 500", null).kind());
        assertEquals("500", Requirement.of("Money: 500", null).argument());
    }

    @Test
    void anUnknownKindIsNotARequirement() {
        assertNull(Requirement.of("coins: 500", null));
        assertNull(Requirement.of("balance 500", null));
    }
}
