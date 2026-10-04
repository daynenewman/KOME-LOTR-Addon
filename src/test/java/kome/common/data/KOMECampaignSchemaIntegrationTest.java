package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

/** Root migrations exercise both PR authorities through the production candidate loader. */
public class KOMECampaignSchemaIntegrationTest {
    @Rule public KOMETileTestResources geometry = new KOMETileTestResources();

    private KOMESeasonResetServiceTest.Fixture fixture() {
        KOMESeasonResetServiceTest.Fixture f = new KOMESeasonResetServiceTest.Fixture();
        KOMEArmyCompany c = f.company("C1", f.foreignTile); f.order(c, true);
        f.delivery.available = false; f.run();
        f.data.hiredUnits.values().iterator().next().companyName = c.name;
        KOMEPlayerGovernance g = new KOMEPlayerGovernance(c.owner, 1, "W1", "gondor", "",
            KOMEPlayerGovernance.State.SUBMITTED, 1, 1, "retained defeat");
        f.data.playerGovernance.put(g.key(), g);
        f.data.dailyJournal.boundary = f.data.dailyJournal.anchor = 100;
        f.data.dailyJournal.nextStage = 2; f.data.dailyJournal.status = "RUNNING";
        return f;
    }
    private NBTTagCompound save(KOMEWorldData data) { return KOMESeasonResetServiceTest.save(data); }
    private KOMEWorldData load(NBTTagCompound tag) {
        KOMEWorldData data = new KOMEWorldData("migration"); data.readFromNBT(tag); return data;
    }

    @Test public void everySupportedRootRetainsEveryPresentAuthorityAcrossTwoRoundTrips() {
        for (int schema = 6; schema <= 10; schema++) {
            KOMESeasonResetServiceTest.Fixture f = fixture();
            NBTTagCompound root = save(f.data); root.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, schema);
            NBTTagCompound saved = save(load(save(load(root))));
            assertEquals(10, saved.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
            for (String key : new String[] {"SeasonReset", "PlayerGovernance", "DailyJournal", "CivilianMusters",
                    "HiredUnits", "FactionPopulations", "WarSeason", "ConflictRecords"})
                assertEquals("schema " + schema + " " + key, root.getTag(key), saved.getTag(key));
            assertFalse(load(saved).seasonReset.complete());
            assertEquals(1, load(saved).hiredUnits.size());
            assertEquals("1:C1", load(saved).hiredUnits.values().iterator().next().seasonReturnToken);
            assertTrue(load(saved).hiredUnits.values().iterator().next().seasonReturnVirtual);
        }
    }

    @Test public void originalSixSevenEightNineLayoutsUpgradeOnlyTheirAbsentSections() {
        for (int schema = 6; schema <= 9; schema++) {
            NBTTagCompound root = save(fixture().data); root.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, schema);
            if (schema != 8) root.removeTag("SeasonReset");
            if (schema < 9) { root.removeTag("GovernanceSchema"); root.removeTag("PlayerGovernance"); root.removeTag("DailyJournal"); }
            if (schema == 6) { root.removeTag("ConflictDataSchemaVersion"); root.removeTag("NextConflictSequence"); root.removeTag("ConflictRecords"); }
            KOMEWorldData loaded = load(root);
            assertEquals(schema == 8 ? 1 : 0, loaded.seasonReset.companies.size());
            assertEquals(schema == 9 ? 1 : 0, loaded.playerGovernance.size());
            assertEquals(schema == 9 ? 2 : 0, loaded.dailyJournal.completedStages());
            assertEquals(777, KOMEPopulationService.getAvailablePopulationCenti(loaded, "gondor"));
            assertEquals(1, loaded.hiredUnits.size());
            assertEquals(10, save(loaded).getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        }
    }

    @Test public void mandatoryAndOptionalMalformedSectionsFailClosedWithoutPublishingCandidate() {
        for (int schema = 6; schema <= 10; schema++) for (String key : new String[] {"SeasonReset", "DailyJournal", "PlayerGovernance"}) {
            NBTTagCompound root = save(fixture().data); root.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, schema);
            root.setString(key, "damaged authority"); reject(root);
        }
        for (int schema : new int[] {8, 10}) {
            NBTTagCompound root = save(fixture().data); root.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, schema);
            root.removeTag("SeasonReset"); reject(root);
        }
        for (int schema : new int[] {9, 10}) for (String key : new String[] {"DailyJournal", "PlayerGovernance"}) {
            NBTTagCompound root = save(fixture().data); root.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, schema);
            root.removeTag(key); reject(root);
        }
    }

    @Test public void wrongResetListElementTypesCannotErasePendingReturns() {
        for (String key : new String[] {"Companies", "OriginChunks", "Units"}) {
            NBTTagCompound root = save(fixture().data), reset = root.getCompoundTag("SeasonReset");
            NBTTagCompound target = "Companies".equals(key) ? reset : reset.getTagList("Companies", 10).getCompoundTagAt(0);
            NBTTagList wrong = new NBTTagList(); wrong.appendTag(new NBTTagString("not a compound")); target.setTag(key, wrong);
            reject(root);
        }
    }

    private void reject(NBTTagCompound root) {
        KOMEWorldData target = fixture().data; NBTTagCompound before = save(target);
        try { target.readFromNBT(root); fail("accepted malformed authority"); }
        catch (IllegalStateException expected) { assertTrue(target.isWriteBlocked()); }
        assertEquals(before.getCompoundTag("SeasonReset"), target.seasonReset.write());
        assertEquals(before.getCompoundTag("DailyJournal"), target.dailyJournal.write());
        assertEquals(1, target.playerGovernance.size()); assertEquals(1, target.hiredUnits.size());
    }
}
