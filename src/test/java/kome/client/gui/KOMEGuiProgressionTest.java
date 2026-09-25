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
        assertEquals(true, source.contains("\"Prince\""));
        assertEquals(false, source.contains("\"Prince / King\""));
    }

    @Test
    public void relationshipDepartureExistsOnlyAtRelationshipNpc() throws Exception {
        String progression = new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")), Charset.forName("UTF-8"));
        String hub = new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiRelationshipHub.java")), Charset.forName("UTF-8"));
        String packet = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/network/KOMEPacketRelationshipAction.java")), Charset.forName("UTF-8"));
        assertFalse(progression.contains("buttonLeaveRelationship"));
        assertFalse(progression.contains("KOMEPacketProgressionRelationshipAction"));
        assertTrue(hub.contains("\"Leave Master\":\"Leave Liege\""));
        assertTrue(hub.contains("KOMEPacketRelationshipAction.LEAVE"));
        assertTrue(packet.contains("p.getDistanceSqToEntity(e)>64"));
        assertTrue(packet.contains("KOMESerfKnightService.leaveSerfdomMaster"));
        assertTrue(packet.contains("KOMESerfKnightService.leaveProspectiveLiege"));
        assertTrue(packet.contains("KOMESerfKnightEscortService.cleanup"));
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

    @Test public void advancementsAndRanksUsePersistentBookTabsWithoutReplacingLegacyRows() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")),Charset.forName("UTF-8"));
        assertTrue(source.contains("\"Advancements\""));assertTrue(source.contains("\"Ranks\""));
        assertTrue(source.contains("Style.TAB"));assertTrue(source.contains("lastSelectedView"));
        assertTrue(source.contains("drawAchievements(groupAchievements)"));assertTrue(source.contains("drawCategoryBar()"));
        assertTrue(source.contains("selectView(View.ADVANCEMENTS)"));assertTrue(source.contains("selectView(View.RANKS)"));
    }

    @Test public void rankLayoutIsContentMeasuredAndOnlyScrollsOverflow() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiProgression.java")),Charset.forName("UTF-8"));
        assertTrue(source.contains("rankSummary.requirements.size()*26"));
        assertTrue(source.contains("listFormattedStringToWidth(rankSummary.activityObjective,184)"));
        assertTrue(source.contains("if(max>0)drawRankScrollbar(max)"));
        assertTrue(source.contains("KOMEGuiTheme.enableScissor"));
    }
}
