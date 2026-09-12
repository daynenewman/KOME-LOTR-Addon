package kome.common.data;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMERulerAuthorizationTest {
    @Test
    public void recognizedRulerIsAuthorizedForTheirFaction() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, "Ruler");
        assertTrue(KOMERulerAuthorization.canActAsRuler(data, "GONDOR", ruler));
    }

    @Test
    public void nonRulerAndRulerOfAnotherFactionAreDenied() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, "Ruler");
        KOMERulerService.assignRuler(data, "rohan", other, "Other");

        assertFalse(KOMERulerAuthorization.canActAsRuler(data, "gondor", other));
        assertFalse(KOMERulerAuthorization.canActAsRuler(data, "gondor", UUID.randomUUID()));
    }

    @Test
    public void noRulerAndProgressionCompletionDoNotGrantAuthority() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID player = UUID.randomUUID();
        KOMEPlayerProgression progression = data.getProgression(player);
        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.forGroup("prince_king")) {
            progression.grant(achievement.id);
        }

        assertFalse(KOMERulerAuthorization.canActAsRuler(data, "gondor", player));
    }

    @Test
    public void replacementMovesAuthorizationImmediately() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", first, "First");
        KOMERulerService.assignRuler(data, "gondor", second, "Second");

        assertFalse(KOMERulerAuthorization.canActAsRuler(data, "gondor", first));
        assertTrue(KOMERulerAuthorization.canActAsRuler(data, "gondor", second));
    }

    @Test
    public void removalRemovesAuthorization() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, "Ruler");
        assertTrue(KOMERulerAuthorization.canActAsRuler(data, "gondor", ruler));

        KOMERulerService.removeRuler(data, "gondor");
        assertFalse(KOMERulerAuthorization.canActAsRuler(data, "gondor", ruler));
    }
}
