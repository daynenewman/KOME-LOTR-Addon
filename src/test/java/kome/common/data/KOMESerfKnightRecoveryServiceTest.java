package kome.common.data;

import java.util.UUID;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

/** Focused identity and persistence contract for the vanilla-item recovery objective. */
public class KOMESerfKnightRecoveryServiceTest {
    private static KOMEProgressionNpcRef liege() { return new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Liege","rohan",0,64,0,0); }
    private static KOMESerfKnightTrialAssignment assignment() { return KOMESerfKnightTrialAssignment.create(KOMESerfKnightTrial.forId("recovery"),liege(),20L,0); }

    @Test public void creationUsesOrdinaryItemWithPrivateAssignmentIdentity() {
        KOMESerfKnightTrialAssignment assignment=assignment(); UUID owner=UUID.randomUUID(); ItemStack stack=KOMESerfKnightRecoveryService.assignedStack(assignment,owner);
        assertSame(Items.gold_ingot,stack.getItem()); assertEquals(1,stack.stackSize);
        assertTrue(stack.hasTagCompound()); assertTrue(stack.getTagCompound().hasKey("KOMERecovery",10));
        assertTrue(KOMESerfKnightRecoveryService.isAssignedTo(stack,assignment,owner));
    }

    @Test public void ordinaryDuplicateOrWrongOwnerCannotSatisfyRecovery() {
        KOMESerfKnightTrialAssignment assignment=assignment(); UUID owner=UUID.randomUUID();
        assertFalse(KOMESerfKnightRecoveryService.isAssignedTo(new ItemStack(Items.gold_ingot),assignment,owner));
        assertFalse(KOMESerfKnightRecoveryService.isAssignedTo(KOMESerfKnightRecoveryService.assignedStack(assignment,owner),assignment,UUID.randomUUID()));
    }

    @Test public void onlyTheCorrectObjectIsConsumedWhenItIsReturned() {
        KOMESerfKnightTrialAssignment assignment=assignment(); UUID owner=UUID.randomUUID(); ItemStack ordinary=new ItemStack(Items.gold_ingot,2); ItemStack correct=KOMESerfKnightRecoveryService.assignedStack(assignment,owner); ItemStack[] inventory={ordinary,correct};
        assertTrue(KOMESerfKnightRecoveryService.consumeAssignedStack(inventory,assignment,owner)); assertSame(ordinary,inventory[0]); assertEquals(2,ordinary.stackSize); assertNull(inventory[1]);
        ItemStack[] wrong={KOMESerfKnightRecoveryService.assignedStack(assignment,owner)}; assertFalse(KOMESerfKnightRecoveryService.consumeAssignedStack(wrong,assignment,UUID.randomUUID())); assertNotNull(wrong[0]);
    }

    @Test public void itemAndEncounterNbtRoundTripWithoutChangingIdentityOrCreatingSecondObject() {
        KOMESerfKnightTrialAssignment assignment=assignment(); UUID owner=UUID.randomUUID(); ItemStack stack=KOMESerfKnightRecoveryService.assignedStack(assignment,owner);
        NBTTagCompound savedStack=(NBTTagCompound)stack.getTagCompound().copy(); ItemStack loadedStack=KOMESerfKnightRecoveryService.assignedStack(assignment,owner); loadedStack.setTagCompound(savedStack);
        assertTrue(KOMESerfKnightRecoveryService.isAssignedTo(loadedStack,assignment,owner));
        NBTTagCompound data=new NBTTagCompound(); data.setBoolean(KOMESerfKnightRecoveryService.DATA_SITE_CREATED,true); data.setInteger(KOMESerfKnightRecoveryService.DATA_DIMENSION,0); data.setInteger(KOMESerfKnightRecoveryService.DATA_X,120); data.setInteger(KOMESerfKnightRecoveryService.DATA_Y,70); data.setInteger(KOMESerfKnightRecoveryService.DATA_Z,-120); data.setString(KOMESerfKnightRecoveryService.DATA_OBJECT,UUID.randomUUID().toString());
        KOMESerfKnightTrialAssignment active=assignment.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data); KOMESerfKnightTrialAssignment loaded=KOMESerfKnightTrialAssignment.readFromNBT(active.writeToNBT());
        assertTrue(loaded.data.getBoolean(KOMESerfKnightRecoveryService.DATA_SITE_CREATED)); assertEquals(data.getString(KOMESerfKnightRecoveryService.DATA_OBJECT),loaded.data.getString(KOMESerfKnightRecoveryService.DATA_OBJECT));
    }

