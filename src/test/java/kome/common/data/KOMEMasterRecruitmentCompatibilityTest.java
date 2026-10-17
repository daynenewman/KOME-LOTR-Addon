package kome.common.data;

import java.lang.reflect.Field;
import java.util.Map;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import kome.common.KOMEAccessFixture;
import kome.common.network.KOMEPacketRelationshipAction;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real native roles; controlled offer shells isolate admission from native random quest generation. */
public class KOMEMasterRecruitmentCompatibilityTest {
    private static final String[] TYPES={"MordorOrcTrader","GundabadOrcTrader","UrukHaiTrader",
        "GaladhrimSmith","WoodElfSmith","RivendellSmith","DorwinionMan","RohanFarmer"};

    static void alignment(KOMEAccessFixture f,LOTRFaction faction,float amount)throws Exception {
        Field field=LOTRPlayerData.class.getDeclaredField("alignments");field.setAccessible(true);
        ((Map)field.get(LOTRLevelData.getData(f.player))).put(faction,amount);
    }
    static LOTREntityNPC nativeNpc(KOMEAccessFixture f,String type)throws Exception {
        f.world.isRemote=true;
        LOTREntityNPC npc=(LOTREntityNPC)Class.forName("lotr.common.entity.npc.LOTREntity"+type).getConstructor(World.class).newInstance(f.world);
        f.world.isRemote=false;
        npc.setPosition(f.player.posX,64,f.player.posZ);npc.questInfo=new QuietQuestInfo(npc);
        f.world.loadedEntityList.add(npc);return npc;
    }
    static void territory(KOMEAccessFixture f,LOTRFaction faction)throws Exception {
        KOMEProgressionServiceLocationTest.territory(f);
        KOMEProgressionGameplayFixture.EnemySpawns spawns=KOMEAccessFixture.allocate(KOMEProgressionGameplayFixture.EnemySpawns.class);
        spawns.faction=faction;((KOMECourierGeographyTest.TestManager)f.world.getWorldChunkManager()).biome.npcSpawnList=spawns;
    }
    static KOMESerfdomOfferQuest shell(KOMEAccessFixture f,LOTREntityNPC npc)throws Exception {
        KOMEProgressionOfferBridge.registerQuestType();
        KOMESerfdomOfferQuest offer=new KOMESerfdomOfferQuest(LOTRLevelData.getData(f.player));offer.setNPCInfo(npc);
        offer.speechBankStart=offer.speechBankProgress=offer.speechBankComplete=offer.speechBankTooMany="fixture";
        offer.quoteStart="Serve";offer.quoteComplete="Welcome";
        NBTTagCompound tag=new NBTTagCompound();offer.writeToNBT(tag);
        tag.setLong("KOMEOpportunityWindow",KOMEProgressionOfferBridge.opportunityWindow(KOMESerfKnightService.calendarDayNow()));
        tag.setLong("KOMEPledgeRevision",f.data.getProgression(f.player.id).observeOfferPledge(npc.getFaction().codeName()));
        offer.readFromNBT(tag);npc.questInfo.setPlayerSpecificOffer(f.player,offer);return offer;
    }
    @Test public void supportedRolesOfferOpenAcceptAndIssueDutyWithOrWithoutNativeHomes()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
            KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture()) {
            for(String type:TYPES)for(int origin=0;origin<3;origin++)try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
                s.state.reset();s.p.setCanonicalRank(KOMEProgressionRank.WANDERER);
                LOTREntityNPC npc=nativeNpc(s.f,type);LOTRFaction faction=npc.getFaction();
                territory(s.f,faction);s.f.pledge(faction);alignment(s.f,faction,10000);
                // Natural civilian, structure resident, and later artificial spawn use the same admission rule.
                if(origin==1)npc.setHomeArea((int)npc.posX,64,(int)npc.posZ,16);
                if(origin==2)npc.onArtificalSpawn();
                assertTrue(type,KOMESerfdomMasterService.ordinaryMaster(npc));
                assertTrue(KOMECanonicalRankService.recognizeCommittedPledge(s.f.data,s.f.player.id,faction.codeName()));
                KOMESerfdomOfferQuest offer=shell(s.f,npc);
                assertTrue(type,KOMEProgressionOfferBridge.canOffer(npc.questInfo,s.f.player));
                assertTrue(type,KOMEProgressionEligibility.evaluate(s.f.player,npc).enabled);
                KOMEProgressionOfferBridge.OfferSender previous=KOMEProgressionOfferBridge.offerSender;
                final int[] opened={0};
                try {
                    KOMEProgressionOfferBridge.offerSender=(p,n,tag)->{assertSame(npc,n);assertEquals(offer.questUUID.toString(),tag.getString("QuestUUID"));opened[0]++;};
                    new KOMEPacketRelationshipAction.Handler().onMessage(new KOMEPacketRelationshipAction(npc.getEntityId(),0,1),s.f.context);
                    assertEquals(type,1,opened[0]);assertFalse(s.state.getSerfdomMaster().isSet());
                    assertTrue(KOMEProgressionOfferBridge.handleResponse(npc.questInfo,s.f.player,true));
                }finally{KOMEProgressionOfferBridge.offerSender=previous;}
                assertEquals(type,npc.getUniqueID().toString(),s.state.getSerfdomMaster().entityUuid);
                assertFalse(KOMEProgressionOfferBridge.canOffer(npc.questInfo,s.f.player));
                assertTrue(KOMESerfdomMasterService.validateCurrentMasterInteraction(s.f.player,s.f.data,npc,true).success);
                // Exercise the real ordinary objective assignment without depending on native trade bootstrap.
                s.state.assignDuty(KOMESerfKnightDutyType.PROVISIONING,null);s.state.completeDuty(KOMESerfKnightDutyType.PROVISIONING);
                s.state.assignDuty(KOMESerfKnightDutyType.COURIER,null);s.state.completeDuty(KOMESerfKnightDutyType.COURIER);
                assertTrue(type,KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,npc).success);
                assertTrue(type,s.state.hasActiveAssignment());
            }
        }
    }
    @Test public void indicatorAndOpenRejectTradingDenialStalePledgeWrongRankAndRange()throws Exception {
        try(KOMEProgressionFollowupTest.NativeItems items=new KOMEProgressionFollowupTest.NativeItems();
            KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture();
            KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.state.reset();LOTREntityNPC npc=nativeNpc(s.f,"MordorOrcTrader");territory(s.f,npc.getFaction());s.f.pledge(npc.getFaction());
            alignment(s.f,npc.getFaction(),10000);shell(s.f,npc);assertTrue(KOMEProgressionOfferBridge.canOffer(npc.questInfo,s.f.player));
            alignment(s.f,npc.getFaction(),0);assertFalse(KOMEProgressionOfferBridge.canOffer(npc.questInfo,s.f.player));
            assertFalse(KOMEProgressionEligibility.evaluate(s.f.player,npc).enabled);
            alignment(s.f,npc.getFaction(),10000);s.f.player.posX+=20;
            assertFalse(KOMEProgressionOfferBridge.handleInteraction(npc.questInfo,s.f.player));s.f.player.posX-=20;
            s.p.setCanonicalRank(KOMEProgressionRank.KNIGHT);assertFalse(KOMEProgressionOfferBridge.canOffer(npc.questInfo,s.f.player));
            s.p.setCanonicalRank(KOMEProgressionRank.SERF);s.f.pledge(LOTRFaction.ROHAN);assertFalse(KOMEProgressionOfferBridge.canOffer(npc.questInfo,s.f.player));
            assertFalse(KOMESerfdomMasterService.ordinaryMaster(nativeNpc(s.f,"UrukHai")));
        }
    }
    static class QuietQuestInfo extends LOTREntityQuestInfo {
        QuietQuestInfo(LOTREntityNPC npc){super(npc);}
        @Override public void sendData(net.minecraft.entity.player.EntityPlayerMP player){}
    }
}
