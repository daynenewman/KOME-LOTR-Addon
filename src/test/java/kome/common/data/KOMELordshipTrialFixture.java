package kome.common.data;

import java.lang.reflect.Field;
import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import static kome.common.data.KOMEKnightCommission.*;

final class KOMELordshipTrialFixture implements AutoCloseable {
    final KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session();
    final LOTREntityNPC liege;
    KOMELordshipTrialFixture() throws Exception {
        s.f.player.posY=65;liege=KOMEKnightCommissionInteractionTest.captain(s);alignment(2000);
        KOMEKnightCommissionService.npcFactory=(world,type)->{try{return type.contains("Guard")?guard():s.npc(type.contains("Enemy")?LOTRFaction.MORDOR:LOTRFaction.ROHAN);}catch(Exception e){throw new RuntimeException(e);}};
    }
    void alignment(float value)throws Exception {
        Field f=LOTRPlayerData.class.getDeclaredField("alignments");f.setAccessible(true);
        ((Map)f.get(LOTRLevelData.getData(s.f.player))).put(LOTRFaction.ROHAN,value);
    }
    void credits(int count) {
        for(int i=0;i<count;i++){
            KOMEKnightCommission a=new KOMEKnightCommission(Type.values()[i],s.p.getSerfKnightProgression().getLiege());
            a.stage=Stage.READY_TO_REPORT;assert s.p.getKnightService().offer(a);assert s.p.getKnightService().report(a.liege);
        }
    }
    KOMELordshipTrial trial(KOMELordshipTrial.Scenario scenario,Stage stage) {
        KOMEKnightCommission a=new KOMEKnightCommission(scenario.objectiveType,s.p.getSerfKnightProgression().getLiege());
        a.x=640;a.y=65;a.z=640;a.destinationX=1000;a.destinationZ=1000;a.place="Refuge";
        a.destinationProof=KOMEProgressionGameplayFixture.shelter(s.f.world,1000,1000,"rohan");
        a.civilianClass="Civilian";a.enemyFaction="mordor";a.enemyClasses.add("Enemy");a.stage=stage;
        KOMELordshipTrial t=new KOMELordshipTrial(scenario,a,Arrays.asList("Guard","Guard","Guard","Guard"));
        if(!s.p.getLordship().offer(t))throw new AssertionError("Existing trial");
        if(stage==Stage.READY_TO_REPORT)try{force(t);for(Actor actor:t.objective.actors){LOTREntityNPC n=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(s.f.world,actor.id);n.posX=liege.posX;n.posZ=liege.posZ;}}catch(Exception e){throw new RuntimeException(e);}
        return t;
    }
    void atSite(KOMELordshipTrial t) {s.f.player.posX=t.objective.x;s.f.player.posZ=t.objective.z;}
    LOTREntityNPC actor(KOMELordshipTrial t,Role role)throws Exception {
        LOTREntityNPC n=role==Role.GUARD?guard():s.npc(role==Role.ENEMY?LOTRFaction.MORDOR:LOTRFaction.ROHAN);
        n.setEntityId(s.f.world.loadedEntityList.size()+100);n.posX=t.objective.x;n.posZ=t.objective.z;
        t.objective.actors.add(new Actor(n.getUniqueID().toString(),n.getClass().getName(),role,n.posX,n.posY,n.posZ));
        KOMEProgressionEncounterMarker.mark(n,KOMELordshipTrialService.MARKER,s.f.player.id,t.objective.token);
        if(role==Role.GUARD||role==Role.CHARGE){n.hiredNPCInfo.isActive=true;n.hiredNPCInfo.setHiringPlayer(s.f.player);}
        s.f.world.loadedEntityList.add(n);return n;
    }
    void force(KOMELordshipTrial t)throws Exception {for(int i=0;i<4;i++)actor(t,Role.GUARD);}
    void kill(LOTREntityNPC npc) {KOMELordshipTrialService.npcDeath(s.f.data,s.f.world,npc.getUniqueID().toString());npc.setDead();}
    void ready(KOMELordshipTrial t) {t.objective.stage=Stage.READY_TO_REPORT;}
    TestGuard guard()throws Exception {
        TestGuard n=KOMEAccessFixture.allocate(TestGuard.class);n.worldObj=s.f.world;n.setUniqueID(UUID.randomUUID());n.posY=65;
        Field bounds=Entity.class.getDeclaredField("boundingBox");bounds.setAccessible(true);bounds.set(n,AxisAlignedBB.getBoundingBox(-.3,65,-.3,.3,66.8,.3));
        n.hiredNPCInfo=new Follower(n);return n;
    }
    @Override public void close()throws Exception {s.close();}
    public static class TestGuard extends LOTREntityRohanMan {
        private TestGuard(){super(null);}
        @Override public LOTRFaction getFaction(){return LOTRFaction.ROHAN;}
        @Override public boolean isCivilianNPC(){return false;}
        @Override public String getNPCName(){return "Guard";}
        @Override public boolean isChild(){return false;}
        @Override public boolean isEntityAlive(){return !isDead;}
        @Override public void setDead(){isDead=true;}
        @Override public IEntityLivingData onSpawnWithEgg(IEntityLivingData data){return data;}
        @Override public void onArtificalSpawn(){}
        @Override public void setAttackTarget(EntityLivingBase target,boolean flag){}
    }
    static class Follower extends KOMEKnightCommissionGameplayTest.Follower {
        boolean halted;
        Follower(LOTREntityNPC npc){super(npc);}
        @Override public void ready(){halted=false;}
        @Override public void halt(){halted=true;}
        @Override public boolean isHalted(){return halted;}
    }
}
