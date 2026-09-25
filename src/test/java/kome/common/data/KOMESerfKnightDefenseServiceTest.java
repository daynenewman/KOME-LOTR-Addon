package kome.common.data;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import static org.junit.Assert.*;

/** Persistence and native-seam contract tests; live LOTR entities retain their own combat AI. */
public class KOMESerfKnightDefenseServiceTest {
    private static KOMEProgressionNpcRef ref(String name){return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),name,"rohan",0,64,0,0);}
    private static KOMESerfKnightTrialAssignment assigned(){return KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId("defense"),ref("Liege"),30L,0);}
    private static void append(NBTTagList list,String id){NBTTagCompound entry=new NBTTagCompound();entry.setString("Id",id);list.appendTag(entry);}

    @Test public void defensePersistsOneObjectiveAndAssignedAttackersAcrossReload(){
        KOMESerfKnightTrialAssignment seed=assigned(); NBTTagCompound data=new NBTTagCompound();data.setBoolean(KOMESerfKnightDefenseService.ACTIVATED,true);data.setTag(KOMESerfKnightDefenseService.OBJECTIVE,ref("Villager").writeToNBT());data.setString(KOMESerfKnightDefenseService.ENEMY_FACTION,"mordor");NBTTagList enemies=new NBTTagList();String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();append(enemies,first);append(enemies,second);data.setTag(KOMESerfKnightDefenseService.ENEMIES,enemies);data.setTag(KOMESerfKnightDefenseService.DEAD,new NBTTagList());
        KOMESerfKnightTrialAssignment loaded=KOMESerfKnightTrialAssignment.readFromNBT(seed.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data).writeToNBT());
        assertTrue(loaded.data.getBoolean(KOMESerfKnightDefenseService.ACTIVATED));assertEquals(first,loaded.data.getTagList(KOMESerfKnightDefenseService.ENEMIES,10).getCompoundTagAt(0).getString("Id"));assertFalse(KOMESerfKnightDefenseService.allDead(loaded));
    }

    @Test public void onlyEveryAssignedAttackerDeathCanCompleteTheEncounter(){
        KOMESerfKnightTrialAssignment seed=assigned();NBTTagCompound data=new NBTTagCompound();NBTTagList enemies=new NBTTagList(),dead=new NBTTagList();String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();append(enemies,first);append(enemies,second);append(dead,UUID.randomUUID().toString());data.setTag(KOMESerfKnightDefenseService.ENEMIES,enemies);data.setTag(KOMESerfKnightDefenseService.DEAD,dead);KOMESerfKnightTrialAssignment active=seed.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data);assertFalse(KOMESerfKnightDefenseService.allDead(active));append(dead,first);data.setTag(KOMESerfKnightDefenseService.DEAD,dead);assertFalse(KOMESerfKnightDefenseService.allDead(seed.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data)));append(dead,second);data.setTag(KOMESerfKnightDefenseService.DEAD,dead);assertTrue(KOMESerfKnightDefenseService.allDead(seed.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data)));
    }

    @Test public void nativeHostilityAndCombatSeamsAreUsedWithoutCustomAi() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightDefenseService.java")),StandardCharsets.UTF_8);assertTrue(source.contains("defender.isBadRelation(attacker)||attacker.isBadRelation(defender)"));assertTrue(source.contains("invasion.invasionMobs"));assertTrue(source.contains("getEntityClass()"));assertTrue(source.contains("onArtificalSpawn()"));assertTrue(source.contains("setAttackTarget(objective,true)"));assertTrue(source.contains("markTrialObjectiveComplete(state)"));assertFalse(source.contains("state.setTrialCompleted("));
    }

    @Test public void activationAndReconciliationNeverRespawnOrDuplicateAttackers() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightDefenseService.java")),StandardCharsets.UTF_8);assertTrue(source.contains("assignment.stage!=KOMESerfKnightTrialAssignment.Stage.ASSIGNED"));assertTrue(source.contains("if(data.getBoolean(ACTIVATED))return false"));assertTrue(source.contains("if(entity==null)return; // unloaded is deliberately inconclusive"));assertTrue(source.contains("if(uuid.equals(objective(assignment).entityUuid)){fail(state,world);continue;}"));
    }

    @Test public void cleanupAndSelectionStayConservativeAcrossTrialTypes() throws Exception {
        String escort=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightEscortService.java")),StandardCharsets.UTF_8);String recovery=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightRecoveryService.java")),StandardCharsets.UTF_8);String defense=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightDefenseService.java")),StandardCharsets.UTF_8);String leave=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketRelationshipAction.java")),StandardCharsets.UTF_8);
        assertTrue(escort.contains("player.getDistanceSqToEntity(npc)>2304D"));assertTrue(escort.contains("dismissUnit(false)"));assertTrue(recovery.contains("for(int i=0;i<24;i++)"));assertTrue(recovery.contains("((EntityItem)value).setDead()"));assertTrue(defense.contains("KOMEProgressionNpcRankService.isValidFactionNpc(npc)"));assertTrue(defense.contains("setAttackTarget(null,false)"));assertTrue(leave.contains("KOMESerfKnightEscortService.cleanup"));assertTrue(leave.contains("KOMESerfKnightRecoveryService.cleanup"));assertTrue(leave.contains("KOMESerfKnightDefenseService.cleanup"));
    }
}
