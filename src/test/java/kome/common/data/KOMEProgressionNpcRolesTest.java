package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionNpcRolesTest {
    private static KOMEProgressionNpcRef ref(UUID id){return new KOMEProgressionNpcRef(id.toString(),"Aldor","rohan",0,100,64,100);}

    @Test public void simultaneousMasterAndLiegeRolesReleaseIndependently(){
        KOMEWorldData world=new KOMEWorldData("roles");UUID player=UUID.randomUUID(),npc=UUID.randomUUID();
        KOMESerfKnightProgression state=world.getProgression(player).getSerfKnightProgression();
        state.setSerfdomMaster(ref(npc));state.setProspectiveLiege(ref(npc));KOMEProgressionNpcRoles.syncPlayer(world,player);
        assertEquals(2,world.progressionNpcRoleLeases.get(npc).size());assertTrue(KOMEProgressionNpcRoles.protects(world,npc));
        state.leaveProspectiveLiege();KOMEProgressionNpcRoles.syncPlayer(world,player);
        assertEquals(1,world.progressionNpcRoleLeases.get(npc).size());
        state.leaveSerfdomMaster();KOMEProgressionNpcRoles.syncPlayer(world,player);
        assertFalse(KOMEProgressionNpcRoles.protects(world,npc));
    }

    @Test public void canonicalNbtRebuildsLeaseAndUnloadedReleaseDoesNotReprotect(){
        KOMEWorldData world=new KOMEWorldData("roles");UUID player=UUID.randomUUID(),npc=UUID.randomUUID();
        world.getProgression(player).getSerfKnightProgression().setSerfdomMaster(ref(npc));
        KOMEProgressionNpcRoles.syncPlayer(world,player);
        NBTTagCompound saved=new NBTTagCompound();world.writeToNBT(saved);
        KOMEWorldData reloaded=new KOMEWorldData("roles");reloaded.readFromNBT(saved);
        assertTrue(KOMEProgressionNpcRoles.protects(reloaded,npc));
        reloaded.getProgression(player).getSerfKnightProgression().leaveSerfdomMaster();
        KOMEProgressionNpcRoles.syncPlayer(reloaded,player);
        assertFalse(KOMEProgressionNpcRoles.protects(reloaded,npc));
        NBTTagCompound after=new NBTTagCompound();reloaded.writeToNBT(after);
        KOMEWorldData next=new KOMEWorldData("roles");next.readFromNBT(after);
        assertFalse(KOMEProgressionNpcRoles.protects(next,npc));
    }

    @Test public void roleProjectionNeverClaimsVanillaPersistence(){
        KOMEWorldData world=new KOMEWorldData("roles");UUID player=UUID.randomUUID(),npc=UUID.randomUUID();
        world.getProgression(player).getSerfKnightProgression().setSerfdomMaster(ref(npc));KOMEProgressionNpcRoles.rebuild(world);
        assertTrue(KOMEProgressionNpcRoles.protects(world,npc));
        assertFalse(KOMEProgressionNpcRoles.protects(world,UUID.randomUUID()));
        assertEquals(npc.toString(),world.progressionNpcRoleLeases.get(npc).iterator().next().token);
    }
    @Test public void activeEscortAndDefenseObjectivesHoldThenReleaseTheirExactUuid(){
        for(String trialId:new String[]{"escort","defense"}){
            KOMEWorldData world=new KOMEWorldData("roles");UUID player=UUID.randomUUID(),npc=UUID.randomUUID();
            KOMESerfKnightProgression state=world.getProgression(player).getSerfKnightProgression();
            KOMESerfKnightTrialAssignment seed=KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId(trialId),ref(UUID.randomUUID()),12L,0);
            NBTTagCompound encounter=new NBTTagCompound();encounter.setTag("escort".equals(trialId)?"EscortTarget":"DefenseObjective",ref(npc).writeToNBT());
            state.setTrial(seed.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,encounter));
            KOMEProgressionNpcRoles.syncPlayer(world,player);
            assertTrue(trialId,KOMEProgressionNpcRoles.protects(world,npc));
            assertEquals(seed.assignmentToken,world.progressionNpcRoleLeases.get(npc).iterator().next().token);
            state.updateTrialAssignment(state.getTrialAssignment().withStage(KOMESerfKnightTrialAssignment.Stage.FAILED,null));
            KOMEProgressionNpcRoles.syncPlayer(world,player);
            assertFalse(trialId,KOMEProgressionNpcRoles.protects(world,npc));
        }
    }
    @Test public void loadedNpcUsesIndexedRolesAndFinalReleaseRestoresNativeDecision() throws Exception {
        kome.common.KOMEAccessFixture fixture=new kome.common.KOMEAccessFixture();UUID npcId=UUID.randomUUID();
        KOMECourierBoundedRecoveryTest.TestNpc npc=kome.common.KOMEAccessFixture.allocate(KOMECourierBoundedRecoveryTest.TestNpc.class);
        npc.id=npcId;npc.setUniqueID(npcId);npc.worldObj=fixture.world;
        fixture.data.getProgression(fixture.player.id).getSerfKnightProgression().setSerfdomMaster(ref(npcId));
        npc.isNPCPersistent=true;assertTrue(npc.isNPCPersistent);
        KOMEProgressionNpcRoles.syncPlayer(fixture.data,fixture.player.id);
        assertTrue(KOMEProgressionNpcRoles.preventDespawn(npc));
        fixture.data.getProgression(fixture.player.id).getSerfKnightProgression().leaveSerfdomMaster();
        KOMEProgressionNpcRoles.syncPlayer(fixture.data,fixture.player.id);
        assertFalse(KOMEProgressionNpcRoles.preventDespawn(npc));
        assertTrue("KOME must never clear persistence owned by another feature",npc.isNPCPersistent);
    }
}
