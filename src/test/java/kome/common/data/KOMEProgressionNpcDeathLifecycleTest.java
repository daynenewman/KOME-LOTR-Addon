package kome.common.data;
import net.minecraft.nbt.NBTTagCompound;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionNpcDeathLifecycleTest {
    @org.junit.Rule
    public final KOMETileTestResources geometry =
        new KOMETileTestResources();
    private static KOMEProgressionNpcRef ref(String name) { return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),name,"rohan",0,0,0,0); }
    private static void master(KOMESerfKnightProgression state, KOMEProgressionNpcRef master) { assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master).success); }
    private static void duties(KOMESerfKnightProgression state) { long day=10L; for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values()){assertTrue(KOMESerfKnightService.assignDuty(state,type,null,day++).success);assertTrue(KOMESerfKnightService.completeDuty(state,type).success);} }
    private static void liegeTrial(KOMESerfKnightProgression state,KOMEProgressionNpcRef liege,boolean complete){assertTrue(KOMESerfKnightService.commitLiegeForTrial(state,liege).success);assertTrue(KOMESerfKnightService.assignTrial(state,new Random(1),20L).success);if(complete)assertTrue(KOMESerfKnightService.completeTrial(state).success);}

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
        assertTrue(KOMESerfKnightService.handleNpcDeath(state,liege.entityUuid,false,10));assertTrue(state.isTrialCompleted());assertFalse(state.getLiege().isSet());
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

    @Test public void kingDeathKeepsCanonicalIdentityAndDoesNotPromotePrinces() {
        KOMEWorldData data=new KOMEWorldData("royal");assertTrue(data.initializeIntegratedWorld());
        UUID king=UUID.randomUUID(),prince=UUID.randomUUID();
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,king,"rohan",KOMEProgressionNpcRank.KING,"King",true).success);
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,prince,"rohan",KOMEProgressionNpcRank.PRINCE,"Prince",true).success);
        assertNull(KOMEProgressionNpcSuccessionService.handleKingDeath(data,king));
        assertEquals(KOMEProgressionNpcRank.KING,data.progressionNpcRanks.get(king).rank);
        assertEquals(KOMEProgressionNpcRank.PRINCE,data.progressionNpcRanks.get(prince).rank);
        assertEquals(600000L,data.progressionNpcRanks.get(king).respawnRemainingMillis);
        assertTrue(data.progressionNpcRoyalRestorations.isEmpty());
    }
    @Test public void restartRetainsRemainingRuntimeAndPrinceDeathStillInvalidatesPrince() {
        KOMEWorldData data=new KOMEWorldData("royal");assertTrue(data.initializeIntegratedWorld());UUID king=UUID.randomUUID();
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,king,"rohan",KOMEProgressionNpcRank.KING,"King",true).success);
        KOMEProgressionRulerService.noteDeath(data,king);KOMEProgressionRulerService.advance(data.progressionNpcRanks.get(king),180000);
        NBTTagCompound saved=new NBTTagCompound();data.writeToNBT(saved);KOMEWorldData loaded=new KOMEWorldData("royal");loaded.readFromNBT(saved);
        assertEquals(420000L,loaded.progressionNpcRanks.get(king).respawnRemainingMillis);
        assertFalse(KOMEProgressionRulerService.noteDeath(loaded,king));
        UUID prince=UUID.randomUUID();assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(loaded,prince,"rohan",KOMEProgressionNpcRank.PRINCE,"Prince",true).success);
        assertTrue(KOMEProgressionNpcSuccessionService.invalidatePrinceDeath(loaded,prince));assertFalse(loaded.progressionNpcRanks.containsKey(prince));
    }
    @Test public void playerKingBlocksSuccessionAndNoPrinceMeansNoKing() {
        KOMEWorldData data=new KOMEWorldData("royal");assertTrue(data.initializeIntegratedWorld());UUID king=UUID.randomUUID(),prince=UUID.randomUUID();
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,king,"rohan",KOMEProgressionNpcRank.KING,"King",true).success);assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,prince,"rohan",KOMEProgressionNpcRank.PRINCE,"Prince",true).success);
        assertTrue(KOMERulerService.assignRuler(data,"rohan",UUID.randomUUID(),"Player"));assertNull(KOMEProgressionNpcSuccessionService.handleKingDeath(data,king,new HashSet<UUID>()));assertEquals(KOMEProgressionNpcRank.PRINCE,data.progressionNpcRanks.get(prince).rank);
    }
}
