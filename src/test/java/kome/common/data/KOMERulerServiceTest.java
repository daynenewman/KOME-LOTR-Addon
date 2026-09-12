package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMERulerServiceTest {
    @Test
    public void assignsFirstRulerWithoutProgressionEligibility() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();

        assertTrue(KOMERulerService.assignRuler(data, " Gondor ", ruler, "Ruler"));
        assertEquals(ruler, KOMERulerService.getRuler(data, "GONDOR"));
        assertEquals("Ruler", KOMERulerService.getRulerName(data, "gondor"));
        assertTrue(KOMERulerService.isRuler(data, "gondor", ruler));
    }

    @Test
    public void replacingRulerIsExplicitAndLeavesOneRecord() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", first, "First");

        assertTrue(KOMERulerService.assignRuler(data, "gondor", second, "Second"));
        assertEquals(second, data.getFactionKingId("gondor"));
        assertFalse(data.isFactionKing("gondor", first));
        assertEquals("Second", data.getFactionKingName("gondor"));
    }

    @Test
    public void assigningAcrossFactionsRemovesStaleOffice() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, "Ruler");

        assertTrue(KOMERulerService.assignRuler(data, "rohan", ruler, "Ruler"));
        assertFalse(data.hasFactionKing("gondor"));
        assertEquals(ruler, data.getFactionKingId("rohan"));
    }

    @Test
    public void removalIsExplicitAndIdempotent() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, "Ruler");

        assertTrue(KOMERulerService.removeRuler(data, "gondor"));
        assertFalse(data.hasFactionKing("gondor"));
        assertFalse(KOMERulerService.removeRuler(data, "gondor"));
    }

    @Test
    public void assignmentAndRemovalPreserveGainLossCallbacks() {
        CountingWorldData data = new CountingWorldData();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        KOMERulerService.assignRuler(data, "gondor", first, "First");
        KOMERulerService.assignRuler(data, "gondor", second, "Second");
        KOMERulerService.removeRuler(data, "gondor");

        assertEquals(2, data.gained);
        assertEquals(2, data.lost);
    }

    @Test
    public void repairNeverInventsRuler() {
        KOMEWorldData data = new KOMEWorldData("test");

        KOMERulerService.RepairResult result = KOMERulerService.repair(data, "gondor", UUID.randomUUID(), "Nobody");
        assertFalse(result.changed);
        assertTrue(result.reason.contains("no ruler invented"));
        assertFalse(data.hasFactionKing("gondor"));
    }

    @Test
    public void repairUpdatesExistingCachedNameButPreservesMissingName() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, "");

        KOMERulerService.RepairResult repaired = KOMERulerService.repair(data, "gondor", ruler, "Authoritative");
        assertTrue(repaired.changed);
        assertEquals("Authoritative", data.getFactionKingName("gondor"));

        KOMEWorldData invalid = new KOMEWorldData("test");
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList kings = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Faction", "gondor");
        entry.setString("Player", ruler.toString());
        entry.setString("Name", "");
        kings.appendTag(entry);
        nbt.setTag("FactionKings", kings);
        invalid.readFromNBT(nbt);

        KOMERulerService.RepairResult unresolved = KOMERulerService.repair(invalid, "gondor", null, null);
        assertFalse(unresolved.changed);
        assertTrue(unresolved.reason.contains("remains unresolved"));
        assertEquals(ruler, invalid.getFactionKingId("gondor"));
        assertTrue(KOMERulerService.repair(invalid, "gondor", ruler, "Authoritative").changed);
        assertEquals("Authoritative", invalid.getFactionKingName("gondor"));
    }

    @Test
    public void mismatchedAuthoritativeIdentityCannotRewriteRulerName() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, "Original");

        KOMERulerService.RepairResult result = KOMERulerService.repair(data, "gondor", other, "Not the Ruler");
        assertFalse(result.changed);
        assertEquals(ruler, data.getFactionKingId("gondor"));
        assertEquals("Original", data.getFactionKingName("gondor"));
    }

    @Test
    public void invalidPersistedUuidDoesNotCreateRuler() {
        KOMEWorldData data = new KOMEWorldData("test");
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList kings = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Faction", "gondor");
        entry.setString("Player", "not-a-uuid");
        entry.setString("Name", "Invalid");
        kings.appendTag(entry);
        nbt.setTag("FactionKings", kings);

        data.readFromNBT(nbt);
        assertFalse(data.hasFactionKing("gondor"));
    }

    @Test
    public void rulerSurvivesFactionKingsNbtRoundTrip() {
        KOMEWorldData original = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();
        KOMERulerService.assignRuler(original, "gondor", ruler, "Ruler");
        NBTTagCompound nbt = new NBTTagCompound();
        original.writeToNBT(nbt);

        KOMEWorldData restored = new KOMEWorldData("test");
        restored.readFromNBT(nbt);
        assertEquals(ruler, restored.getFactionKingId("gondor"));
        assertEquals("Ruler", restored.getFactionKingName("gondor"));
        assertTrue(restored.isFactionKing("GONDOR", ruler));
    }

    @Test
    public void compatibilityReadsUseCanonicalServiceState() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID ruler = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, "Ruler");

        assertTrue(data.hasFactionKing("GONDOR"));
        assertTrue(data.isFactionKing("gondor", ruler));
        assertEquals(ruler, data.getFactionKingId(" Gondor "));
    }

    private static class CountingWorldData extends KOMEWorldData {
        private int gained;
        private int lost;

        private CountingWorldData() {
            super("test");
        }

        @Override
        public void onFactionKingGained(String factionKey, long nowMillis) {
            gained++;
        }

        @Override
        public void onFactionKingLost(String factionKey, long nowMillis) {
            lost++;
        }
    }
}
