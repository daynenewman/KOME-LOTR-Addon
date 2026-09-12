package kome.common.data;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionPermissionRegistryTest {
    @Test public void rankPrerequisitesBlockCrossGroupButAllowCompleteChains() {
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        KOMEProgressionAchievement serf = KOMEProgressionAchievement.forID("serf.quest_seeker");
        assertFalse(KOMEProgressionPermissionRegistry.canComplete(progression, serf));
        for (KOMEProgressionAchievement a : KOMEProgressionAchievement.forGroup("wanderer")) progression.grant(a.id);
        assertTrue(KOMEProgressionPermissionRegistry.canComplete(progression, serf));
    }
    @Test public void requirementTextAndPredicateDescribeTheSameRankGate() {
        KOMEProgressionAchievement lord = KOMEProgressionAchievement.forID("lord.redstone");
        assertTrue(KOMEProgressionPermissionRegistry.requirementText(lord).contains("Knight progression"));
        assertFalse(KOMEProgressionPermissionRegistry.canComplete(new KOMEPlayerProgression(), lord));
    }
    @Test public void endgameAndRulerOfficeRemainIndependent() {
        KOMEWorldData data = new KOMEWorldData("progression"); UUID player = UUID.randomUUID();
        assertTrue(KOMERulerService.assignRuler(data,"gondor",player,"Ruler"));
        assertFalse(data.getProgression(player).isCompleted(KOMEProgressionAchievement.forID("prince_king.true_silver")));
        KOMEPlayerProgression p = data.getProgression(player);
        for (KOMEProgressionAchievement a : KOMEProgressionAchievement.forGroup("baseline")) p.grant(a.id);
        for (KOMEProgressionAchievement a : KOMEProgressionAchievement.forGroup("wanderer")) p.grant(a.id);
        for (KOMEProgressionAchievement a : KOMEProgressionAchievement.forGroup("serf")) p.grant(a.id);
        for (KOMEProgressionAchievement a : KOMEProgressionAchievement.forGroup("knight")) p.grant(a.id);
        for (KOMEProgressionAchievement a : KOMEProgressionAchievement.forGroup("lord")) p.grant(a.id);
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
}
