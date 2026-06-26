package kome.common.gui;

import cpw.mods.fml.common.network.IGuiHandler;
import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceInventory;
import kome.common.data.KOMEWorldData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KOMEAllianceGuiHandler implements IGuiHandler {
    public static final int ALLIANCE_LEDGER = 1;
    private static final Map pendingLedgers = new HashMap();

    public static void openAllianceLedger(EntityPlayerMP player, KOMEAlliance alliance) {
        pendingLedgers.put(KOMEReflection.getEntityUUID(player), alliance);
    }

    public static void resetSessionState() {
        pendingLedgers.clear();
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != ALLIANCE_LEDGER || !(player instanceof EntityPlayerMP)) {
            return null;
        }
        UUID uuid = KOMEReflection.getEntityUUID(player);
        KOMEAlliance alliance = (KOMEAlliance) pendingLedgers.remove(uuid);
        if (alliance == null) {
            return null;
        }
        KOMEAllianceInventory inventory = new KOMEAllianceInventory(KOMEWorldData.get(world), alliance, (EntityPlayerMP) player);
        return new KOMEContainerAllianceLedger(player.inventory, inventory);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != ALLIANCE_LEDGER) {
            return null;
        }
        InventoryBasic clientInventory = new InventoryBasic("Alliance Ledger", true, KOMEAlliance.STORAGE_SLOTS);
        return new kome.client.gui.KOMEGuiAllianceLedger(player.inventory, clientInventory);
    }
}
