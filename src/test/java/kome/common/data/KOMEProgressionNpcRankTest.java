package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionNpcRankTest {
    @Test public void rankAndRelationshipPolicyAreExactAndSeparate() {
        assertTrue(KOMEProgressionNpcRank.UNRANKED.order < KOMEProgressionNpcRank.LORD.order);
        assertTrue(KOMEProgressionNpcRank.LORD.order < KOMEProgressionNpcRank.PRINCE.order);
        assertTrue(KOMEProgressionNpcRank.PRINCE.order < KOMEProgressionNpcRank.KING.order);
        assertEquals(KOMEProgressionNpcRankScarcity.UNRESTRICTED, KOMEProgressionNpcRank.UNRANKED.scarcity);
        assertEquals(KOMEProgressionNpcRankScarcity.COMMON, KOMEProgressionNpcRank.LORD.scarcity);
        assertEquals(KOMEProgressionNpcRankScarcity.RARE, KOMEProgressionNpcRank.PRINCE.scarcity);
        assertEquals(KOMEProgressionNpcRankScarcity.UNIQUE, KOMEProgressionNpcRank.KING.scarcity);
        assertEquals(KOMEProgressionNpcRank.LORD, KOMEProgressionNpcRank.forKey("lord"));
        assertNull(KOMEProgressionNpcRank.forKey("LORD")); assertNull(KOMEProgressionNpcRank.forKey("knight"));
        assertEquals(KOMEProgressionNpcRank.UNRANKED, KOMEProgressionLiegePolicy.requiredNpcRankForPlayerRank(KOMEProgressionRank.SERF));
        assertEquals(KOMEProgressionNpcRank.LORD, KOMEProgressionLiegePolicy.requiredNpcRankForPlayerRank(KOMEProgressionRank.KNIGHT));
        assertEquals(KOMEProgressionNpcRank.PRINCE, KOMEProgressionLiegePolicy.requiredNpcRankForPlayerRank(KOMEProgressionRank.LORD));
        assertEquals(KOMEProgressionNpcRank.KING, KOMEProgressionLiegePolicy.requiredNpcRankForPlayerRank(KOMEProgressionRank.PRINCE));
        assertNull(KOMEProgressionLiegePolicy.requiredNpcRankForPlayerRank(KOMEProgressionRank.WANDERER));
    }

    @Test public void effectiveRanksAndKingUniquenessUseExplicitAuthorityOnly() {
        KOMEWorldData data = new KOMEWorldData("npcs"); UUID ordinary=UUID.randomUUID(), hiring=UUID.randomUUID(), prince=UUID.randomUUID(), king=UUID.randomUUID();
        assertEquals(KOMEProgressionNpcRank.UNRANKED, KOMEProgressionNpcRankService.effectiveRank(data, ordinary, false));
        assertEquals(KOMEProgressionNpcRank.LORD, KOMEProgressionNpcRankService.effectiveRank(data, hiring, true));
        assertEquals(KOMEProgressionNpcRank.UNRANKED, KOMEProgressionNpcRankService.effectiveRank(data, hiring, false));
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data, prince, "ROHAN", KOMEProgressionNpcRank.PRINCE, "Prince", true).success);
        assertEquals(KOMEProgressionNpcRank.PRINCE, KOMEProgressionNpcRankService.effectiveRank(data, prince, true));
        assertFalse(KOMEProgressionNpcRankService.assignElevatedRank(data, UUID.randomUUID(), "rohan", KOMEProgressionNpcRank.PRINCE, "Farmer", false).success);
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data, king, "rohan", KOMEProgressionNpcRank.KING, "King", true).success);
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data, king, "rohan", KOMEProgressionNpcRank.KING, "King", true).success);
        assertFalse(KOMEProgressionNpcRankService.assignElevatedRank(data, UUID.randomUUID(), "rohan", KOMEProgressionNpcRank.KING, "Other", true).success);
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data, UUID.randomUUID(), "gondor", KOMEProgressionNpcRank.KING, "Other", true).success);
    }

    @Test public void recordsReconcileDuplicatesAndPlayerKingDoesNotDeleteNpcKing() {
        KOMEWorldData data = new KOMEWorldData("npcs"); assertTrue(data.initializeIntegratedWorld()); UUID king=UUID.randomUUID();
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data, king, "rohan", KOMEProgressionNpcRank.KING, "NPC King", true).success);
        assertTrue(KOMEProgressionNpcRankService.isActivePoliticalNpcKing(data,king));
        assertTrue(KOMERulerService.assignRuler(data,"rohan",UUID.randomUUID(),"Player King"));
        assertTrue(data.progressionNpcRanks.containsKey(king)); assertFalse(KOMEProgressionNpcRankService.isActivePoliticalNpcKing(data,king));
        NBTTagCompound tag=new NBTTagCompound(); data.writeToNBT(tag); NBTTagList records=tag.getTagList("ProgressionNpcRanks",10);
        NBTTagCompound duplicate=((KOMEProgressionNpcRankRecord) data.progressionNpcRanks.get(king)).writeToNBT(); duplicate.setString("UUID",UUID.randomUUID().toString()); records.appendTag(duplicate);
        NBTTagCompound malformed=new NBTTagCompound(); malformed.setString("UUID","bad"); malformed.setString("Faction","rohan"); malformed.setString("Rank","king"); records.appendTag(malformed);
        KOMEWorldData loaded=new KOMEWorldData("npcs"); loaded.readFromNBT(tag);
        assertTrue(loaded.progressionNpcRanks.isEmpty());
        NBTTagCompound reversed=(NBTTagCompound)tag.copy(); NBTTagList reversedRecords=new NBTTagList();
        reversedRecords.appendTag(duplicate); reversedRecords.appendTag(((KOMEProgressionNpcRankRecord) data.progressionNpcRanks.get(king)).writeToNBT()); reversedRecords.appendTag(malformed); reversed.setTag("ProgressionNpcRanks",reversedRecords);
        KOMEWorldData reverseLoaded=new KOMEWorldData("npcs"); reverseLoaded.readFromNBT(reversed);
        assertTrue(reverseLoaded.progressionNpcRanks.isEmpty());
    }

    @Test public void rankAwareSerfKnightSelectionRequiresExactRanks() {
        KOMESerfKnightProgression state=new KOMESerfKnightProgression(); KOMEProgressionNpcRef master=ref("master"), liege=ref("liege");
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,master,KOMEProgressionNpcRank.UNRANKED,true).success);
        long day=10L;
        for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values()) { assertTrue(KOMESerfKnightService.assignDuty(state,type,null,day++).success); assertTrue(KOMESerfKnightService.completeDuty(state,type).success); }
        assertFalse(KOMESerfKnightService.setProspectiveLiege(state,liege,KOMEProgressionNpcRank.UNRANKED,true).success);
        assertFalse(KOMESerfKnightService.setProspectiveLiege(state,liege,KOMEProgressionNpcRank.PRINCE,true).success);
        assertFalse(KOMESerfKnightService.setProspectiveLiege(state,liege,KOMEProgressionNpcRank.KING,true).success);
        assertTrue(KOMESerfKnightService.setProspectiveLiege(state,liege,KOMEProgressionNpcRank.LORD,true).success);
        assertFalse(KOMESerfKnightService.setSerfdomMaster(new KOMESerfKnightProgression(),ref("ranked"),KOMEProgressionNpcRank.LORD,true).success);
    }

    @Test public void protectionAuthorityIsDerivedFromRelationshipsAndElevatedRecords() {
        KOMEWorldData data=new KOMEWorldData("npcs"); UUID player=UUID.randomUUID(), master=UUID.randomUUID(), prince=UUID.randomUUID();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(data.getProgression(player).getSerfKnightProgression(),new KOMEProgressionNpcRef(master.toString(),"Master","rohan",0,0,0,0)).success);
        KOMEProgressionNpcRoles.syncPlayer(data,player);
        assertTrue(KOMEProgressionNpcRankService.isProgressionReferenced(data,master));
        assertTrue(KOMEProgressionNpcRankService.shouldPreventNaturalDespawn(data,master));
        assertFalse(KOMEProgressionNpcRankService.shouldPreventNaturalDespawn(data,UUID.randomUUID()));
        assertTrue(KOMEProgressionNpcRankService.assignElevatedRank(data,prince,"rohan",KOMEProgressionNpcRank.PRINCE,"Prince",true).success);
        assertTrue(KOMEProgressionNpcRankService.shouldPreventNaturalDespawn(data,prince));
    }
    private static KOMEProgressionNpcRef ref(String name) { return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),name,"rohan",0,0,0,0); }
}
