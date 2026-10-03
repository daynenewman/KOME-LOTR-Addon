package kome.common.data;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionPermissionRegistryTest {
    @Test public void canonicalRankRatherThanPreviousAchievementGroupControlsTierAccess() {
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        KOMEProgressionAchievement serf = KOMEProgressionAchievement.forID("serf.quest_seeker");
        assertFalse(KOMEProgressionPermissionRegistry.canComplete(progression, serf));
        progression.setCanonicalRank(KOMEProgressionRank.SERF);
        assertTrue(KOMEProgressionPermissionRegistry.canComplete(progression, serf));
        assertFalse("Optional Wanderer advancements are not rank authority",
            progression.isCompleted(KOMEProgressionAchievement.forID("wanderer.expert_traveler")));
        KOMEProgressionAchievement knight = KOMEProgressionAchievement.forID("knight.craftsman");
        assertFalse(KOMEProgressionPermissionRegistry.canComplete(progression, knight));
        progression.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        assertTrue(KOMEProgressionPermissionRegistry.canComplete(progression, knight));
    }

    @Test public void lordMarkerCannotBeSelfCertifiedEvenWithHistoricalChecklist() {
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        progression.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        KOMEProgressionAchievement marker = KOMEProgressionAchievement.forID("knight.title_lord");
        assertFalse(KOMEProgressionPermissionRegistry.canComplete(progression, marker));
        KOMEHigherRankTransitionService.Transition transition =
            KOMEHigherRankTransitionService.forCurrentRank(KOMEProgressionRank.KNIGHT);
        for (KOMEHigherRankTransitionService.RequirementGroup group : transition.groups)
            for (String id : group.achievementIds) progression.grant(id);
        assertFalse(KOMEProgressionPermissionRegistry.canComplete(progression, marker));
        assertFalse(progression.isCompleted(KOMEProgressionAchievement.forID("knight.craftsman")));
    }
    @Test public void requirementTextAndPredicateDescribeTheSameCanonicalRankGate() {
        KOMEProgressionAchievement lord = KOMEProgressionAchievement.forID("lord.redstone");
        assertTrue(KOMEProgressionPermissionRegistry.requirementText(lord).contains("canonical faction rank"));
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        progression.setCanonicalRank(KOMEProgressionRank.KNIGHT);
        assertFalse(KOMEProgressionPermissionRegistry.canComplete(progression, lord));
        progression.setCanonicalRank(KOMEProgressionRank.LORD);
        assertTrue(KOMEProgressionPermissionRegistry.canComplete(progression, lord));
    }
    @Test public void endgameAndRulerOfficeRemainIndependent() {
        KOMEWorldData data = new KOMEWorldData("progression"); UUID player = UUID.randomUUID();
        assertTrue(KOMERulerService.assignRuler(data,"gondor",player,"Ruler"));
        assertFalse(data.getProgression(player).isCompleted(KOMEProgressionAchievement.forID("prince_king.true_silver")));
        KOMEPlayerProgression p = data.getProgression(player);
        p.setCanonicalRank(KOMEProgressionRank.PRINCE);
        KOMEProgressionAchievement endgame=KOMEProgressionAchievement.forID("prince_king.true_silver");
        assertTrue(KOMEProgressionPermissionRegistry.canComplete(p,endgame)); assertTrue(p.grant(endgame.id));
        assertTrue(KOMERulerAuthorization.canActAsRuler(data,"gondor",player));
    }
    @Test public void mappingCoversEveryPermissionConstantAndStatePersists() {
        Map<String,KOMEProgressionPermissionRegistry.Gate> gates=KOMEProgressionPermissionRegistry.gates();
        for(String id:new String[]{KOMEProgressionPermissions.ALCOHOL_PIPEWEED,KOMEProgressionPermissions.COOKING,KOMEProgressionPermissions.FARMING,KOMEProgressionPermissions.FIRE,KOMEProgressionPermissions.MEAT,KOMEProgressionPermissions.MINIQUESTS,KOMEProgressionPermissions.MOUNTS,KOMEProgressionPermissions.NPC_TRADE,KOMEProgressionPermissions.PLEDGE,KOMEProgressionPermissions.POUCHES,KOMEProgressionPermissions.STONEWORK,KOMEProgressionPermissions.FAST_TRAVEL,KOMEProgressionPermissions.TAKE_WAYPOINTS,KOMEProgressionPermissions.RECLAIM_WAYPOINTS,KOMEProgressionPermissions.HIRE_UNITS,KOMEProgressionPermissions.GROW_POPULATION}) assertNotNull(gates.get(id));
        KOMEPlayerProgression p=new KOMEPlayerProgression(); p.grant("wanderer.expert_traveler"); NBTTagCompound tag=p.writeToNBT(); KOMEPlayerProgression restored=new KOMEPlayerProgression(); restored.readFromNBT(tag);
        assertTrue(restored.isCompleted(KOMEProgressionAchievement.forID("wanderer.expert_traveler")));
    }
    @Test public void legacyMiniquestMilestoneLoadsButIsNotAnEnforcedGate() {
        KOMEPlayerProgression player=new KOMEPlayerProgression();player.grant("baseline.miniquests");
        KOMEPlayerProgression loaded=new KOMEPlayerProgression();loaded.readFromNBT(player.writeToNBT());
        assertTrue(loaded.isCompleted(KOMEProgressionAchievement.forID("baseline.miniquests")));
        assertEquals(KOMEProgressionPermissionRegistry.Status.INFORMATIONAL,
            KOMEProgressionPermissionRegistry.gate("baseline.miniquests").status);
    }
}
