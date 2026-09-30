package kome.common.data;

import java.util.UUID;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMECourierDispatchDropTest {
    @Test
    public void courierDispatchPickupIsRestrictedToRecordedOwner() {
        UUID owner=UUID.randomUUID();
        UUID stranger=UUID.randomUUID();

        ItemStack dispatch=new ItemStack(Items.written_book);

        NBTTagCompound root=new NBTTagCompound();
        NBTTagCompound courier=new NBTTagCompound();

        courier.setString("Owner",owner.toString());
        root.setTag("KOMECourier",courier);
        dispatch.setTagCompound(root);

        assertTrue(
            KOMECourierService.canPickup(
                dispatch,
                owner));

        assertFalse(
            KOMECourierService.canPickup(
                dispatch,
                stranger));

        assertTrue(
            KOMECourierService.canPickup(
                new ItemStack(Items.paper),
                stranger));
    }

    @Test
    public void directInteractionDropsCourierLetterInsteadOfSilentlyInsertingIt()
            throws Exception {
        String source=
            new String(
                java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(
                        "src/main/java/kome/common/data/KOMEProgressionNpcInteractionService.java")),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(source.contains(
            "KOMECourierService.dropMessageFromMaster"));

        assertTrue(source.contains(
            "KOMECourierService.hasDispatch"));

        assertFalse(source.contains(
            "player.inventory.addItemStackToInventory(" +
            "\n                    KOMECourierService.message"));
    }
}
