package kome.common.data;

import cpw.mods.fml.common.FMLCommonHandler;
import kome.common.network.KOMEPacketAllianceData;
import kome.common.network.KOMEPacketHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Refreshes the public diplomacy projection after an effective relation
 * changes. This keeps faction-screen relationship labels current without
 * creating a second diplomacy authority.
 */
final class KOMEDiplomacyClientSync {
    private KOMEDiplomacyClientSync() {
    }

    static void refreshAll(
            KOMEWorldData data) {
        if(data==null
                ||KOMEPacketHandler.network==null) {
            return;
        }

        MinecraftServer server=
            FMLCommonHandler.instance()
                .getMinecraftServerInstance();

        if(server==null
                ||server.getConfigurationManager()==null) {
            return;
        }

        for(Object value:
                server.getConfigurationManager()
                    .playerEntityList) {
            if(!(value instanceof EntityPlayerMP)) {
                continue;
            }

            EntityPlayerMP player=
                (EntityPlayerMP)value;

            KOMEPacketHandler.network.sendTo(
                new KOMEPacketAllianceData(
                    KOMEAllianceRecordBuilder.build(
                        data,
                        player)),
                player);
        }
    }
}
