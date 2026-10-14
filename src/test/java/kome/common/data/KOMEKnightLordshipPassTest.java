package kome.common.data;

import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.LOTREntityInvasionSpawner;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEKnightLordshipPassTest {
    @Test public void directReportCancelsGuiAndCreditsOnlyOnce()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        LOTREntityNPC lord=KOMEKnightCommissionInteractionTest.captain(s);
        KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(s,lord,Type.BORDER_INCURSION);a.stage=Stage.READY_TO_REPORT;
        EntityInteractEvent click=new EntityInteractEvent(s.f.player,lord){@Override public boolean isCancelable(){return true;}};
        new KOMEEvents().onEntityInteract(click);assertTrue(click.isCanceled());assertTrue(a.rewardReserved);
        assertNull(s.p.getKnightService().assignment());assertEquals(1,s.p.getKnightService().history().size());
        assertFalse(KOMEProgressionNpcInteractionService.interact(s.f.player,s.f.data,lord));assertEquals(1,s.p.getKnightService().qualifyingTypes("rohan").size());
    }}
    @Test public void activeAndOfferedCommissionDoNotConsumeOrdinaryClick()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        LOTREntityNPC lord=KOMEKnightCommissionInteractionTest.captain(s);KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(s,lord,Type.RELIEF);
        for(Stage stage:Arrays.asList(Stage.OFFERED,Stage.ACTIVE)){a.stage=stage;assertFalse(KOMEProgressionNpcInteractionService.interact(s.f.player,s.f.data,lord));assertEquals(stage,a.stage);}
    }}
    @Test public void everyCommissionBinaryStageHasNoFractionWhileCombatAndGoodsKeepCounts()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        for(Type type:Type.values()){
            KOMEKnightCommission a=new KOMEKnightCommission(type,s.liege);
            for(Stage stage:Stage.values()){a.stage=stage;assertFalse(type+" "+stage,KOMEProgressionTrackerSnapshot.commission(a).hasNumericProgress());}
            a.stage=Stage.ACTIVE;a.encounterCreated=true;
            if(type==Type.RELIEF){a.goods.add(new Goods("minecraft:bread",0,32));a.goods.get(0).delivered=12;assertEquals("12/32",KOMEProgressionTrackerSnapshot.commission(a).progress);}
            else {a.actors.add(new Actor(UUID.randomUUID().toString(),"Enemy",Role.ENEMY,0,65,0));assertTrue(KOMEProgressionTrackerSnapshot.commission(a).hasNumericProgress());a.threatResolved=true;assertFalse(KOMEProgressionTrackerSnapshot.commission(a).hasNumericProgress());}
        }
    }}
    @Test public void nativeLeatherCapRetainsIdentityNameMetadataAndRejectsUnboundCopies()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);a.goods.add(new Goods(String.valueOf(Item.itemRegistry.getNameForObject(Items.leather_helmet)),0,1));
        ItemStack cap=KOMEKnightCommissionService.property(a,s.f.player.id);assertSame(Items.leather_helmet,cap.getItem());assertEquals(0,cap.getItemDamage());
        assertEquals(new ItemStack(Items.leather_helmet).getDisplayName(),cap.getDisplayName());assertTrue(KOMEKnightCommissionService.matchesProperty(cap,a,s.f.player.id));
        assertFalse(KOMEKnightCommissionService.matchesProperty(new ItemStack(Items.leather_helmet),a,s.f.player.id));
        assertSame(Items.leather_helmet,KOMEProgressionLostItems.stack(a).getItem());
        assertFalse(KOMEGearRestrictionService.evaluatePlayer(s.f.player,cap,KOMEGearRestrictionService.Action.EQUIP_ARMOR).allowed);
        assertFalse(KOMEGearRestrictionService.evaluatePlayer(s.f.player,cap,KOMEGearRestrictionService.Action.USE).allowed);
        assertTrue(KOMEKnightCommissionService.consumeProperty(new ItemStack[]{cap},a,s.f.player.id));
    }}
    @Test public void savedWrapperMigratesWithoutChangingBindingOrCreatingExtraProperty()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);a.goods.add(new Goods(String.valueOf(Item.itemRegistry.getNameForObject(Items.leather_helmet)),0,1));
        ItemStack wrapper=new ItemStack(KOMEKnightCommissionService.STOLEN_PROPERTY);wrapper.setTagCompound(KOMEKnightCommissionService.property(a,s.f.player.id).getTagCompound());
        ItemStack[] inventory={wrapper};KOMEProgressionInventoryReconciler.reconcile(inventory,s.p,s.f.player.id);
        assertSame(Items.leather_helmet,inventory[0].getItem());assertEquals(1,inventory[0].stackSize);assertTrue(KOMEKnightCommissionService.matchesProperty(inventory[0],a,s.f.player.id));
        a.revision++;KOMEProgressionInventoryReconciler.reconcile(inventory,s.p,s.f.player.id);assertNull(inventory[0]);
    }}
    @Test public void exactlyOneCarrierAndOnePersistentDropAcrossDeathReplayAndReload()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);a.goods.add(new Goods(String.valueOf(Item.itemRegistry.getNameForObject(Items.leather_helmet)),0,1));a.encounterCreated=true;
        KOMEKnightCommissionGameplayTest.TestNpc thief=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY),other=KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);
        KOMEKnightCommissionService.equipCarrier(s.f.player,a);assertEquals(thief.getUniqueID().toString(),a.carrierId);assertSame(Items.leather_helmet,thief.getEquipmentInSlot(4).getItem());assertNull(other.getEquipmentInSlot(4));
        KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,a.carrierId);KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,a.carrierId);
        assertTrue(a.propertyDropped);assertEquals(1,s.f.world.loadedEntityList.stream().filter(e->e instanceof EntityItem).count());
        EntityItem item=(EntityItem)s.f.world.loadedEntityList.stream().filter(e->e instanceof EntityItem).findFirst().get();assertEquals(Integer.MAX_VALUE,item.lifespan);
        s.reload();a=s.p.getKnightService().assignment();KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,a.carrierId);assertEquals(1,s.f.world.loadedEntityList.stream().filter(e->e instanceof EntityItem).count());
    }}
    @Test public void partialActivationAndPreEngagementResidentLossAreTechnicalRetries()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        KOMEKnightCommission a=s.active(Type.SETTLEMENT_DEFENSE);
        LOTREntityNPC resident=KOMEKnightCommissionGameplayTest.actor(s,a,Role.BENEFICIARY);
        KOMEKnightCommissionGameplayTest.actor(s,a,Role.ENEMY);KOMEKnightCommissionService.npcDeath(s.f.data,s.f.world,resident.getUniqueID().toString());
        assertEquals(Stage.ACTIVE,a.stage);assertTrue(a.activationPending);assertFalse(a.encounterCreated);assertTrue(a.actors.isEmpty());assertTrue(s.p.getKnightService().history().isEmpty());
        s.reload();assertTrue(s.p.getKnightService().assignment().activationPending);
    }}
    @Test public void settlementMarkersUseSavedAnchorAndNeverFollowProtectedResidents()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        KOMEKnightCommission a=s.active(Type.SETTLEMENT_DEFENSE);a.encounterCreated=true;
        LOTREntityNPC resident=KOMEKnightCommissionGameplayTest.actor(s,a,Role.BENEFICIARY);resident.posX+=200;
        List<KOMEVisualMarker> markers=KOMEVisualLocationService.markersFor(s.p);
        assertFalse(markers.stream().anyMatch(m->m.role==KOMEVisualMarker.Role.COMMISSION&&!m.entityUuid.isEmpty()));
        KOMEVisualMarker anchor=markers.stream().filter(m->m.role==KOMEVisualMarker.Role.COMMISSION).findFirst().get();assertEquals(a.x,anchor.x,0);assertEquals(a.z,anchor.z,0);
        s.reload();assertTrue(KOMEVisualLocationService.markersFor(s.p).stream().anyMatch(m->m.role==KOMEVisualMarker.Role.COMMISSION&&m.x==anchor.x&&m.z==anchor.z));
    }}
    @Test public void princeOnlyAuthorityKeepsSameFactionServiceAndAlignmentGates()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);LOTREntityNPC lord=(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,f.s.p.getSerfKnightProgression().getLiege().entityUuid);
        assertFalse(KOMELordshipTrialService.eligible(f.s.f.player,lord));assertTrue(KOMELordshipTrialService.eligible(f.s.f.player,f.liege));
        assertTrue(KOMEProgressionEligibility.evaluate(f.s.f.player,f.liege).enabled);f.alignment(1999);assertFalse(KOMEProgressionEligibility.evaluate(f.s.f.player,f.liege).enabled);
    }}
    @Test public void playerDeathIsRecordedAndCannotBeErasedByReload()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.ACTIVE);
        KOMEObjectiveInvasionBridge.playerDeath(f.s.f.player);assertTrue(t.playerDied);assertEquals(Stage.FAILED,t.objective.stage);
        f.s.reload();assertTrue(f.s.p.getLordship().assignment().playerDied);assertFalse(KOMELordshipTrialService.promote(f.s.f.player,f.liege));
    }}
    @Test public void nativeOutcomeRequiresParticipationAndDoesNotPromoteWithoutPrinceReport()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.ACTIVE);
        t.nativeInvasion=true;t.invasionType=lotr.common.world.spawning.LOTRInvasions.DUNLAND.codeName();t.authority=KOMEProgressionNpcRankService.referenceOf(f.liege);t.objective.encounterCreated=true;t.objective.participated=true;
        LOTREntityInvasionSpawner controller=KOMEAccessFixture.allocate(LOTREntityInvasionSpawner.class);controller.worldObj=f.s.f.world;KOMEKnightCommissionGameplayTest.setField(net.minecraft.entity.Entity.class,controller,"entityUniqueID",UUID.randomUUID());
        KOMEKnightCommissionGameplayTest.setField(LOTREntityInvasionSpawner.class,controller,"invasionSize",30);controller.posX=f.s.f.player.posX;controller.posY=f.s.f.player.posY;controller.posZ=f.s.f.player.posZ;t.invasionId=controller.getUniqueID().toString();
        KOMEObjectiveInvasionBridge.ended(controller,true);assertTrue(t.nativeVictory);assertTrue(t.ready());assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
        KOMEObjectiveInvasionBridge.ended(controller,true);assertTrue(KOMELordshipTrialService.promote(f.s.f.player,f.liege));assertFalse(KOMELordshipTrialService.promote(f.s.f.player,f.liege));
        assertEquals(KOMEProgressionRank.LORD,f.s.p.getCanonicalRank());assertTrue(f.s.p.getSerfKnightProgression().getLiege().hasSameIdentity(t.authority));
    }}
    @Test public void knightBookContainsOnlyActiveWorkAndCompactCreditedHistory()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);String context=KOMEKnightBookPresentation.context(f.s.p,"rohan");assertTrue(context.contains("Credited service"));assertFalse(context.contains("Stage:"));
        KOMEProgressionRankSummary summary=KOMEProgressionRankSummary.project(f.s.p,2000,"rohan");assertTrue(summary.activityObjective.contains("Seek a Prince"));assertFalse(summary.activityObjective.contains("survivors"));
        KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.READY_TO_REPORT);assertFalse(KOMEProgressionTrackerSnapshot.lordship(t).hasNumericProgress());
    }}
    @Test public void unknownPrinceRemainsPrivateAndPersonalDiscoverySurvivesReload()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        assertNull(KOMEVisualLocationService.knownPrince(f.s.p,f.s.f.data,0,0,0,"rohan"));
        EntityInteractEvent click=new EntityInteractEvent(f.s.f.player,f.liege){@Override public boolean isCancelable(){return true;}};
        new KOMEEvents().onEntityInteract(click);
        assertEquals(f.liege.getUniqueID().toString(),KOMEVisualLocationService.knownPrince(f.s.p,f.s.f.data,0,0,0,"rohan").entityUuid);
        f.s.reload();assertEquals(1,f.s.p.getKnightService().knownPrinces().size());
        assertNull(KOMEVisualLocationService.knownPrince(f.s.p,f.s.f.data,0,0,0,"gondor"));
        f.s.f.data.progressionNpcRanks.remove(f.liege.getUniqueID());assertNull(KOMEVisualLocationService.knownPrince(f.s.p,f.s.f.data,0,0,0,"rohan"));
    }}
    @Test public void technicalReassignmentIsRateLimitedAcrossReloadAndKeepsCredits()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMEKnightCommission a=KOMEKnightCommissionInteractionTest.offered(f.s,(LOTREntityNPC)KOMEKnightCommissionService.findLoaded(f.s.f.world,f.s.p.getSerfKnightProgression().getLiege().entityUuid),Type.RELIEF);
        a.activationPending=true;a.activationRetryAt=200;
        assertFalse(f.s.p.getKnightService().discardTechnical(100));assertTrue(f.s.p.getKnightService().discardTechnical(200));
        KOMEKnightCommission next=new KOMEKnightCommission(Type.RELIEF,a.liege);next.activationPending=true;assertTrue(f.s.p.getKnightService().offer(next));f.s.reload();
        assertFalse(f.s.p.getKnightService().discardTechnical(300));assertTrue(f.s.p.getKnightService().discardTechnical(1400));assertEquals(3,f.s.p.getKnightService().qualifyingTypes("rohan").size());
    }}
    @Test public void savedTrialRemovesQuotaWithoutRewritingItsObjectiveOrErasingEvidence()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial trial=f.trial(KOMELordshipTrial.Scenario.PROTECTED_EXPEDITION,Stage.ACTIVE);
        NBTTagCompound old=trial.writeToNBT();old.setInteger("Version",1);old.setInteger("RequiredSurvivors",2);old.removeTag("Authority");old.removeTag("NativeInvasion");
        KOMELordshipTrial migrated=KOMELordshipTrial.readFromNBT(old);assertNotNull(migrated);assertEquals(trial.objective.token,migrated.objective.token);assertEquals(Stage.ACTIVE,migrated.objective.stage);
        assertEquals(trial.objective.destinationX,migrated.objective.destinationX,0);assertEquals(0,migrated.requiredSurvivors);assertFalse(migrated.nativeInvasion);assertEquals(3,f.s.p.getKnightService().qualifyingTypes("rohan").size());
    }}
    @Test public void nativeAbortedControllerIsTechnicalAndCannotProduceFreeVictory()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.ACTIVE);t.nativeInvasion=true;t.invasionType=lotr.common.world.spawning.LOTRInvasions.DUNLAND.codeName();t.objective.encounterCreated=true;
        LOTREntityInvasionSpawner controller=KOMEAccessFixture.allocate(LOTREntityInvasionSpawner.class);controller.worldObj=f.s.f.world;
        KOMEKnightCommissionGameplayTest.setField(net.minecraft.entity.Entity.class,controller,"entityUniqueID",UUID.randomUUID());KOMEKnightCommissionGameplayTest.setField(LOTREntityInvasionSpawner.class,controller,"invasionSize",30);
        t.invasionId=controller.getUniqueID().toString();KOMEObjectiveInvasionBridge.ended(controller,false);
        assertEquals(Stage.ACTIVE,t.objective.stage);assertTrue(t.objective.activationPending);assertFalse(t.nativeVictory);assertFalse(t.playerDied);assertFalse(t.ready());
    }}
    @Test public void princeEntrustsFourSoldiersButNoInvadersBeforeStaging()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.OFFERED);t.nativeInvasion=true;t.invasionType=lotr.common.world.spawning.LOTRInvasions.DUNLAND.codeName();
        assertTrue(KOMELordshipTrialService.acceptOrReport(f.s.f.player,f.liege));assertEquals(Stage.ACTIVE,t.objective.stage);assertEquals(4,t.confirmedSurvivors());
        assertFalse(t.objective.encounterCreated);assertTrue(t.invasionId.isEmpty());assertFalse(t.objective.actors.stream().anyMatch(a->a.role==Role.ENEMY));
        KOMELordshipTrialService.tickPlayer(f.s.f.player);assertFalse(t.objective.encounterCreated);assertEquals(4,t.objective.actors.size());
        f.s.reload();t=f.s.p.getLordship().assignment();assertTrue(t.invasionId.isEmpty());assertEquals(4,t.confirmedSurvivors());
    }}
    @Test public void boundPropertyBlocksProcessingWhileOrdinaryEquipmentRemainsUsable()throws Exception {try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
        KOMEKnightCommission a=s.active(Type.STOLEN_GOODS);a.goods.add(new Goods(String.valueOf(Item.itemRegistry.getNameForObject(Items.leather_helmet)),0,1));
        net.minecraft.inventory.Container owner=new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}};
        net.minecraft.inventory.InventoryCrafting grid=new net.minecraft.inventory.InventoryCrafting(owner,3,3);
        grid.setInventorySlotContents(0,new ItemStack(Items.leather_helmet));assertFalse(KOMEQuestPropertyUseGuard.boundRecipe(grid));
        grid.setInventorySlotContents(0,KOMEKnightCommissionService.property(a,s.f.player.id));assertTrue(KOMEQuestPropertyUseGuard.boundRecipe(grid));
        assertFalse(KOMEQuestPropertyUseGuard.bound(new ItemStack(Items.leather_helmet)));
        ItemStack standingProperty=new ItemStack(Items.leather_helmet);NBTTagCompound binding=new NBTTagCompound();binding.setTag("KOMERecovery",new NBTTagCompound());standingProperty.setTagCompound(binding);
        assertTrue(KOMEQuestPropertyUseGuard.bound(standingProperty));assertFalse(KOMEGearRestrictionService.evaluatePlayer(s.f.player,standingProperty,KOMEGearRestrictionService.Action.EQUIP_ARMOR).allowed);
        grid.setInventorySlotContents(0,standingProperty);assertTrue(KOMEQuestPropertyUseGuard.boundRecipe(grid));
    }}
    @Test public void offlineParticipantRetainsRealNativeVictoryUntilPersonalPrinceReport()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        f.credits(3);KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.ACTIVE);t.nativeInvasion=true;t.invasionType=lotr.common.world.spawning.LOTRInvasions.DUNLAND.codeName();t.objective.encounterCreated=true;t.objective.participated=true;
        LOTREntityInvasionSpawner controller=KOMEAccessFixture.allocate(LOTREntityInvasionSpawner.class);controller.worldObj=f.s.f.world;KOMEKnightCommissionGameplayTest.setField(net.minecraft.entity.Entity.class,controller,"entityUniqueID",UUID.randomUUID());KOMEKnightCommissionGameplayTest.setField(LOTREntityInvasionSpawner.class,controller,"invasionSize",30);t.invasionId=controller.getUniqueID().toString();
        f.s.f.world.playerEntities.remove(f.s.f.player);KOMEObjectiveInvasionBridge.ended(controller,true);assertTrue(t.ready());assertTrue(t.nativeVictory);assertEquals(KOMEProgressionRank.KNIGHT,f.s.p.getCanonicalRank());
        f.s.f.world.playerEntities.add(f.s.f.player);f.s.reload();assertTrue(f.s.p.getLordship().assignment().ready());assertTrue(KOMELordshipTrialService.promote(f.s.f.player,f.liege));
    }}
    @Test public void realInvadersKeepNativeLootAndCannotReceiveFriendlyGuardCommands()throws Exception {try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
        KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.ACTIVE);t.nativeInvasion=true;t.invasionType=lotr.common.world.spawning.LOTRInvasions.DUNLAND.codeName();
        LOTREntityNPC enemy=f.actor(t,Role.ENEMY);assertFalse(KOMELordshipTrialService.interactFollower(f.s.f.player,enemy));
        UUID controller=UUID.randomUUID();t.invasionId=controller.toString();KOMEKnightCommissionGameplayTest.setField(LOTREntityNPC.class,enemy,"invasionID",controller);
        assertTrue(KOMEObjectiveInvasionBridge.nativeInvader(f.s.f.data,enemy));t.objective.actors.get(0).dead=true;assertTrue(KOMEObjectiveInvasionBridge.nativeInvader(f.s.f.data,enemy));
        t.invasionId=UUID.randomUUID().toString();assertFalse(KOMEObjectiveInvasionBridge.nativeInvader(f.s.f.data,enemy));
    }}
}
