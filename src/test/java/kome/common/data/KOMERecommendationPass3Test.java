package kome.common.data;

import java.util.*;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

/** Real report/admission/item paths with authoritative saved provenance. */
public class KOMERecommendationPass3Test {
    private static int letters(KOMEProgressionFollowupTest.Session s){int count=0;for(ItemStack b:s.f.player.inventory.mainInventory)if(b!=null&&b.hasTagCompound()&&b.getTagCompound().hasKey("KOMERecommendation"))count++;return count;}
    private static KOMEServiceRecognition.Record earn(KOMEProgressionFollowupTest.Session s){s.p.setCanonicalRank(KOMEProgressionRank.KNIGHT);s.state.retireSerfdomMasterAfterPromotion();return s.p.getServiceRecognition().earn(s.f.player.id,KOMEProgressionNpcRankService.referenceOf(s.master),KOMEProgressionRank.SERF,KOMEProgressionRank.KNIGHT,"Provisions, escort and recovery",42);}
    @Test public void fullInventoryRetainsAuthorityAndReissueUsesSameIdentity()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            KOMEServiceRecognition.Record r=earn(s);Arrays.fill(s.f.player.inventory.mainInventory,new ItemStack(Items.bread));
            assertFalse(KOMERecommendationService.deliver(s.f.player));assertEquals(1,s.p.getServiceRecognition().records().size());assertFalse(r.redeemed);
            s.f.player.inventory.mainInventory[4]=null;assertTrue(KOMERecommendationService.deliver(s.f.player));assertTrue(KOMERecommendationService.matches(s.f.player.inventory.mainInventory[4],r));
            assertTrue(KOMERecommendationService.deliver(s.f.player));assertEquals(1,letters(s));
        }
    }
    @Test public void lostLetterRecoversAfterRestartAndUnavailableIssuer()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            KOMEServiceRecognition.Record r=earn(s);assertTrue(KOMERecommendationService.deliver(s.f.player));Arrays.fill(s.f.player.inventory.mainInventory,null);
            s.f.world.loadedEntityList.remove(s.master);s.p.readFromNBT(s.p.writeToNBT());
            assertTrue(KOMERecommendationService.deliver(s.f.player));assertTrue(KOMERecommendationService.matches(s.f.player.inventory.mainInventory[0],s.p.getServiceRecognition().records().get(0)));
            assertEquals(r.id,s.p.getServiceRecognition().records().get(0).id);assertEquals(1,s.p.getServiceRecognition().records().size());
        }
    }
    @Test public void forgedAndRenamedBooksNeverCreateSavedProvenance()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            KOMEServiceRecognition.Record r=earn(s);ItemStack book=KOMERecommendationService.letter(r,"Eadric");assertTrue(KOMERecommendationService.matches(book,r));
            book.getTagCompound().setString("KOMERecommendation",UUID.randomUUID().toString());assertFalse(KOMERecommendationService.matches(book,r));s.f.player.inventory.mainInventory[0]=book;assertFalse(KOMERecommendationService.hasIntroduction(s.f.player));
            book=KOMERecommendationService.letter(r,"Eadric");book.getTagCompound().setString("Recipient",UUID.randomUUID().toString());assertFalse(KOMERecommendationService.matches(book,r));
            book=KOMERecommendationService.letter(r,"Eadric");book.getTagCompound().setString("Standing","lord");assertFalse(KOMERecommendationService.matches(book,r));
            s.p.getServiceRecognition().read(null);s.f.player.inventory.mainInventory[0]=KOMERecommendationService.letter(r,"Eadric");assertFalse(KOMERecommendationService.hasIntroduction(s.f.player));
        }
    }
    @Test public void duplicatedKeepsakesCannotOpenSecondRelationshipAfterRedemption()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            LOTRCaptainPair pair=new LOTRCaptainPair(s);ItemStack copy=s.f.player.inventory.mainInventory[0].copy();
            assertTrue(KOMESerfKnightRelationshipService.establishLiege(s.f.player,s.f.data,pair.first).success);
            s.f.player.inventory.mainInventory[1]=copy;assertFalse(KOMERecommendationService.hasIntroduction(s.f.player));
            assertFalse(KOMESerfKnightRelationshipService.establishLiege(s.f.player,s.f.data,pair.second).success);
            s.state.releaseLiegeAfterPromotion();s.p.readFromNBT(s.p.writeToNBT());assertFalse(KOMERecommendationService.hasIntroduction(s.f.player));assertFalse(KOMERecommendationService.deliver(s.f.player));
        }
    }
    private static final class LOTRCaptainPair {
        final lotr.common.entity.npc.LOTREntityNPC first,second;
        LOTRCaptainPair(KOMEProgressionFollowupTest.Session s)throws Exception{first=KOMEProgressionServiceLocationTest.sponsored(s);second=KOMESerfKnightStabilizationTest.captain(s);KOMEProgressionServiceLocationTest.generatedHome(first,"RohanFortress");KOMEProgressionServiceLocationTest.generatedHome(second,"RohanFortress");}
    }
    @Test public void formerMentorRecognitionLeavesNativeInteractionAndNoActiveLease()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            earn(s);KOMEProgressionNpcRoles.syncPlayer(s.f.data,s.f.player.id);
            assertTrue(KOMERecommendationService.recognizeFormer(s.f.player,s.master));assertFalse(KOMEProgressionNpcRoles.protects(s.f.data,s.master.getUniqueID()));
            assertFalse(KOMEProgressionNpcInteractionService.interact(s.f.player,s.f.data,s.master));assertTrue(s.master.isEntityAlive());assertEquals(1,letters(s));
        }
    }
    @Test public void noLetterCannotBypassRegionalSeatAndIssuerDeathDoesNotEraseRecord()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            LOTRCaptainPair pair=new LOTRCaptainPair(s);Arrays.fill(s.f.player.inventory.mainInventory,null);
            assertFalse(KOMESerfKnightRelationshipService.establishLiege(s.f.player,s.f.data,pair.first).success);
            KOMESerfKnightService.handleNpcDeath(s.state,s.master.getUniqueID().toString(),false,0);s.p.readFromNBT(s.p.writeToNBT());
            assertTrue(KOMERecommendationService.deliver(s.f.player));assertTrue(KOMESerfKnightRelationshipService.establishLiege(s.f.player,s.f.data,pair.first).success);
        }
    }
    @Test public void thirdOrdinaryDutyReportPromotesImmediatelyWithoutTrialOrFourthAssignment()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            for(KOMESerfKnightDutyType t:new KOMESerfKnightDutyType[]{KOMESerfKnightDutyType.PROVISIONING,KOMESerfKnightDutyType.COURIER}){s.state.assignDuty(t,null);s.state.completeDuty(t);}
            KOMESerfKnightTrialAssignment a=KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId("escort"),KOMEProgressionNpcRankService.referenceOf(s.master),0,0);a.data.setBoolean("OrdinaryDuty",true);
            s.state.assignDuty(KOMESerfKnightDutyType.ESCORT,a.writeToNBT());s.state.setTrial(a.withStage(KOMESerfKnightTrialAssignment.Stage.OBJECTIVE_COMPLETE,a.data));
            java.lang.reflect.Field field=lotr.common.LOTRPlayerData.class.getDeclaredField("alignments");field.setAccessible(true);((Map)field.get(LOTRLevelData.getData(s.f.player))).put(LOTRFaction.ROHAN,150F);
            assertTrue(KOMESerfServiceObjectives.interact(s.f.player,s.master));assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());assertEquals(3,KOMEServicePromotionService.duties(s.state));
            assertFalse(s.state.hasMasterRelationship());assertFalse(s.state.hasLiege());assertNull(s.state.getTrialAssignment());assertEquals(1,letters(s));
            assertFalse(KOMESerfdomMasterService.requestDuty(s.f.player,s.f.data,s.master).success);
            assertTrue(KOMEProgressionAbilityPolicy.unlocked(s.f.data,s.f.player.id,KOMEProgressionPermissionRegistry.Ability.BATTLE_SERVICE));
        }
    }
    @Test public void nativeInvasionCanBeThirdCommissionAndCurrentLordConfersImmediately()throws Exception {
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture()){
            f.credits(2);KOMELordshipTrial t=f.trial(KOMELordshipTrial.Scenario.BORDER_PATROL,Stage.READY_TO_REPORT);
            t.ordinaryService=true;t.nativeInvasion=true;t.nativeVictory=true;t.objective.participated=true;t.invasionType=lotr.common.world.spawning.LOTRInvasions.DUNLAND.codeName();
            assertTrue(KOMELordshipTrialService.acceptOrReport(f.s.f.player,f.lord));assertEquals(KOMEProgressionRank.LORD,f.s.p.getCanonicalRank());assertEquals(3,f.s.p.getKnightService().qualifyingTypes("rohan").size());
            assertFalse(f.s.p.getSerfKnightProgression().hasLiege());assertEquals(f.lord.getUniqueID().toString(),f.s.p.getServiceRecognition().records().get(0).issuer.entityUuid);
            assertTrue(KOMEProgressionAbilityPolicy.unlocked(f.s.f.data,f.s.f.player.id,KOMEProgressionPermissionRegistry.Ability.CAMPAIGN_RECRUITMENT));assertTrue(f.s.f.data.hiredUnits.isEmpty());
        }
    }
    @Test public void ceremonialFailureDoesNotUndoRankHistoryLetterOrSynchronization()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            s.ready();KOMEProgressionAnnouncements.Sender before=KOMEProgressionAnnouncements.sender;KOMEProgressionAnnouncements.sender=text->{throw new IllegalStateException("cosmetic fixture");};
            try{assertTrue(KOMEServicePromotionService.report(s.f.player,s.master));assertEquals(KOMEProgressionRank.KNIGHT,s.p.getCanonicalRank());assertEquals(1,s.p.getServiceRecognition().records().size());assertEquals(1,letters(s));assertFalse(KOMEServicePromotionService.report(s.f.player,s.master));}finally{KOMEProgressionAnnouncements.sender=before;}
        }
    }
    @Test public void royalRecommendationNamesCapitalAndRecognizesAlreadyEarnedStanding()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            KOMEServiceRecognition.Record r=s.p.getServiceRecognition().earn(s.f.player.id,KOMEProgressionNpcRankService.referenceOf(s.master),KOMEProgressionRank.KNIGHT,KOMEProgressionRank.LORD,"Defense, relief and invasion",0);
            ItemStack letter=KOMERecommendationService.letter(r,"Eadric");String pages=letter.getTagCompound().getTagList("pages",8).toString();
            assertTrue(pages.contains("capital"));assertTrue(pages.contains("King"));assertTrue(pages.contains("Eadric"));assertTrue(pages.contains("standing"));
            assertFalse(pages.contains("a High Marshal"));
        }
    }
    @Test public void letterPagesFitNativeBookBoundsWithoutLosingServiceText()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()){
            KOMEServiceRecognition.Record r=earn(s);net.minecraft.nbt.NBTTagList pages=KOMERecommendationService.letter(r,"Eadric").getTagCompound().getTagList("pages",8);StringBuilder all=new StringBuilder();
            for(int i=0;i<pages.tagCount();i++){
                String page=pages.getStringTagAt(i);assertTrue(page.length()<=256);String[] lines=page.split("\n",-1);assertTrue(lines.length<=13);
                for(String line:lines){int width=0;for(char c:line.toCharArray())width+=c==' '?4:8;assertTrue(width<=116);}all.append(page).append(' ');
            }
            String text=all.toString().replace('\n',' ');assertTrue(text.contains("Eadric"));assertTrue(text.contains("Provisions, escort and recovery"));assertTrue(text.contains("keepsake"));
        }
    }
}
