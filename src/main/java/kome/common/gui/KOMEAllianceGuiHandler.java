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

    public static void openAllianceLedger(EntityPlayerMP player, KOMEAlliance alliance, String contributingFaction) {
        pendingLedgers.put(KOMEReflection.getEntityUUID(player), new PendingLedger(alliance, contributingFaction));
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
        PendingLedger pending = (PendingLedger) pendingLedgers.remove(uuid);
        if (pending == null || pending.alliance == null) {
            return null;
        }
        KOMEAllianceInventory inventory = new KOMEAllianceInventory(KOMEWorldData.get(world), pending.alliance, (EntityPlayerMP) player, pending.contributingFaction);
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

    private static class PendingLedger {
        private final KOMEAlliance alliance;
        private final String contributingFaction;

        private PendingLedger(KOMEAlliance alliance, String contributingFaction) {
            this.alliance = alliance;
            this.contributingFaction = KOMEAlliance.normalizeFactionKey(contributingFaction);
        }
    }
}
