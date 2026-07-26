package kome.common.data;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEAllianceSystemsTest {
    @Test
    public void legacyTradePostReaderCarriesOnlyMigrationOwnershipAndFarmerFlag() {
        NBTTagCompound legacy = legacyTradePost("TP-TEST", "gondor", "rohan");
        legacy.setBoolean("FarmerReserved", true);
        legacy.setString("Status", "ACTIVE");
        legacy.setLong("ProductionCycles", 4L);
        KOMELegacyTradePostRecord loaded = new KOMELegacyTradePostRecord();
        loaded.readFromNBT(legacy);
        assertEquals("TP-TEST", loaded.id);
        assertEquals("gondor", loaded.operatingFaction);
        assertEquals("rohan", loaded.hostFaction);
        assertTrue(loaded.legacyFarmerReservation);
    }

    @Test
    public void tradeTierTwoUsesFutureOnlyProduceBenefitMetadataAndWritesNoRuntimeState() {
        KOMEAllianceBenefits.Benefit benefit = KOMEAllianceBenefits.get(KOMEAlliance.TRADE, 2);
        assertEquals("Additional Produce Farmer Slot", benefit.title);
        assertEquals("This Trade T2 alliance has unlocked an additional Produce Farmer slot. "
            + "Produce Farmer integration will be added in a future update.", benefit.restriction);

        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("faction_one", "faction_two", true);
        alliance.requestTrack(KOMEAlliance.TRADE, "test", 0L, false);
        alliance.setTier(KOMEAlliance.TRADE, 2, "test", 1L);
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("TradeProduceSlotsMaximum", 99);
        saved.setTag("AllianceProduceSlots", new net.minecraft.nbt.NBTTagList());
        data.writeToNBT(saved);
        assertEquals(2, alliance.getTier(KOMEAlliance.TRADE));
        assertFalse(saved.hasKey("TradeProduceSlotsMaximum"));
        assertFalse(saved.hasKey("AllianceProduceSlots"));
    }

    @Test
    public void retiredProducePendingItemMigratesToRecoveryExactlyOnce() {
        KOMEWorldData migrated = new KOMEWorldData("test");
        assertTrue(migrated.recoverRetiredProducePending(
            KOMEAlliance.pairKey("faction_one", "faction_two"), "faction_one",
            new ItemStack(new net.minecraft.item.Item(), 3)));
        KOMEAlliance alliance = migrated.getAlliance("faction_one", "faction_two", false);
        assertNotNull(alliance);
        assertEquals(1, alliance.getFactionLedger("faction_one").getRecoveryStackCount());
        ItemStack recovered = alliance.getFactionLedger("faction_one").removeRecoveryStack(0);
        assertNotNull(recovered);
        assertEquals(3, recovered.stackSize);
        alliance.getFactionLedger("faction_one").addRecoveryStack(recovered);

        NBTTagCompound cleanSave = new NBTTagCompound();
        cleanSave.setTag("AllianceProduceSlots", new net.minecraft.nbt.NBTTagList());
        cleanSave.setInteger("TradeProduceSlotsMaximum", 1);
        migrated.writeToNBT(cleanSave);
        assertFalse(cleanSave.hasKey("AllianceProduceSlots"));
        assertFalse(cleanSave.hasKey("TradeProduceSlotsMaximum"));
    }

    @Test
    public void tradeTierTwoHasNoStructuralOrPopulationRequirement() {
        KOMEWorldData data = new KOMEWorldData("test");
        assertEquals(0, data.getAlliancePopulationRequirement(KOMEAlliance.TRADE, 2));
        assertEquals(250, data.getAllianceActivityRequirement(KOMEAlliance.TRADE, 2));
    }

    @Test
    public void stewardshipCapIsGlobalAndExcludesAllocations() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = new KOMEConquestTile("T002");
        tile.claim("rohan", 0L);
        data.conquestTiles.put(tile.id, tile);
        KOMETilePopulation pool = data.getOrCreateTilePopulationPool(tile.id, "rohan");
        pool.offensiveTotal = 100;
        assertEquals(100, data.getKinglessStewardshipAvailable("rohan"));
        KOMEPlayerTilePopulationAllocation allocation = data.getOrCreateAllocation(tile.id, "rohan", UUID.randomUUID(), "Rider");
        allocation.offensiveAllocated = 20;
        assertEquals(80, data.getKinglessStewardshipAvailable("rohan"));
        KOMEHiredUnitRecord reserved = new KOMEHiredUnitRecord();
        reserved.entity = UUID.randomUUID();
        reserved.owner = UUID.randomUUID();
        reserved.cost = 10;
        reserved.benefitSource = "MILITARY_T3_STEWARDSHIP";
        reserved.populationOwningFaction = "rohan";
        data.hiredUnits.put(reserved.entity, reserved);
        pool.offensiveUsed = 10;
        assertEquals(70, data.getKinglessStewardshipAvailable("rohan"));
    }

    @Test
    public void stewardshipExcludesCapturedHistoricalPoolsAndPlayerAllocations() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = new KOMEConquestTile("T003");
        tile.claim("rohan", 0L);
        data.conquestTiles.put(tile.id, tile);
        data.getOrCreateTilePopulationPool(tile.id, "rohan").offensiveTotal = 100;
        data.getOrCreateTilePopulationPool(tile.id, "gondor").offensiveTotal = 100;
        KOMEPlayerTilePopulationAllocation allocation = data.getOrCreateAllocation(tile.id, "rohan", UUID.randomUUID(), "Rider");
        allocation.offensiveAllocated = 20;
        // Only the 100 native-source population is eligible; the 20 allocation remains excluded.
        assertEquals(80, data.getKinglessStewardshipAvailable("rohan"));
    }

    @Test
    public void movementAndCompanyAuthorityRoundTrip() {
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "MOVE-1";
        order.owner = UUID.randomUUID();
        order.ownerFaction = "gondor";
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.routeTiles.add("T001");
        order.routeTiles.add("T002");
        order.traveledRouteTiles.add("T001");
        order.accessLossReason = "revoked";
        KOMEArmyMovementOrder loaded = new KOMEArmyMovementOrder();
        loaded.readFromNBT(order.writeToNBT());
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, loaded.status);
        assertEquals(1, loaded.traveledRouteTiles.size());
        assertEquals("revoked", loaded.accessLossReason);

        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "COMPANY";
        company.owner = UUID.randomUUID();
        company.tendency = KOMEArmyCompany.AGGRESSIVE;
        company.temporaryController = UUID.randomUUID();
        company.controllerAuthority = KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;
        NBTTagCompound saved = company.writeToNBT();
        KOMEArmyCompany restored = new KOMEArmyCompany();
        restored.readFromNBT(saved);
        assertEquals(KOMEArmyCompany.AGGRESSIVE, restored.tendency);
        assertEquals(company.temporaryController, restored.temporaryController);
        assertEquals(KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE, restored.controllerAuthority);
    }

    @Test
    public void legacyCaptainMigrationReturnsExactPlayerReservationAndKeepsNpc() {
        UUID owner = UUID.randomUUID();
        KOMEHiredUnitRecord source = new KOMEHiredUnitRecord();
        source.entity = UUID.randomUUID();
        source.owner = owner;
        source.sourcePlayer = owner;
        source.sourceType = KOMEHiredUnitRecord.SOURCE_PLAYER_RESERVE;
        source.cost = 75;
        source.alliancePair = KOMEAlliance.pairKey("gondor", "rohan");
        source.spawningFaction = "gondor";
        source.unitFaction = "rohan";
        source.benefitSource = "MILITARY_T4_CAPTAIN";
        NBTTagCompound legacyUnit = source.writeToNBT();
        legacyUnit.setBoolean("AllianceCaptain", true);
        legacyUnit.setBoolean("CaptainSuspended", true);
        legacyUnit.setInteger("CaptainPopulationReservation", 50);

        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("AllianceDataSchemaVersion", 2);
        net.minecraft.nbt.NBTTagList units = new net.minecraft.nbt.NBTTagList();
        units.appendTag(legacyUnit);
        root.setTag("HiredUnits", units);
        KOMEPlayerPopulation population = new KOMEPlayerPopulation();
        population.offensiveTotal = 100;
        population.offensiveUsed = 75;
        NBTTagCompound populationNbt = population.writeToNBT();
        populationNbt.setString("Player", owner.toString());
        net.minecraft.nbt.NBTTagList populations = new net.minecraft.nbt.NBTTagList();
        populations.appendTag(populationNbt);
        root.setTag("Populations", populations);

        KOMEWorldData loaded = new KOMEWorldData("test");
        loaded.readFromNBT(root);
        KOMEHiredUnitRecord migrated = loaded.hiredUnits.get(source.entity);
        assertNotNull(migrated);
        assertEquals(25, migrated.cost);
        assertFalse(migrated.legacyAllianceCaptain);
        assertFalse(migrated.legacyCaptainSuspended);
        assertEquals(0, migrated.legacyCaptainPopulationReservation);
        assertEquals("", migrated.benefitSource);
        assertEquals(25, loaded.getPopulation(owner).offensiveUsed);
        NBTTagCompound saved = new NBTTagCompound();
        loaded.writeToNBT(saved);
        String savedText = saved.toString();
        assertFalse(savedText.contains("AllianceCaptain"));
        assertFalse(savedText.contains("CaptainPopulationReservation"));
    }

    @Test
    public void schemaThreeSettingsAndMaximumMilitaryTierPersist() {
        KOMEWorldData source = new KOMEWorldData("test");
        source.allianceDifficulty = KOMEAllianceRequirements.HARD;
        source.waypointRestrictionEnabled = false;
        NBTTagCompound saved = new NBTTagCompound();
        source.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("test");
        loaded.readFromNBT(saved);
        assertEquals(KOMEAllianceRequirements.HARD, loaded.allianceDifficulty);
        assertFalse(loaded.waypointRestrictionEnabled);
        assertEquals(3, KOMEAlliance.maxTier(KOMEAlliance.MILITARY));
        assertEquals("Accepted base", KOMEAllianceBenefits.get(KOMEAlliance.MILITARY, 0).title);
        assertTrue(KOMEAllianceBenefits.get(KOMEAlliance.MILITARY, 3).restriction.indexOf("dormant during peace") >= 0);

        NBTTagCompound legacyAlliance = new NBTTagCompound();
        legacyAlliance.setString("FactionA", "gondor");
        legacyAlliance.setString("FactionB", "rohan");
        legacyAlliance.setInteger("CivilTier", 2);
        legacyAlliance.setInteger("TradeTier", 2);
        legacyAlliance.setInteger("MilitaryTier", 4);
        net.minecraft.nbt.NBTTagList legacyAlliances = new net.minecraft.nbt.NBTTagList();
        legacyAlliances.appendTag(legacyAlliance);
        NBTTagCompound legacyRoot = new NBTTagCompound();
        legacyRoot.setInteger("AllianceDataSchemaVersion", 2);
        legacyRoot.setTag("Alliances", legacyAlliances);
        KOMEWorldData migrated = new KOMEWorldData("test");
        migrated.readFromNBT(legacyRoot);
        assertEquals(3, migrated.getAlliance("gondor", "rohan", false).militaryTier);
    }

    @Test
    public void standardRequirementMatrixAndDifficultyScalingAreExact() {
        KOMEWorldData data = new KOMEWorldData("test");
        assertEquals(4, data.getAllianceItemStackEquivalents(KOMEAlliance.CIVIL, 1));
        assertEquals(6, data.getAllianceItemStackEquivalents(KOMEAlliance.CIVIL, 2));
        assertEquals(8, data.getAllianceItemStackEquivalents(KOMEAlliance.TRADE, 1));
        assertEquals(12, data.getAllianceItemStackEquivalents(KOMEAlliance.TRADE, 2));
        assertEquals(6, data.getAllianceItemStackEquivalents(KOMEAlliance.MILITARY, 1));
        assertEquals(12, data.getAllianceItemStackEquivalents(KOMEAlliance.MILITARY, 2));
        assertEquals(20, data.getAllianceItemStackEquivalents(KOMEAlliance.MILITARY, 3));
        assertEquals(100, data.getAllianceActivityRequirement(KOMEAlliance.CIVIL, 2));
        assertEquals(50, data.getAllianceActivityRequirement(KOMEAlliance.TRADE, 1));
        assertEquals(1000, data.getAllianceActivityRequirement(KOMEAlliance.MILITARY, 3));
        assertEquals(300, data.getAlliancePopulationRequirement(KOMEAlliance.MILITARY, 3));
        assertEquals(384, KOMEAllianceQuotaPool.weightedQuantityForTest(6, 1, KOMEAllianceRequirements.STANDARD));
        assertEquals(48, KOMEAllianceQuotaPool.weightedQuantityForTest(6, 8, KOMEAllianceRequirements.STANDARD));
        assertEquals(24, KOMEAllianceQuotaPool.weightedQuantityForTest(6, 16, KOMEAllianceRequirements.STANDARD));
        assertEquals(12, KOMEAllianceQuotaPool.weightedQuantityForTest(6, 32, KOMEAllianceRequirements.STANDARD));
        assertEquals(6, KOMEAllianceQuotaPool.weightedQuantityForTest(6, 64, KOMEAllianceRequirements.STANDARD));
        assertEquals(250, KOMEAllianceQuotaPool.weightedQuantityForTest(6, 1, KOMEAllianceRequirements.EASY));
        assertEquals(576, KOMEAllianceQuotaPool.weightedQuantityForTest(6, 1, KOMEAllianceRequirements.HARD));
        data.allianceDifficulty = KOMEAllianceRequirements.EASY;
        assertEquals(65, data.getAllianceActivityRequirement(KOMEAlliance.CIVIL, 2));
        assertEquals(300, data.getAlliancePopulationRequirement(KOMEAlliance.MILITARY, 3));
    }

    @Test
    public void graceDurationUnitsAreExactAndRejectInvalidValues() {
        assertEquals(30_000L, KOMEAllianceRequirements.parseDurationMillis("30s"));
        assertEquals(600_000L, KOMEAllianceRequirements.parseDurationMillis("10m"));
        assertEquals(43_200_000L, KOMEAllianceRequirements.parseDurationMillis("12h"));
        assertEquals(1_209_600_000L, KOMEAllianceRequirements.parseDurationMillis("14d"));
        try {
            KOMEAllianceRequirements.parseDurationMillis("0m");
            fail("Zero duration must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    @Test
    public void configurationDoesNotRevokeCompletionAndKillCounterIsCumulative() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 0L, false);
        KOMEAllianceFactionLedger side = alliance.getFactionLedger("gondor");
        side.setCompletedTier(KOMEAlliance.MILITARY, 1);
        alliance.setDelivered("gondor", KOMEAllianceProgressionService.ELIGIBLE_KILLS, 500);
        data.setAllianceRequirement(KOMEAlliance.MILITARY, 1, "activity", 900);
        data.setAllianceRequirement(KOMEAlliance.MILITARY, 1, "population", 900);
        assertEquals(1, side.getCompletedTier(KOMEAlliance.MILITARY));
        assertEquals(500, alliance.getDelivered("gondor", KOMEAllianceProgressionService.ELIGIBLE_KILLS));
        alliance.addDelivered("gondor", KOMEAllianceProgressionService.ELIGIBLE_KILLS, 500);
        assertEquals(1000, alliance.getDelivered("gondor", KOMEAllianceProgressionService.ELIGIBLE_KILLS));
    }

    @Test
    public void farmerFlagMigrationIsIdempotentAndRealFarmhandRecordSurvives() {
        UUID owner = UUID.randomUUID();
        String postId = "TP-LEGACY";
        NBTTagCompound postNbt = legacyTradePost(postId, "gondor", "rohan");
        postNbt.setBoolean("FarmerReserved", true);

        KOMEHiredUnitRecord farmhand = new KOMEHiredUnitRecord();
        farmhand.entity = UUID.randomUUID();
        farmhand.owner = owner;
        farmhand.sourcePlayer = owner;
        farmhand.farmhand = true;
        farmhand.cost = 1;

        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("AllianceDataSchemaVersion", 2);
        net.minecraft.nbt.NBTTagList posts = new net.minecraft.nbt.NBTTagList();
        posts.appendTag(postNbt);
        root.setTag("AllianceTradePosts", posts);
        net.minecraft.nbt.NBTTagList units = new net.minecraft.nbt.NBTTagList();
        units.appendTag(farmhand.writeToNBT());
        root.setTag("HiredUnits", units);

        KOMEWorldData migrated = new KOMEWorldData("test");
        migrated.readFromNBT(root);
        assertTrue(migrated.recoveredLegacyTradePostIds.contains(postId));
        assertTrue(migrated.hiredUnits.get(farmhand.entity).farmhand);
        assertEquals(1, migrated.hiredUnits.get(farmhand.entity).cost);
        assertEquals(1, migrated.getFarmhandsUsed(owner));

        NBTTagCompound saved = new NBTTagCompound();
        migrated.writeToNBT(saved);
        assertFalse(saved.toString().contains("FarmerReserved"));
        assertFalse(saved.hasKey("AllianceTradePosts"));
        KOMEWorldData reloaded = new KOMEWorldData("test");
        reloaded.readFromNBT(saved);
        assertEquals(1, reloaded.getFarmhandsUsed(owner));
        assertEquals(1, reloaded.hiredUnits.get(farmhand.entity).cost);
    }

    @Test
    public void waypointGraceAndRequirementConfigurationSurviveRestart() {
        UUID player = UUID.randomUUID();
        KOMEWorldData source = new KOMEWorldData("test");
        source.allianceDifficulty = KOMEAllianceRequirements.EASY;
        source.waypointRestrictionEnabled = false;
        source.setWaypointRestrictionBypass(player, true);
        source.successionGraceDefaultMillis = 60_000L;
        source.contributionGraceDefaultMillis = 30_000L;
        source.setAllianceRequirement(KOMEAlliance.MILITARY, 2, "population", 222);
        source.recordAllianceAdminAction("tester", "persistence check");
        NBTTagCompound saved = new NBTTagCompound();
        source.writeToNBT(saved);

        KOMEWorldData loaded = new KOMEWorldData("test");
        loaded.readFromNBT(saved);
        assertEquals(KOMEAllianceRequirements.EASY, loaded.allianceDifficulty);
        assertFalse(loaded.waypointRestrictionEnabled);
        assertTrue(loaded.hasWaypointRestrictionBypass(player));
        assertEquals(60_000L, loaded.successionGraceDefaultMillis);
        assertEquals(30_000L, loaded.contributionGraceDefaultMillis);
        assertEquals(222, loaded.getAlliancePopulationRequirement(KOMEAlliance.MILITARY, 2));
        assertEquals(1, loaded.allianceAdminAudit.size());
    }

    @Test
    public void viewerScopedContractCarriesUnifiedStageAndConfigurationFields() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 0L, false);
        java.util.List lines = KOMEAllianceRecordBuilder.build(data, null);
        boolean config = false;
        boolean requirement = false;
        int relationships = 0;
        for (Object value : lines) {
            String line = String.valueOf(value);
            String[] parts = line.split("\\t", -1);
            config |= parts.length >= 7 && "CONFIG".equals(parts[0]);
            requirement |= parts.length >= 6 && "REQUIREMENT".equals(parts[0]);
            if (parts.length >= 30 && "STAGE_RELATION".equals(parts[0])) {
                relationships++;
                assertEquals("active", parts[10]);
                assertEquals("Neutral", parts[13]);
            }
            assertFalse("TRACK".equals(parts[0]));
            assertFalse("ALLIANCE".equals(parts[0]));
            assertFalse(line.contains("FarmerReserved"));
        }
        assertTrue(config);
        assertTrue(requirement);
        assertEquals(1, relationships);
    }

    @Test
    public void ledgerSummaryUsesTheSelectedContributionSideInsteadOfCanonicalPairOrder() {
        KOMEAlliance alliance = new KOMEAlliance("angmar", "bree");
        String[] parts = KOMEAllianceInventory.buildSummaryLine(alliance, "bree").split("\\t", -1);

        assertEquals("SUMMARY", parts[0]);
        assertEquals("bree", parts[3]);
        assertEquals("angmar", parts[4]);
        assertEquals(alliance.getPairKey(), parts[5]);
    }

    @Test
    public void normalAllianceRequestsAlwaysRespectOriginalFactionRelations() {
        KOMEAllianceAuthority.Decision enemyCivil = KOMEAllianceAuthority.decideRequestAlliance(
            KOMEAlliance.CIVIL, "dunedain", "mordor", "dunedain", true, true, true,
            lotr.common.fac.LOTRFactionRelations.Relation.ENEMY);
        KOMEAllianceAuthority.Decision neutralCivil = KOMEAllianceAuthority.decideRequestAlliance(
            KOMEAlliance.CIVIL, "dunedain", "bree", "dunedain", true, true, true,
            lotr.common.fac.LOTRFactionRelations.Relation.NEUTRAL);
        KOMEAllianceAuthority.Decision friendTrade = KOMEAllianceAuthority.decideRequestAlliance(
            KOMEAlliance.TRADE, "dunedain", "bree", "dunedain", true, true, false,
            lotr.common.fac.LOTRFactionRelations.Relation.FRIEND);
        KOMEAllianceAuthority.Decision friendMilitary = KOMEAllianceAuthority.decideRequestAlliance(
            KOMEAlliance.MILITARY, "dunedain", "bree", "dunedain", true, true, false,
            lotr.common.fac.LOTRFactionRelations.Relation.FRIEND);
        KOMEAllianceAuthority.Decision nonKing = KOMEAllianceAuthority.decideRequestAlliance(
            KOMEAlliance.CIVIL, "dunedain", "bree", "dunedain", false, true, true,
            lotr.common.fac.LOTRFactionRelations.Relation.ALLY);

        assertTrue(enemyCivil.allowed);
        assertFalse(enemyCivil.automaticAcceptance);
        assertTrue(neutralCivil.allowed);
        assertFalse(neutralCivil.automaticAcceptance);
        assertTrue(friendTrade.allowed);
        assertTrue(friendTrade.automaticAcceptance);
        assertTrue(friendMilitary.allowed);
        assertTrue(friendMilitary.automaticAcceptance);
        assertEquals(2, friendMilitary.automaticStage);
        assertFalse(nonKing.allowed);
    }

    @Test
    public void oneKingRequestsUseLoreCapsWhileTwoKingsMayNegotiateAcrossHostility() {
        lotr.common.fac.LOTRFactionRelations.Relation enemy = lotr.common.fac.LOTRFactionRelations.Relation.ENEMY;
        lotr.common.fac.LOTRFactionRelations.Relation neutral = lotr.common.fac.LOTRFactionRelations.Relation.NEUTRAL;
        lotr.common.fac.LOTRFactionRelations.Relation friend = lotr.common.fac.LOTRFactionRelations.Relation.FRIEND;
        lotr.common.fac.LOTRFactionRelations.Relation ally = lotr.common.fac.LOTRFactionRelations.Relation.ALLY;

        assertTrue(KOMEAllianceAuthority.decideRequestAlliance(KOMEAlliance.MILITARY, "gondor", "mordor",
            "gondor", true, true, true, enemy).allowed);
        assertFalse(KOMEAllianceAuthority.decideRequestAlliance(KOMEAlliance.CIVIL, "gondor", "mordor",
            "gondor", true, true, false, enemy).allowed);
        assertTrue(KOMEAllianceAuthority.decideRequestAlliance(KOMEAlliance.CIVIL, "gondor", "bree",
            "gondor", true, true, false, neutral).automaticAcceptance);
        assertTrue(KOMEAllianceAuthority.decideRequestAlliance(KOMEAlliance.TRADE, "gondor", "bree",
            "gondor", true, true, false, friend).automaticAcceptance);
        assertEquals(2, KOMEAllianceAuthority.decideRequestAlliance(KOMEAlliance.MILITARY, "gondor", "bree",
            "gondor", true, true, false, friend).automaticStage);
        assertTrue(KOMEAllianceAuthority.decideRequestAlliance(KOMEAlliance.MILITARY, "gondor", "bree",
            "gondor", true, true, false, ally).automaticAcceptance);
        assertFalse(KOMEAllianceAuthority.decideRequestAlliance(KOMEAlliance.CIVIL, "gondor", "bree",
            "gondor", false, false, false, neutral).allowed);
    }

    @Test
    public void acceptanceIsRestrictedToThePendingReceiverKing() {
        assertTrue(KOMEAllianceAuthority.decideAcceptAlliance(true, "rohan", "rohan", true).allowed);
        assertFalse(KOMEAllianceAuthority.decideAcceptAlliance(true, "rohan", "gondor", true).allowed);
        assertFalse(KOMEAllianceAuthority.decideAcceptAlliance(true, "rohan", "rohan", false).allowed);
        assertFalse(KOMEAllianceAuthority.decideAcceptAlliance(false, "rohan", "rohan", true).allowed);
    }

    @Test
    public void quotaMaximumsExcludeCandidatesAndCanFailCleanly() {
        assertEquals(2, KOMEAllianceQuotaPool.eligibleQuantityCountForTest(20,
            KOMEAllianceRequirements.STANDARD, new int[] {8, 16, 32}, new int[] {100, 80, 40},
            new boolean[] {true, true, true}));
        assertEquals(0, KOMEAllianceQuotaPool.eligibleQuantityCountForTest(20,
            KOMEAllianceRequirements.HARD, new int[] {16, 32, 64}, new int[] {32, 16, 8},
            new boolean[] {true, true, true}));
        assertEquals(0, KOMEAllianceQuotaPool.eligibleQuantityCountForTest(4,
            KOMEAllianceRequirements.STANDARD, new int[] {64}, new int[] {64}, new boolean[] {false}));
    }

    @Test
    public void quotaItemOverridesPersistAndInvalidRollRepairPreservesDeposits() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        String token = "minecraft:iron_sword";
        KOMEAllianceQuotaPool.EntryView entry = KOMEAllianceQuotaPool.findEntry(data, token);
        assertNotNull(entry);
        data.setAllianceQuotaWeight(entry.key, 16);
        data.setAllianceQuotaMaximum(entry.key, 1);
        data.setAllianceQuotaItemEnabled(entry.key, true);
        String id = KOMEAllianceQuotaPool.assignmentId(KOMEAlliance.CIVIL, 1);
        alliance.setDelivered("gondor", id, 5);
        assertEquals(KOMEAllianceQuotaPool.INVALID_REQUIREMENT,
            KOMEAllianceQuotaPool.validateQuantityForTest(24, 16, 1, true).state);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("test");
        loaded.readFromNBT(saved);
        KOMEAllianceQuotaPool.EntryView restoredEntry = KOMEAllianceQuotaPool.findEntry(loaded, token);
        assertEquals(16, restoredEntry.effortWeight);
        assertEquals(1, restoredEntry.maximumQuantity);
        KOMEAlliance restored = loaded.getAlliance("gondor", "rohan", false);
        assertTrue(KOMEAllianceQuotaPool.recoverDeliveredGoodsForTest(restored, "gondor", id,
            new ItemStack(new net.minecraft.item.Item(), 1)));
        assertEquals(0, restored.getDelivered("gondor", id));
        KOMEAllianceFactionLedger ledger = restored.getFactionLedger("gondor");
        int recovered = 0;
        while (ledger.getRecoveryStackCount() > 0) {
            recovered += ledger.removeRecoveryStack(0).stackSize;
        }
        assertEquals(5, recovered);
    }

    @Test
    public void failedQuotaRepairLeavesAssignmentAndDeliveredGoodsUntouched() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        for (String token : new String[] {"minecraft:bread", "minecraft:cooked_beef", "minecraft:carrot", "minecraft:baked_potato"}) {
            KOMEAllianceQuotaPool.EntryView entry = KOMEAllianceQuotaPool.findEntry(data, token);
            assertNotNull(entry);
            data.setAllianceQuotaItemEnabled(entry.key, false);
        }
        String id = KOMEAllianceQuotaPool.assignmentId(KOMEAlliance.CIVIL, 1);
        String assignment = "REQ3|missing|item|0|7|1|items|Missing|INVALID_REQUIREMENT|1|test|missing";
        alliance.setAssignment("gondor", id, assignment);
        alliance.setDelivered("gondor", id, 7);
        assertNull(KOMEAllianceQuotaPool.reroll(data, alliance, KOMEAlliance.CIVIL, 1, "gondor", 12));
        assertEquals(assignment, alliance.getAssignment("gondor", id));
        assertEquals(7, alliance.getDelivered("gondor", id));
        assertEquals(0, alliance.getFactionLedger("gondor").getRecoveryStackCount());
    }

    @Test
    public void deliveredTradeGoodsAlreadyClaimableAreNotDuplicatedDuringRepair() {
        KOMEAlliance alliance = new KOMEAlliance("gondor", "rohan");
        String id = KOMEAllianceQuotaPool.assignmentId(KOMEAlliance.TRADE, 1);
        ItemStack sample = new ItemStack(new net.minecraft.item.Item(), 1);
        alliance.setDelivered("gondor", id, 5);
        alliance.addClaimGoods("gondor", id, sample, 5);
        assertTrue(KOMEAllianceQuotaPool.recoverDeliveredGoodsForTest(alliance, "gondor", id, sample));
        assertEquals(0, alliance.getDelivered("gondor", id));
        assertEquals(5, alliance.getClaimAmount("gondor", id));
        assertEquals(0, alliance.getFactionLedger("gondor").getRecoveryStackCount());
    }

    @Test
    public void benefitAuthorityGatesEveryEstablishedTier() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        KOMEAllianceAuthority authority = new KOMEAllianceAuthority(data);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        assertTrue(authority.canFactionUseAlliedWaypoint("gondor", "rohan"));
        assertFalse(authority.canFactionHireAlliedFarmhand("gondor", "rohan"));
        alliance.setFactionStage("gondor", 1, "test", 0L, 1L);
        assertTrue(authority.canFactionUseAlliedWaypoint("gondor", "rohan"));
        assertTrue(authority.canFactionHireAlliedFarmhand("gondor", "rohan"));
        alliance.setFactionStage("gondor", 2, "test", 0L, 2L);
        assertTrue(alliance.hasProduceMerchantSlot("gondor"));
        assertFalse(authority.canFactionUseMilitaryPassage("gondor", "rohan"));
        alliance.setFactionStage("gondor", 3, "test", 0L, 3L);
        assertTrue(authority.canFactionUseMilitaryPassage("gondor", "rohan"));
        assertFalse(authority.canFactionUseMilitaryPassage("rohan", "gondor"));
        assertFalse(authority.canTemporarilyCommand("gondor", "rohan"));
        alliance.setFactionStage("rohan", 4, "test", 0L, 4L);
        data.claimFactionKing("gondor", "Gondor", UUID.randomUUID(), "King");
        assertTrue(authority.canTemporarilyCommand("gondor", "rohan"));
        assertTrue(authority.canFactionUseAlliedWaypoint("gondor", "mordor"));
    }

    @Test
    public void militaryTierThreeTemporaryCommandWhitelistIsExplicitAndClosed() {
        for (String action : new String[] {"view", "dispatch", "continue", "halt", "stay", "retreat", "resume"}) {
            assertTrue(action, KOMEAllianceTemporaryCommandPolicy.allows(action));
        }
        for (String action : new String[] {"transfer", "redelegate", "delegate", "disband", "rename", "split",
                "merge", "add units", "remove units", "change membership", "change unit caps",
                "consume population", "return population", "change faction", "change funding source",
                "change engagement tendency", "owner-only administration"}) {
            assertFalse(action, KOMEAllianceTemporaryCommandPolicy.allows(action));
        }
    }

    @Test
    public void legacyTradePostInventoryMigratesOnceIntoOperatingFactionRecoveryLedger() {
        KOMELegacyTradePostRecord post = new KOMELegacyTradePostRecord();
        post.id = "TP-RECOVERY";
        post.operatingFaction = "gondor";
        post.hostFaction = "rohan";
        ItemStack stored = new ItemStack(new net.minecraft.item.Item(), 17);
        post.outputs[0] = stored;
        KOMEAllianceFactionLedger directRecovery = new KOMEAllianceFactionLedger("gondor");
        KOMEWorldData.ItemRecovery recovery = KOMEWorldData.recoverTradePostStacks(post, directRecovery);
        assertEquals(1, recovery.stacks);
        assertEquals(17, recovery.items);
        assertEquals(1, directRecovery.getRecoveryStackCount());
        assertEquals(17, directRecovery.removeRecoveryStack(0).stackSize);

        // Plain unit tests do not bootstrap the Forge item registry, so verify the serialized
        // migration marker/idempotence independently from the direct inventory recovery above.
        post.outputs[0] = null;
        net.minecraft.nbt.NBTTagList posts = new net.minecraft.nbt.NBTTagList();
        posts.appendTag(legacyTradePost(post.id, post.operatingFaction, post.hostFaction));
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("AllianceDataSchemaVersion", 4);
        root.setTag("AllianceTradePosts", posts);
        KOMEWorldData migrated = new KOMEWorldData("test");
        migrated.readFromNBT(root);
        KOMEAlliance alliance = migrated.getAlliance("gondor", "rohan", false);
        assertNotNull(alliance);
        assertTrue(migrated.recoveredLegacyTradePostIds.contains(post.id));
        assertEquals(0, alliance.getFactionLedger("gondor").getRecoveryStackCount());
        NBTTagCompound saved = new NBTTagCompound();
        migrated.writeToNBT(saved);
        KOMEWorldData twice = new KOMEWorldData("test");
        twice.readFromNBT(saved);
        assertTrue(twice.recoveredLegacyTradePostIds.contains(post.id));
        assertFalse(saved.hasKey("AllianceTradePosts"));
    }

    @Test
    public void viewerTracksRemainSideSpecificAndOmitAbsentMilitary() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.setFactionStage("gondor", 3, "test", 0L, 10L);
        alliance.setFactionStage("rohan", 1, "test", 0L, 11L);
        int stageRecords = 0;
        for (Object value : KOMEAllianceRecordBuilder.build(data, null)) {
            String line = String.valueOf(value);
            assertFalse(line.contains("military.t4"));
            String[] parts = line.split("\\t", -1);
            if (parts.length < 27 || !"STAGE_RELATION".equals(parts[0])) continue;
            stageRecords++;
            assertEquals("gondor", parts[6]);
            assertEquals("rohan", parts[7]);
            assertEquals("3", parts[8]);
            assertEquals("1", parts[9]);
            assertEquals("Neutral", parts[13]);
        }
        assertEquals(1, stageRecords);
    }

    @Test
    public void operatorRecordVisibilityRequiresExplicitOptIn() {
        assertFalse(KOMEAllianceRecordBuilder.resolveOperatorView(true, false));
        assertFalse(KOMEAllianceRecordBuilder.resolveOperatorView(false, true));
        assertTrue(KOMEAllianceRecordBuilder.resolveOperatorView(true, true));
        assertTrue(KOMEAllianceRecordBuilder.includeViewerRecord(false, true));
        assertFalse(KOMEAllianceRecordBuilder.includeViewerRecord(false, false));
        assertTrue(KOMEAllianceRecordBuilder.includeViewerRecord(true, false));
    }

    @Test
    public void coalitionWarHasNeutralMultiFactionSidesAndPersistsWithoutDuplicateOpposition() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEWar war = KOMEWarService.createWar(data, "gondor", "mordor", "War of the East", "tester", 100L);
        assertNotNull(war);
        assertEquals("Side One", war.sideOneName);
        assertEquals("Side Two", war.sideTwoName);
        assertTrue(war.addFaction(1, "rohan"));
        assertTrue(war.addFaction(2, "rhudel"));
        assertFalse(war.addFaction(2, "rohan"));
        assertSame(war, KOMEWarService.createWar(data, "rohan", "rhudel", "duplicate", "tester", 200L));
        KOMEWarService.recordHostileCapture(data, "T100", "rhudel", "rohan", UUID.randomUUID(), "Rider", 300L, "PLAYER_CONQUEST");
        assertEquals(1, war.tileCaptureHistory.size());
        assertEquals("T100", war.tileCaptureHistory.get(0).tileId);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("test");
        loaded.readFromNBT(saved);
        KOMEWar restored = loaded.wars.get(war.id);
        assertNotNull(restored);
        assertEquals(2, restored.sideOneFactions.size());
        assertEquals(2, restored.sideTwoFactions.size());
        assertEquals(1, restored.tileCaptureHistory.size());
    }

    @Test
    public void alliedClaimRequiresFreshSecondServerConfirmationAndBreaksDirectTracks() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 0L, false);
        KOMEConquestTile tile = new KOMEConquestTile("T200");
        tile.claim("rohan", 0L);
        data.conquestTiles.put(tile.id, tile);
        UUID claimant = UUID.randomUUID();
        KOMEConquestClaimService.Result first = KOMEConquestClaimService.claim(data, tile, "gondor", claimant,
            "Tester", 10L, 1_000L);
        assertFalse(first.success);
        assertTrue(first.confirmationRequired);
        assertEquals("rohan", tile.currentRulingFaction());
        KOMEConquestClaimService.Result second = KOMEConquestClaimService.claim(data, tile, "gondor", claimant,
            "Tester", 11L, 1_001L);
        assertTrue(second.success);
        assertTrue(second.allianceBroken);
        assertEquals("gondor", tile.currentRulingFaction());
        assertNotNull(second.war);
        assertTrue(second.war.opposes("gondor", "rohan"));
        assertFalse(alliance.hasAnyAlliance());
    }

    @Test
    public void alliedClaimConfirmationExpiresAndOwnerMutationInvalidatesIt() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.getAlliance("gondor", "rohan", true).requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        KOMEConquestTile tile = new KOMEConquestTile("T201");
        tile.claim("rohan", 0L);
        UUID claimant = UUID.randomUUID();
        assertTrue(KOMEConquestClaimService.claim(data, tile, "gondor", claimant, "Tester", 0L, 1_000L).confirmationRequired);
        KOMEConquestClaimService.Result expired = KOMEConquestClaimService.claim(data, tile, "gondor", claimant,
            "Tester", 0L, 1_000L + KOMEWarService.CLAIM_CONFIRMATION_MILLIS + 1L);
        assertTrue(expired.staleConfirmation);
        assertEquals("rohan", tile.currentRulingFaction());

        KOMEConquestClaimService.claim(data, tile, "gondor", claimant, "Tester", 0L, 2_000L);
        tile.claim("mordor", 1L);
        KOMEConquestClaimService.Result changed = KOMEConquestClaimService.claim(data, tile, "gondor", claimant,
            "Tester", 1L, 2_001L);
        assertFalse(changed.success);
        assertEquals("mordor", tile.currentRulingFaction());
    }

    @Test
    public void wartimeStewardshipIsDormantInPeaceAndTargetsOnlyOpposingSide() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 0L, false);
        alliance.setTier(KOMEAlliance.MILITARY, 3, "test", 0L);
        assertFalse(KOMEWartimeStewardshipService.isAuthorized(data, "rohan", "gondor"));
        KOMEWar war = KOMEWarService.createWar(data, "gondor", "rhudel", "", "tester", 100L);
        assertTrue(war.sameSide("gondor", "rohan"));
        assertTrue(KOMEWartimeStewardshipService.isAuthorized(data, "rohan", "gondor"));
        assertTrue(KOMEWarService.authorizedOpponents(data, "rohan", "gondor").contains("rhudel"));
        assertFalse(KOMEWarService.authorizedOpponents(data, "rohan", "gondor").contains("mordor"));
        war.status = KOMEWar.ENDING;
        assertFalse(KOMEWartimeStewardshipService.isAuthorized(data, "rohan", "gondor"));
    }

    @Test
    public void wholeCompanyReserveTransferIsAtomicAndPreservesNativeFaction() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID owner = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        data.getPopulation(owner).offensiveTotal = 50;
        data.getPopulation(owner).offensiveUsed = 25;
        data.getPopulation(recipient).offensiveTotal = 50;
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID(); unit.owner = owner; unit.sourcePlayer = owner;
        unit.sourceType = KOMEHiredUnitRecord.SOURCE_PLAYER_RESERVE; unit.sourceFaction = "gondor";
        unit.unitFaction = "gondor"; unit.type = KOMEPopulationType.OFFENSIVE; unit.cost = 25;
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "transfer-test"; company.owner = owner; company.ownerName = "Old"; company.faction = "gondor";
        company.nativeFaction = "gondor"; company.units.add(unit.entity); unit.companyId = company.id;
        data.hiredUnits.put(unit.entity, unit); data.armyCompanies.put(company.id, company);
        assertTrue(KOMECompanyTransferService.offer(data, company, owner, recipient, "New", 100L).success);
        assertTrue(KOMECompanyTransferService.accept(data, company, recipient, "New", 101L).success);
        assertEquals(recipient, company.owner);
        assertEquals(recipient, unit.owner);
        assertEquals("gondor", company.nativeFaction);
        assertEquals("gondor", unit.sourceFaction);
        assertEquals(0, data.getPopulation(owner).offensiveUsed);
        assertEquals(25, data.getPopulation(recipient).offensiveUsed);
    }

    @Test
    public void pledgeReleaseTombstoneAndAuditStatePersistIdempotently() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID player = UUID.randomUUID();
        KOMEPledgeReleaseTombstone tombstone = new KOMEPledgeReleaseTombstone();
        tombstone.unitUuid = UUID.randomUUID(); tombstone.formerOwner = player; tombstone.formerFaction = "gondor";
        tombstone.fundingSource = KOMEHiredUnitRecord.SOURCE_PLAYER_RESERVE; tombstone.populationAmount = 25;
        tombstone.populationReturned = true; tombstone.entityRemoved = false; tombstone.createdTimestamp = 100L;
        data.pledgeReleaseTombstones.put(tombstone.unitUuid, tombstone);
        data.lastKnownPlayerFactions.put(player, "gondor");
        data.pledgeReleaseAudit.add("test release");
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("test");
        loaded.readFromNBT(saved);
        KOMEPledgeReleaseTombstone restored = loaded.pledgeReleaseTombstones.get(tombstone.unitUuid);
        assertNotNull(restored);
        assertTrue(restored.populationReturned);
        assertFalse(restored.entityRemoved);
        assertFalse(restored.complete());
        assertEquals("gondor", loaded.lastKnownPlayerFactions.get(player));
        assertEquals(1, loaded.pledgeReleaseAudit.size());
    }

    @Test
    public void warSidesSupportAddMoveRemoveAndEndingPersistence() {
        KOMEWar war = new KOMEWar();
        war.id = "war-test";
        war.sideOneFactions.add("gondor");
        war.sideTwoFactions.add("mordor");
        assertTrue(war.addFaction(1, "rohan"));
        assertTrue(war.addFaction(2, "rhudel"));
        assertFalse(war.addFaction(2, "rohan"));
        assertTrue(war.moveFaction("rhudel", 1));
        assertEquals(1, war.sideOf("rhudel"));
        assertTrue(war.removeFaction("rhudel"));
        assertEquals(0, war.sideOf("rhudel"));
        war.status = KOMEWar.ENDING;
        war.endingAtMillis = 500L;
        war.endingReason = "withdrawal";
        KOMEWar restored = new KOMEWar();
        restored.readFromNBT(war.writeToNBT());
        assertEquals(KOMEWar.ENDING, restored.status);
        assertEquals(500L, restored.endingAtMillis);
        assertEquals("withdrawal", restored.endingReason);
    }

    @Test
    public void unclaimedAndAdministrativeClaimsDoNotCreateWars() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile unclaimed = new KOMEConquestTile("T300");
        KOMEConquestClaimService.Result result = KOMEConquestClaimService.claim(data, unclaimed, "gondor",
            UUID.randomUUID(), "Tester", 10L, 100L);
        assertTrue(result.success);
        assertNull(result.war);
        assertTrue(data.wars.isEmpty());

        KOMEConquestTile transferred = new KOMEConquestTile("T301");
        transferred.claim("rohan", 0L);
        data.claimTile(transferred, "gondor", 20L, UUID.randomUUID(), "Agreed transfer");
        assertTrue(data.wars.isEmpty());
        transferred.claim("", 30L, UUID.randomUUID(), "Correction");
        assertTrue(data.wars.isEmpty());
    }

    @Test
    public void freshWorldInitializesCanonicalConquestMapDefaults() {
        assertFalse(KOMEConquestTileDefaults.getKnownTileIds().isEmpty());
        KOMEWorldData data = new KOMEWorldData("test");
        data.readFromNBT(new NBTTagCompound());
        String first = KOMEConquestTileDefaults.getKnownTileIds().iterator().next();
        assertTrue(data.conquestTiles.containsKey(KOMEConquestTile.normalizeId(first)));
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        assertTrue(saved.getBoolean("ConquestDefaultsInitialized"));
    }

    @Test
    public void stewardshipSupportsMultipleWarsAndRevokesOnSideChangeOrKingReturn() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("rohan", "gondor", true);
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 0L, false);
        alliance.setTier(KOMEAlliance.MILITARY, 3, "test", 0L);
        UUID controller = crown(data, "gondor", "Supporting King");
        KOMEWar east = KOMEWarService.createWar(data, "gondor", "rhudel", "East", "tester", 1L);
        assertTrue(east.addFaction(1, "rohan"));
        KOMEWar south = KOMEWarService.createWar(data, "gondor", "mordor", "South", "tester", 2L);
        assertTrue(south.addFaction(1, "rohan"));

        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "steward-company";
        company.faction = "rohan";
        company.nativeFaction = "rohan";
        company.temporaryController = controller;
        company.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP;
        KOMEWartimeStewardshipService.authorizeCompany(data, company, "gondor", "test", 3L);
        assertEquals(2, company.authorizedWarIds.size());
        assertTrue(KOMEWartimeStewardshipService.authorizedOpponents(data, company).contains("rhudel"));
        assertTrue(KOMEWartimeStewardshipService.authorizedOpponents(data, company).contains("mordor"));
        assertFalse(KOMEWartimeStewardshipService.authorizedOpponents(data, company).contains("angmar"));

        east.removeFaction("rohan");
        KOMEWartimeStewardshipService.revalidateCompany(data, company, 4L, "side changed");
        assertEquals(1, company.authorizedWarIds.size());
        assertTrue(company.authorizedWarIds.contains(south.id));
        data.claimFactionKing("rohan", "Rohan", UUID.randomUUID(), "King");
        assertFalse(KOMEWartimeStewardshipService.revalidateCompany(data, company, 5L, "king returned"));
        assertNull(company.temporaryController);
    }

    @Test
    public void stewardshipMovementUsesNativeIdentityAndOnlyAuthorizedOpponents() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("rohan", "gondor", true);
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 0L, false);
        alliance.setTier(KOMEAlliance.MILITARY, 3, "test", 0L);
        KOMEWar war = KOMEWarService.createWar(data, "gondor", "rhudel", "", "tester", 1L);
        assertTrue(war.sameSide("gondor", "rohan"));
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.faction = "rohan";
        company.nativeFaction = "rohan";
        company.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP;
        company.authorizedWarIds.add(war.id);
        assertTrue(KOMEWartimeStewardshipService.canEnter(data, company, "rohan", false));
        assertTrue(KOMEWartimeStewardshipService.canEnter(data, company, "rhudel", false));
        assertFalse(KOMEWartimeStewardshipService.canEnter(data, company, "mordor", false));
        war.status = KOMEWar.ENDING;
        assertFalse(KOMEWartimeStewardshipService.canEnter(data, company, "rhudel", false));
    }

    @Test
    public void stewardshipReservationsShareOneHundredPercentNativePoolAcrossControllers() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = new KOMEConquestTile("T400");
        tile.claim("rohan", 0L);
        data.conquestTiles.put(tile.id, tile);
        KOMETilePopulation pool = data.getOrCreateTilePopulationPool(tile.id, "rohan");
        pool.offensiveTotal = 100;
        for (int i = 0; i < 2; i++) {
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.entity = UUID.randomUUID();
            record.cost = 30;
            record.sourceType = KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION;
            record.benefitSource = "MILITARY_T3_STEWARDSHIP";
            record.populationOwningFaction = "rohan";
            record.controller = UUID.randomUUID();
            data.hiredUnits.put(record.entity, record);
        }
        pool.offensiveUsed = 60;
        assertEquals(60, data.getKinglessStewardshipReserved("rohan"));
        assertEquals(100, data.getKinglessStewardshipGlobalCap("rohan"));
        assertEquals(40, data.getKinglessStewardshipAvailable("rohan"));
        assertNull(data.findKinglessStewardshipPool("rohan", 41));
        assertSame(pool, data.findKinglessStewardshipPool("rohan", 40));
    }

    @Test
    public void automaticMilitarySupportEnrollmentIsGenericIdempotentAndProvenanced() {
        KOMEWorldData data = new KOMEWorldData("test");
        establishMilitaryT3(data, "native_alpha", "support_beta");
        UUID supportingKing = crown(data, "support_beta", "Supporting King");

        KOMEWar war = KOMEWarService.createWar(data, "native_alpha", "opponent_gamma", "", "test", 1L);
        assertNotNull(war);
        assertEquals(war.sideOf("native_alpha"), war.sideOf("support_beta"));
        KOMEWar.MilitarySupportEnrollment enrollment = war.supportEnrollment("native_alpha", "support_beta", false);
        assertNotNull(enrollment);
        assertEquals("ACTIVE", enrollment.state);
        assertEquals(supportingKing, enrollment.authorizedKing);
        assertEquals(1, activeMembershipCount(war, "support_beta", "AUTOMATIC_MILITARY_T3_SUPPORT"));

        KOMEWarService.reconcileAutomaticMilitarySupport(data, 2L, "restart pass one");
        KOMEWarService.reconcileAutomaticMilitarySupport(data, 3L, "restart pass two");
        assertEquals(1, activeMembershipCount(war, "support_beta", "AUTOMATIC_MILITARY_T3_SUPPORT"));
        assertEquals(3, war.sideOneFactions.size() + war.sideTwoFactions.size());

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData restarted = new KOMEWorldData("test");
        restarted.readFromNBT(saved);
        KOMEWar restored = restarted.wars.get(war.id);
        assertNotNull(restored);
        KOMEWarService.reconcileAutomaticMilitarySupport(restarted, 4L, "cold restart reconciliation");
        assertEquals(1, activeMembershipCount(restored, "support_beta", "AUTOMATIC_MILITARY_T3_SUPPORT"));
        assertEquals(3, restored.sideOneFactions.size() + restored.sideTwoFactions.size());
    }

    @Test
    public void opposingMilitarySupporterIsNeverMovedAndRecordsContradiction() {
        KOMEWorldData data = new KOMEWorldData("test");
        establishMilitaryT3(data, "native_delta", "support_epsilon");
        crown(data, "support_epsilon", "Supporting King");

        KOMEWar war = KOMEWarService.createWar(data, "native_delta", "support_epsilon", "", "test", 1L);
        assertNotNull(war);
        assertTrue(war.opposes("native_delta", "support_epsilon"));
        KOMEWar.MilitarySupportEnrollment enrollment = war.supportEnrollment("native_delta", "support_epsilon", false);
        assertNotNull(enrollment);
        assertEquals("CONTRADICTION", enrollment.state);
        assertTrue(enrollment.reason.contains("opposing side"));
        assertEquals(0, activeMembershipCount(war, "support_epsilon", "AUTOMATIC_MILITARY_T3_SUPPORT"));
        assertFalse(KOMEWarService.supportingKingDecision(data, "native_delta", "support_epsilon",
            data.getFactionKingId("support_epsilon")).allowed);
    }

    @Test
    public void stewardshipIsKingOnlyAndSupportingKingReplacementReauthorizesWithoutChangingCoalition() {
        KOMEWorldData data = new KOMEWorldData("test");
        establishMilitaryT3(data, "native_zeta", "support_eta");
        UUID originalKing = crown(data, "support_eta", "First King");
        UUID ordinaryMember = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(ordinaryMember, "support_eta");
        KOMEWar war = KOMEWarService.createWar(data, "native_zeta", "opponent_theta", "", "test", 1L);

        assertFalse(KOMEWarService.supportingKingDecision(data, "native_zeta", "support_eta", ordinaryMember).allowed);
        assertTrue(KOMEWarService.supportingKingDecision(data, "native_zeta", "support_eta", originalKing).allowed);

        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "replacement-company";
        company.faction = "native_zeta";
        company.nativeFaction = "native_zeta";
        company.temporaryController = originalKing;
        company.temporaryControllerName = "First King";
        company.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP;
        company.stewardshipCreated = true;
        data.armyCompanies.put(company.id, company);
        KOMEWartimeStewardshipService.authorizeCompany(data, company, "support_eta", "test", 2L);
        assertTrue(company.authorizedWarIds.contains(war.id));

        data.lastKnownPlayerFactions.put(originalKing, "");
        data.reconcilePlayerKingship("", originalKing, "First King", false);
        assertNull(company.temporaryController);
        assertEquals(KOMEArmyCompany.AUTHORITY_STEWARDSHIP, company.controllerAuthority);
        assertEquals(war.sideOf("native_zeta"), war.sideOf("support_eta"));
        assertEquals("DORMANT", war.supportEnrollment("native_zeta", "support_eta", false).state);

        UUID replacementKing = crown(data, "support_eta", "Second King");
        assertEquals(replacementKing, company.temporaryController);
        assertTrue(KOMEWarService.supportingKingDecision(data, "native_zeta", "support_eta", replacementKing).allowed);
        assertEquals("ACTIVE", war.supportEnrollment("native_zeta", "support_eta", false).state);
    }

    @Test
    public void nativeKingReturnRevokesKinglessControlButPreservesWarMembershipAndNativeCompany() {
        KOMEWorldData data = new KOMEWorldData("test");
        establishMilitaryT3(data, "native_iota", "support_kappa");
        UUID supportingKing = crown(data, "support_kappa", "Supporting King");
        KOMEWar war = KOMEWarService.createWar(data, "native_iota", "opponent_lambda", "", "test", 1L);
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "native-return-company";
        company.owner = UUID.randomUUID();
        company.faction = "native_iota";
        company.nativeFaction = "native_iota";
        company.temporaryController = supportingKing;
        company.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP;
        company.stewardshipCreated = false;
        data.armyCompanies.put(company.id, company);
        KOMEWartimeStewardshipService.authorizeCompany(data, company, "support_kappa", "test", 2L);

        crown(data, "native_iota", "Native King");
        assertNull(company.temporaryController);
        assertTrue(data.armyCompanies.containsKey(company.id));
        assertEquals(war.sideOf("native_iota"), war.sideOf("support_kappa"));
        assertEquals("DORMANT", war.supportEnrollment("native_iota", "support_kappa", false).state);
        assertTrue(war.supportEnrollment("native_iota", "support_kappa", false).reason.contains("native faction has"));
        assertFalse(KOMEWarService.supportingKingDecision(data, "native_iota", "support_kappa", supportingKing).allowed);
    }

    @Test
    public void overlappingWarEndRetainsOtherAuthorizationAndOpponentUnion() {
        KOMEWorldData data = new KOMEWorldData("test");
        establishMilitaryT3(data, "native_mu", "support_nu");
        UUID king = crown(data, "support_nu", "Supporting King");
        KOMEWar first = KOMEWarService.createWar(data, "native_mu", "opponent_xi", "First", "test", 1L);
        KOMEWar second = KOMEWarService.createWar(data, "native_mu", "opponent_omicron", "Second", "test", 2L);
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "overlap-company";
        company.faction = "native_mu";
        company.nativeFaction = "native_mu";
        company.temporaryController = king;
        company.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP;
        data.armyCompanies.put(company.id, company);
        KOMEWartimeStewardshipService.authorizeCompany(data, company, "support_nu", "test", 3L);
        assertEquals(2, company.authorizedWarIds.size());
        assertTrue(KOMEWartimeStewardshipService.authorizedOpponents(data, company).contains(
            KOMEAlliance.normalizeFactionKey("opponent_xi")));
        assertTrue(KOMEWartimeStewardshipService.authorizedOpponents(data, company).contains(
            KOMEAlliance.normalizeFactionKey("opponent_omicron")));

        first.status = KOMEWar.ENDING;
        assertTrue(KOMEWartimeStewardshipService.revalidateCompany(data, company, 4L, "first war ending"));
        assertEquals(1, company.authorizedWarIds.size());
        assertTrue(company.authorizedWarIds.contains(second.id));
        assertFalse(KOMEWartimeStewardshipService.authorizedOpponents(data, company).contains(
            KOMEAlliance.normalizeFactionKey("opponent_xi")));
        assertTrue(KOMEWartimeStewardshipService.authorizedOpponents(data, company).contains(
            KOMEAlliance.normalizeFactionKey("opponent_omicron")));
    }

    @Test
    public void voluntaryDelegationRequiresBothRecognizedPledgedMilitaryTierThreeKings() {
        KOMEWorldData data = new KOMEWorldData("test");
        establishMilitaryT3(data, "native_pi", "support_rho");
        UUID nativeKing = crown(data, "native_pi", "Native King");
        UUID supportingKing = crown(data, "support_rho", "Supporting King");
        UUID member = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(member, "support_rho");
        KOMEAllianceAuthority authority = new KOMEAllianceAuthority(data);

        assertTrue(authority.canVoluntarilyDelegate("native_pi", nativeKing, "support_rho", supportingKing).allowed);
        assertFalse(authority.canVoluntarilyDelegate("native_pi", nativeKing, "support_rho", member).allowed);
        assertFalse(authority.canVoluntarilyDelegate("native_pi", UUID.randomUUID(), "support_rho", supportingKing).allowed);
        data.lastKnownPlayerFactions.put(supportingKing, "");
        assertFalse(authority.canVoluntarilyDelegate("native_pi", nativeKing, "support_rho", supportingKing).allowed);
    }

    @Test
    public void reserveTransferRejectsUnprovenFormerFundingWithoutPartialMutation() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID owner = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        data.getPopulation(owner).offensiveTotal = 50;
        data.getPopulation(owner).offensiveUsed = 0;
        data.getPopulation(recipient).offensiveTotal = 50;
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID();
        unit.owner = owner;
        unit.sourcePlayer = owner;
        unit.sourceType = KOMEHiredUnitRecord.SOURCE_PLAYER_RESERVE;
        unit.sourceFaction = "gondor";
        unit.type = KOMEPopulationType.OFFENSIVE;
        unit.cost = 25;
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "bad-funding";
        company.owner = owner;
        company.faction = "gondor";
        company.nativeFaction = "gondor";
        company.units.add(unit.entity);
        unit.companyId = company.id;
        data.hiredUnits.put(unit.entity, unit);
        assertTrue(KOMECompanyTransferService.offer(data, company, owner, recipient, "New", 10L).success);
        assertFalse(KOMECompanyTransferService.accept(data, company, recipient, "New", 11L).success);
        assertEquals(owner, company.owner);
        assertEquals(owner, unit.owner);
        assertEquals(0, data.getPopulation(recipient).offensiveUsed);
    }

    @Test
    public void pledgeReleaseReturnsReserveOnceAndLeavesPersistentUnloadedTombstone() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID player = UUID.randomUUID();
        KOMEPlayerPopulation population = data.getPopulation(player);
        population.offensiveTotal = 50;
        population.offensiveUsed = 25;
        KOMEHiredUnitRecord unit = ownedUnit(player, "gondor", KOMEHiredUnitRecord.SOURCE_PLAYER_RESERVE, 25);
        unit.sourcePlayer = player;
        KOMEArmyCompany company = company("release-company", player, unit);
        data.hiredUnits.put(unit.entity, unit);
        data.armyCompanies.put(company.id, company);
        KOMEPledgeReleaseService.Result first = KOMEPledgeReleaseService.release(data, player, "Rider", "gondor", "",
            100L, "test unpledge");
        assertEquals(1, first.unitsReleased);
        assertEquals(1, first.companiesRemoved);
        assertEquals(25, first.offensiveReturned);
        assertEquals(0, population.offensiveUsed);
        KOMEPledgeReleaseTombstone tombstone = data.pledgeReleaseTombstones.get(unit.entity);
        assertNotNull(tombstone);
        assertTrue(tombstone.populationReturned);
        assertFalse(tombstone.entityRemoved);
        assertFalse(data.hiredUnits.containsKey(unit.entity));

        KOMEPledgeReleaseService.release(data, player, "Rider", "gondor", "", 101L, "idempotence retry");
        assertEquals(0, population.offensiveUsed);
        assertTrue(tombstone.populationReturned);
    }

    @Test
    public void pledgeReleaseCancelsSnapshotsReconcilesAllocationAndQuarantinesUnknownSources() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID player = UUID.randomUUID();
        KOMEConquestTile tile = new KOMEConquestTile("T500");
        tile.claim("gondor", 0L);
        data.conquestTiles.put(tile.id, tile);
        KOMETilePopulation pool = data.getOrCreateTilePopulationPool(tile.id, "gondor");
        pool.offensiveTotal = 50;
        pool.offensiveUsed = 25;
        KOMEPlayerTilePopulationAllocation allocation = data.getOrCreateAllocation(tile.id, "gondor", player, "Rider");
        allocation.offensiveAllocated = 25;
        allocation.offensiveUsed = 25;

        KOMEHiredUnitRecord moving = ownedUnit(player, "gondor", KOMEHiredUnitRecord.SOURCE_TILE_ALLOCATION, 25);
        moving.sourceTileId = tile.id;
        moving.allocationTileId = tile.id;
        moving.allocationFaction = "gondor";
        moving.allocationPlayer = player;
        moving.movementOrderId = "move-release";
        moving.movingEntityData = new NBTTagCompound();
        KOMEArmyCompany company = company("moving-company", player, moving);
        company.status = KOMEArmyCompany.MOVING;
        company.movementOrderId = moving.movementOrderId;
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = moving.movementOrderId;
        order.companyId = company.id;
        order.owner = player;
        order.ownerFaction = "gondor";
        order.status = KOMEArmyMovementOrder.MOVING;
        data.hiredUnits.put(moving.entity, moving);
        data.armyCompanies.put(company.id, company);
        data.armyMovements.put(order.id, order);

        KOMEHiredUnitRecord ambiguous = ownedUnit(player, "gondor", KOMEHiredUnitRecord.SOURCE_OTHER_LEGACY, 10);
        data.hiredUnits.put(ambiguous.entity, ambiguous);
        KOMEPledgeReleaseService.Result result = KOMEPledgeReleaseService.release(data, player, "Rider", "gondor", "rohan",
            200L, "direct switch");
        assertEquals("rohan", result.newFaction);
        assertEquals(1, result.movementsCancelled);
        assertEquals(1, result.snapshotUnitsRemoved);
        assertEquals(0, pool.offensiveUsed);
        assertNull(data.getAllocation(tile.id, "gondor", player));
        assertTrue(data.pledgeReleaseTombstones.get(moving.entity).entityRemoved);
        assertTrue(data.pledgeReleaseTombstones.get(moving.entity).populationReturned);
        assertTrue(data.pledgeReleaseTombstones.get(ambiguous.entity).quarantined);
        assertTrue(data.pledgeReleaseQuarantine.containsKey(ambiguous.entity));
    }

    @Test
    public void departingKingPreservesStagesWithoutGraceAndControllerDepartureDoesNotDeleteNativeForces() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID king = UUID.randomUUID();
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        data.claimFactionKing("gondor", "Gondor", king, "King");
        KOMEPledgeReleaseService.Result kingResult = KOMEPledgeReleaseService.release(data, king, "King", "gondor", "",
            300L, "king unpledged");
        assertTrue(kingResult.wasKing);
        assertFalse(data.hasFactionKing("gondor"));
        assertEquals(0L, alliance.getFactionLedger("gondor").successionEndMillis);
        assertEquals(0, alliance.getFactionStage("gondor"));

        UUID nativeOwner = UUID.randomUUID();
        UUID controller = UUID.randomUUID();
        KOMEHiredUnitRecord nativeUnit = ownedUnit(nativeOwner, "rohan", KOMEHiredUnitRecord.SOURCE_TILE_POOL, 25);
        nativeUnit.sourceTileId = "T600";
        KOMEArmyCompany nativeCompany = company("native-company", nativeOwner, nativeUnit);
        nativeCompany.temporaryController = controller;
        nativeCompany.controllerAuthority = KOMEArmyCompany.AUTHORITY_STEWARDSHIP;
        nativeCompany.stewardshipCreated = false;
        data.hiredUnits.put(nativeUnit.entity, nativeUnit);
        data.armyCompanies.put(nativeCompany.id, nativeCompany);
        KOMEPledgeReleaseService.release(data, controller, "Controller", "gondor", "", 301L, "controller unpledged");
        assertTrue(data.hiredUnits.containsKey(nativeUnit.entity));
        assertTrue(data.armyCompanies.containsKey(nativeCompany.id));
        assertNull(nativeCompany.temporaryController);
    }

    private static KOMEHiredUnitRecord ownedUnit(UUID owner, String faction, String sourceType, int cost) {
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID();
        unit.owner = owner;
        unit.sourcePlayer = owner;
        unit.sourceFaction = faction;
        unit.spawningFaction = faction;
        unit.unitFaction = faction;
        unit.populationOwningFaction = faction;
        unit.sourceType = sourceType;
        unit.type = KOMEPopulationType.OFFENSIVE;
        unit.cost = cost;
        return unit;
    }

    private static KOMEArmyCompany company(String id, UUID owner, KOMEHiredUnitRecord unit) {
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = id;
        company.owner = owner;
        company.faction = unit.unitFaction;
        company.nativeFaction = unit.unitFaction;
        company.units.add(unit.entity);
        unit.companyId = id;
        return company;
    }

    private static KOMEAlliance establishMilitaryT3(KOMEWorldData data, String first, String second) {
        KOMEAlliance alliance = data.getAlliance(first, second, true);
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 0L, false);
        alliance.setTier(KOMEAlliance.MILITARY, 3, "test", 1L);
        return alliance;
    }

    private static UUID crown(KOMEWorldData data, String faction, String name) {
        UUID player = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(player, faction);
        assertTrue(data.claimFactionKing(faction, faction, player, name));
        return player;
    }

    private static int activeMembershipCount(KOMEWar war, String faction, String source) {
        int result = 0;
        String key = KOMEAlliance.normalizeFactionKey(faction);
        for (KOMEWar.MembershipRecord record : war.membershipHistory) {
            if (record.active && key.equals(record.faction) && source.equals(record.source)) result++;
        }
        return result;
    }

    private static NBTTagCompound legacyTradePost(String id, String operatingFaction, String hostFaction) {
        NBTTagCompound post = new NBTTagCompound();
        post.setString("Id", id);
        post.setString("OperatingFaction", operatingFaction);
        post.setString("HostFaction", hostFaction);
        post.setTag("Inputs", new net.minecraft.nbt.NBTTagList());
        post.setTag("Outputs", new net.minecraft.nbt.NBTTagList());
        return post;
    }
}
