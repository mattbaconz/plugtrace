package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionStormBudgetTest {
    @Test
    void limitsFullStacksPerSecondThenResets() {
        ExceptionStormBudget budget = new ExceptionStormBudget(2);
        assertTrue(budget.tryAcquireFullStack(1_000L));
        assertTrue(budget.tryAcquireFullStack(1_100L));
        assertFalse(budget.tryAcquireFullStack(1_200L));
        assertTrue(budget.tryAcquireFullStack(2_000L));
    }
}
