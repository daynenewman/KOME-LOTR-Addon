package kome.common.data;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEProgressionTitlesTest {
    private static final String FACTION = "gondor";

    @Test
    public void finalProgressionIsPrinceWithoutCreatingRuler() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID player = UUID.randomUUID();
        KOMEPlayerProgression progression = complete(data, player, "prince_king");

        assertEquals("Prince", KOMEProgressionTitles.resolveRankName(data, player, progression, FACTION));
        assertFalse(data.hasFactionKing(FACTION));
        assertFalse(data.isFactionKing(FACTION, player));
    }

    @Test
    public void repeatedResolutionDoesNotMutateRulerState() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID player = UUID.randomUUID();
        KOMEPlayerProgression progression = complete(data, player, "prince_king");

        assertEquals("Prince", KOMEProgressionTitles.resolveRankName(data, player, progression, FACTION));
        assertEquals("Prince", KOMEProgressionTitles.resolveRankName(data, player, progression, FACTION));
        assertNull(data.getFactionKingId(FACTION));
        assertFalse(data.hasFactionKing(FACTION));
    }

    @Test
    public void politicalRulerAndFinalProgressionRemainIndependent() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID endgame = UUID.randomUUID();
        UUID ruler = UUID.randomUUID();
        KOMEPlayerProgression endgameProgression = complete(data, endgame, "prince_king");

        assertTrue(KOMERulerService.assignRuler(data, FACTION, ruler, "Ruler"));
        assertEquals("Prince", KOMEProgressionTitles.resolveRankName(data, endgame, endgameProgression, FACTION));
        assertEquals("King", KOMEProgressionTitles.resolveRankName(data, ruler, data.getProgression(ruler), FACTION));
        assertEquals(ruler, data.getFactionKingId(FACTION));
        assertTrue(data.isFactionKing(FACTION, ruler));
        assertFalse(data.isFactionKing(FACTION, endgame));
    }

    @Test
    public void recognizedRulerIsKingWithoutFinalProgression() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();
        assertTrue(KOMERulerService.assignRuler(data, FACTION, ruler, "Ruler"));

        assertEquals("King", KOMEProgressionTitles.resolveRankName(data, ruler, data.getProgression(ruler), FACTION));
        assertEquals("King", KOMEServerRecordBuilder.getRank(data, ruler, data.getProgression(ruler), FACTION));
    }

    @Test
    public void completionAndProgressionPermissionsRemainIndependentOfOffice() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID player = UUID.randomUUID();
        KOMEProgressionAchievement finalAchievement = KOMEProgressionAchievement.forGroup("prince_king").get(0);
        KOMEPlayerProgression progression = data.getProgression(player);

        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.forGroup("prince_king")) {
            progression.grant(achievement.id);
        }
        assertTrue(progression.isCompleted(finalAchievement));
        assertEquals("Prince", KOMEProgressionTitles.resolveRankName(data, player, progression, FACTION));
        assertTrue(progression.isCompleted(finalAchievement));
    }

    @Test
    public void serverRecordsUsePrinceForFinalProgressionWithoutRulership() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID player = UUID.randomUUID();
        KOMEPlayerProgression progression = complete(data, player, "prince_king");

        assertEquals("Prince", KOMEServerRecordBuilder.getRank(data, player, progression, FACTION));
    }

    private static KOMEPlayerProgression complete(KOMEWorldData data, UUID player, String group) {
        KOMEPlayerProgression progression = data.getProgression(player);
        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.forGroup(group)) {
            progression.grant(achievement.id);
        }
        return progression;
    }
}
