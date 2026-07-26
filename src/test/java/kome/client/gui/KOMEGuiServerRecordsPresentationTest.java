package kome.client.gui;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KOMEGuiServerRecordsPresentationTest {
    @Test
    public void populationSummaryHandlesZeroAndThreeDigitValues() {
        KOMEServerRecordPresentation.PopulationSummary zero =
            KOMEServerRecordPresentation.parsePopulationSummary("Off 0, Def 0, Total 0");
        assertEquals(0, zero.offensive);
        assertEquals(0, zero.defensive);
        assertEquals(0, zero.total);

        KOMEServerRecordPresentation.PopulationSummary large =
            KOMEServerRecordPresentation.parsePopulationSummary("Off 1250, Def 375, Total 1625");
        assertEquals(1250, large.offensive);
        assertEquals(375, large.defensive);
        assertEquals(1625, large.total);
    }

    @Test
    public void populationSummaryFallsBackToComponentTotal() {
        KOMEServerRecordPresentation.PopulationSummary summary =
            KOMEServerRecordPresentation.parsePopulationSummary("Off 120, Def 30");
        assertEquals(150, summary.total);
    }

    @Test
    public void noAllianceStatesProduceAnEmptyPresentation() {
        assertTrue(KOMEServerRecordPresentation.parseAllianceSummaries("No alliances").isEmpty());
        assertTrue(KOMEServerRecordPresentation.parseAllianceSummaries("No faction alliances").isEmpty());
        assertTrue(KOMEServerRecordPresentation.parseAllianceSummaries("").isEmpty());
    }

    @Test
    public void allianceSummaryExpandsTracksAndLocksTierZero() {
        List alliances = KOMEServerRecordPresentation.parseAllianceSummaries(
            "From Blue Mountains: C T1, M T0, T None");
        assertEquals(1, alliances.size());
        KOMEServerRecordPresentation.AllianceSummary alliance =
            (KOMEServerRecordPresentation.AllianceSummary) alliances.get(0);
        assertEquals("Blue Mountains", alliance.faction);
        assertEquals("T1", alliance.civilian);
        assertEquals("Locked", alliance.military);
        assertEquals("Locked", alliance.trade);
    }

    @Test
    public void severalAlliancesAndLongFactionNamesRemainSeparate() {
        List alliances = KOMEServerRecordPresentation.parseAllianceSummaries(
            "To The Extremely Long Kingdom Beyond the Northern Mountains: C T2, M T1, T T3, "
                + "From Blue Mountains: C T1, M Pending, T T0, "
                + "To Gondor: C None, M T3, T T1");
        assertEquals(3, alliances.size());
        assertEquals("The Extremely Long Kingdom Beyond the Northern Mountains",
            ((KOMEServerRecordPresentation.AllianceSummary) alliances.get(0)).faction);
        assertEquals("Pending", ((KOMEServerRecordPresentation.AllianceSummary) alliances.get(1)).military);
        assertEquals("Locked", ((KOMEServerRecordPresentation.AllianceSummary) alliances.get(2)).civilian);
    }

    @Test
    public void controlledTilesHandleEmptyTenAndOverflowSets() {
        assertTrue(KOMEServerRecordPresentation.parseTileIds("").isEmpty());
        List ten = KOMEServerRecordPresentation.parseTileIds(
            "T064, T090, T109, T116, T132, T136, T142, T185, T208, T217");
        assertEquals(10, ten.size());
        List waypointFirst = KOMEServerRecordPresentation.parseTileIds(
            "Fornost (T116), Weather Hills (T132), T217");
        assertEquals("Fornost (T116)", waypointFirst.get(0));
        assertEquals("Weather Hills (T132)", waypointFirst.get(1));
        assertEquals("T217", waypointFirst.get(2));

        KOMEServerRecordPresentation.TileBadgeLayout fitting =
            KOMEServerRecordPresentation.computeTileBadgeLayout(10, 62, 620, 20, 5, 3);
        assertEquals(10, fitting.visibleTiles);
        assertEquals(0, fitting.hiddenTiles);
        assertTrue(fitting.rows >= 1 && fitting.rows <= 3);

        KOMEServerRecordPresentation.TileBadgeLayout overflow =
            KOMEServerRecordPresentation.computeTileBadgeLayout(100, 62, 620, 20, 5, 3);
        assertTrue(overflow.hiddenTiles > 0);
        assertEquals(3, overflow.rows);
        assertEquals(100, overflow.visibleTiles + overflow.hiddenTiles);
    }
}
