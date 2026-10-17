package kome.common.data;

import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECommonerFoundationTest {
    @Test public void activeRanksAndLegacyStartingKeysHaveOneCommonerIdentity() {
        assertArrayEquals(new KOMEProgressionRank[]{KOMEProgressionRank.COMMONER,KOMEProgressionRank.KNIGHT,
            KOMEProgressionRank.LORD,KOMEProgressionRank.PRINCE},KOMEProgressionRank.values());
        for(String key:new String[]{"commoner","serf","wanderer"," SERF "})assertSame(KOMEProgressionRank.COMMONER,KOMEProgressionRank.forKey(key));
        assertNull(KOMEProgressionRank.forKey("king"));assertEquals(2,KOMEProgressionRank.KNIGHT.order);
        assertEquals(3,KOMEProgressionRank.LORD.order);assertEquals(4,KOMEProgressionRank.PRINCE.order);
        assertEquals(KOMEProgressionRank.COMMONER,new KOMEPlayerProgression().getCanonicalRank());
    }
    @Test public void legacyStartingRanksMigrateWithoutErasingRelationshipsDutiesOrHistory() {
        for(String oldKey:new String[]{"wanderer","serf"}) {
            KOMEPlayerProgression original=new KOMEPlayerProgression();
            KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Old Master","rohan",0,10,65,20);
            original.getSerfKnightProgression().setSerfdomMaster(master);
            original.getSerfKnightProgression().assignDuty(KOMESerfKnightDutyType.COURIER,null);
            original.getSerfKnightProgression().completeDuty(KOMESerfKnightDutyType.COURIER);
            original.grant("wanderer.expert_traveler");original.grant("serf.quest_seeker");
            NBTTagCompound saved=original.writeToNBT();saved.setString("CanonicalRank",oldKey);
            saved.removeTag("RankFoundationVersion");
            NBTTagCompound service=(NBTTagCompound)saved.getCompoundTag("SerfKnightProgression").copy();
            KOMEPlayerProgression loaded=new KOMEPlayerProgression();loaded.readFromNBT(saved);
            assertEquals(KOMEProgressionRank.COMMONER,loaded.getCanonicalRank());
            assertEquals(service,loaded.writeToNBT().getCompoundTag("SerfKnightProgression"));
            assertTrue(loaded.isCompleted(KOMEProgressionAchievement.forID("wanderer.expert_traveler")));
            assertTrue(loaded.isCompleted(KOMEProgressionAchievement.forID("serf.quest_seeker")));
            NBTTagCompound migrated=loaded.writeToNBT();assertEquals("commoner",migrated.getString("CanonicalRank"));
            assertEquals(oldKey,migrated.getString("LegacyStartingRank"));
            loaded.readFromNBT(migrated);assertEquals(migrated,loaded.writeToNBT());
        }
    }
    @Test public void earnedHigherRanksAndRecommendationRecordsSurviveMigration() {
        for(KOMEProgressionRank rank:new KOMEProgressionRank[]{KOMEProgressionRank.KNIGHT,KOMEProgressionRank.LORD,KOMEProgressionRank.PRINCE}) {
            KOMEPlayerProgression p=new KOMEPlayerProgression();p.setCanonicalRank(rank);
            KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Witness","rohan",0,1,65,2);
            p.getServiceRecognition().earn(UUID.randomUUID(),master,KOMEProgressionRank.COMMONER,KOMEProgressionRank.KNIGHT,"Reported service",100);
            assertEquals(1,p.getServiceRecognition().records().size());
            NBTTagCompound saved=p.writeToNBT();KOMEPlayerProgression loaded=new KOMEPlayerProgression();loaded.readFromNBT(saved);
            assertEquals(rank,loaded.getCanonicalRank());assertEquals(1,loaded.getServiceRecognition().records().size());
            assertEquals(saved.getCompoundTag("ServiceRecognition"),loaded.writeToNBT().getCompoundTag("ServiceRecognition"));
        }
    }
    @Test public void nativePledgeObservationNeverPromotesOrChangesAlignment()throws Exception {
        try(KOMEProgressionFollowupTest.Session s=new KOMEProgressionFollowupTest.Session()) {
            s.state.reset();s.f.pledge(null);
            assertFalse(KOMECanonicalRankService.recognizePledge(s.f.player,true));
            s.f.pledge(LOTRFaction.ROHAN);float before=LOTRLevelData.getData(s.f.player).getAlignment(LOTRFaction.ROHAN);
            assertTrue(KOMECanonicalRankService.recognizePledge(s.f.player,true));
            assertFalse(KOMECanonicalRankService.recognizePledge(s.f.player,true));
            assertEquals(KOMEProgressionRank.COMMONER,s.p.getCanonicalRank());assertFalse(s.state.hasMasterRelationship());
            assertEquals(before,LOTRLevelData.getData(s.f.player).getAlignment(LOTRFaction.ROHAN),0);
            s.p.setCanonicalRank(KOMEProgressionRank.LORD);s.f.pledge(LOTRFaction.GONDOR);
            KOMECanonicalRankService.recognizePledge(s.f.player,true);assertEquals(KOMEProgressionRank.LORD,s.p.getCanonicalRank());
        }
    }
    @Test public void rankBookSeparatesCommonerStatusFromAllegianceAndKingOffice() {
        KOMEPlayerProgression p=new KOMEPlayerProgression();
        KOMEProgressionRankSummary unpledged=KOMEProgressionRankSummary.project(p,0,"");
        assertEquals("Commoner",unpledged.currentRank);assertEquals("Unpledged",unpledged.activityTitle);
        assertFalse(unpledged.activityObjective.contains("earn"));
        KOMEProgressionRankSummary pledged=KOMEProgressionRankSummary.project(p,100,"rohan");
        assertEquals("Commoner",pledged.currentRank);assertEquals("Knight",pledged.nextRank);
        assertArrayEquals(new String[]{"Commoner","Knight","Lord","Prince"},KOMEFactionProgressionTitles.ladder("rohan"));
        assertFalse(pledged.activityObjective.contains("Serf"));
        assertEquals("A faction pledge establishes allegiance without changing your rank.",
            KOMEProgressionAchievement.forID("wanderer.find_serf_lord").requirement);
    }
}
