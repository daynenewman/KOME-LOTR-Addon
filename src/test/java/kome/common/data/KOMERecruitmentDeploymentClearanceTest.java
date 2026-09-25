package kome.common.data;

import kome.common.KOMEAccessFixture;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class KOMERecruitmentDeploymentClearanceTest {
    @Test public void mountedFootprintIncludesActualMountWidthAndRiderHeight() throws Exception {
        TestEntity rider = entity(0.6F, 1.8F);
        TestEntity mount = entity(1.4F, 1.6F);
        rider.ridingEntity = mount;
        mount.riddenByEntity = rider;

        KOMERecruitmentDeploymentService.Footprint footprint =
            KOMERecruitmentDeploymentService.footprint(rider);

        assertEquals(1.4D, footprint.width, 0.00001D);
        assertEquals(mount.getMountedYOffset() + rider.height,
            footprint.height, 0.00001D);
    }

    @Test public void mountHeightAndWidthCanRejectSpaceSafeForHumanoid() throws Exception {
        ClearanceWorld world = KOMEAccessFixture.allocate(ClearanceWorld.class);
        world.maximumClearY = 66.0D;
        world.onlyCenterGround = false;
        assertTrue(KOMEStrategicDeploymentResolver.isSafeStandingAnchor(
            world, 0, 64, 0, 0.6D, 1.8D));
        assertFalse(KOMEStrategicDeploymentResolver.isSafeStandingAnchor(
            world, 0, 64, 0, 1.4D, 3.0D));

        world.maximumClearY = 256.0D;
        world.onlyCenterGround = true;
        assertTrue(KOMEStrategicDeploymentResolver.isSafeStandingAnchor(
            world, 0, 64, 0, 0.6D, 1.8D));
        assertFalse(KOMEStrategicDeploymentResolver.isSafeStandingAnchor(
            world, 0, 64, 0, 1.4D, 3.0D));
    }

    private static TestEntity entity(float width, float height) throws Exception {
        TestEntity entity = new TestEntity();
        entity.width = width;
        entity.height = height;
        return entity;
    }

    private static final class TestEntity extends Entity {
        private TestEntity() { super(null); }
        @Override protected void entityInit() { }
        @Override protected void readEntityFromNBT(NBTTagCompound tag) { }
        @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
    }

    private static final class ClearanceWorld extends World {
        private static final Block SOLID = new SolidBlock();
        double maximumClearY;
        boolean onlyCenterGround;
        private ClearanceWorld() {
            super((ISaveHandler) null, "clearance", (WorldProvider) null,
                (WorldSettings) null, (Profiler) null);
        }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public IChunkProvider getChunkProvider() { return null; }
        @Override public Chunk getChunkFromChunkCoords(int x, int z) { return null; }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
        @Override public int getActualHeight() { return 256; }
        @Override public Block getBlock(int x, int y, int z) {
            return onlyCenterGround && (x != 0 || z != 0) ? null : SOLID;
        }
        @Override public List getCollidingBoundingBoxes(Entity entity, AxisAlignedBB box) {
            return box.maxY <= maximumClearY
                ? Collections.emptyList() : Collections.singletonList(box);
        }
    }

    private static final class SolidBlock extends Block {
        SolidBlock() { super(Material.rock); }
    }
}
