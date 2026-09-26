package kome.common.data;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class KOMECombatHireMountSafetyTest {
    @Test public void deniedMountedHireDiscardsRiderAndMount() throws Exception {
        TestEntity rider = entity(0.6F, 1.8F);
        TestEntity mount = entity(1.4F, 1.6F);
        rider.ridingEntity = mount;
        mount.riddenByEntity = rider;

        KOMEEvents.discardDeniedHireEntityTree(rider);

        assertTrue(rider.isDead);
        assertTrue(mount.isDead);
    }

    @Test public void successfulMountedHirePositionsAndPreservesRiderAndMount() throws Exception {
        TestEntity rider = entity(0.6F, 1.8F);
        TestEntity mount = entity(1.4F, 1.6F);
        rider.ridingEntity = mount;
        mount.riddenByEntity = rider;

        KOMEEvents.positionEntityTree(rider, 12.5D, 70.0D, -8.5D);

        for (Entity part : new Entity[] {rider, mount}) {
            assertFalse(part.isDead);
            assertEquals(12.5D, part.posX, 0.0D);
            assertEquals(70.0D, part.posY, 0.0D);
            assertEquals(-8.5D, part.posZ, 0.0D);
        }
    }

    @Test public void recruitmentCommitIsDeferredUntilPostJoinUpdateAndHaltFollowsCommit()
            throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/kome/common/data/KOMEEvents.java")), StandardCharsets.UTF_8);
        String join = between(source, "public void onEntityJoinWorld", "public void onEntityInteract");
        String update = between(source, "public void onLivingUpdate", "public void onLivingAttack");
        String hire = between(source, "private void handleHiredUnit", "static void positionEntityTree");

        assertFalse(join.contains("handleHiredUnit("));
        assertTrue(update.contains("handleHiredUnit(npc)"));
        int committed = hire.indexOf("debit.commit();");
        int halted = hire.indexOf("initializeSuccessfulCombatHireAsHalted(npc);", committed);
        int snapshot = hire.indexOf(
            "record.stationedEntityData = KOMEEntitySnapshots.snapshot(npc);", halted);
        assertTrue(committed >= 0 && halted > committed);
        assertTrue(snapshot > halted);
        String haltHelper = between(source,
            "static void initializeSuccessfulCombatHireAsHalted",
            "static void discardDeniedHireEntityTree");
        assertTrue(haltHelper.contains("npc.hiredNPCInfo.halt();"));
        assertFalse(haltHelper.contains(".ready();"));
    }

    @Test public void deferredCrossChunkPlacementKeepsMountedTreeCoherent() throws Exception {
        TestEntity rider = entity(0.6F, 1.8F);
        TestEntity mount = entity(1.4F, 1.6F);
        rider.ridingEntity = mount;
        mount.riddenByEntity = rider;
        rider.setPosition(15.5D, 70.0D, 15.5D);
        mount.setPosition(15.5D, 70.0D, 15.5D);

        KOMEEvents.positionEntityTree(rider, 16.5D, 70.0D, 16.5D);

        assertEquals(1, ((int) Math.floor(rider.posX)) >> 4);
        assertEquals(1, ((int) Math.floor(rider.posZ)) >> 4);
        assertSame(mount, rider.ridingEntity);
        assertSame(rider, mount.riddenByEntity);
        assertEquals(rider.posX, mount.posX, 0.0D);
        assertEquals(rider.posZ, mount.posZ, 0.0D);
    }

    @Test public void everyCombatHireDenialUsesEntityTreeCleanup() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/kome/common/data/KOMEEvents.java")), StandardCharsets.UTF_8);
        assertFalse(source.contains("KOMEReflection.setDead(npc)"));
        assertEquals(7, occurrences(source, "discardDeniedHireEntityTree(npc)"));
        assertTrue(source.contains("catch (RuntimeException failure)"));
        assertTrue(source.contains("debit.rollback();"));
    }

    private static TestEntity entity(float width, float height) throws Exception {
        TestEntity entity = new TestEntity();
        entity.width = width;
        entity.height = height;
        return entity;
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int from = 0; (from = text.indexOf(needle, from)) >= 0;
                from += needle.length()) count++;
        return count;
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing source range " + start + " to " + end, from >= 0 && to > from);
        return source.substring(from, to);
    }

    private static final class TestEntity extends Entity {
        private TestEntity() { super(null); }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound tag) { }
        @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
    }
}
