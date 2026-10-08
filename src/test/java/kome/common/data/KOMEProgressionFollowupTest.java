package kome.common.data;

import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import kome.common.KOMEAccessFixture;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketVisualMarkers;
import lotr.common.LOTRMod;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import lotr.common.inventory.LOTRInventoryPouch;
import lotr.common.item.LOTRItemCoin;
import lotr.common.item.LOTRItemPouch;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionFollowupTest {
    static final class Session implements AutoCloseable {
        final KOMEAccessFixture f=new KOMEAccessFixture();
        final KOMEProgressionFixesTest.Worker master;
        final KOMEPlayerProgression p;
        final KOMESerfKnightProgression state;
        final SimpleNetworkWrapper previous=KOMEPacketHandler.network;
        final KOMEProgressionNpcSpeech.Sender previousSpeech=KOMEProgressionNpcSpeech.sender;
        Session()throws Exception {
            f.pledge(LOTRFaction.ROHAN);f.world.rand=new Random(1);f.world.flatTerrain=true;f.world.spawnSucceeds=true;
            f.player.inventory=new InventoryPlayer(f.player);f.player.inventoryContainer=new Container(){public boolean canInteractWith(EntityPlayer p){return true;}};
            master=KOMEAccessFixture.allocate(KOMEProgressionFixesTest.Worker.class);master.worldObj=f.world;master.setUniqueID(UUID.randomUUID());
            master.posX=LOTRWaypoint.EDORAS.getXCoord()+900;master.posZ=LOTRWaypoint.EDORAS.getZCoord();master.posY=64;
            f.player.posX=master.posX;f.player.posZ=master.posZ;f.player.posY=64;f.world.loadedEntityList.add(master);
            p=f.data.getProgression(f.player.id);p.setCanonicalRank(KOMEProgressionRank.SERF);state=p.getSerfKnightProgression();state.setSerfdomMaster(KOMEProgressionNpcRankService.referenceOf(master));
            KOMEPacketHandler.network=f.network;
            KOMEProgressionNpcSpeech.sender=(player,npc,text)->f.player.messages.add(text);
        }
        KOMESerfCourierAssignment courier(){
            KOMESerfCourierAssignment a=KOMESerfCourierAssignment.create(state.getSerfdomMaster(),LOTRWaypoint.EDORAS);
            a.recipientName="Eadric";a.recipientRole="smith";a.masterRole="farmer";
            a.letterText=KOMECourierCorrespondence.compose(a,state.getSerfdomMaster());
            state.assignDuty(KOMESerfKnightDutyType.COURIER,a.writeToNBT());return a;
        }
        KOMESerfCourierAssignment active(){return KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());}
        ItemStack latest(){ItemStack result=null;for(Object entity:f.world.loadedEntityList)if(entity instanceof EntityItem)result=((EntityItem)entity).getEntityItem();return result;}
        int drops(){int count=0;for(Object entity:f.world.loadedEntityList)if(entity instanceof EntityItem)count++;return count;}
        void ready()throws Exception {
            for(KOMESerfKnightDutyType duty:KOMESerfKnightDutyType.values()){state.assignDuty(duty,null);state.completeDuty(duty);}
            KOMEProgressionNpcRef liege=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Liege","rohan",0,20,64,30);
            assertTrue(KOMESerfKnightService.acceptStandingTrial(state,liege,KOMEProgressionNpcRank.LORD,true,new Random(4),10,f.player.id).success);
            assertTrue(KOMESerfKnightService.markTrialObjectiveComplete(state).success);
            Field alignments=LOTRPlayerData.class.getDeclaredField("alignments");alignments.setAccessible(true);((Map)alignments.get(LOTRLevelData.getData(f.player))).put(LOTRFaction.ROHAN,150F);
        }
        @Override public void close(){KOMEPacketHandler.network=previous;KOMEProgressionNpcSpeech.sender=previousSpeech;}
        void loseDispatch(){for(Object entity:f.world.loadedEntityList)if(entity instanceof EntityItem)((Entity)entity).setDead();Arrays.fill(f.player.inventory.mainInventory,null);}
        boolean interact(){return courierInteraction(false);}
        boolean replace(){return courierInteraction(true);}
        boolean courierInteraction(boolean replacementRequested){return KOMECourierIssuance.interact(f.player,f.data,master,new KOMECourierIssuance.Dialogue(){public void say(net.minecraft.entity.player.EntityPlayerMP player,lotr.common.entity.npc.LOTREntityNPC npc,String text){f.player.messages.add(text);}},replacementRequested);}
    }
    /** Native pouch/coin APIs with inert registered items; restore mutable LOTR fields after each test. */
    static final class NativeItems implements AutoCloseable {
        static final Map<Field,Item> registered=new LinkedHashMap<Field,Item>();
        static int nextId=28000;
        final Map<Field,Object> original=new LinkedHashMap<Field,Object>();
        NativeItems()throws Exception {
            java.lang.reflect.Method raw=Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);raw.setAccessible(true);
            for(Field field:LOTRMod.class.getFields())if(field.getType()==Item.class&&Modifier.isStatic(field.getModifiers())&&field.get(null)==null){
                Item item=field.getName().equals("pouch")?new LOTRItemPouch():field.getName().equals("silverCoin")?new LOTRItemCoin():field.getName().equals("mugWater")?new lotr.common.item.LOTRItemMug(true,false):new ItemFood(8,0.8F,false);
                if(field.getName().matches("(sword|spear|dagger|helmet|body|hammer|scimitar|rangerBow).*"))item.setMaxStackSize(1);
                if(registered.containsKey(field))item=registered.get(field);
                else {raw.invoke(Item.itemRegistry,nextId++,"kome:followup_"+field.getName().toLowerCase(Locale.ROOT),item);registered.put(field,item);}
                original.put(field,null);field.set(null,item);
            }
        }
        @Override public void close()throws Exception{for(Map.Entry<Field,Object> entry:original.entrySet())entry.getKey().set(null,entry.getValue());}
    }
    @Test public void localWaterBottleQuotaIconMatchingAndTurnInAgree()throws Exception {
        try(NativeItems items=new NativeItems();Session s=new Session()) {
            List<KOMESerfProvisioningAssignment.Candidate> foods=Arrays.asList(KOMESerfProvisioningAssignment.Candidate.runtime(Items.bread,0),KOMESerfProvisioningAssignment.Candidate.runtime(Items.apple,0),KOMESerfProvisioningAssignment.Candidate.runtime(LOTRMod.deerCooked,0));
            KOMESerfProvisioningAssignment a=KOMESerfProvisioningAssignment.generate(foods,Arrays.asList(KOMESerfProvisioningAssignment.Candidate.runtime(LOTRMod.mugWater,0)),Arrays.asList("BOTTLE"),new Random(6));
            assertTrue(a.drink.required>=1&&a.drink.required<=16);assertSame(Items.potionitem,a.drink.requestedStack().getItem());assertEquals(1,a.drink.requestedStack().getMaxStackSize());
            a=KOMESerfProvisioningAssignment.readFromNBT(a.writeToNBT());assertNotNull(a);
            for(int slot=0;slot<a.drink.required;slot++){s.f.player.inventory.mainInventory[slot]=a.drink.requestedStack();assertTrue(KOMESerfProvisioningService.matches(s.f.player.inventory.mainInventory[slot],a.drink));}
            assertEquals(a.drink.required,KOMESerfProvisioningService.deliver(a,s.f.player.inventory));assertTrue(a.drink.complete());assertEquals(0,KOMESerfProvisioningService.deliver(a,s.f.player.inventory));
        }
    }
    @Test public void physicalIssuanceCooldownsThreeReplacementsAndDismissalPersist()throws Exception {
        try(Session s=new Session()) {
            s.courier();Arrays.fill(s.f.player.inventory.mainInventory,new ItemStack(Items.bread));
            assertTrue(KOMECourierIssuance.initial(s.f.player,s.f.data,s.master));assertEquals(1,s.drops());assertEquals(0,s.active().replacements);
            assertEquals(1,Collections.frequency(s.f.world.playedSounds,"random.pop"));
            assertTrue(KOMECourierIssuance.initial(s.f.player,s.f.data,s.master));assertEquals(1,s.drops());
            ItemStack initial=s.latest().copy();String text=s.active().letterText,recipient=s.active().recipientName;
            for(int count=1;count<=3;count++) {
                s.f.world.testWorldTime=s.active().nextReplacementWorldTime-1;
                assertTrue(s.interact());assertEquals(count-1,s.active().replacements);assertEquals(count,s.drops());
                s.state.readFromNBT(s.state.writeToNBT());
                s.f.world.testWorldTime=s.active().nextReplacementWorldTime;
                s.loseDispatch();assertTrue(s.replace());assertEquals(count,s.active().replacements);assertEquals(count+1,s.drops());
                assertEquals(text,s.active().letterText);assertEquals(recipient,s.active().recipientName);
                assertFalse(KOMECourierService.matching(initial,s.active(),s.f.player,s.state.getSerfdomMaster()));
                assertTrue(KOMECourierService.matching(s.latest(),s.active(),s.f.player,s.state.getSerfdomMaster()));
                assertTrue(s.interact());assertTrue(s.state.getSerfdomMaster().isSet());assertEquals(count+1,s.drops());
            }
            assertTrue(KOMECourierIssuance.warning("rohan","farmer",3).contains("final warning"));
            s.p.grant("baseline.miniquests");s.f.world.testWorldTime=s.active().nextReplacementWorldTime;
            assertEquals("0/1",KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).progress);
            assertTrue(KOMEProgressionSummary.courierCopy(s.p,s.f.world.testWorldTime).contains("No copies remain"));
            s.loseDispatch();assertTrue(s.replace());assertFalse(s.state.getSerfdomMaster().isSet());assertEquals("",s.state.getActiveAssignmentKind());
            assertEquals(KOMEProgressionRank.SERF,s.p.getCanonicalRank());assertTrue(s.p.isCompleted(KOMEProgressionAchievement.forID("baseline.miniquests")));
            for(Object entity:s.f.world.loadedEntityList)if(entity instanceof EntityItem)assertTrue(((Entity)entity).isDead);
        }
    }
    @Test public void confirmedDeathReturnsValidBookBeforeReplacementAndCompletesOnce()throws Exception {
        try(Session s=new Session()) {
            KOMESerfCourierAssignment a=s.courier();a.recipient=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Eadric","rohan",0,a.destinationX,64,a.destinationZ);
            s.state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());
            assertTrue(KOMECourierIssuance.initial(s.f.player,s.f.data,s.master));ItemStack book=s.latest().copy();
            assertFalse(KOMECourierService.handleRecipientDeath(s.f.data,UUID.randomUUID().toString()));assertFalse(s.active().confirmedRecipientDeath);
            s.f.world.testWorldTime=100;KOMECourierService.tickPlayer(s.f.player);assertFalse(s.active().confirmedRecipientDeath);assertEquals(a.recipient.entityUuid,s.active().recipient.entityUuid);
            assertTrue(KOMECourierService.handleRecipientDeath(s.f.data,a.recipient.entityUuid));assertFalse(KOMECourierService.handleRecipientDeath(s.f.data,a.recipient.entityUuid));
            s.state.readFromNBT(s.state.writeToNBT());assertTrue(s.active().confirmedRecipientDeath);assertEquals("Eadric",s.active().recipient.displayName);
            assertTrue(KOMEProgressionTrackerSnapshot.project(s.f.player,s.p).objective.contains("Return to your Master"));
            s.f.player.inventory.mainInventory[0]=book;s.f.world.testWorldTime=s.active().nextReplacementWorldTime;
            assertTrue(s.interact());assertTrue(s.state.getDuty(KOMESerfKnightDutyType.COURIER).isCompleted());assertNull(s.f.player.inventory.mainInventory[0]);assertEquals(1,s.drops());
            assertFalse(KOMECourierService.reportToMaster(s.f.player,s.f.data,s.p));
        }
    }
    @Test public void deadRecipientLostBookCanBeReplacedButOldRevisionCannotReturn()throws Exception {
        try(Session s=new Session()) {
            KOMESerfCourierAssignment a=s.courier();a.recipient=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Eadric","rohan",0,a.destinationX,64,a.destinationZ);s.state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());
            assertTrue(KOMECourierIssuance.initial(s.f.player,s.f.data,s.master));ItemStack old=s.latest().copy();assertTrue(KOMECourierService.handleRecipientDeath(s.f.data,a.recipient.entityUuid));
            s.f.world.testWorldTime=s.active().nextReplacementWorldTime;s.loseDispatch();assertTrue(s.replace());assertEquals(1,s.active().replacements);
            s.f.player.inventory.mainInventory[0]=old;assertFalse(KOMECourierService.reportToMaster(s.f.player,s.f.data,s.p));
            s.f.player.inventory.mainInventory[0]=s.latest().copy();assertTrue(KOMECourierService.reportToMaster(s.f.player,s.f.data,s.p));
        }
    }
    @Test public void failedSpawnDoesNotIssueCountOrPlayPop()throws Exception {
        try(Session s=new Session()) {
            s.courier();s.f.world.spawnSucceeds=false;assertFalse(KOMECourierIssuance.initial(s.f.player,s.f.data,s.master));assertFalse(s.active().documentIssued);assertEquals(0,s.drops());assertTrue(s.f.world.playedSounds.isEmpty());
            s.f.world.spawnSucceeds=true;assertTrue(KOMECourierIssuance.initial(s.f.player,s.f.data,s.master));s.f.world.testWorldTime=s.active().nextReplacementWorldTime;s.f.world.spawnSucceeds=false;
            s.loseDispatch();assertTrue(s.replace());assertEquals(0,s.active().replacements);assertEquals(1,s.drops());
        }
    }
    @Test public void giftGenerationFitsNativePouchAndClaimIsIdempotentWithFullInventory()throws Exception {
        try(NativeItems items=new NativeItems();Session s=new Session()) {
            String[] factions={"rohan","gondor","durinsfolk","bluemountains","highelves","woodelf","lothlorien","dorwinion","mordor","angmar","gundabad","dolguldur","isengard","halftroll","harad","morwaith","taurethrim","rhudel","dunedain","dale","dunland","fangorn","hobbit","bree"};
            for(String faction:factions)for(int seed=0;seed<12;seed++) {
                for(Item equipment:KOMEPartingGiftService.equipment(faction))assertNotNull(faction+" equipment",equipment);
                ItemStack gift=KOMEPartingGiftService.generate(faction,new Random(seed));assertNotNull(faction,gift);assertEquals(1,gift.getItemDamage());assertEquals(18,LOTRItemPouch.getCapacity(gift));
                assertEquals(KOMEProgressionFactionResolver.resolve(faction).getFactionColor(),LOTRItemPouch.getPouchColor(gift));
                LOTRInventoryPouch pouch=new LOTRInventoryPouch(gift);int value=0,filled=0;
                for(int slot=0;slot<pouch.getSizeInventory();slot++){ItemStack content=pouch.getStackInSlot(slot);if(content!=null){filled++;assertTrue(content.stackSize<=content.getMaxStackSize());value+=LOTRItemCoin.getStackValue(content,false);}}
                assertTrue(filled>=4&&filled<=18);assertTrue(value>=32&&value<=64);
            }
            s.ready();Arrays.fill(s.f.player.inventory.mainInventory,new ItemStack(Items.bread));
            ItemStack pending=KOMEPartingGiftService.pending(s.state,new Random(5));assertNotNull(pending);assertFalse(s.state.hasPartingGift());
            s.state.readFromNBT(s.state.writeToNBT());assertTrue(ItemStack.areItemStacksEqual(pending,s.state.getPendingPartingGift()));
            assertTrue(KOMESerfdomMasterService.conferKnighthood(s.f.player,s.f.data,s.master).success);
            assertEquals(0,s.drops());assertTrue(KOMEProgressionNpcInteractionService.interact(s.f.player,s.f.data,s.master));
            assertTrue(ItemStack.areItemStacksEqual(pending,s.latest()));assertEquals(Items.bread,s.f.player.inventory.mainInventory[4].getItem());assertEquals(1,s.drops());assertEquals(1,s.f.world.playedSounds.size());assertTrue(s.state.hasPartingGift());assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());
            assertFalse(KOMESerfdomMasterService.conferKnighthood(s.f.player,s.f.data,s.master).success);assertNull(KOMEPartingGiftService.pending(s.state,new Random(5)));
            s.state.readFromNBT(s.state.writeToNBT());assertTrue(s.state.hasPartingGift());
        }
    }
    @Test public void giftAndMedallionHaveIndependentOwnersAcrossReloadAndClaim()throws Exception {
        try(NativeItems items=new NativeItems();Session s=new Session()) {
            s.ready();String master=s.state.getSerfdomMaster().entityUuid,liege=s.state.getLiege().entityUuid;
            List<KOMEVisualMarker> markers=KOMEVisualLocationService.markersFor(s.p);assertEquals(2,markers.size());assertEquals(liege,markers.get(0).entityUuid);assertEquals(KOMEVisualMarker.Role.MASTER_GIFT,markers.get(1).role);assertEquals(master,markers.get(1).entityUuid);
            KOMEPlayerProgression reload=new KOMEPlayerProgression();reload.readFromNBT(s.p.writeToNBT());assertEquals(KOMEVisualLocationService.signature(markers),KOMEVisualLocationService.signature(KOMEVisualLocationService.markersFor(reload)));
            ByteBuf buffer=Unpooled.buffer();new KOMEPacketVisualMarkers(markers).toBytes(buffer);KOMEPacketVisualMarkers decoded=new KOMEPacketVisualMarkers();decoded.fromBytes(buffer);assertEquals(0,buffer.readableBytes());assertEquals(KOMEVisualMarker.Role.MASTER_GIFT,decoded.markers.get(1).role);
            assertTrue(KOMESerfdomMasterService.conferKnighthood(s.f.player,s.f.data,s.master).success);assertEquals(0,s.drops());assertTrue(KOMEProgressionNpcInteractionService.interact(s.f.player,s.f.data,s.master));assertEquals(1,s.drops());assertTrue(s.state.hasPartingGift());markers=KOMEVisualLocationService.markersFor(s.p);assertEquals(1,markers.size());assertEquals(liege,markers.get(0).entityUuid);
            KOMESerfKnightService.leaveLiege(s.p);assertTrue(KOMEVisualLocationService.markersFor(s.p).isEmpty());
        }
    }
    @Test public void correspondencePaginatesAllTextAndKeepsIdentifiersHidden()throws Exception {
        try(Session s=new Session()) {
            KOMESerfCourierAssignment a=s.courier();String text=a.letterText;
            assertTrue(text.startsWith("Eadric,"));assertTrue(text.endsWith("Worker"));
            for(String forbidden:new String[]{"Carry this","cooldown",a.token,a.destinationName,"coordinates","replacement","Master:"})assertFalse(forbidden,text.contains(forbidden));
            List<String> pages=KOMECourierCorrespondence.pages(text,new KOMECourierCorrespondence.Width(){public int pixels(String value){return value.length()*6;}});assertTrue(pages.size()>1);
            for(String page:pages){assertTrue(page.length()<=256);assertTrue(page.split("\n",-1).length<=13);for(String line:page.split("\n"))assertTrue(line.length()*6<=116);}
            assertEquals(text.replaceAll("\\s+"," ").trim(),String.join(" ",pages).replaceAll("\\s+"," ").trim());
            KOMESerfCourierAssignment reload=KOMESerfCourierAssignment.readFromNBT(a.writeToNBT());assertEquals(text,reload.letterText);assertEquals("To Eadric",KOMECourierService.dispatchTitle(reload));
        }
    }
    @Test public void cancelledDeathAndDimensionChangesNeverConfirmDeath()throws Exception {
        try(Session s=new Session()) {
            KOMESerfCourierAssignment a=s.courier();a.recipient=KOMEProgressionNpcRankService.referenceOf(s.master);s.state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());
            // The plain JUnit loader does not run Forge's @Cancelable transformer.
            net.minecraftforge.event.entity.living.LivingDeathEvent event=new net.minecraftforge.event.entity.living.LivingDeathEvent(s.master,net.minecraft.util.DamageSource.generic){@Override public boolean isCancelable(){return true;}};event.setCanceled(true);
            new KOMEEvents().onCourierRecipientDeath(event);assertFalse(s.active().confirmedRecipientDeath);
            s.f.player.dimension=42;s.f.world.testWorldTime=100;KOMECourierService.tickPlayer(s.f.player);assertFalse(s.active().confirmedRecipientDeath);
            assertEquals(a.recipient.entityUuid,s.active().recipient.entityUuid);
        }
    }
    @Test public void legacyBooksAndPendingGiftMetadataHaveBackwardCompatibleDefaults()throws Exception {
        try(NativeItems items=new NativeItems();Session s=new Session()) {
            KOMESerfCourierAssignment a=s.courier();NBTTagCompound legacy=a.writeToNBT();legacy.setInteger("Version",7);legacy.removeTag("DocumentIssued");legacy.removeTag("DocumentRevision");legacy.removeTag("Replacements");legacy.removeTag("NextReplacementWorldTime");legacy.removeTag("ConfirmedRecipientDeath");
            KOMESerfCourierAssignment loaded=KOMESerfCourierAssignment.readFromNBT(legacy);assertTrue(loaded.documentIssued);assertEquals(0,loaded.replacements);assertFalse(loaded.confirmedRecipientDeath);assertEquals(a.destinationX,loaded.destinationX,0);
            ItemStack book=KOMECourierService.message(a,s.f.player,s.state.getSerfdomMaster());book.getTagCompound().getCompoundTag("KOMECourier").removeTag("Revision");
            assertSame("native written book identity",Items.written_book,book.getItem());assertTrue("loaded assignment",loaded.valid());
            assertEquals(a.token,loaded.token);assertEquals(a.destinationKey,loaded.destinationKey);assertEquals(0,loaded.documentRevision);
            assertTrue(KOMECourierService.matching(book,loaded,s.f.player,s.state.getSerfdomMaster()));
            ItemStack pouch=new ItemStack(LOTRMod.pouch,1,1),content=new ItemStack(LOTRMod.swordRohan,1,7);NBTTagCompound detail=new NBTTagCompound();detail.setString("GiftMark","kept");content.setTagCompound(detail);
            KOMEPartingGiftService.pack(pouch,Arrays.asList(content));assertTrue(ItemStack.areItemStacksEqual(content,new LOTRInventoryPouch(pouch).getStackInSlot(0)));
        }
    }
    @Test public void completedLiegeIndicatorSurvivesDutyChangesAndMasterCleanup()throws Exception {
        try(Session s=new Session()) {
            s.ready();KOMEProgressionNpcRef liege=s.state.getLiege();
            NBTTagCompound changed=s.state.writeToNBT();changed.getCompoundTag("Duties").getCompoundTag("courier").setBoolean("Completed",false);
            s.state.readFromNBT(changed);assertTrue(s.state.isTrialCompleted());assertEquals(liege.entityUuid,KOMEVisualLocationService.markersFor(s.p).get(0).entityUuid);
            KOMESerfKnightService.leaveSerfdomMaster(s.state);assertTrue(s.state.hasLiege());assertTrue(s.state.isTrialCompleted());assertEquals(liege.entityUuid,KOMEVisualLocationService.markersFor(s.p).get(0).entityUuid);
        }
    }
}
