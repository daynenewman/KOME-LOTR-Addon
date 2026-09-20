package kome.common.data;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionNpcDeathLifecycleTest {
    private static KOMEProgressionNpcRef ref(String name) { return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),name,"rohan",0,0,0,0); }
    private static void master(KOMESerfKnightProgression state, KOMEProgressionNpcRef master) { assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success); }
    private static void duties(KOMESerfKnightProgression state) { long day=10L; for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values()){assertTrue(KOMESerfKnightService.assignDuty(state,type,null,day++).success);assertTrue(KOMESerfKnightService.completeDuty(state,type).success);} }
    private static void liegeTrial(KOMESerfKnightProgression state,KOMEProgressionNpcRef liege,boolean complete){assertTrue(KOMESerfKnightService.setProspectiveLiege(state,liege).success);assertTrue(KOMESerfKnightService.assignTrial(state,new Random(1),20L).success);if(complete)assertTrue(KOMESerfKnightService.completeTrial(state).success);}

    @Test public void normalMasterDeathCancelsAllUnfinishedDutiesAndAllowsReplacement() {
        KOMESerfKnightProgression state=new KOMESerfKnightProgression(); KOMEProgressionNpcRef old=ref("old"); master(state,old);
        assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.PROVISIONING,null,10L).success);assertTrue(KOMESerfKnightService.completeDuty(state,KOMESerfKnightDutyType.PROVISIONING).success);
        // Simulate a pre-2D/corrupt state: death cleanup must cancel every unfinished duty.
        state.assignDuty(KOMESerfKnightDutyType.PROFESSION,null);
        state.assignDuty(KOMESerfKnightDutyType.COURIER,null);
        assertTrue(KOMESerfKnightService.handleNpcDeath(state,old.entityUuid,false,10));
        assertTrue(state.getDuty(KOMESerfKnightDutyType.PROVISIONING).isCompleted());assertFalse(state.getDuty(KOMESerfKnightDutyType.PROFESSION).isAssigned());assertFalse(state.getDuty(KOMESerfKnightDutyType.COURIER).isAssigned());assertTrue(state.isMasterReplacementRequired());assertFalse(state.getSerfdomMaster().isSet());
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,ref("replacement")).success);
    }

    @Test public void completedTrialSurvivesDeadLiegeAndReplacementMasterCanFinishGift() {
        KOMESerfKnightProgression state=new KOMESerfKnightProgression();KOMEProgressionNpcRef oldMaster=ref("master");master(state,oldMaster);duties(state);KOMEProgressionNpcRef liege=ref("liege");liegeTrial(state,liege,true);
        assertTrue(KOMESerfKnightService.handleNpcDeath(state,liege.entityUuid,false,10));assertTrue(state.isTrialCompleted());assertFalse(state.getProspectiveLiege().isSet());
        assertTrue(KOMESerfKnightService.handleNpcDeath(state,oldMaster.entityUuid,false,10));assertTrue(KOMESerfKnightService.allDutiesComplete(state));
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,ref("replacement")).success);assertTrue(KOMESerfKnightService.recordPartingGift(state).success);assertTrue(KOMESerfKnightService.canPromote(state,150));assertEquals(KOMESerfKnightPhase.READY_FOR_KNIGHT,state.getPhase());
    }

    @Test public void normalAndBetrayalLiegeDeathsDifferAndOneDayLockoutPersists() {
        KOMESerfKnightProgression normal=new KOMESerfKnightProgression(); master(normal,ref("master"));duties(normal);KOMEProgressionNpcRef liege=ref("liege");liegeTrial(normal,liege,false);
        assertTrue(KOMESerfKnightService.handleNpcDeath(normal,liege.entityUuid,false,10));assertEquals("",normal.getTrialId());assertTrue(normal.isLiegeReplacementRequired());assertTrue(KOMESerfKnightService.canSelectReplacement(normal,10));
        KOMESerfKnightProgression proven=new KOMESerfKnightProgression();master(proven,ref("master"));duties(proven);KOMEProgressionNpcRef provenLiege=ref("liege");liegeTrial(proven,provenLiege,true);
        assertTrue(KOMESerfKnightService.handleNpcDeath(proven,provenLiege.entityUuid,false,10));assertTrue(proven.isTrialCompleted());
        KOMESerfKnightProgression betrayal=new KOMESerfKnightProgression();KOMEProgressionNpcRef betrayedMaster=ref("master");master(betrayal,betrayedMaster);duties(betrayal);
        assertTrue(KOMESerfKnightService.handleNpcDeath(betrayal,betrayedMaster.entityUuid,true,20));assertFalse(betrayal.getDuty(KOMESerfKnightDutyType.PROVISIONING).isCompleted());assertFalse(KOMESerfKnightService.canSelectReplacement(betrayal,20));assertTrue(KOMESerfKnightService.canSelectReplacement(betrayal,21));
        KOMESerfKnightProgression restored=new KOMESerfKnightProgression();restored.readFromNBT(betrayal.writeToNBT());assertEquals(21L,restored.getBetrayalLockoutUntilDay());assertTrue(restored.isLockedOut(20));assertFalse(restored.isLockedOut(21));
    }

    @Test public void successionSelectsLowestUuidPrinceAndRecordsFormerKingAtCapital() {
        KOMEWorldData data=new KOMEWorldData("royal");assertTrue(data.initializeIntegratedWorld());UUID king=UUID.randomUUID(),first=UUID.fromString("00000000-0000-0000-0000-000000000001"),second=UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),other=UUID.randomUUID();
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,king,"rohan",KOMEProgressionNpcRank.KING,"King",true).success);
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,second,"rohan",KOMEProgressionNpcRank.PRINCE,"Second",true).success);
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,first,"rohan",KOMEProgressionNpcRank.PRINCE,"First",true).success);
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,other,"gondor",KOMEProgressionNpcRank.PRINCE,"Other",true).success);
        Set<UUID> eligible=new HashSet<UUID>();eligible.add(second);eligible.add(first);eligible.add(other);
        assertEquals(first,KOMEProgressionNpcSuccessionService.handleKingDeath(data,king,eligible));assertEquals(KOMEProgressionNpcRank.KING,data.progressionNpcRanks.get(first).rank);assertFalse(data.progressionNpcRanks.containsKey(king));
        KOMEProgressionNpcRoyalRestoration restoration=data.progressionNpcRoyalRestorations.get(king);assertNotNull(restoration);assertEquals(KOMEProgressionNpcRank.PRINCE.key,restoration.writeToNBT().getString("RestoreRank"));assertEquals(KOMEFactionCapitalService.getCapitalTileId(data,"rohan"),restoration.capitalTileId);
    }

    @Test public void unloadedPrinceRecordsSucceedDeterministicallyAndDeadPrinceIsRemoved() {
        KOMEWorldData firstData=new KOMEWorldData("royal");assertTrue(firstData.initializeIntegratedWorld());UUID king=UUID.randomUUID(),low=UUID.fromString("00000000-0000-0000-0000-000000000010"),high=UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff0");
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(firstData,king,"rohan",KOMEProgressionNpcRank.KING,"King",true).success);assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(firstData,high,"rohan",KOMEProgressionNpcRank.PRINCE,"High",true).success);assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(firstData,low,"rohan",KOMEProgressionNpcRank.PRINCE,"Low",true).success);
        assertEquals(low,KOMEProgressionNpcSuccessionService.handleKingDeath(firstData,king,new HashSet<UUID>()));assertEquals(KOMEProgressionNpcRank.KING,firstData.progressionNpcRanks.get(low).rank);
        KOMEWorldData princeDeath=new KOMEWorldData("royal");UUID deadPrince=UUID.randomUUID();assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(princeDeath,deadPrince,"rohan",KOMEProgressionNpcRank.PRINCE,"Dead",true).success);assertTrue(KOMEProgressionNpcSuccessionService.invalidatePrinceDeath(princeDeath,deadPrince));assertFalse(princeDeath.progressionNpcRanks.containsKey(deadPrince));
    }

    @Test public void playerKingBlocksSuccessionAndNoPrinceMeansNoKing() {
        KOMEWorldData data=new KOMEWorldData("royal");assertTrue(data.initializeIntegratedWorld());UUID king=UUID.randomUUID(),prince=UUID.randomUUID();
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,king,"rohan",KOMEProgressionNpcRank.KING,"King",true).success);assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,prince,"rohan",KOMEProgressionNpcRank.PRINCE,"Prince",true).success);
        assertTrue(KOMERulerService.assignRuler(data,"rohan",UUID.randomUUID(),"Player"));assertNull(KOMEProgressionNpcSuccessionService.handleKingDeath(data,king,new HashSet<UUID>()));assertEquals(KOMEProgressionNpcRank.PRINCE,data.progressionNpcRanks.get(prince).rank);
    }
}
