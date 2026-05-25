package kome.common.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import kome.common.KOMEAddon;

public class KOMEPacketHandler {
    public static SimpleNetworkWrapper network;

    public static void init() {
        network = NetworkRegistry.INSTANCE.newSimpleChannel(KOMEAddon.MODID);
        network.registerMessage(KOMEPacketPopulationGui.Handler.class, KOMEPacketPopulationGui.class, 0, Side.CLIENT);
        network.registerMessage(KOMEPacketTerritoryGui.Handler.class, KOMEPacketTerritoryGui.class, 1, Side.CLIENT);
        network.registerMessage(KOMEPacketTerritoryData.Handler.class, KOMEPacketTerritoryData.class, 2, Side.CLIENT);
        network.registerMessage(KOMEPacketPopulationUnitsGui.Handler.class, KOMEPacketPopulationUnitsGui.class, 3, Side.CLIENT);
        network.registerMessage(KOMEPacketHireType.Handler.class, KOMEPacketHireType.class, 4, Side.CLIENT);
        network.registerMessage(KOMEPacketConquestCaptureGui.Handler.class, KOMEPacketConquestCaptureGui.class, 5, Side.CLIENT);
        network.registerMessage(KOMEPacketConquestClaim.Handler.class, KOMEPacketConquestClaim.class, 6, Side.SERVER);
        network.registerMessage(KOMEPacketConquestOpenCapture.Handler.class, KOMEPacketConquestOpenCapture.class, 7, Side.SERVER);
        network.registerMessage(KOMEPacketProgressionData.Handler.class, KOMEPacketProgressionData.class, 9, Side.CLIENT);
        network.registerMessage(KOMEPacketQuotaLedger.Handler.class, KOMEPacketQuotaLedger.class, 10, Side.CLIENT);
        network.registerMessage(KOMEPacketServerRecordRequest.Handler.class, KOMEPacketServerRecordRequest.class, 11, Side.SERVER);
        network.registerMessage(KOMEPacketServerRecordData.Handler.class, KOMEPacketServerRecordData.class, 12, Side.CLIENT);
        network.registerMessage(KOMEPacketConquestData.Handler.class, KOMEPacketConquestData.class, 13, Side.CLIENT);
        network.registerMessage(KOMEPacketUnitCapRequest.Handler.class, KOMEPacketUnitCapRequest.class, 14, Side.SERVER);
        network.registerMessage(KOMEPacketUnitCapUpdate.Handler.class, KOMEPacketUnitCapUpdate.class, 15, Side.SERVER);
        network.registerMessage(KOMEPacketUnitCapSync.Handler.class, KOMEPacketUnitCapSync.class, 16, Side.CLIENT);
    }
}
