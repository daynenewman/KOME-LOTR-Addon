package kome.common.data;

import java.util.*;
import lotr.common.entity.npc.LOTREntityNPC;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEKnightHelpServiceTest {
    private static KOMEKnightCommission field(KOMELordshipTrialFixture f){
        KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(f.s,f.lord,Type.GO_FOR_HELP);KOMEKnightFieldService.profile(a,2);a.complication=Complication.NONE;a.stage=Stage.ACTIVE;a.y=65;a.encounterCreated=true;a.civilianClass="Civilian";a.enemyFaction="mordor";for(int i=0;i<3;i++)a.enemyClasses.add("Enemy");f.s.f.player.posX=a.x;f.s.f.player.posZ=a.z;return a;
    }
    @Test public void reinforcementJourneyRequiresWitnessCommanderAndPhysicalReturnWithoutDuplicateTroops()throws Exception{
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            KOMEKnightCommission a=field(f);a.destinationProof=KOMEProgressionGameplayFixture.shelter(f.s.f.world,(int)a.x,(int)a.z,"rohan");
            KOMEProgressionServiceLocationTest.generatedHome(f.liege,"RohanFortress");a.helpCommander=KOMEProgressionNpcRankService.referenceOf(f.liege);
            LOTREntityNPC witness=KOMEKnightCommissionGameplayTest.actor(f.s,a,Role.BENEFICIARY);
            assertFalse(KOMEKnightHelpService.interact(f.s.f.player,f.liege));assertTrue(KOMEKnightHelpService.interact(f.s.f.player,witness));assertEquals(Phase.SEEK_HELP,a.phase);
            f.s.f.player.posX=f.liege.posX;f.s.f.player.posZ=f.liege.posZ;assertTrue(KOMEKnightHelpService.interact(f.s.f.player,f.liege));assertEquals(Phase.RETURN_WITH_HELP,a.phase);assertEquals(4,a.actors.stream().filter(actor->actor.role==Role.GUARD).count());assertFalse(KOMEKnightHelpService.interact(f.s.f.player,f.liege));
            f.s.reload();a=f.s.p.getKnightService().assignment();assertTrue(KOMEKnightHelpService.tick(f.s.f.player,a));assertEquals(Phase.RETURN_WITH_HELP,a.phase);
            f.s.f.player.posX=a.x;f.s.f.player.posZ=a.z;assertTrue(KOMEKnightHelpService.tick(f.s.f.player,a));assertEquals("Player arrival alone is insufficient",Phase.RETURN_WITH_HELP,a.phase);
            for(Actor actor:a.actors)if(actor.role==Role.GUARD){LOTREntityNPC guard=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,actor.id);assertFalse(guard.hiredNPCInfo.teleportAutomatically);guard.posX=a.x;guard.posZ=a.z;}
            assertTrue(KOMEKnightHelpService.tick(f.s.f.player,a));assertEquals(Phase.ENGAGE,a.phase);long count=a.actors.stream().filter(actor->actor.role==Role.ENEMY).count();assertEquals(3,count);assertTrue(KOMEKnightHelpService.tick(f.s.f.player,a));assertEquals(count,a.actors.stream().filter(actor->actor.role==Role.ENEMY).count());
            for(Actor actor:new ArrayList<Actor>(a.actors))if(actor.role==Role.ENEMY)KOMEKnightFieldServiceTest.kill(f.s,a,(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,actor.id));assertEquals(Stage.READY_TO_REPORT,a.stage);assertTrue(f.s.f.data.hiredUnits.isEmpty());assertTrue(f.s.f.data.armyCompanies.isEmpty());
        }
    }
    @Test public void helpNeverInventsACommanderOrIgnoresHisDeath()throws Exception{
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            KOMEKnightCommission a=KOMEKnightFieldServiceTest.field(f.s,Type.GO_FOR_HELP,2);assertFalse(KOMEKnightHelpService.contact(f.s.f.world,a).isSet());a.helpCommander=KOMEProgressionNpcRankService.referenceOf(f.liege);a.phase=Phase.SEEK_HELP;
            KOMEKnightCommissionService.npcDeath(f.s.f.data,f.s.f.world,f.liege.getUniqueID().toString());assertEquals(Stage.FAILED,a.stage);assertTrue(f.s.p.getKnightService().qualifyingTypes("rohan").isEmpty());
        }
    }
    @Test public void unloadedCommanderWaitsAndLoadedTechnicalLossCanRecover()throws Exception{
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            KOMEKnightCommission a=KOMEKnightFieldServiceTest.field(f.s,Type.GO_FOR_HELP,2);a.helpCommander=KOMEProgressionNpcRankService.referenceOf(f.liege);a.phase=Phase.SEEK_HELP;f.s.f.world.loadedEntityList.remove(f.liege);f.s.f.player.posX=f.liege.posX;f.s.f.player.posZ=f.liege.posZ;
            f.s.chunks=false;for(int i=0;i<80;i++)assertTrue(KOMEKnightHelpService.tick(f.s.f.player,a));assertEquals(0,a.helpMissingTicks);f.s.chunks=true;for(int i=0;i<59;i++)assertTrue(KOMEKnightHelpService.tick(f.s.f.player,a));assertFalse(KOMEKnightHelpService.tick(f.s.f.player,a));assertEquals(Stage.ACTIVE,a.stage);
        }
    }
}
