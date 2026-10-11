package kome.common.data;

import kome.common.KOMEAccessFixture;
import kome.common.network.*;
import lotr.common.entity.npc.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class KOMECampaignHiredPresentationTest {
    private KOMEAccessFixture f;
    private KOMECampaignMovementReconstructionTest.MovementNpc npc;
    private KOMEHiredUnitRecord record;
    private cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous;
    private Object priorType;
    private java.lang.reflect.Field nativeNetwork;
    @Before public void setup() throws Exception {
        priorType=net.minecraft.entity.EntityList.classToStringMapping.get(KOMECampaignMovementReconstructionTest.MovementNpc.class);
        f=new KOMEAccessFixture();f.world.rand=new Random(4);
        npc=new KOMECampaignMovementReconstructionTest.MovementNpc(f.world);
        f.world.isRemote=true;
        npc.hiredNPCInfo.hireUnit(f.player,false,null,new LOTRUnitTradeEntry(npc.getClass(),0,0F),"North Guard",null);
        f.world.isRemote=false;
        record=new KOMEHiredUnitRecord();record.entity=npc.getUniqueID();record.owner=f.player.getUniqueID();
        f.data.hiredUnits.put(record.entity,record);f.world.loadedEntityList.add(npc);
        nativeNetwork=lotr.common.network.LOTRPacketHandler.class.getDeclaredField("networkWrapper");nativeNetwork.setAccessible(true);
        previous=(cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper)nativeNetwork.get(null);nativeNetwork.set(null,f.network);
    }
    @After public void reset() throws Exception {nativeNetwork.set(null,previous);
        if(priorType!=null)net.minecraft.entity.EntityList.classToStringMapping.put(npc.getClass(),priorType);
        else net.minecraft.entity.EntityList.classToStringMapping.remove(npc.getClass());
    }
    @Test public void ordinaryNativeHireBaselineRemainsOwnedAndReceivesNoCampaignPublication(){
        assertTrue(npc.hiredNPCInfo.isActive);assertEquals(record.owner,npc.hiredNPCInfo.getHiringPlayerUUID());
        assertEquals(LOTRHiredNPCInfo.Task.WARRIOR,npc.hiredNPCInfo.getTask());
        refresh();assertTrue(f.network.messages.isEmpty());
        assertFalse(KOMEHiredUnitClassification.isCampaignUnit(record));
    }
    @Test public void campaignPublishesExactNativeStateAfterTrackingAndRecordPublication(){
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        f.data.hiredUnits.remove(record.entity);
        assertFalse(refresh());assertTrue(f.network.messages.isEmpty());
        f.data.hiredUnits.put(record.entity,record);assertTrue(refresh());
        lotr.common.network.LOTRPacketHiredInfo p=packet();
        assertEquals(record.owner,p.hiringPlayer);assertEquals(npc.hiredNPCInfo.getTask(),p.task);
        assertEquals("North Guard",p.squadron);assertEquals(npc.hiredNPCInfo.xpLevel,p.xpLvl);assertTrue(npc.hiredNPCInfo.isActive);
    }
    @Test public void haltedAndReadyCampaignPresentationUsesSameOwnerTask(){
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        NBTTagCompound nativeNbt=new NBTTagCompound();npc.hiredNPCInfo.writeToNBT(nativeNbt);
        nativeNbt.getCompoundTag("HiredNPCInfo").setBoolean("CanMove",false);
        f.world.isRemote=true;npc.hiredNPCInfo.readFromNBT(nativeNbt);f.world.isRemote=false;
        assertTrue(npc.hiredNPCInfo.isHalted());
        refresh();assertEquals(record.owner,packet().hiringPlayer);
        f.network.messages.clear();f.world.isRemote=true;npc.hiredNPCInfo.ready();f.world.isRemote=false;
        assertFalse(npc.hiredNPCInfo.isHalted());
        refresh();assertEquals(record.owner,packet().hiringPlayer);
    }
    @Test public void unrelatedOrdinaryWrongDimensionDisconnectedAndFailedSpawnDoNotPublish(){
        assertFalse(refresh());KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        assertFalse(KOMECampaignHiredPresentation.refresh(f.player,f.world.provider.dimensionId+1,npc.getEntityId()));
        f.player.connected=false;assertFalse(refresh());f.player.connected=true;
        UUID owner=record.owner;record.owner=UUID.randomUUID();assertFalse(refresh());record.owner=owner;
        f.world.loadedEntityList.clear();assertFalse(refresh());assertTrue(f.network.messages.isEmpty());
    }
    @Test public void persistedNativeHireStillPublishesOnRestartAndVerifiedReplacement() throws Exception {
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        priorType=net.minecraft.entity.EntityList.classToStringMapping.put(npc.getClass(),"kom89.presentation");
        record.stationedEntityData=KOMEEntitySnapshots.snapshot(record,npc);
        NBTTagCompound recordNbt=record.writeToNBT();
        KOMEHiredUnitRecord restored=new KOMEHiredUnitRecord();restored.readFromNBT(recordNbt);
        f.data.hiredUnits.put(restored.entity,restored);record=restored;
        NBTTagCompound physical=KOMECampaignHealth.movementSnapshot(record,record.stationedEntityData);
        KOMECampaignHealth.prepareReconstruction(physical);
        KOMECampaignMovementReconstructionTest.MovementNpc replacement=new KOMECampaignMovementReconstructionTest.MovementNpc(f.world);
        replacement.readFromNBT(physical);
        UUID old=record.entity;
        KOMECampaignHealth.reconstructed(record,old,replacement.getUniqueID(),null,null);
        f.data.hiredUnits.remove(old);record.entity=replacement.getUniqueID();f.data.hiredUnits.put(record.entity,record);
        f.world.loadedEntityList.clear();f.world.loadedEntityList.add(replacement);
        npc=replacement;assertTrue(refresh());
        assertEquals(record.owner,packet().hiringPlayer);assertTrue(f.network.messages.get(0) instanceof lotr.common.network.LOTRPacketEntityUUID);
        assertTrue(replacement.hiredNPCInfo.isActive);assertEquals(LOTRHiredNPCInfo.Task.WARRIOR,replacement.hiredNPCInfo.getTask());
    }
    @Test public void lostInitialUuidAndHirePacketsRecoverThroughClientSpawnAcknowledgmentAndNativeGui() throws Exception {
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        npc.setHealth(13F);
        KOMEAccessFixture client=new KOMEAccessFixture();client.world.rand=new Random(5);client.player.id=f.player.id;
        KOMECampaignMovementReconstructionTest.MovementNpc seen=new KOMECampaignMovementReconstructionTest.MovementNpc(client.world);
        seen.setEntityId(npc.getEntityId());seen.setHealth(13F);client.world.isRemote=true;
        assertNotEquals(npc.getUniqueID(),seen.getUniqueID());
        lotr.common.LOTRCommonProxy previousProxy=lotr.common.LOTRMod.proxy;
        java.util.concurrent.atomic.AtomicReference<LOTREntityNPC> opened=new java.util.concurrent.atomic.AtomicReference<>();
        try {
            lotr.common.LOTRMod.proxy=new lotr.common.LOTRCommonProxy(){
                @Override public net.minecraft.world.World getClientWorld(){return client.world;}
                @Override public net.minecraft.entity.player.EntityPlayer getClientPlayer(){return client.player;}
                @Override public void openHiredNPCGui(LOTREntityNPC entity){opened.set(entity);}
            };
            assertTrue(refresh());
            deliver(client); // Actual native handlers discard BOTH pre-spawn messages.
            assertNull(seen.hiredNPCInfo.getHiringPlayer());assertNotEquals(npc.getUniqueID(),seen.getUniqueID());
            List<KOMEPacketCampaignHiredInfoRequest> requests=new ArrayList<>();
            kome.client.KOMECampaignHiredInfoClient acknowledgment=new kome.client.KOMECampaignHiredInfoClient(()->client.world,requests::add);
            acknowledgment.joined(seen);acknowledgment.tick();assertTrue(requests.isEmpty());
            client.world.loadedEntityList.add(seen);
            // Server right-click eligibility succeeds, but the actual client GUI owner predicate fails.
            new lotr.common.network.LOTRPacketHiredUnitInteract.Handler().onMessage(
                new lotr.common.network.LOTRPacketHiredUnitInteract(npc.getEntityId(),1),f.context);
            deliver(client);assertNull(opened.get());assertNull(seen.hiredNPCInfo.getHiringPlayer());
            acknowledgment.tick();assertEquals(1,requests.size());
            KOMEPacketCampaignHiredInfoRequest request=wire(requests.get(0));
            new KOMEPacketCampaignHiredInfoRequest.Handler().onMessage(request,f.context);
            deliver(client);
            assertEquals(npc.getUniqueID(),seen.getUniqueID());assertSame(client.player,seen.hiredNPCInfo.getHiringPlayer());
            assertEquals(LOTRHiredNPCInfo.Task.WARRIOR,seen.hiredNPCInfo.getTask());assertEquals("North Guard",seen.hiredNPCInfo.getSquadron());
            assertEquals(13F,npc.getHealth(),0);assertEquals(13F,seen.getHealth(),0);
            assertFalse(seen.hiredNPCInfo.isActive); // Native basic-info contract; client isActive is NOT the renderer predicate.
            new lotr.common.network.LOTRPacketHiredUnitInteract.Handler().onMessage(
                new lotr.common.network.LOTRPacketHiredUnitInteract(npc.getEntityId(),1),f.context);
            deliver(client);
            assertSame(seen,opened.get());assertTrue(seen.hiredNPCInfo.isActive);
            assertSame(client.player,seen.hiredNPCInfo.getHiringPlayer());
            assertTrue(f.network.messages.isEmpty());
        } finally {lotr.common.LOTRMod.proxy=previousProxy;}
    }
    @Test public void ordinaryNativeClientBaselineUsesSameOwnerPredicateAndGuiContract() throws Exception {
        KOMEAccessFixture client=new KOMEAccessFixture();client.world.rand=new Random(5);client.player.id=f.player.id;
        KOMECampaignMovementReconstructionTest.MovementNpc seen=new KOMECampaignMovementReconstructionTest.MovementNpc(client.world);
        seen.setEntityId(npc.getEntityId());client.world.isRemote=true;client.world.loadedEntityList.add(seen);
        lotr.common.LOTRCommonProxy previousProxy=lotr.common.LOTRMod.proxy;
        java.util.concurrent.atomic.AtomicReference<LOTREntityNPC> opened=new java.util.concurrent.atomic.AtomicReference<>();
        try {
            lotr.common.LOTRMod.proxy=new lotr.common.LOTRCommonProxy(){
                @Override public net.minecraft.world.World getClientWorld(){return client.world;}
                @Override public net.minecraft.entity.player.EntityPlayer getClientPlayer(){return client.player;}
                @Override public void openHiredNPCGui(LOTREntityNPC entity){opened.set(entity);}
            };
            npc.hiredNPCInfo.sendBasicData(f.player);deliver(client);
            assertSame(client.player,seen.hiredNPCInfo.getHiringPlayer());assertFalse(seen.hiredNPCInfo.isActive);
            new lotr.common.network.LOTRPacketHiredUnitInteract.Handler().onMessage(
                new lotr.common.network.LOTRPacketHiredUnitInteract(npc.getEntityId(),1),f.context);
            deliver(client);assertSame(seen,opened.get());assertFalse(KOMEHiredUnitClassification.isCampaignUnit(record));
        } finally {lotr.common.LOTRMod.proxy=previousProxy;}
    }
    @Test public void nativeHornReadyStillUsesServerOwnershipWhileClientOwnershipIsAbsent(){
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        NBTTagCompound halted=new NBTTagCompound();npc.hiredNPCInfo.writeToNBT(halted);
        halted.getCompoundTag("HiredNPCInfo").setBoolean("CanMove",false);
        f.world.isRemote=true;npc.hiredNPCInfo.readFromNBT(halted);f.world.isRemote=false;
        assertTrue(npc.hiredNPCInfo.isHalted());assertSame(f.player,npc.hiredNPCInfo.getHiringPlayer());
        lotr.common.item.LOTRItemCommandHorn horn=new lotr.common.item.LOTRItemCommandHorn();
        net.minecraft.item.ItemStack ready=new net.minecraft.item.ItemStack(horn,1,2);
        lotr.common.LOTRSquadrons.setSquadron(ready,"North Guard");
        horn.onEaten(ready,f.world,f.player);
        assertFalse(npc.hiredNPCInfo.isHalted());assertTrue(npc.hiredNPCInfo.isActive);
        assertEquals(record.owner,npc.hiredNPCInfo.getHiringPlayerUUID());
    }
    @Test public void actualNativeRendererAndGuiHandlerShareResolvedOwnerPredicate() throws Exception {
        org.objectweb.asm.tree.ClassNode render=new org.objectweb.asm.tree.ClassNode();
        try(java.io.InputStream bytes=getClass().getClassLoader().getResourceAsStream("lotr/client/render/entity/LOTRRenderBiped.class")){
            new org.objectweb.asm.ClassReader(bytes).accept(render,0);
        }
        boolean matched=false;
        for(org.objectweb.asm.tree.MethodNode method:render.methods){
            boolean owner=false,player=false,identity=false,bar=false;
            for(org.objectweb.asm.tree.AbstractInsnNode insn:method.instructions.toArray()){
                if(insn instanceof org.objectweb.asm.tree.MethodInsnNode){
                    org.objectweb.asm.tree.MethodInsnNode call=(org.objectweb.asm.tree.MethodInsnNode)insn;
                    if(call.owner.equals("lotr/common/entity/npc/LOTRHiredNPCInfo")&&call.name.equals("getHiringPlayer"))owner=true;
                    if(call.name.equals("renderNPCHealthBar"))bar=true;
                }
                if(owner&&insn instanceof org.objectweb.asm.tree.FieldInsnNode){
                    org.objectweb.asm.tree.FieldInsnNode field=(org.objectweb.asm.tree.FieldInsnNode)insn;
                    if(field.owner.equals("net/minecraft/client/renderer/entity/RenderManager")
                        &&field.desc.equals("Lnet/minecraft/entity/EntityLivingBase;"))player=true;
                }
                if(owner&&player&&insn.getOpcode()==org.objectweb.asm.Opcodes.IF_ACMPNE)identity=true;
            }
            matched|=owner&&player&&identity&&bar;
        }
        assertTrue("Native bar requires identity equality of resolved owner and render player",matched);
        org.objectweb.asm.tree.ClassNode gui=new org.objectweb.asm.tree.ClassNode();
        try(java.io.InputStream bytes=getClass().getClassLoader().getResourceAsStream("lotr/common/network/LOTRPacketHiredGui$Handler.class")){
            new org.objectweb.asm.ClassReader(bytes).accept(gui,0);
        }
        boolean guiOwner=false,guiOpen=false;
        for(org.objectweb.asm.tree.MethodNode method:gui.methods)for(org.objectweb.asm.tree.AbstractInsnNode insn:method.instructions.toArray())
            if(insn instanceof org.objectweb.asm.tree.MethodInsnNode){
                String name=((org.objectweb.asm.tree.MethodInsnNode)insn).name;
                guiOwner|=name.equals("getHiringPlayer");guiOpen|=name.equals("openHiredNPCGui");
            }
        assertTrue(guiOwner&&guiOpen);
    }
    private void deliver(KOMEAccessFixture client) throws Exception {
        List<cpw.mods.fml.common.network.simpleimpl.IMessage> messages=new ArrayList<>(f.network.messages);f.network.messages.clear();
        for(cpw.mods.fml.common.network.simpleimpl.IMessage raw:messages){
            cpw.mods.fml.common.network.simpleimpl.IMessage message=wire(raw);
            if(message instanceof lotr.common.network.LOTRPacketEntityUUID)
                new lotr.common.network.LOTRPacketEntityUUID.Handler().onMessage((lotr.common.network.LOTRPacketEntityUUID)message,null);
            else if(message instanceof lotr.common.network.LOTRPacketHiredInfo)
                new lotr.common.network.LOTRPacketHiredInfo.Handler().onMessage((lotr.common.network.LOTRPacketHiredInfo)message,null);
            else if(message instanceof lotr.common.network.LOTRPacketHiredGui)
                new lotr.common.network.LOTRPacketHiredGui.Handler().onMessage((lotr.common.network.LOTRPacketHiredGui)message,null);
            else fail("Unexpected native message "+message.getClass());
        }
    }
    @SuppressWarnings("unchecked") private static <T extends cpw.mods.fml.common.network.simpleimpl.IMessage> T wire(T message) throws Exception {
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();message.toBytes(bytes);
        T decoded=(T)message.getClass().newInstance();decoded.fromBytes(bytes);assertFalse(bytes.isReadable());return decoded;
    }
    private boolean refresh(){return KOMECampaignHiredPresentation.refresh(f.player,f.world.provider.dimensionId,npc.getEntityId());}
    private lotr.common.network.LOTRPacketHiredInfo packet(){assertEquals(2,f.network.messages.size());return (lotr.common.network.LOTRPacketHiredInfo)f.network.messages.get(1); }
}
