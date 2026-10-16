package kome.common.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

/** Exercises occupied placement and the real per-NPC/player lifecycle, not just initial spawn counts. */
public class KOMEPatrolFollowerLifecycleTest {
    private static KOMEKnightCommission offer(KOMELordshipTrialFixture f) throws Exception {
        KOMEProgressionHardeningGeographyTest.territory(f);
        f.s.p.getSerfKnightProgression().setLiege(KOMEProgressionNpcRankService.referenceOf(f.lord));
        KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(f.s,f.lord,Type.FIELD_COMMAND);
        KOMEKnightFieldService.profile(a,2);
        a.complication=Complication.NONE;
        a.civilianClass="Guard";
        a.enemyFaction="mordor";
        a.enemyClasses.add("Enemy");
        a.adminForced=true;
        a.rewardReserved=true;
        f.s.p.getKnightService().testing(true);
        f.s.f.player.operator=true;
        return a;
    }

    private static LOTREntityNPC guard(KOMELordshipTrialFixture f) throws Exception {
        LOTREntityNPC npc=KOMEAccessFixture.allocate(PatrolGuard.class);
        npc.worldObj=f.s.f.world;
        npc.setUniqueID(UUID.randomUUID());
        npc.width=.6F;
        npc.height=1.8F;
        npc.preventEntitySpawning=true;
        KOMEKnightCommissionGameplayTest.setField(Entity.class,npc,"boundingBox",
            net.minecraft.util.AxisAlignedBB.getBoundingBox(-.3,65,-.3,.3,66.8,.3));
        KOMEKnightCommissionGameplayTest.setField(net.minecraft.entity.EntityLiving.class,npc,"equipment",new net.minecraft.item.ItemStack[5]);
        KOMEKnightCommissionGameplayTest.setField(LOTREntityNPC.class,npc,"initFestiveItems",true);
        KOMEKnightCommissionGameplayTest.setField(LOTREntityNPC.class,npc,"festiveItems",new net.minecraft.item.ItemStack[5]);
        npc.hiredNPCInfo=new KOMELordshipTrialFixture.Follower(npc);
        return npc;
    }

    private static void factory(KOMELordshipTrialFixture f) {
        KOMEKnightCommissionService.npcFactory=(world,type)->{
            try{return guard(f);}catch(Exception e){throw new RuntimeException(e);}
        };
        f.s.f.world.checkEntityOccupancy=true;
    }

    private static List<LOTREntityNPC> followers(KOMELordshipTrialFixture f,KOMEKnightCommission a) {
        List<LOTREntityNPC> result=new ArrayList<LOTREntityNPC>();
        for(Actor actor:a.actors)if(actor.role==Role.GUARD)
            result.add((LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,actor.id));
        return result;
    }

    private static Set<String> ids(KOMEKnightCommission a) {
        Set<String> ids=new HashSet<String>();
        for(Actor actor:a.actors)if(actor.role==Role.GUARD)ids.add(actor.id);
        return ids;
    }

    private static void update(KOMELordshipTrialFixture f,int ticks) {
        KOMEEvents hooks=new KOMEEvents();
        for(int tick=0;tick<ticks;tick++) {
            f.s.f.world.testWorldTime++;
            for(Object value:new ArrayList<Object>(f.s.f.world.loadedEntityList))
                if(value instanceof PatrolGuard&&!((Entity)value).isDead) {
                    PatrolGuard npc=(PatrolGuard)value;
                    npc.ticksExisted++;
                    hooks.onLivingUpdate(new LivingEvent.LivingUpdateEvent(npc));
                }
            if(tick%20==0)KOMEKnightCommissionService.tickPlayer(f.s.f.player);
        }
    }

