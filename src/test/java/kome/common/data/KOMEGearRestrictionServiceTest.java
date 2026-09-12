package kome.common.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Deterministic policy coverage; live hooks are deliberately thin adapters over this service. */
public class KOMEGearRestrictionServiceTest {
    @Test public void requiredMaterialMappingsAreCentralAndStable() {
        assertCategory("lotr:swordMithril", KOMEGearRestrictionService.Category.MITHRIL);
        assertCategory("lotr:swordUtumno", KOMEGearRestrictionService.Category.UTUMNO);
        assertCategory("lotr:swordGondolin", KOMEGearRestrictionService.Category.GONDOLIN);
        assertCategory("lotr:swordMallorn", KOMEGearRestrictionService.Category.MALLORN);
        assertCategory("lotr:morgulBlade", KOMEGearRestrictionService.Category.MORGUL);
        assertCategory("lotr:helmetGalvorn", KOMEGearRestrictionService.Category.GALVORN);
        assertCategory("minecraft:stone_pickaxe", KOMEGearRestrictionService.Category.STONE_TOOL);
    }

    @Test public void playerDecisionsSeparateUseFromPossessionAndUseCanonicalPermissions() {
        KOMEGearRestrictionService.Rule mithril = KOMEGearRestrictionService.ruleForId("lotr:swordMithril");
        assertTrue(KOMEGearRestrictionService.evaluate(null, KOMEGearRestrictionService.Action.USE, true, "gondor").allowed);
        assertTrue(KOMEGearRestrictionService.evaluate(mithril, KOMEGearRestrictionService.Action.POSSESS, false, "mordor").allowed);
        KOMEGearRestrictionService.Decision denied = KOMEGearRestrictionService.evaluate(mithril, KOMEGearRestrictionService.Action.USE, false, "gondor");
        assertFalse(denied.allowed); assertEquals(KOMEGearRestrictionService.Reason.MISSING_PROGRESSION, denied.reason); assertTrue(denied.playerMessage().contains("progression"));
        assertTrue(KOMEGearRestrictionService.evaluate(mithril, KOMEGearRestrictionService.Action.USE, true, "gondor").allowed);
        // There is no ruler input to the policy: office cannot replace the required progression permission.
        assertFalse(KOMEGearRestrictionService.evaluate(mithril, KOMEGearRestrictionService.Action.USE, false, "gondor").allowed);
        assertEquals(KOMEProgressionPermissions.MITHRIL_GEAR, mithril.gearPermission);
        assertEquals(KOMEProgressionPermissions.UTUMNO_GEAR, KOMEGearRestrictionService.ruleForId("lotr:swordUtumno").gearPermission);
    }

    @Test public void factionNpcBossAndFutureRulesUseTheSameDataModel() {
        Set<String> elves = new HashSet<String>(); elves.add("gondolin");
        KOMEGearRestrictionService.Rule rule = new KOMEGearRestrictionService.Rule(KOMEGearRestrictionService.Category.FUTURE,
                "example:legend", "baseline.faction_gear", "baseline.faction_armor", elves, true, false);
        assertFalse(KOMEGearRestrictionService.evaluate(rule, KOMEGearRestrictionService.Action.USE, true, "mordor").allowed);
        assertTrue(KOMEGearRestrictionService.evaluate(rule, KOMEGearRestrictionService.Action.EQUIP_ARMOR, true, "gondolin").allowed);
        assertFalse(KOMEGearRestrictionService.evaluateNpc(rule, "mordor").allowed);
        assertTrue(KOMEGearRestrictionService.evaluateNpc(rule, "gondolin").allowed);
        KOMEGearRestrictionService.Rule boss = new KOMEGearRestrictionService.Rule(KOMEGearRestrictionService.Category.BOSS,
                "example:boss", "", "", Collections.<String>emptySet(), false, true);
        assertEquals(KOMEGearRestrictionService.Reason.BOSS_EXCLUSIVE, KOMEGearRestrictionService.evaluate(boss, KOMEGearRestrictionService.Action.USE, true, "").reason);
        assertTrue(KOMEGearRestrictionService.evaluate(boss, KOMEGearRestrictionService.Action.DISPLAY, false, "").allowed);
        Map<String, KOMEGearRestrictionService.Rule> extended = KOMEGearRestrictionService.withRule(Collections.<String, KOMEGearRestrictionService.Rule>emptyMap(), rule);
        assertSame(rule, extended.get("example:legend"));
    }

    @Test public void progressionRegistryShowsSingleGearAndStoneworkEnforcementAuthority() {
        assertEquals(KOMEProgressionPermissionRegistry.Status.CANONICAL_ENFORCED,
                KOMEProgressionPermissionRegistry.gate(KOMEProgressionPermissions.FACTION_GEAR).status);
        assertTrue(KOMEProgressionPermissionRegistry.gate(KOMEProgressionPermissions.STONEWORK).enforcementSite.contains("KOMEGearRestrictionService"));
        KOMEGearRestrictionService.Rule stone = KOMEGearRestrictionService.ruleForId("minecraft:stone_pickaxe");
        assertEquals(KOMEGearRestrictionService.Reason.MISSING_PROGRESSION,
                KOMEGearRestrictionService.evaluate(stone, KOMEGearRestrictionService.Action.USE, false, "").reason);
    }

    private static void assertCategory(String id, KOMEGearRestrictionService.Category category) {
        assertNotNull(id, KOMEGearRestrictionService.ruleForId(id));
        assertEquals(category, KOMEGearRestrictionService.ruleForId(id).category);
    }
}
