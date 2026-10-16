package kome.common.data;

import java.util.UUID;
import lotr.common.entity.item.LOTREntityTraderRespawn;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native carrier persistence plus conservative identity/home rebinding; no world generation claim. */
public class KOMEProgressionSeatReplacementTest {
    private static LOTREntityNPC seat(KOMEProgressionFollowupTest.Session s) throws Exception {
        LOTREntityNPC npc=KOMESerfKnightStabilizationTest.captain(s);
        KOMEProgressionServiceLocationTest.generatedHome(npc,"RohanFortress");
        npc.setShouldTraderRespawn(true);return npc;
    }
    private static LOTREntityTraderRespawn capture(KOMEProgressionFollowupTest.Session s,LOTREntityNPC source) {
        LOTREntityTraderRespawn carrier=new LOTREntityTraderRespawn(s.f.world);
        Object previous=net.minecraft.entity.EntityList.classToStringMapping.put(source.getClass(),"kome.test.SeatCaptain");
        try { carrier.copyTraderDataFrom(source); } // Actual native class/home copying, then the injected callback.
        finally {
            if(previous==null)net.minecraft.entity.EntityList.classToStringMapping.remove(source.getClass());
            else net.minecraft.entity.EntityList.classToStringMapping.put(source.getClass(),previous);
        }
        KOMEProgressionServiceLocation.captureReplacement(carrier,source);
        return carrier;
    }
    @Test public void nativeCarrierSaveRestartRebindsNewIdentityOnceWithoutChangingPlayerHistory() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            LOTREntityNPC source=seat(s);s.state.setLiege(KOMEProgressionNpcRankService.referenceOf(source));
            s.p.getServiceRecognition().earn(s.f.player.id,KOMEProgressionNpcRankService.referenceOf(s.master),
                KOMEProgressionRank.SERF,KOMEProgressionRank.KNIGHT,"Three duties",1L);
            NBTTagCompound playerBefore=s.p.writeToNBT(),proofBefore=(NBTTagCompound)source.getEntityData().getCompoundTag(KOMEProgressionServiceLocation.TAG).copy();
            LOTREntityTraderRespawn carrier=capture(s,source);NBTTagCompound saved=new NBTTagCompound();carrier.writeToNBT(saved);
            assertTrue(saved.getCompoundTag("ForgeData").hasKey(KOMEProgressionServiceLocation.REPLACEMENT_TAG,10));
            LOTREntityTraderRespawn restarted=new LOTREntityTraderRespawn(s.f.world);restarted.readFromNBT(saved);
            LOTREntityNPC replacement=seat(s);replacement.getEntityData().removeTag(KOMEProgressionServiceLocation.TAG);
            assertNotEquals(source.getUniqueID(),replacement.getUniqueID());
            assertTrue(KOMEProgressionServiceLocation.replacementSpawned(s.f.world,replacement,true,restarted));
            assertTrue(KOMEProgressionServiceLocation.regional(replacement));
            assertEquals(replacement.getUniqueID().toString(),replacement.getEntityData().getCompoundTag(KOMEProgressionServiceLocation.TAG).getString("NPC"));
            assertEquals(proofBefore,source.getEntityData().getCompoundTag(KOMEProgressionServiceLocation.TAG));
            assertEquals(playerBefore,s.p.writeToNBT());
            assertFalse(restarted.getEntityData().hasKey(KOMEProgressionServiceLocation.REPLACEMENT_TAG));
            LOTREntityNPC duplicate=seat(s);duplicate.getEntityData().removeTag(KOMEProgressionServiceLocation.TAG);
            KOMEProgressionServiceLocation.replacementSpawned(s.f.world,duplicate,true,restarted);
            assertFalse(KOMEProgressionServiceLocation.regional(duplicate));
            // Entity data uses the same ForgeData save boundary after replacement and unloading.
            NBTTagCompound forge=(NBTTagCompound)replacement.getEntityData().copy();
            replacement.getEntityData().removeTag(KOMEProgressionServiceLocation.TAG);
            replacement.getEntityData().setTag(KOMEProgressionServiceLocation.TAG,forge.getCompoundTag(KOMEProgressionServiceLocation.TAG).copy());
            assertTrue(KOMEProgressionServiceLocation.regional(replacement));
        }
    }
    @Test public void failedNativeSpawnRetainsReceiptForRetryButPublishesNoProof() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            LOTREntityNPC source=seat(s),replacement=seat(s);replacement.getEntityData().removeTag(KOMEProgressionServiceLocation.TAG);
            LOTREntityTraderRespawn carrier=capture(s,source);
            assertFalse(KOMEProgressionServiceLocation.replacementSpawned(s.f.world,replacement,false,carrier));
            assertFalse(KOMEProgressionServiceLocation.regional(replacement));assertTrue(carrier.getEntityData().hasKey(KOMEProgressionServiceLocation.REPLACEMENT_TAG));
            assertTrue(KOMEProgressionServiceLocation.replacementSpawned(s.f.world,replacement,true,carrier));assertTrue(KOMEProgressionServiceLocation.regional(replacement));
        }
    }
    @Test public void nativeNpcSaveLoadRetainsUuidHomeAndForgeProofBeforeReplacement() throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
                KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.f.world.isRemote=true;
            LOTREntityNPC source=new lotr.common.entity.npc.LOTREntityRohirrimMarshal(s.f.world);
            s.f.world.isRemote=false;
            source.setPosition(s.master.posX,64,s.master.posZ);
            KOMEProgressionServiceLocationTest.generatedHome(source,"RohanFortress");source.setShouldTraderRespawn(true);
            NBTTagCompound saved=new NBTTagCompound();source.writeToNBT(saved);
            s.f.world.isRemote=true;
            LOTREntityNPC loaded=new lotr.common.entity.npc.LOTREntityRohirrimMarshal(s.f.world);loaded.readFromNBT(saved);
            s.f.world.isRemote=false;
            assertEquals(source.getUniqueID(),loaded.getUniqueID());assertTrue(loaded.shouldTraderRespawn());
            assertTrue(KOMEProgressionServiceLocation.regional(loaded));
            assertEquals(source.getHomePosition(),loaded.getHomePosition());assertEquals(source.func_110174_bM(),loaded.func_110174_bM(),0F);
            assertTrue(capture(s,loaded).getEntityData().hasKey(KOMEProgressionServiceLocation.REPLACEMENT_TAG,10));
        }
    }
    @Test public void encounterAndHiredOrNonRespawningSourcesCannotAuthorizeReplacement() throws Exception {
        for(int invalid=0;invalid<3;invalid++)try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            LOTREntityNPC source=seat(s);
            if(invalid==0)KOMEProgressionEncounterMarker.mark(source,KOMEProgressionEncounterMarker.DEFENSE,s.f.player.id,UUID.randomUUID().toString());
            if(invalid==1)source.hiredNPCInfo.isActive=true;
            if(invalid==2)source.setShouldTraderRespawn(false);
            assertFalse(capture(s,source).getEntityData().hasKey(KOMEProgressionServiceLocation.REPLACEMENT_TAG));
        }
    }
    @Test public void arbitraryLegacyHomeAndCopiedProofCannotBeAdoptedThroughReplacement() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            LOTREntityNPC source=seat(s),replacement=seat(s);source.getEntityData().removeTag(KOMEProgressionServiceLocation.TAG);
            LOTREntityTraderRespawn carrier=capture(s,source);assertFalse(carrier.getEntityData().hasKey(KOMEProgressionServiceLocation.REPLACEMENT_TAG));
            NBTTagCompound forged=(NBTTagCompound)replacement.getEntityData().getCompoundTag(KOMEProgressionServiceLocation.TAG).copy();
            source.getEntityData().setTag(KOMEProgressionServiceLocation.TAG,forged);KOMEProgressionServiceLocation.captureReplacement(carrier,source);
            assertFalse(carrier.getEntityData().hasKey(KOMEProgressionServiceLocation.REPLACEMENT_TAG));
            assertFalse(KOMEProgressionServiceLocation.regional(source));
        }
    }
    @Test public void movedHomeWrongCarrierClassFactionDimensionAndMalformedProofFailClosed() throws Exception {
        for(int invalid=0;invalid<8;invalid++)try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            LOTREntityNPC source=seat(s),replacement=seat(s);replacement.getEntityData().removeTag(KOMEProgressionServiceLocation.TAG);
            LOTREntityTraderRespawn carrier=capture(s,source);NBTTagCompound receipt=carrier.getEntityData().getCompoundTag(KOMEProgressionServiceLocation.REPLACEMENT_TAG);
            if(invalid==0)replacement.setHomeArea((int)replacement.posX+100,64,(int)replacement.posZ,8);
            if(invalid==1)receipt.setString("Carrier",UUID.randomUUID().toString());
            if(invalid==2)receipt.setString("Class","lotr.common.entity.npc.LOTREntityRohanMan");
            if(invalid==3)receipt.getCompoundTag("Proof").setString("Faction","gondor");
            if(invalid==4)receipt.getCompoundTag("Proof").setInteger("Dimension",999);
            if(invalid==5)replacement.setShouldTraderRespawn(false);
            if(invalid==6)receipt.removeTag("HomeX");
            if(invalid==7)receipt.getCompoundTag("Proof").setDouble("X",Double.NaN);
            assertTrue(KOMEProgressionServiceLocation.replacementSpawned(s.f.world,replacement,true,carrier));
            assertFalse("case "+invalid,KOMEProgressionServiceLocation.regional(replacement));
        }
    }
    @Test public void staleCaptureClearsOldReceiptAndUnprovenAcceptedRelationshipSurvivesRestart() throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            LOTREntityNPC source=seat(s);LOTREntityTraderRespawn carrier=capture(s,source);
            source.getEntityData().removeTag(KOMEProgressionServiceLocation.TAG);KOMEProgressionServiceLocation.captureReplacement(carrier,source);
            assertFalse(carrier.getEntityData().hasKey(KOMEProgressionServiceLocation.REPLACEMENT_TAG));
            s.p.setCanonicalRank(KOMEProgressionRank.KNIGHT);s.state.retireSerfdomMasterAfterPromotion();s.state.setLiege(KOMEProgressionNpcRankService.referenceOf(source));
            s.p.readFromNBT(s.p.writeToNBT());assertTrue(KOMECurrentLiege.valid(s.f.player,source));
        }
    }
}