    @Test public void recoveryPresentationContainsOnlyHumanState() {
        KOMEPlayerProgression player=new KOMEPlayerProgression(); player.setCanonicalRank(KOMEProgressionRank.SERF); KOMESerfKnightProgression state=player.getSerfKnightProgression();
        assertTrue(KOMESerfKnightService.setSerfdomMaster(state,liege()).success); for(KOMESerfKnightDutyType duty:KOMESerfKnightDutyType.values()){assertTrue(KOMESerfKnightService.assignDuty(state,duty,null,10+duty.ordinal()).success);assertTrue(KOMESerfKnightService.completeDuty(state,duty).success);} assertTrue(KOMESerfKnightService.setProspectiveLiege(state,liege()).success);
        KOMESerfKnightTrialAssignment a=assignment(); state.setTrial(a); assertTrue(KOMEProgressionSummary.text(player).contains("Recover the lost item"));
        NBTTagCompound data=(NBTTagCompound)a.data.copy(); data.setBoolean(KOMESerfKnightRecoveryService.DATA_RETRIEVED,true); state.updateTrialAssignment(a.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data)); assertTrue(KOMEProgressionSummary.text(player).contains("Return the recovered item to your Liege")); assertFalse(KOMEProgressionSummary.text(player).contains(a.assignmentToken));
    }

    @Test public void retrievedStateAndDroppedStackIdentitySurviveAssignmentReload() {
        KOMESerfKnightTrialAssignment source=assignment(); NBTTagCompound data=(NBTTagCompound)source.data.copy(); data.setBoolean(KOMESerfKnightRecoveryService.DATA_SITE_CREATED,true); data.setBoolean(KOMESerfKnightRecoveryService.DATA_RETRIEVED,true); data.setInteger(KOMESerfKnightRecoveryService.DATA_X,144);
        KOMESerfKnightTrialAssignment loaded=KOMESerfKnightTrialAssignment.readFromNBT(source.withStage(KOMESerfKnightTrialAssignment.Stage.ACTIVE,data).writeToNBT());
        assertEquals(KOMESerfKnightTrialAssignment.Stage.ACTIVE,loaded.stage); assertTrue(loaded.data.getBoolean(KOMESerfKnightRecoveryService.DATA_RETRIEVED)); assertTrue(loaded.data.getBoolean(KOMESerfKnightRecoveryService.DATA_SITE_CREATED));
        UUID owner=UUID.randomUUID(); assertTrue(KOMESerfKnightRecoveryService.isAssignedTo(KOMESerfKnightRecoveryService.assignedStack(loaded,owner),loaded,owner));
    }

    @Test public void recoveryCompletionIsOnlyReachedThroughTheCanonicalSeam() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightRecoveryService.java")),StandardCharsets.UTF_8);
        assertTrue(source.contains("KOMESerfKnightService.markTrialObjectiveComplete(state)")); assertFalse(source.contains("state.setTrialCompleted("));
        String packet=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketRelationshipAction.java")),StandardCharsets.UTF_8);
        assertTrue(packet.contains("KOMESerfKnightRecoveryService.deliver(p,progression,n)")); assertFalse(packet.contains("markTrialObjectiveComplete"));
    }

    @Test public void recoveryItemCannotAgeDespawnWhileWaiting() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/KOMESerfKnightRecoveryService.java")),StandardCharsets.UTF_8);
        assertTrue(source.contains("dropped.lifespan=Integer.MAX_VALUE"));
    }
}
