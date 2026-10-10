package kome.common.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketProgressionTracker;
import net.minecraft.entity.player.EntityPlayerMP;

/** Diff-based publication boundary for the server-authoritative progression tracker. */
public final class KOMEProgressionTrackerService {
    private static final Map<UUID,String> LAST =
        new HashMap<UUID,String>();
    private static final Map<UUID,String> BOOK=new HashMap<UUID,String>();
    public static void syncInventoryBook(EntityPlayerMP player,KOMEWorldData data) {
        KOMEPlayerProgression p=data.getProgression(player.getUniqueID());
        String projection=KOMEProgressionGoodsPresentation.objective(p,player.inventory.mainInventory)
            +KOMEProgressionSummary.courierCopy(p,player.worldObj.getTotalWorldTime()/1200*1200);
        if(!projection.equals(BOOK.put(player.getUniqueID(),projection))&&!projection.isEmpty())
            KOMEProgressionAutoCompleter.syncPlayer(player,p);
    }

    private KOMEProgressionTrackerService() {
    }

    public static void syncIfChanged(
            EntityPlayerMP player,
            KOMEWorldData data,
            boolean force) {
        if(player==null
                ||data==null
                ||KOMEPacketHandler.network==null) {
            return;
        }

        UUID playerId=KOMEReflection.getEntityUUID(player);

        KOMEProgressionTrackerSnapshot snapshot=
            data.isProgressionEnabled()
                ?KOMEProgressionTrackerSnapshot.project(
                    player,
                    data.getProgression(playerId))
                :KOMEProgressionTrackerSnapshot.EMPTY;

        String signature=snapshot.signature();

        if(!force&&signature.equals(LAST.get(playerId))) {
            return;
        }

        LAST.put(playerId,signature);

        KOMEPacketHandler.network.sendTo(
            new KOMEPacketProgressionTracker(snapshot),
            player);
    }

    public static void clearPlayer(UUID playerId) {
        if(playerId!=null) {
            LAST.remove(playerId);
            BOOK.remove(playerId);
        }
    }

    public static void resetSession() {
        LAST.clear();
        BOOK.clear();
    }
}
