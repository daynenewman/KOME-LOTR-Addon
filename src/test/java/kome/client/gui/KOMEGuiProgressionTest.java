package kome.client.gui;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class KOMEGuiProgressionTest {
    @Test
    public void finalProgressionKeepsInternalKeyButDisplaysPrince() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")), Charset.forName("UTF-8"));
        assertEquals(true, source.contains("\"prince_king\""));
        assertEquals(true, source.contains("KOMEFactionProgressionTitles.title"));
        assertEquals(false, source.contains("\"Prince / King\""));
    }

    @Test
    public void relationshipDepartureLivesInProgressionFooter() throws Exception {
        String progression = new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")), Charset.forName("UTF-8"));
        String packet = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketProgressionRelationshipAction.java")), Charset.forName("UTF-8"));

        assertTrue(progression.contains("buttonLeaveRelationship"));
        assertTrue(progression.contains("KOMEPacketProgressionRelationshipAction"));
        assertTrue(progression.contains("KOMEGuiButton.Style.DESTRUCTIVE"));
        assertTrue(progression.contains("relationshipFooterHeight()"));

        assertTrue(packet.contains("KOMEProgressionEncounterCleanup.cleanup"));
        assertTrue(packet.contains("KOMESerfKnightService.leaveSerfdomMaster"));
        assertTrue(packet.contains("KOMESerfKnightService.leaveLiege"));
    }

    @Test public void masterLayoutIsMeasuredAndDutyViewTargetsRanks() throws Exception {
        String master=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiSerfdomMaster.java")),Charset.forName("UTF-8"));
        String progression=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")),Charset.forName("UTF-8"));
        assertTrue(master.contains("if(mode==1&&canRequestDuty)"));
        assertTrue(master.contains("if(mode==1&&hasActiveDuty)"));
        assertTrue(master.contains("buttonStartOffset(detailLines.size())"));
        assertTrue(master.contains("96+Math.max(0,detailLines)*10"));
        assertTrue(master.contains("KOMEGuiProgression.dutyView()"));
        assertTrue(progression.contains("view=focusDuty?View.RANKS:lastSelectedView"));
        assertTrue(progression.contains("if(focusDuty)lastSelectedView=View.RANKS"));
    }

    @Test public void advancementsAndRanksUsePersistentBookTabsWithRankRowsFilteredOutOfAdvancements() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")),Charset.forName("UTF-8"));
        assertTrue(source.contains("\"Advancements\""));assertTrue(source.contains("\"Ranks\""));
        assertTrue(source.contains("Style.TAB"));assertTrue(source.contains("lastSelectedView"));
        assertTrue(source.contains("drawAchievements(groupAchievements)"));assertTrue(source.contains("drawCategoryBar()"));
        assertTrue(source.contains("selectView(View.ADVANCEMENTS)"));assertTrue(source.contains("selectView(View.RANKS)"));
        assertTrue(source.contains("KOMEProgressionAchievement.forAdvancementsGroup"));
    }

    @Test public void openingProgressionRequestsFreshServerSnapshot() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")),Charset.forName("UTF-8"));
        assertTrue(source.contains("new KOMEPacketProgressionRequest()"));
        assertTrue(source.contains("KOMEPacketHandler.network.sendToServer"));
    }

    @Test public void rankLayoutIsContentMeasuredAndOnlyScrollsOverflow() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")),Charset.forName("UTF-8"));
        assertTrue(source.contains("KOMEProgressionRankLayout.requirementsHeight(rankSummary.requirements,dutiesExpanded)"));
        assertTrue(source.contains("listFormattedStringToWidth(rankSummary.activityObjective,184)"));
        assertTrue(source.contains("if(max>0)drawRankScrollbar(max)"));
        assertTrue(source.contains("KOMEGuiTheme.enableScissor"));
    }

    @Test public void sharedHeaderIsStableAndAdvancementsDoNotRenderRankDetails() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")),Charset.forName("UTF-8"));
        assertTrue(source.contains("drawCenteredString(\"KOME Progression\""));
        assertTrue(source.contains("drawCenteredString(owner,"));
        assertFalse(source.contains("owner + \" - \""));
        int advancementsStart=source.indexOf("private void drawAdvancements()");
        int ranksStart=source.indexOf("private void drawRanks()");
        String advancements=source.substring(advancementsStart,ranksStart);
        assertFalse(advancements.contains("drawSummary"));
        assertFalse(advancements.contains("canonicalSummary"));
        assertTrue(source.contains("view == View.RANKS"));
    }

    @Test public void dutiesAreCollapsibleChildrenInsideTheRankScrollRegion() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")),Charset.forName("UTF-8"));
        assertTrue(source.contains("private boolean dutiesExpanded"));
        assertTrue(source.contains("dutiesExpanded=!dutiesExpanded"));
        assertTrue(source.contains("requirement.hasChildren()"));
        assertTrue(source.contains("drawRankRequirementChild"));
        assertTrue(source.contains("guiLeft+50"));
        assertTrue(source.contains("\\u25B6"));assertTrue(source.contains("\\u25BC"));
    }
}
