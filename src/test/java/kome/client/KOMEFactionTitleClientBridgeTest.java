package kome.client;

import kome.common.data.KOMEDiplomacyRelation;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEFactionTitleClientBridgeTest {
    @Test
    public void wandererIsNeutralToEveryFactionUntilPledged() {
        assertEquals(
            "Neutral",
            KOMEFactionTitleClientBridge.project(
                "",
                "rohan",
                "",
                KOMEDiplomacyRelation.ALLIES));

        assertEquals(
            "Neutral",
            KOMEFactionTitleClientBridge.project(
                "",
                "mordor",
                "",
                KOMEDiplomacyRelation.MORTAL_ENEMIES));
    }

    @Test
    public void ownFactionUsesOnlyProgressionRankTitle() {
        assertEquals(
            "Rider of Rohan",
            KOMEFactionTitleClientBridge.project(
                "rohan",
                "rohan",
                "Rider of Rohan",
                KOMEDiplomacyRelation.ALLIES));

        assertEquals(
            "Wanderer",
            KOMEFactionTitleClientBridge.project(
                "rohan",
                "rohan",
                "",
                KOMEDiplomacyRelation.ALLIES));
    }

    @Test
    public void foreignFactionUsesFiveStepDiplomacyLanguage() {
        assertEquals(
            "Mortal Enemy",
            KOMEFactionTitleClientBridge.project(
                "rohan",
                "mordor",
                "",
                KOMEDiplomacyRelation.MORTAL_ENEMIES));

        assertEquals(
            "Enemy",
            KOMEFactionTitleClientBridge.project(
                "rohan",
                "dunland",
                "",
                KOMEDiplomacyRelation.ENEMIES));

        assertEquals(
            "Neutral",
            KOMEFactionTitleClientBridge.project(
                "rohan",
                "bree",
                "",
                KOMEDiplomacyRelation.NEUTRAL));

        assertEquals(
            "Friendly",
            KOMEFactionTitleClientBridge.project(
                "rohan",
                "dale",
                "",
                KOMEDiplomacyRelation.FRIENDS));

        assertEquals(
            "Allies",
            KOMEFactionTitleClientBridge.project(
                "rohan",
                "gondor",
                "",
                KOMEDiplomacyRelation.ALLIES));
    }
}
