package kome.common.data;

import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEFactionProgressionTitlesTest {
    @Test
    public void rohanUsesFactionSpecificProgressionTitles() {
        assertEquals("Commoner",
            KOMEFactionProgressionTitles.title("rohan", KOMEProgressionRank.WANDERER));
        assertEquals("Commoner",
            KOMEFactionProgressionTitles.title("rohan", KOMEProgressionRank.SERF));
        assertEquals("Rider of Rohan",
            KOMEFactionProgressionTitles.title("rohan", KOMEProgressionRank.KNIGHT));
        assertEquals("Captain",
            KOMEFactionProgressionTitles.title("rohan", KOMEProgressionRank.LORD));
        assertEquals("Marshal",
            KOMEFactionProgressionTitles.title("rohan", KOMEProgressionRank.PRINCE));
    }

    @Test
    public void gondorUsesFactionSpecificProgressionTitles() {
        assertArrayEquals(
            new String[] {"Commoner", "Knight", "Lord", "Prince"},
            KOMEFactionProgressionTitles.ladder("gondor"));
    }

    @Test
    public void normalizedFactionAliasesUseSameTitles() {
        assertEquals(
            "Ranger",
            KOMEFactionProgressionTitles.title(
                "Dúnedain of the North",
                KOMEProgressionRank.KNIGHT));

        assertEquals(
            "Serpent Guard",
            KOMEFactionProgressionTitles.title(
                "Near Harad",
                KOMEProgressionRank.LORD));
    }

    @Test
    public void darkerFactionsUseTheirOwnTitles() {
        assertEquals(
            "Commander of Lugbúrz",
            KOMEFactionProgressionTitles.title(
                "mordor",
                KOMEProgressionRank.PRINCE));

        assertEquals(
            "Captain of the Hand",
            KOMEFactionProgressionTitles.title(
                "isengard",
                KOMEProgressionRank.PRINCE));
    }

    @Test
    public void unknownFactionFallsBackToCanonicalNames() {
        assertEquals(
            "Commoner",
            KOMEFactionProgressionTitles.title(
                "unknown_faction",
                KOMEProgressionRank.SERF));

        assertEquals(
            "Prince",
            KOMEFactionProgressionTitles.title(
                "",
                KOMEProgressionRank.PRINCE));
    }

    @Test
    public void nullRankSafelyFallsBackToWanderer() {
        assertEquals(
            "Commoner",
            KOMEFactionProgressionTitles.title("rohan", null));
    }
}