package com.lotrcharactercreation.trait;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UrukHeavyBlowRulesTest {

    @Test
    public void minimumRageGatesEmpoweredChanceOnTheServer() {
        assertEquals(0.0F, UrukHeavyBlowRules.chance(0.0F, 50.0F, 100.0F), 0.0F);
        assertEquals(0.0F, UrukHeavyBlowRules.chance(25.0F, 50.0F, 100.0F), 0.0F);
        assertEquals(0.0F, UrukHeavyBlowRules.chance(49.0F, 50.0F, 100.0F), 0.0F);
        assertEquals(0.0F, UrukHeavyBlowRules.chance(49.999F, 50.0F, 100.0F), 0.0F);
        assertEquals(0.0F, UrukHeavyBlowRules.chance(Float.NaN, 50.0F, 100.0F), 0.0F);
        assertEquals(0.50F, UrukHeavyBlowRules.chance(50.0F, 50.0F, 100.0F), 0.0F);
        assertEquals(0.75F, UrukHeavyBlowRules.chance(75.0F, 50.0F, 100.0F), 0.0F);
        assertEquals(1.0F, UrukHeavyBlowRules.chance(100.0F, 50.0F, 100.0F), 0.0F);
    }

    @Test
    public void successfulHeavyBlowSpendsExactlyFiftyRage() {
        assertEquals(0.0F, UrukHeavyBlowRules.remainingRage(50.0F, 50.0F), 0.0F);
        assertEquals(25.0F, UrukHeavyBlowRules.remainingRage(75.0F, 50.0F), 0.0F);
        assertEquals(50.0F, UrukHeavyBlowRules.remainingRage(100.0F, 50.0F), 0.0F);
    }
}
