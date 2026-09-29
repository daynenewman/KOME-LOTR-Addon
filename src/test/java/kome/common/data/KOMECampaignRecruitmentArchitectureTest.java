package kome.common.data;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class KOMECampaignRecruitmentArchitectureTest {
    @Test public void backendHasNoCaptainGuiDependencyAndSupportsFutureSourceRequest()
            throws Exception {
        String backend = text("src/main/java/kome/common/data/KOMECampaignRecruitmentService.java");
        assertFalse(backend.contains("lotr.client.gui"));
        assertFalse(backend.contains("LOTRGui"));
        assertTrue(backend.contains("interface RecruitmentSource"));
        assertTrue(backend.contains("FUTURE_RECRUITMENT_SOURCE"));
        assertTrue(backend.contains("requestedClass != KOMEHiredUnitClass.CAMPAIGN"));
        assertTrue(backend.indexOf("assignForCampaignWorkflow")
            < backend.indexOf("assignUnitToCampaignCompanyAtTile"));
    }

    @Test public void nativeAdapterReResolvesEveryAuthoritativePurchaseInput()
            throws Exception {
        String adapter = text("src/main/java/kome/common/data/KOMENativeTraderCampaignRecruitment.java");
        assertTrue(adapter.contains("player.openContainer"));
        assertTrue(adapter.contains("player.worldObj.getEntityByID(traderEntityId)"));
        assertTrue(adapter.contains("entries[tradeIndex]"));
        assertTrue(adapter.contains("trade.getCost(player, hireable)"));
        assertTrue(adapter.contains("KOMEUnitPopulationCostService.calculate"));
        assertFalse(adapter.contains("tileAtWorldCoordinates"));
        assertTrue(adapter.contains("KOMERecruitmentDeploymentService.resolve"));
        String backend = text("src/main/java/kome/common/data/KOMECampaignRecruitmentService.java");
        assertTrue(backend.contains("KOMERecruitmentLocationService.resolveSelectedOrDefault"));
        assertFalse(backend.contains("prepared.strategicTile"));
        assertTrue(adapter.contains("LOTRItemCoin.takeCoins"));
        assertTrue(adapter.contains("LOTRItemCoin.giveCoins"));
        assertTrue(adapter.contains("hiredNPCInfo.hireUnit"));
        assertTrue(adapter.contains("spawnEntityInWorld"));
        assertTrue(adapter.indexOf("prepareDeployment")
            < adapter.indexOf("performNativeHire"));
    }

    @Test public void existingGuiReplacementOwnsSecondActionWithoutAsmAndHidesFarmhands()
            throws Exception {
        String gui = text("src/main/java/com/enovak/lotrmoremobs/client/gui/LOTRGuiUnitTradePledgeNavigation.java");
        String handler = text("src/main/java/com/enovak/lotrmoremobs/client/UnitTradePledgeNavigationHandler.java");
        assertTrue(handler.contains("GuiOpenEvent"));
        assertTrue(handler.contains("event.gui = new LOTRGuiUnitTradePledgeNavigation"));
        assertTrue(gui.contains("kome.unitTrade.campaignHire"));
        assertTrue(gui.contains("new KOMEPacketCampaignHire"));
        assertTrue(gui.contains("Task.WARRIOR"));
        assertTrue(gui.contains("supportsDirectCampaignHire"));
        assertFalse(gui.contains("LOTRPacketBuyUnit"));
        assertFalse(gui.toLowerCase(java.util.Locale.ROOT).contains("asm"));
    }

    @Test public void ordinaryObserverAndRemovedAdminCommandRemainSeparate()
            throws Exception {
        String events = text("src/main/java/kome/common/data/KOMEEvents.java");
        String command = text("src/main/java/kome/common/command/KOMECommandTroops.java");
        assertTrue(events.contains("registerOrdinaryCombatHire"));
        assertFalse(events.contains("KOMECampaignRecruitmentService"));
        assertFalse(command.contains("campaign <unitId>"));
        assertFalse(command.contains("handleAdminCampaignAdmission"));
        assertFalse(command.contains("ADMIN_CAMPAIGN_ADMISSION"));
    }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
