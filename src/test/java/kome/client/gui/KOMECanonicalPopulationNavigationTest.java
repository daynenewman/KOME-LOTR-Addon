package kome.client.gui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

/** Headless structural coverage of real navigation; rendered scale/interaction testing is manual. */
public class KOMECanonicalPopulationNavigationTest {
    @Test public void normalInitializationExposesBothTabsAndInitialBuildActionsRemain() throws Exception {
        String source = source();
        String init = section(source, "public void initGui()", "private void addTabButtons()");
        assertTrue(init.contains("addTabButtons();")); assertTrue(init.contains("activeTab == 0"));
        assertTrue(init.contains("initBuildControls();")); assertTrue(source.contains("private int activeTab;"));
        String tabs = section(source, "private void addTabButtons()", "private void initBuildControls()");
        assertTrue(tabs.contains("ID_TAB_BUILDS")); assertTrue(tabs.contains("\"Builds\""));
        assertTrue(tabs.contains("ID_TAB_POPULATION")); assertTrue(tabs.contains("\"Canonical Population\""));
        assertFalse(tabs.contains("ALLOCATIONS"));
        String actions = section(source, "protected void actionPerformed(", "private void sendBuildAction(");
        assertTrue(actions.contains("button.id == ID_TAB_BUILDS || button.id == ID_TAB_POPULATION"));
        assertTrue(actions.contains("activeTab = button.id - ID_TAB_BUILDS")); assertTrue(actions.contains("initGui();"));
        assertFalse(actions.contains("sendPopulationUpdate(")); assertFalse(actions.contains("sendAllocationUpdate("));
        assertFalse(init.contains("initPopulationControls(")); assertFalse(init.contains("initAllocationControls("));
        assertTrue(source.contains(
            "create.enabled = KOMEBuildCreatePresentation.canCreateBuild(selectablePopulationOwners)"));
    }

    @Test public void selectedTabRendersOnlyCanonicalPopulationOrExistingBuildContent() throws Exception {
        String source = source(); String draw = section(source, "public void drawScreen(", "protected void keyTyped(");
        assertTrue(draw.contains("activeTab == 0")); assertTrue(draw.contains("drawBuildTab("));
        assertTrue(draw.contains("activeTab == 1")); assertTrue(draw.contains("drawCanonicalPopulationTab("));
        assertFalse(draw.contains("drawCards("));
        String card = section(source, "private void drawPopulationCard(", "private void drawStationedCard(");
        for (String label : new String[] {"Available Population", "Active Population", "Total represented population", "Permanent unit investment", "Tactical strength"})
            assertTrue(label, card.contains("\"" + label + "\""));
        for (String field : new String[] {"population.availablePopulationCenti", "population.activePopulationCenti", "population.representedPopulationCenti", "population.dailyRateUnits", "population.capCenti"})
            assertTrue(field, card.contains(field));
        assertTrue(card.contains("\"Faction Daily Rate\""));
        assertFalse(card.contains("\"Daily rate\""));
        assertFalse(card.contains("getAvailablePopulation(")); assertFalse(card.contains("sendToServer("));
    }

    @Test public void populationTileManageClosesSourceBeforeOpeningServerAuthoritativeTileGui() throws Exception {
        String population = new String(Files.readAllBytes(Paths.get(
            "src/main/java/kome/client/gui/KOMEGuiPopulation.java")), StandardCharsets.UTF_8);
        String manage = section(population, "private boolean handleTileRowClick(",
            "private List getPlayerRows()");
        int request = manage.indexOf("sendToServer(new KOMEPacketConquestOpenCapture(row.tileId))");
        int close = manage.indexOf("KOMEMinecraftClient.closePlayerScreen()");
        assertTrue(request >= 0);
        assertTrue(close > request);
    }

    @Test public void disabledClaimReasonChecksActualOwnershipBeforePermissionDenial() throws Exception {
        String source = source();
        String reason = section(source, "private String disabledReason(int id)",
            "private boolean hasViewerFaction()");
        int pledge = reason.indexOf("!hasViewerFaction()");
        int owned = reason.indexOf("isOwnedByPledge()");
        int authorization = reason.indexOf("Take Waypoints");
        assertTrue(pledge >= 0);
        assertTrue(owned > pledge);
        assertTrue(authorization > owned);
    }

    private static String section(String text, String from, String to) {
        int start = text.indexOf(from), end = text.indexOf(to, start + from.length());
        assertTrue(from, start >= 0); assertTrue(to, end > start); return text.substring(start, end);
    }
    private static String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiConquestCapture.java")), StandardCharsets.UTF_8);
    }
}
