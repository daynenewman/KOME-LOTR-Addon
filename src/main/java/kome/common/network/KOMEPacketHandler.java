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
        network.registerMessage(KOMEPacketAllianceRequest.Handler.class, KOMEPacketAllianceRequest.class, 17, Side.SERVER);
        network.registerMessage(KOMEPacketAllianceData.Handler.class, KOMEPacketAllianceData.class, 18, Side.CLIENT);
        network.registerMessage(KOMEPacketConquestTransfer.Handler.class, KOMEPacketConquestTransfer.class, 19, Side.SERVER);
        network.registerMessage(KOMEPacketLordMenu.Handler.class, KOMEPacketLordMenu.class, 20, Side.CLIENT);
        network.registerMessage(KOMEPacketLordAction.Handler.class, KOMEPacketLordAction.class, 21, Side.SERVER);
        network.registerMessage(KOMEPacketLordHighlight.Handler.class, KOMEPacketLordHighlight.class, 22, Side.CLIENT);
        network.registerMessage(KOMEPacketTilePopulationUpdate.Handler.class, KOMEPacketTilePopulationUpdate.class, 23, Side.SERVER);
        network.registerMessage(KOMEPacketTileAllocationUpdate.Handler.class, KOMEPacketTileAllocationUpdate.class, 24, Side.SERVER);
        network.registerMessage(KOMEPacketCompanyListGui.Handler.class, KOMEPacketCompanyListGui.class, 25, Side.CLIENT);
        network.registerMessage(KOMEPacketCompanyMoveConfirmGui.Handler.class, KOMEPacketCompanyMoveConfirmGui.class, 26, Side.CLIENT);
        network.registerMessage(KOMEPacketCompanyMovePreviewResult.Handler.class, KOMEPacketCompanyMovePreviewResult.class, 27, Side.CLIENT);
        network.registerMessage(KOMEPacketMovementHistoryRequest.Handler.class, KOMEPacketMovementHistoryRequest.class, 28, Side.SERVER);
        network.registerMessage(KOMEPacketMovementHistoryData.Handler.class, KOMEPacketMovementHistoryData.class, 29, Side.CLIENT);
    }
}
