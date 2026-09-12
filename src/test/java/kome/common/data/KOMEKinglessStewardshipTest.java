package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/** KOM-31: kingless stewardship is a temporary controller authority, never ownership transfer. */
public class KOMEKinglessStewardshipTest {
    @Test public void friendsSameSideWarAllowsRecognizedSupportingRulerWithoutStageFour() {
        KOMEWorldData data = worldWithFriends("rohan", "gondor");
        UUID king = crown(data, "gondor");
        KOMEWar war = KOMEWarService.createWar(data, "rohan", "mordor", "", "test", 10L);
        war.addFaction(1, "gondor");
        assertTrue(KOMEWarService.supportingKingDecision(data, "rohan", "gondor", king).allowed);
        assertTrue(new KOMEAllianceAuthority(data).getEffectiveStage("gondor", "rohan") < 4);
    }

    @Test public void authorizationRejectsNativeRulerNonRulerAndActiveOpposition() {
        KOMEWorldData data = worldWithFriends("rohan", "gondor");
        UUID king = crown(data, "gondor");
        UUID member = UUID.randomUUID(); data.lastKnownPlayerFactions.put(member, "gondor");
        KOMEWar sameSide = KOMEWarService.createWar(data, "rohan", "mordor", "", "test", 10L);
        sameSide.addFaction(1, "gondor");
        assertFalse(KOMEWarService.supportingKingDecision(data, "rohan", "gondor", member).allowed);
        assertTrue(KOMEWarService.supportingKingDecision(data, "rohan", "gondor", king).allowed);
        KOMEWarService.createWar(data, "rohan", "gondor", "opposition", "test", 11L);
        assertFalse(KOMEWarService.supportingKingDecision(data, "rohan", "gondor", king).allowed);
        assertTrue(KOMERulerService.assignRuler(data, "rohan", UUID.randomUUID(), "Native"));
        assertFalse(KOMEWarService.supportingKingDecision(data, "rohan", "gondor", king).allowed);
    }

    @Test public void revocationPreservesNativeOwnershipAndRepairIsIdempotent() {
        KOMEWorldData data = worldWithFriends("rohan", "gondor");
        UUID king = crown(data, "gondor");
        KOMEWar war = KOMEWarService.createWar(data, "rohan", "mordor", "", "test", 10L);
        war.addFaction(1, "gondor");
        KOMEArmyCompany company = company("c", "rohan", king);
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID(); unit.owner = UUID.randomUUID(); unit.sourceFaction = "rohan";
        unit.populationOwningFaction = "rohan"; unit.unitFaction = "rohan"; unit.companyId = "wrong";
        unit.sourceType = KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION;
        unit.benefitSource = "MILITARY_T3_STEWARDSHIP";
        company.units.add(unit.entity); data.hiredUnits.put(unit.entity, unit); data.armyCompanies.put(company.id, company);
        KOMEWartimeStewardshipService.authorizeCompany(data, company, "gondor", "test", 11L);
        int grantsAfterAuthorization = countAudits(data, "action=STEWARDSHIP_GRANTED");
        assertEquals("rohan", company.nativeFaction);
        assertEquals("rohan", unit.populationOwningFaction);
        assertEquals(1, KOMEWartimeStewardshipService.reconcileDefensiveUnits(data, "rohan", 12L).repairedLinks);
        assertEquals(0, KOMEWartimeStewardshipService.reconcileDefensiveUnits(data, "rohan", 13L).repairedLinks);
        assertEquals(grantsAfterAuthorization, countAudits(data, "action=STEWARDSHIP_GRANTED"));
        war.removeFaction("gondor");
        assertFalse(KOMEWartimeStewardshipService.revalidateCompany(data, company, 14L, "membership removed"));
        assertEquals("rohan", company.nativeFaction);
        assertEquals("rohan", unit.populationOwningFaction);
        assertNull(company.temporaryController);
    }

    @Test public void invalidStewardshipControllerIsRevokedBeforeRepair() {
        KOMEWorldData data = worldWithFriends("rohan", "gondor");
        UUID stale = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(stale, "gondor");
        KOMEArmyCompany company = company("stale", "rohan", stale);
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID(); unit.unitFaction = "rohan";
        unit.sourceType = KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION;
        unit.benefitSource = "MILITARY_T3_STEWARDSHIP";
        unit.companyId = "wrong"; company.units.add(unit.entity);
        data.hiredUnits.put(unit.entity, unit); data.armyCompanies.put(company.id, company);
        KOMEWartimeStewardshipService.RepairResult result =
            KOMEWartimeStewardshipService.reconcileDefensiveUnits(data, "rohan", 20L);
        assertTrue(result.allowed);
        assertNull(company.temporaryController);
        assertEquals("wrong", unit.companyId);
        assertEquals(KOMEArmyCompany.AUTHORITY_NATIVE, company.controllerAuthority);
    }

    @Test public void rulerRestorationRevokesStewardshipAndPersistsNativeCompany() {
        KOMEWorldData data = worldWithFriends("rohan", "gondor");
        UUID king = crown(data, "gondor");
        KOMEWar war = KOMEWarService.createWar(data, "rohan", "mordor", "", "test", 10L);
        war.addFaction(1, "gondor");
        KOMEArmyCompany company = company("c", "rohan", king);
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID(); unit.owner = UUID.randomUUID(); unit.unitFaction = "rohan";
        unit.sourceFaction = "rohan"; unit.companyId = company.id; company.units.add(unit.entity);
        data.hiredUnits.put(unit.entity, unit); data.armyCompanies.put(company.id, company);
        KOMEWartimeStewardshipService.authorizeCompany(data, company, "gondor", "test", 11L);
        crown(data, "rohan");
        assertNull(company.temporaryController);
        assertEquals("rohan", company.nativeFaction);
        NBTTagCompound nbt = company.writeToNBT();
        KOMEArmyCompany reloaded = new KOMEArmyCompany(); reloaded.readFromNBT(nbt);
        assertEquals("rohan", reloaded.nativeFaction);
    }

    private static KOMEWorldData worldWithFriends(String first, String second) {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEDiplomacyRecord record = new KOMEDiplomacyRecord(first, second);
        record.relation = KOMEDiplomacyRelation.FRIENDS;
        data.canonicalDiplomacyRecords.put(record.key(), record);
        return data;
    }
    private static UUID crown(KOMEWorldData data, String faction) {
        UUID id = UUID.randomUUID(); data.lastKnownPlayerFactions.put(id, faction);
        assertTrue(KOMERulerService.assignRuler(data, faction, id, "King")); return id;
    }
    private static KOMEArmyCompany company(String id, String faction, UUID controller) {
        KOMEArmyCompany company = new KOMEArmyCompany(); company.id = id; company.faction = faction;
        company.nativeFaction = faction; company.temporaryController = controller;
        company.temporaryControllerName = "King"; company.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP;
        return company;
    }
    private static int countAudits(KOMEWorldData data, String marker) {
        int count = 0;
        for (String entry : data.companyDelegationAudit) if (entry.contains(marker)) count++;
        return count;
    }
}