    @Test public void occupiedFirstSpotStillProducesFourStableOwnedFollowers() throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()) {
            KOMEKnightCommission a=offer(f);factory(f);
            double[] occupied=KOMEKnightCommissionService.safeSite(f.s.f.world,(int)f.s.f.player.posX,(int)f.s.f.player.posZ+3);
            LOTREntityNPC bystander=guard(f);
            bystander.setLocationAndAngles(occupied[0],occupied[1],occupied[2],0,0);
            f.s.f.world.loadedEntityList.add(bystander);
            assertTrue(KOMEKnightCommissionService.acceptOrReport(f.s.f.player,f.lord));
            assertEquals("An occupied first spot must not roll back the patrol",4,a.actors.size());
            Set<String> original=ids(a);
            for(LOTREntityNPC npc:followers(f,a)) {
                assertTrue(KOMEKnightCommissionService.activeEscort(f.s.f.data,npc));
                assertFalse(npc.boundingBox.intersectsWith(bystander.boundingBox));
            }
            update(f,1200);
            assertEquals(original,ids(a));
            assertFalse(a.activationPending);
            for(LOTREntityNPC npc:followers(f,a))assertTrue(npc.isEntityAlive());
            assertTrue(bystander.isEntityAlive());
            assertTrue(f.s.f.data.hiredUnits.isEmpty());
            assertTrue(f.s.f.data.armyCompanies.isEmpty());
        }
    }

    @Test public void partialPreparationFailureDoesNotAutomaticallySpawnAndDespawnAgain() throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()) {
            KOMEKnightCommission a=offer(f);final int[] attempts={0};
            KOMEKnightCommissionService.npcFactory=(world,type)->{
                try{return ++attempts[0]%3==0?null:guard(f);}catch(Exception e){throw new RuntimeException(e);}
            };
            assertTrue(KOMEKnightCommissionService.acceptOrReport(f.s.f.player,f.lord));
            assertTrue(a.activationPending);
            assertTrue(a.actors.isEmpty());
            int initialAttempts=attempts[0];
            update(f,2000); // Includes the retry deadline: near-Liege polling is not a new acceptance.
            assertEquals("Pending patrol preparation must wait for fresh orders",initialAttempts,attempts[0]);
            assertTrue(f.s.f.world.loadedEntityList.stream().filter(e->e instanceof PatrolGuard).allMatch(e->((Entity)e).isDead));
            assertTrue(KOMEKnightCommissionPresentation.objective(a).contains("Return to your Liege"));
            assertFalse(KOMEKnightCommissionPresentation.objective(a).contains("approach the marked ground"));
        }
    }

    @Test public void waitFollowReloadAndEndKeepIdentityAndCleanOnlyOwnedPatrol() throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()) {
            KOMEKnightCommission a=offer(f);factory(f);
            assertTrue(KOMEKnightCommissionService.acceptOrReport(f.s.f.player,f.lord));
            assertEquals(4,a.actors.size());
            Set<String> original=ids(a);
            LOTREntityNPC soldier=followers(f,a).get(0);
            f.s.f.player.posX=soldier.posX;f.s.f.player.posY=soldier.posY;f.s.f.player.posZ=soldier.posZ;
            f.s.f.player.setSneaking(true);
            assertTrue(KOMEProgressionEscortFollowing.interact(f.s.f.player,soldier));
            assertTrue(soldier.hiredNPCInfo.isHalted());
            update(f,80);
            assertTrue(soldier.hiredNPCInfo.isActive);
            assertTrue(soldier.isEntityAlive());
            assertTrue(soldier.hiredNPCInfo.isHalted());
            f.s.f.player.setSneaking(false);
            assertTrue(KOMEProgressionEscortFollowing.interact(f.s.f.player,soldier));
            assertFalse(soldier.hiredNPCInfo.isHalted());
            NBTTagCompound saved=f.s.p.writeToNBT();
            f.s.p.readFromNBT(saved);KOMEProgressionNpcRoles.syncPlayer(f.s.f.data,f.s.f.player.id);
            a=f.s.p.getKnightService().assignment();
            update(f,1200);
            assertEquals(original,ids(a));
            for(LOTREntityNPC npc:followers(f,a)) {
                assertTrue(npc.isEntityAlive());assertTrue(npc.hiredNPCInfo.isActive);
                assertFalse(npc.hiredNPCInfo.teleportAutomatically);
            }
            LOTREntityNPC unrelated=guard(f);
            unrelated.setLocationAndAngles(10,65,10,0,0);
            f.s.f.world.loadedEntityList.add(unrelated);
            List<LOTREntityNPC> patrol=followers(f,a);
            KOMEProgressionDevelopment.commission(f.s.f.player,new String[]{"end"});
            assertNull(f.s.p.getKnightService().assignment());
            assertFalse(f.s.p.getKnightService().testing());
            for(LOTREntityNPC npc:patrol) {assertTrue(npc.isDead);assertFalse(npc.hiredNPCInfo.isActive);}
            assertTrue(unrelated.isEntityAlive());
            assertTrue(f.lord.isEntityAlive());
            assertTrue(f.s.f.data.hiredUnits.isEmpty());
            assertTrue(f.s.f.data.armyCompanies.isEmpty());
        }
    }

    public static class PatrolGuard extends KOMELordshipTrialFixture.TestGuard {
        private PatrolGuard(){super();}
        @Override public PathNavigate getNavigator(){
            try{return KOMEAccessFixture.allocate(KOMEKnightCommissionGameplayTest.InertNavigator.class);}
            catch(Exception e){throw new RuntimeException(e);}
        }
    }
}
