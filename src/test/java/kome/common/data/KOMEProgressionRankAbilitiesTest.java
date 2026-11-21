package kome.common.data;

import kome.common.KOMEAccessFixture;
import kome.common.data.KOMEProgressionPermissionRegistry.Ability;
import kome.common.data.KOMEProgressionAbilityPolicy.Availability;
import kome.common.data.KOMEProgressionAbilityPolicy.Outcome;
import kome.common.network.KOMEPacketProgressionData;
import kome.common.network.KOMEPopulationWire;
import io.netty.buffer.Unpooled;
import io.netty.buffer.ByteBuf;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;
import com.lotrcharactercreation.race.PlayerRaceData;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class KOMEProgressionRankAbilitiesTest {
    @Test public void catalogHasUniqueStableIdsAndNoFictionalRewards() {
        Set<String> ids=new HashSet<>();
        for(Ability a:Ability.values()) {assertTrue(ids.add(a.id));assertSame(a,Ability.forId(a.id));assertFalse(a.enforcementSite.isEmpty());}
        assertNull(Ability.forId("fief.grant"));assertNull(Ability.forId("warfare.capstone"));assertNull(Ability.forId("king"));
    }
    @Test public void rankInheritanceIgnoresHistoricalFlagsAndDisabledMode() {
        KOMEWorldData data=new KOMEWorldData("abilities");UUID actor=UUID.randomUUID();
        KOMEPlayerProgression progression=data.getProgression(actor);
        progression.grant("baseline.hire_units");progression.grant("knight.title_lord");
        assertFalse(KOMEProgressionAbilityPolicy.unlocked(data,actor,Ability.CAMPAIGN_RECRUITMENT));
        for(KOMEProgressionRank rank:KOMEProgressionRank.values()) {
            KOMECanonicalRankService.setCanonicalRank(data,actor,rank);
            for(Ability ability:Ability.values())assertEquals(rank.order>=ability.minimumRank.order,KOMEProgressionAbilityPolicy.unlocked(data,actor,ability));
        }
        data.setProgressionEnabled(false);
        KOMECanonicalRankService.setCanonicalRank(data,actor,KOMEProgressionRank.KNIGHT);
        assertFalse(KOMEProgressionPermissions.campaignRecruitmentDenial(data,actor,"gondor",false).isEmpty());
    }
    @Test public void eligibilityReadDoesNotCreatePlayerState() {
        KOMEWorldData data=new KOMEWorldData("read-only");
        assertFalse(KOMEProgressionAbilityPolicy.unlocked(data,UUID.randomUUID(),Ability.BUILD_SUBMISSION));
        assertTrue(data.progressions.isEmpty());
    }
    @Test public void warfareEligibilityIsRankDerivedWithoutInventedRewards() {
        KOMEWorldData data=new KOMEWorldData("warfare");UUID actor=UUID.randomUUID();
        assertFalse(KOMEProgressionAbilityPolicy.warfareRankEligible(data,actor));
        KOMECanonicalRankService.setCanonicalRank(data,actor,KOMEProgressionRank.KNIGHT);
        int existingHistory=data.getProgression(actor).getCompletedCount("baseline");
        assertTrue(KOMEProgressionAbilityPolicy.warfareRankEligible(data,actor));
        assertEquals(existingHistory,data.getProgression(actor).getCompletedCount("baseline"));
    }
    @Test public void kingOfficeIsSeparateFromRank() {
        KOMEWorldData data=new KOMEWorldData("office");UUID actor=UUID.randomUUID();
        data.lastKnownPlayerFactions.put(actor,"gondor");KOMERulerService.assignRuler(data,"gondor",actor,"King");
        assertEquals(KOMEProgressionRank.WANDERER,KOMEProgressionAbilityPolicy.rank(data,actor));
        assertTrue(KOMERulerAuthorization.canActAsRuler(data,"gondor",actor));
        assertFalse(KOMEProgressionAbilityPolicy.unlocked(data,actor,Ability.CAMPAIGN_RECRUITMENT));
    }
    @Test public void princeDoesNotBecomeKingOrReceivePopulation() {
        KOMEWorldData data=new KOMEWorldData("prince");UUID actor=UUID.randomUUID();
        KOMECanonicalRankService.setCanonicalRank(data,actor,KOMEProgressionRank.PRINCE);
        assertFalse(KOMERulerAuthorization.canActAsRuler(data,"gondor",actor));
        assertEquals(0L,KOMEPopulationService.getAvailablePopulationCenti(data,"gondor"));
        assertTrue(KOMEProgressionAbilityPolicy.unlocked(data,actor,Ability.BATTLE_SERVICE));
    }
    @Test public void lordEligibilityAndCrownDenialAreDistinctAndShared() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.GONDOR);
        KOMECanonicalRankService.setCanonicalRank(f.data,f.player.id,KOMEProgressionRank.LORD);
        f.data.lastKnownPlayerFactions.put(f.player.id,"gondor");
        Outcome row=row(f,Ability.CAMPAIGN_RECRUITMENT);
        assertTrue(row.rankUnlocked);assertEquals(Availability.UNAVAILABLE,row.availability);
        assertEquals(KOMEProgressionPermissions.campaignRecruitmentDenial(f.data,f.player.id,"gondor",false),row.reason);
        assertTrue(row.reason.contains("Crown"));
    }
    @Test public void crownWithEmptyBankShowsResourceDenialWithoutRevokingEligibility() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.GONDOR);
        KOMECanonicalRankService.setCanonicalRank(f.data,f.player.id,KOMEProgressionRank.LORD);
        KOMERulerService.assignRuler(f.data,"gondor",f.player.id,"King");
        Outcome row=row(f,Ability.CAMPAIGN_RECRUITMENT);
        assertTrue(row.rankUnlocked);assertEquals("Not enough faction population.",row.reason);
        f.data.grantFactionPopulationCenti("gondor",10000L);
        assertEquals(Availability.CONTEXT_REQUIRED,row(f,Ability.CAMPAIGN_RECRUITMENT).availability);
    }
    @Test public void factionChangeDoesNotCreateCrownSpendingAuthority() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.GONDOR);
        KOMECanonicalRankService.setCanonicalRank(f.data,f.player.id,KOMEProgressionRank.LORD);
        KOMERulerService.assignRuler(f.data,"gondor",f.player.id,"King");
        f.pledge(LOTRFaction.ROHAN);
        f.data.lastKnownPlayerFactions.put(f.player.id,"rohan");
        assertTrue(row(f,Ability.CAMPAIGN_RECRUITMENT).rankUnlocked);
        assertTrue(row(f,Ability.CAMPAIGN_RECRUITMENT).reason.contains("Crown"));
        assertFalse(KOMEProgressionPermissions.campaignRecruitmentDenial(f.data,f.player.id,"gondor",false).isEmpty());
    }
    @Test public void existingOwnedCompanySurvivesRankCorrectionAndCommandMaintenance() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.GONDOR);
        KOMEArmyCompany company=new KOMEArmyCompany();company.id="C1";company.owner=f.player.id;company.faction="gondor";
        f.data.armyCompanies.put(company.id,company);
        KOMECanonicalRankService.setCanonicalRank(f.data,f.player.id,KOMEProgressionRank.LORD);
        assertEquals(Availability.CONTEXT_REQUIRED,row(f,Ability.COMPANY_COMMAND).availability);
        KOMECanonicalRankService.setCanonicalRank(f.data,f.player.id,KOMEProgressionRank.SERF);
        assertTrue(KOMECompanyCommandAuthority.canPersonallyCommand(f.data,company,f.player.id));
        assertSame(company,f.data.armyCompanies.get("C1"));
        assertFalse(KOMEProgressionAbilityPolicy.unlocked(f.data,f.player.id,Ability.COMPANY_COMMAND));
    }
    @Test public void newCommandAppointmentRequiresLordButContinuationSurvivesCorrection() {
        KOMEWorldData data=new KOMEWorldData("appointment");UUID ruler=UUID.randomUUID(),recipient=UUID.randomUUID();
        data.lastKnownPlayerFactions.put(ruler,"gondor");data.lastKnownPlayerFactions.put(recipient,"gondor");
        KOMERulerService.assignRuler(data,"gondor",ruler,"King");
        KOMECanonicalRankService.setCanonicalRank(data,recipient,KOMEProgressionRank.KNIGHT);
        assertEquals("Requires Lord standing.",KOMECompanyDiplomacyAuthorization.canStartDelegation(data,"gondor",ruler,recipient).reason);
        KOMECanonicalRankService.setCanonicalRank(data,recipient,KOMEProgressionRank.LORD);
        assertTrue(KOMECompanyDiplomacyAuthorization.canStartDelegation(data,"gondor",ruler,recipient).allowed);
        KOMECanonicalRankService.setCanonicalRank(data,recipient,KOMEProgressionRank.SERF);
        assertTrue(KOMECompanyDiplomacyAuthorization.canContinueDelegation(data,"gondor",recipient).allowed);
    }
    @Test public void ownershipTransferRechecksRankAndPreservesOwnerOnDeniedAcceptance() {
        KOMEWorldData data=new KOMEWorldData("transfer");UUID owner=UUID.randomUUID(),recipient=UUID.randomUUID();
        data.lastKnownPlayerFactions.put(owner,"gondor");data.lastKnownPlayerFactions.put(recipient,"gondor");
        KOMEArmyCompany company=new KOMEArmyCompany();company.owner=owner;company.faction="gondor";
        KOMECanonicalRankService.setCanonicalRank(data,recipient,KOMEProgressionRank.KNIGHT);
        assertFalse(KOMECompanyTransferService.offer(data,company,owner,recipient,"Recipient",10L).success);
        assertNull(company.transferRecipient);
        KOMECanonicalRankService.setCanonicalRank(data,recipient,KOMEProgressionRank.LORD);
        assertTrue(KOMECompanyTransferService.offer(data,company,owner,recipient,"Recipient",10L).success);
        KOMECanonicalRankService.setCanonicalRank(data,recipient,KOMEProgressionRank.KNIGHT);
        assertFalse(KOMECompanyTransferService.accept(data,company,recipient,"Recipient",11L).success);
        assertEquals(owner,company.owner);assertEquals(recipient,company.transferRecipient);
    }
    @Test public void rankPageShowsOnlyCurrentAndNextRankWithoutLegacyBaselineList() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.GONDOR);
        KOMECanonicalRankService.setCanonicalRank(f.data,f.player.id,KOMEProgressionRank.KNIGHT);
        List<Outcome> rows=KOMEProgressionAbilityPolicy.project(f.data,f.player);
        assertEquals(Ability.values().length,rows.size());
        for(Outcome row:rows) {
            Ability ability=Ability.forId(row.abilityId);
            if(ability.minimumRank==KOMEProgressionRank.LORD){assertFalse(row.rankUnlocked);assertEquals("Requires Lord standing.",row.reason);}
            assertFalse(row.abilityId.startsWith("baseline."));
        }
        KOMECanonicalRankService.setCanonicalRank(f.data,f.player.id,KOMEProgressionRank.WANDERER);
        for(Outcome row:KOMEProgressionAbilityPolicy.project(f.data,f.player)) {
            KOMEProgressionRank floor=Ability.forId(row.abilityId).minimumRank;
            assertTrue(floor==KOMEProgressionRank.COMMONER||floor==KOMEProgressionRank.KNIGHT);
            assertEquals(floor==KOMEProgressionRank.COMMONER,row.rankUnlocked);
        }
    }
    @Test public void standardEquipmentDoesNotPretendToUnlockExceptionalMaterials() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.GONDOR);
        KOMECanonicalRankService.setCanonicalRank(f.data,f.player.id,KOMEProgressionRank.KNIGHT);
        assertTrue(row(f,Ability.MILITARY_EQUIPMENT).reason.contains("Exceptional"));
        assertFalse(KOMEProgressionPermissions.has(f.player,KOMEProgressionPermissions.MITHRIL_GEAR));
        assertTrue(KOMEGearRestrictionService.evaluatePlayer(f.player,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_sword),KOMEGearRestrictionService.Action.USE).allowed);
    }
    @Test public void missingStartIsNotReplacedWithFactionCapital() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.GONDOR);
        KOMECanonicalRankService.setCanonicalRank(f.data,f.player.id,KOMEProgressionRank.SERF);
        assertNull(KOMEWaypointAccessService.originalStartingWaypoint(f.player));
        assertEquals(Availability.UNAVAILABLE,row(f,Ability.START_RETURN).availability);
    }
    @Test public void commonerStartingReturnRequiresExactSavedAppliedWaypoint() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.GONDOR);
        PlayerRaceData.setStartingWaypointCodeName(f.player,LOTRWaypoint.MINAS_TIRITH.getCodeName());
        assertNull(KOMEWaypointAccessService.originalStartingWaypoint(f.player));
        PlayerRaceData.setStartingWaypointApplied(f.player,true);
        assertSame(LOTRWaypoint.MINAS_TIRITH,KOMEWaypointAccessService.originalStartingWaypoint(f.player));
        assertFalse(KOMEWaypointAccessService.hasTravelPermission(f.player,LOTRWaypoint.MINAS_TIRITH));
        assertEquals(KOMEProgressionRank.COMMONER,f.data.getProgression(f.player.id).getCanonicalRank());
        assertTrue(KOMEWaypointAccessService.hasTravelPermission(f.player,LOTRWaypoint.MINAS_TIRITH));
        assertFalse(KOMEWaypointAccessService.hasTravelPermission(f.player,LOTRWaypoint.EDORAS));
        KOMECanonicalRankService.setCanonicalRank(f.data,f.player.id,KOMEProgressionRank.WANDERER);
        assertTrue(KOMEWaypointAccessService.hasTravelPermission(f.player,LOTRWaypoint.MINAS_TIRITH));
    }
    @Test public void abilityPacketRoundTripCopyAndProtocolFence() {
        KOMEPacketProgressionData source=new KOMEPacketProgressionData("Name",Collections.emptyList());
        List<Outcome> rows=new ArrayList<>();
        rows.add(new Outcome(Ability.CAMPAIGN_RECRUITMENT.id,true,Availability.UNAVAILABLE,"Requires Crown authority.",""));
        source.abilities(rows);rows.clear();
        ByteBuf bytes=Unpooled.buffer();source.toBytes(bytes);
        KOMEPacketProgressionData decoded=new KOMEPacketProgressionData();decoded.fromBytes(bytes);
        assertEquals(1,decoded.copy().abilities.size());
        assertEquals(source.abilities.toString(),decoded.abilities.toString());
        assertFalse(KOMEPopulationWire.accepts("1.0.9-integration-g8"));
        bytes.release();
    }
    @Test public void lockedAndUnavailableAreStructurallyDifferent() {
        Outcome locked=new Outcome(Ability.CAMPAIGN_RECRUITMENT.id,false,Availability.LOCKED,"Requires Lord standing.","");
        Outcome unavailable=new Outcome(Ability.CAMPAIGN_RECRUITMENT.id,true,Availability.UNAVAILABLE,"Requires Crown authority.","");
        assertFalse(locked.tooltip().contains("Standing earned"));assertTrue(unavailable.tooltip().contains("Standing earned"));
    }
    @Test(expected=IllegalArgumentException.class) public void contradictoryOutcomeIsRejected() {
        new Outcome(Ability.BATTLE_SERVICE.id,true,Availability.LOCKED,"","");
    }
    static Outcome row(KOMEAccessFixture f,Ability ability) {
        for(Outcome row:KOMEProgressionAbilityPolicy.project(f.data,f.player))if(ability.id.equals(row.abilityId))return row;
        throw new AssertionError(ability);
    }
}
