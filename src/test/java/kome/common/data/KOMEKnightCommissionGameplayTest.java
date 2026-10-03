package kome.common.data;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEKnightCommissionGameplayTest {
    static final class Session implements AutoCloseable {
        final KOMEProgressionFollowupTest.Session original=new KOMEProgressionFollowupTest.Session();
        final KOMEAccessFixture f=original.f;
        final KOMEPlayerProgression p=original.p;
        final KOMEKnightCommissionService.NpcFactory oldFactory=KOMEKnightCommissionService.npcFactory;
        final KOMEProgressionNpcRef liege=KOMEKnightCommissionStateTest.liege();
        boolean chunks=true;
        Session()throws Exception {
            if(Item.itemRegistry.getNameForObject(KOMEKnightCommissionService.STOLEN_PROPERTY)==null){
                java.lang.reflect.Method raw=Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);raw.setAccessible(true);
                raw.invoke(Item.itemRegistry,29099,"kome:stolenProperty",KOMEKnightCommissionService.STOLEN_PROPERTY);
            }
            p.setCanonicalRank(KOMEProgressionRank.KNIGHT);p.getSerfKnightProgression().setLiege(liege);f.player.dimension=0;
            f.world.testChunkProvider=(IChunkProvider)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IChunkProvider.class},(proxy,method,args)->method.getName().equals("chunkExists")?chunks:method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:null);
            KOMEKnightCommissionService.npcFactory=(world,type)->{try{return npc(type.contains("Enemy")?LOTRFaction.MORDOR:LOTRFaction.ROHAN);}catch(Exception e){throw new RuntimeException(e);}};
        }
        KOMEKnightCommission active(Type type){KOMEKnightCommission a=new KOMEKnightCommission(type,liege);a.stage=Stage.ACTIVE;a.x=640;a.y=65;a.z=640;a.destinationX=1000;a.destinationZ=1000;a.place="Refuge";a.civilianClass="Civilian";a.enemyFaction="mordor";if(type!=Type.RELIEF)for(int i=0;i<3;i++)a.enemyClasses.add("Enemy");p.getKnightService().offer(a);f.player.posX=a.x;f.player.posY=a.y;f.player.posZ=a.z;return a;}
        TestNpc npc(LOTRFaction faction)throws Exception {TestNpc n=KOMEAccessFixture.allocate(TestNpc.class);n.worldObj=f.world;n.faction=faction;n.setUniqueID(UUID.randomUUID());n.posY=65;Field bounds=Entity.class.getDeclaredField("boundingBox");bounds.setAccessible(true);bounds.set(n,AxisAlignedBB.getBoundingBox(-0.3,65,-0.3,0.3,66.8,0.3));n.hiredNPCInfo=new Follower(n);return n;}
        void reload(){p.readFromNBT(p.writeToNBT());KOMEProgressionNpcRoles.syncPlayer(f.data,f.player.id);}
        @Override public void close()throws Exception {KOMEKnightCommissionService.npcFactory=oldFactory;original.close();}
    }
    static TestNpc actor(Session s,KOMEKnightCommission a,Role role)throws Exception {TestNpc n=s.npc(role==Role.ENEMY?LOTRFaction.MORDOR:LOTRFaction.ROHAN);n.posX=a.x;n.posZ=a.z;Actor actor=new Actor(n.getUniqueID().toString(),n.getClass().getName(),role,n.posX,n.posY,n.posZ);a.actors.add(actor);s.f.world.loadedEntityList.add(n);KOMEProgressionEncounterMarker.mark(n,KOMEKnightCommissionService.MARKER,s.f.player.id,a.token);return n;}
    static void goods(KOMEKnightCommission a){a.goods.add(new Goods(String.valueOf(Item.itemRegistry.getNameForObject(Items.gold_ingot)),0,3));}
    @Test public void defenseActivatesOnceAndReservesActorsBeforeSpawning()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.SETTLEMENT_DEFENSE);assertTrue(KOMEKnightCommissionService.activate(s.f.player,a));assertEquals(4,a.actors.size());assertEquals(4,s.f.world.loadedEntityList.stream().filter(e->e instanceof TestNpc).count());KOMEProgressionNpcRoles.syncPlayer(s.f.data,s.f.player.id);for(Actor actor:a.actors)assertTrue(KOMEProgressionNpcRoles.protects(s.f.data,UUID.fromString(actor.id)));KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(4,a.actors.size());}}
    @Test public void alliesCanFinishDefenseAfterOwnerDamagesAnEnemy()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.SETTLEMENT_DEFENSE);a.encounterCreated=true;actor(s,a,Role.BENEFICIARY);TestNpc enemy=actor(s,a,Role.ENEMY);assertTrue(KOMEKnightCommissionService.noteDamage(s.f.data,s.f.player.id,enemy.getUniqueID().toString()));KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,enemy.getUniqueID().toString());assertEquals(Stage.READY_TO_REPORT,a.stage);assertTrue(s.p.getKnightService().completedTypes().isEmpty());assertTrue(s.p.getKnightService().report(a.liege));assertTrue(s.p.getKnightService().completedTypes().contains(Type.SETTLEMENT_DEFENSE));}}
    @Test public void proximityAndAllyLastHitsCannotReplaceOwnerParticipation()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.SETTLEMENT_DEFENSE);a.encounterCreated=true;actor(s,a,Role.BENEFICIARY);TestNpc enemy=actor(s,a,Role.ENEMY);assertFalse(KOMEKnightCommissionService.noteDamage(s.f.data,UUID.randomUUID(),enemy.getUniqueID().toString()));KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,enemy.getUniqueID().toString());assertEquals(Stage.FAILED,a.stage);assertFalse(KOMEKnightCommissionService.resolveCombat(a,true));}}
    @Test public void ownerMustBePresentForFinalEnemyDeath()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.BORDER_INCURSION);a.encounterCreated=true;TestNpc enemy=actor(s,a,Role.ENEMY);KOMEKnightCommissionService.noteDamage(s.f.data,s.f.player.id,enemy.getUniqueID().toString());s.f.player.posX=2000;KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,enemy.getUniqueID().toString());assertEquals(Stage.FAILED,a.stage);}}
    @Test public void protectedDeathFailsAndCleansGeneratedEntitiesAndLeases()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.SETTLEMENT_DEFENSE);TestNpc beneficiary=actor(s,a,Role.BENEFICIARY),enemy=actor(s,a,Role.ENEMY);KOMEProgressionNpcRoles.syncPlayer(s.f.data,s.f.player.id);KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,beneficiary.getUniqueID().toString());assertEquals(Stage.FAILED,a.stage);assertTrue(enemy.isDead);assertFalse(KOMEProgressionNpcRoles.protects(s.f.data,beneficiary.getUniqueID()));}}
    @Test public void borderTravelAloneCannotCompleteAndAlliedKillsCan()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.BORDER_INCURSION);a.encounterCreated=true;TestNpc enemy=actor(s,a,Role.ENEMY);KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(Stage.ACTIVE,a.stage);KOMEKnightCommissionService.noteDamage(s.f.data,s.f.player.id,enemy.getUniqueID().toString());KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,enemy.getUniqueID().toString());KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(Stage.READY_TO_REPORT,a.stage);}}
    @Test public void invasionChoiceIsGenuinelyHostile() {for(LOTRFaction faction:new LOTRFaction[]{LOTRFaction.ROHAN,LOTRFaction.GONDOR,LOTRFaction.MORDOR}){lotr.common.world.spawning.LOTRInvasions invasion=KOMESerfKnightDefenseService.chooseHostileInvasion(faction,"stable");if(invasion!=null)assertTrue(KOMESerfKnightDefenseService.hostile(faction,invasion.invasionFaction));}assertFalse(KOMESerfKnightDefenseService.hostile(LOTRFaction.ROHAN,LOTRFaction.ROHAN));}
    @Test public void propertyCannotBeSpoofedByRenamingOrWrongIdentity()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);goods(a);ItemStack ordinary=new ItemStack(Items.gold_ingot,3);ordinary.setStackDisplayName("Stolen silver");assertFalse(KOMEKnightCommissionService.matchesProperty(ordinary,a,s.f.player.id));ItemStack proper=KOMEKnightCommissionService.property(a,s.f.player.id);assertTrue(KOMEKnightCommissionService.matchesProperty(proper,a,s.f.player.id));assertFalse(KOMEKnightCommissionService.matchesProperty(proper,a,UUID.randomUUID()));assertFalse(KOMEKnightCommissionService.matchesProperty(proper,KOMEKnightCommissionStateTest.commission(Type.STOLEN_GOODS),s.f.player.id));assertFalse(KOMEKnightCommissionService.canPickup(proper,UUID.randomUUID()));}}
    @Test public void recoveredPropertyMustBePhysicallyReturnedAndSplitStacksWork()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);goods(a);ItemStack one=KOMEKnightCommissionService.property(a,s.f.player.id),two=one.copy();one.stackSize=1;two.stackSize=2;ItemStack[] inventory={one,two};assertTrue(KOMEKnightCommissionService.consumeProperty(inventory,a,s.f.player.id));assertNull(inventory[0]);assertNull(inventory[1]);assertTrue(a.goodsDelivered());assertFalse(KOMEKnightCommissionService.consumeProperty(inventory,a,s.f.player.id));}}
    @Test public void lostPropertyRespawnsWithRevisionAndObsoleteItemsCannotReport()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);goods(a);a.encounterCreated=true;a.threatResolved=true;ItemStack old=KOMEKnightCommissionService.property(a,s.f.player.id);KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(1,a.revision);EntityItem item=(EntityItem)s.f.world.loadedEntityList.stream().filter(e->e instanceof EntityItem).findFirst().get();assertTrue(KOMEKnightCommissionService.matchesProperty(item.getEntityItem(),a,s.f.player.id));assertFalse(KOMEKnightCommissionService.matchesProperty(old,a,s.f.player.id));item.setDead();s.f.world.testWorldTime=1200;KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(2,a.revision);}}
    @Test public void recoveryCombatDoesNotCompleteUntilPropertyIsRecovered()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);goods(a);a.encounterCreated=true;TestNpc enemy=actor(s,a,Role.ENEMY);KOMEKnightCommissionService.noteDamage(s.f.data,s.f.player.id,enemy.getUniqueID().toString());KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,enemy.getUniqueID().toString());KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(Stage.ACTIVE,a.stage);s.f.player.inventory.mainInventory[0]=KOMEKnightCommissionService.property(a,s.f.player.id);KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(Stage.READY_TO_REPORT,a.stage);assertTrue(s.p.getKnightService().completedTypes().isEmpty());}}
    @Test public void escortUsesDestinationNotDistanceAndRequiresRoadThreatResolution()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.DANGEROUS_ESCORT);assertFalse(KOMEKnightCommissionService.escortArrived(a,0,1000,1000,0));a.threatResolved=true;assertFalse(KOMEKnightCommissionService.escortArrived(a,0,-1000,-1000,0));assertFalse(KOMEKnightCommissionService.escortArrived(a,1,1000,1000,0));assertFalse(KOMEKnightCommissionService.escortArrived(a,0,1000,1000,300));assertTrue(KOMEKnightCommissionService.escortArrived(a,0,1000,1000,0));}}
    @Test public void temporaryEscortBypassesHiringAndSurvivesReloadAndUnload()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.DANGEROUS_ESCORT);a.encounterCreated=true;TestNpc charge=actor(s,a,Role.CHARGE);charge.hiredNPCInfo.isActive=true;charge.hiredNPCInfo.setHiringPlayer(s.f.player);assertTrue(KOMESerfKnightEscortService.isActiveEscort(s.f.data,charge));new KOMEEvents().onLivingUpdate(new LivingEvent.LivingUpdateEvent(charge));assertFalse(charge.isDead);assertTrue(s.f.data.hiredUnits.isEmpty());s.f.world.loadedEntityList.removeIf(e->e==charge);s.chunks=false;s.reload();KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(Stage.ACTIVE,s.p.getKnightService().assignment().stage);s.f.world.loadedEntityList.add(charge);KOMEKnightCommissionService.reconcileNpc(s.f.data,charge);assertFalse(charge.isDead);assertTrue(KOMESerfKnightEscortService.isActiveEscort(s.f.data,charge));}}
    @Test public void escortDeathAndCancellationDismissFollower()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.DANGEROUS_ESCORT);TestNpc charge=actor(s,a,Role.CHARGE);charge.hiredNPCInfo.isActive=true;charge.hiredNPCInfo.setHiringPlayer(s.f.player);KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,charge.getUniqueID().toString());assertEquals(Stage.FAILED,a.stage);assertFalse(charge.hiredNPCInfo.isActive);assertFalse(KOMESerfKnightEscortService.isActiveEscort(s.f.data,charge));}}
    @Test public void civilianEscortInteractionDoesNotOpenSoldierControls()throws Exception {try(Session s=new Session()){
        KOMEKnightCommission a=s.active(Type.DANGEROUS_ESCORT);TestNpc charge=actor(s,a,Role.CHARGE);charge.hiredNPCInfo.isActive=true;charge.hiredNPCInfo.setHiringPlayer(s.f.player);
        net.minecraftforge.event.entity.player.EntityInteractEvent event=new net.minecraftforge.event.entity.player.EntityInteractEvent(s.f.player,charge){@Override public boolean isCancelable(){return true;}};
        new KOMEEvents().onEntityInteract(event);assertTrue(event.isCanceled());assertTrue(s.f.data.hiredUnits.isEmpty());
    }}
    @Test public void acceptedChargeCanResumeAfterReload()throws Exception {try(Session s=new Session()){
        KOMEKnightCommission a=s.active(Type.BORDER_INCURSION);a.stage=Stage.ACCEPTED;s.reload();s.f.player.posX=0;s.f.player.posZ=0;
        KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(Stage.ACTIVE,s.p.getKnightService().assignment().stage);assertFalse(s.p.getKnightService().assignment().encounterCreated);
    }}
    @Test public void escortActualArrivalCreatesDestinationHostAndReleasesFollower()throws Exception {try(Session s=new Session()){
        KOMEKnightCommission a=s.active(Type.DANGEROUS_ESCORT);a.encounterCreated=true;a.threatResolved=true;TestNpc charge=actor(s,a,Role.CHARGE);charge.hiredNPCInfo.isActive=true;charge.hiredNPCInfo.setHiringPlayer(s.f.player);
        charge.posX=-1000;charge.posZ=-1000;s.f.player.posX=charge.posX;s.f.player.posZ=charge.posZ;KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(Stage.ACTIVE,a.stage);
        charge.posX=a.destinationX;charge.posZ=a.destinationZ;s.f.player.posX=charge.posX;s.f.player.posZ=charge.posZ;KOMEKnightCommissionService.tickPlayer(s.f.player);
        assertEquals(Stage.READY_TO_REPORT,a.stage);assertFalse(charge.hiredNPCInfo.isActive);assertTrue(a.actors.stream().anyMatch(actor->actor.role==Role.BENEFICIARY));assertTrue(s.p.getKnightService().completedTypes().isEmpty());
    }}
    @Test public void propertyRevisionAndPhysicalIdentitySurviveItemNbtReload()throws Exception {try(Session s=new Session()){
        KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);goods(a);a.revision=4;ItemStack item=KOMEKnightCommissionService.property(a,s.f.player.id);
        ItemStack loaded=ItemStack.loadItemStackFromNBT(item.writeToNBT(new NBTTagCompound()));assertTrue(KOMEKnightCommissionService.matchesProperty(loaded,a,s.f.player.id));assertNotSame(Items.gold_ingot,loaded.getItem());
        loaded.getTagCompound().getCompoundTag(KOMEKnightCommissionService.ITEM_TAG).setString("Contents","wrong:goods");assertFalse(KOMEKnightCommissionService.matchesProperty(loaded,a,s.f.player.id));
    }}
    @Test public void reliefFoodIsLocalAndQuantitiesBounded()throws Exception {try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();Session s=new Session()){for(String faction:new String[]{"rohan","mordor","dorwinion","halftroll"})for(int seed=0;seed<20;seed++){List<Goods> goods=KOMEKnightCommissionService.reliefGoods(faction,new Random(seed));assertFalse(goods.isEmpty());for(Goods good:goods){assertTrue(good.required>=6&&good.required<=32);boolean local=false;for(Item item:KOMELocalProvisionFoods.forFaction(faction))if(item!=null&&good.itemKey.equals(String.valueOf(Item.itemRegistry.getNameForObject(item))))local=true;assertTrue(local);}}}}
    @Test public void reliefPartialDeliveryPersistsAndStillRequiresReport()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.RELIEF);a.encounterCreated=true;a.goods.add(new Goods(String.valueOf(Item.itemRegistry.getNameForObject(Items.bread)),0,24));TestNpc beneficiary=actor(s,a,Role.BENEFICIARY);s.f.player.inventory.mainInventory[0]=new ItemStack(Items.bread,10);assertTrue(KOMEKnightCommissionService.deliverRelief(s.f.player,beneficiary));assertEquals(10,a.goods.get(0).delivered);assertEquals(Stage.ACTIVE,a.stage);s.reload();a=s.p.getKnightService().assignment();assertEquals(10,a.goods.get(0).delivered);s.f.player.inventory.mainInventory[0]=new ItemStack(Items.bread,14);assertTrue(KOMEKnightCommissionService.deliverRelief(s.f.player,beneficiary));assertEquals(Stage.READY_TO_REPORT,a.stage);assertTrue(s.p.getKnightService().completedTypes().isEmpty());}}
    @Test public void reliefCannotBeDeliveredToLiegeOrAnotherNpc()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.RELIEF);a.goods.add(new Goods(String.valueOf(Item.itemRegistry.getNameForObject(Items.bread)),0,24));actor(s,a,Role.BENEFICIARY);TestNpc wrong=s.npc(LOTRFaction.ROHAN);s.f.player.inventory.mainInventory[0]=new ItemStack(Items.bread,24);assertFalse(KOMEKnightCommissionService.deliverRelief(s.f.player,wrong));assertEquals(24,s.f.player.inventory.mainInventory[0].stackSize);}}
    @Test public void absentLoadedActorsFailRecoverablyInsteadOfCountingAsKills()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.BORDER_INCURSION);a.encounterCreated=true;TestNpc enemy=actor(s,a,Role.ENEMY);s.f.world.loadedEntityList.removeIf(e->e==enemy);assertTrue(KOMEKnightCommissionService.loadedAround(s.f.world,a.x,a.z));for(int i=0;i<59;i++)KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals("loaded observation count",1180,a.missingTicks);assertEquals(Stage.ACTIVE,a.stage);KOMEKnightCommissionService.tickPlayer(s.f.player);assertEquals(Stage.FAILED,a.stage);assertFalse(a.allEnemiesDead());}}
    @Test public void obsoleteUnloadedActorsCleanWhenTheyReturn()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.RELIEF);TestNpc beneficiary=actor(s,a,Role.BENEFICIARY);s.f.world.loadedEntityList.removeIf(e->e==beneficiary);KOMEKnightCommissionService.cancel(s.f.world,s.f.player.id,s.p);s.reload();KOMEKnightCommissionService.reconcileNpc(s.f.data,beneficiary);assertTrue(beneficiary.isDead);}}
    @Test public void cancelledDamageAndGenericPvpNeverCount()throws Exception {try(Session s=new Session()){KOMEKnightCommission a=s.active(Type.BORDER_INCURSION);TestNpc enemy=actor(s,a,Role.ENEMY);LivingHurtEvent event=new LivingHurtEvent(enemy,DamageSource.causePlayerDamage(s.f.player),3){@Override public boolean isCancelable(){return true;}};event.setCanceled(true);new KOMEEvents().onDefenseParticipation(event);assertFalse(a.participated);assertFalse(KOMEKnightCommissionService.noteDamage(s.f.data,s.f.player.id,UUID.randomUUID().toString()));}}
    public static class TestNpc extends LOTREntityRohanMan {
        LOTRFaction faction;
        private TestNpc(){super(null);}
        @Override public LOTRFaction getFaction(){return faction;}
        @Override public String getNPCName(){return "Civilian";}
        @Override public boolean isChild(){return false;}
        @Override public boolean isEntityAlive(){return !isDead;}
        @Override public void setDead(){isDead=true;}
        @Override public IEntityLivingData onSpawnWithEgg(IEntityLivingData data){return data;}
        @Override public void onArtificalSpawn(){}
        @Override public void setAttackTarget(EntityLivingBase target,boolean flag){}
    }
    static class Follower extends LOTRHiredNPCInfo {
        EntityPlayer player;
        Follower(LOTREntityNPC npc){super(npc);}
        @Override public void setHiringPlayer(EntityPlayer p){player=p;}
        @Override public UUID getHiringPlayerUUID(){return player==null?null:player.getUniqueID();}
        @Override public EntityPlayer getHiringPlayer(){return player;}
        @Override public void setTask(Task task){}
        @Override public void ready(){}
        @Override public void dismissUnit(boolean disband){isActive=false;}
    }
}
