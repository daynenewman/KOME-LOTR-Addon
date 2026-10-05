package kome.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import kome.common.data.KOMEProgressionTrackerSnapshot;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEPacketProgressionTrackerTest {
    @Test public void requestedItemPreservesMetadataNbtAndClearsWithObjective() throws Exception {
        net.minecraft.item.Item item=new net.minecraft.item.Item().setHasSubtypes(true);
        // Register in the inert registry without starting Forge's LaunchClassLoader.
        java.lang.reflect.Method raw=net.minecraft.item.Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);
        raw.setAccessible(true);raw.invoke(net.minecraft.item.Item.itemRegistry,31901,"kome:tracker_test_item",item);
        net.minecraft.item.ItemStack target=new net.minecraft.item.ItemStack(item,1,1);
        net.minecraft.nbt.NBTTagCompound nbt=new net.minecraft.nbt.NBTTagCompound();nbt.setString("Variant","collection target");target.setTagCompound(nbt);
        KOMEProgressionTrackerSnapshot source=new KOMEProgressionTrackerSnapshot(true,"provisioning","Bring fish","0 / 64",0F,target);
        ByteBuf buffer=Unpooled.buffer();new KOMEPacketProgressionTracker(source).toBytes(buffer);
        KOMEPacketProgressionTracker decoded=new KOMEPacketProgressionTracker();decoded.fromBytes(buffer);
        assertTrue(net.minecraft.item.ItemStack.areItemStacksEqual(target,decoded.snapshot.requestedItem));
        assertEquals(source.signature(),decoded.snapshot.signature());assertEquals(0,buffer.readableBytes());
        buffer.clear();new KOMEPacketProgressionTracker(KOMEProgressionTrackerSnapshot.EMPTY).toBytes(buffer);decoded.fromBytes(buffer);
        assertNull(decoded.snapshot.requestedItem);assertFalse(decoded.snapshot.visible);assertEquals(0,buffer.readableBytes());
        assertNull(new KOMEProgressionTrackerSnapshot(true,"provisioning","Missing target","",0F,new net.minecraft.item.ItemStack((net.minecraft.item.Item)null)).requestedItem);
    }
    @Test
    public void snapshotRoundTripsAndClampsCompletion() {
        KOMEProgressionTrackerSnapshot source=
            new KOMEProgressionTrackerSnapshot(
                true,
                "provisioning",
                "Bring 28 Ceramic Mugs of Strong Ale to your Master.",
                "9 / 28",
                2F);

        ByteBuf buffer=Unpooled.buffer();

        new KOMEPacketProgressionTracker(source)
            .toBytes(buffer);

        KOMEPacketProgressionTracker decoded=
            new KOMEPacketProgressionTracker();

        decoded.fromBytes(buffer);

        assertTrue(decoded.snapshot.visible);
        assertEquals(
            "provisioning",
            decoded.snapshot.iconKey);
        assertEquals(
            source.objective,
            decoded.snapshot.objective);
        assertEquals(
            "9 / 28",
            decoded.snapshot.progress);
        assertEquals(
            1F,
            decoded.snapshot.completion,
            0F);
    }
}
