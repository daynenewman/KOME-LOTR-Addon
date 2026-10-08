package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/** Strict schema-3 codec for activity, deferred observations and mobilization authority. */
final class KOMEEmergencyDefensePersistence {
    static final String SCHEMA_KEY = "EmergencyDefenseDataSchemaVersion";
    static final String RECORDS_KEY = "EmergencyDefenseActivities";
    static final String COMMITMENTS_KEY = "EmergencyDefenseCommitments";
    static final String OBSERVATIONS_KEY = "EmergencyDefenseObservations";
    static final int DATA_SCHEMA_VERSION = 3;

    static final class Loaded {
        final Map<String, KOMEEmergencyDefenseActivity> activities;
        final Map<String, KOMEEmergencyDefenseCommitment> commitments;
        final Map<String, KOMEEmergencyDefenseObservation> observations;
        Loaded(Map<String, KOMEEmergencyDefenseActivity> activities,
                Map<String, KOMEEmergencyDefenseCommitment> commitments,
                Map<String, KOMEEmergencyDefenseObservation> observations) {
            this.activities = activities;
            this.commitments = commitments;
            this.observations = observations;
        }
    }

    private KOMEEmergencyDefensePersistence() { }

    static NBTTagCompound write(Map<String, KOMEEmergencyDefenseActivity> records,
            Map<String, KOMEEmergencyDefenseCommitment> commitments,
            Map<String, KOMEEmergencyDefenseObservation> observations) {
        if (records == null) throw new IllegalArgumentException("Emergency-defense registry is required.");
        NBTTagCompound result = new NBTTagCompound();
        result.setInteger(SCHEMA_KEY, DATA_SCHEMA_VERSION);
        NBTTagList list = new NBTTagList();
        for (String key : new TreeSet<String>(records.keySet())) {
            KOMEEmergencyDefenseActivity activity = records.get(key);
            if (activity == null || !key.equals(activity.factionId))
                throw new IllegalArgumentException("Emergency-defense registry identity mismatch.");
            NBTTagCompound row = new NBTTagCompound();
            row.setString("Faction", activity.factionId);
            row.setLong("ObservationStartedAtMillis", activity.observationStartedAtMillis);
            row.setLong("LastQualifyingHireAtMillis", activity.lastQualifyingHireAtMillis);
            row.setLong("UpdatedAtMillis", activity.updatedAtMillis);
            row.setString("UpdateSource", activity.updateSource);
            list.appendTag(row);
        }
        result.setTag(RECORDS_KEY, list);
        if (commitments == null)
            throw new IllegalArgumentException("Emergency-defense commitments are required.");
        NBTTagList commitmentList = new NBTTagList();
        for (String conflictId : new TreeSet<String>(commitments.keySet())) {
            KOMEEmergencyDefenseCommitment value = commitments.get(conflictId);
            if (value == null || !conflictId.equals(value.conflictId))
                throw new IllegalArgumentException("Emergency-defense commitment identity mismatch.");
            NBTTagCompound row = new NBTTagCompound();
            row.setString("ConflictId", value.conflictId);
            row.setString("Tile", value.tileId);
            row.setString("NativeFaction", value.nativeFaction);
            row.setLong("AttackedTileProductionRate", value.attackedTileProductionRate);
            row.setLong("TotalFactionProductionRate", value.totalFactionProductionRate);
            row.setLong("AvailablePopulationBasisCenti", value.availablePopulationBasisCenti);
            row.setString("TemplateId", value.templateId);
            row.setInteger("DefenderPopulationCost", value.defenderPopulationCost);
            row.setInteger("CalculatedUnitCount", value.calculatedUnitCount);
            row.setLong("PopulationCommittedCenti", value.populationCommittedCenti);
            row.setLong("CreatedAtMillis", value.createdAtMillis);
            row.setString("State", value.state.name());
            row.setString("Diagnostic", value.diagnostic);
            NBTTagList defenders = new NBTTagList();
            for (KOMEEmergencyDefenseCommitment.Defender defender : value.defenders.values()) {
                NBTTagCompound unit = new NBTTagCompound();
                unit.setString("IntentId", defender.intentId);
                unit.setString("EntityUuid", defender.entityUuid.toString());
                unit.setInteger("PopulationCost", defender.populationCost);
                unit.setLong("CreatedAtMillis", defender.createdAtMillis);
                unit.setString("Disposition", defender.disposition.name());
                unit.setBoolean("PopulationRefunded", defender.populationRefunded);
                unit.setString("Diagnostic", defender.diagnostic);
                defenders.appendTag(unit);
            }
            row.setTag("Defenders", defenders);
            commitmentList.appendTag(row);
        }
        result.setTag(COMMITMENTS_KEY, commitmentList);
        if (observations == null)
            throw new IllegalArgumentException("Emergency-defense observations are required.");
        NBTTagList observationList = new NBTTagList();
        for (String conflictId : new TreeSet<String>(observations.keySet())) {
            KOMEEmergencyDefenseObservation value = observations.get(conflictId);
            if (value == null || !conflictId.equals(value.conflictId))
                throw new IllegalArgumentException("Emergency-defense observation identity mismatch.");
            NBTTagCompound row = new NBTTagCompound();
            row.setString("ConflictId", value.conflictId);
            row.setString("Tile", value.tileId);
            row.setString("NativeFaction", value.nativeFaction);
            row.setLong("ObservedAtMillis", value.observedAtMillis);
            row.setString("Reason", value.reason);
            observationList.appendTag(row);
        }
        result.setTag(OBSERVATIONS_KEY, observationList);
        return result;
    }

    static Loaded read(NBTTagCompound root) {
        requireType(root, SCHEMA_KEY, 3);
        if (root.getInteger(SCHEMA_KEY) != DATA_SCHEMA_VERSION)
            throw new IllegalArgumentException("Unsupported EmergencyDefenseDataSchemaVersion.");
        requireType(root, RECORDS_KEY, 9);
        NBTTagList raw = (NBTTagList) root.getTag(RECORDS_KEY);
        if (raw.tagCount() > 0 && raw.func_150303_d() != 10)
            throw new IllegalArgumentException("Emergency-defense activity list must contain compounds.");
        NBTTagList list = root.getTagList(RECORDS_KEY, 10);
        Map<String, KOMEEmergencyDefenseActivity> result =
            new LinkedHashMap<String, KOMEEmergencyDefenseActivity>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound row = list.getCompoundTagAt(i);
            requireType(row, "Faction", 8);
            requireType(row, "ObservationStartedAtMillis", 4);
            requireType(row, "LastQualifyingHireAtMillis", 4);
            requireType(row, "UpdatedAtMillis", 4);
            requireType(row, "UpdateSource", 8);
            KOMEEmergencyDefenseActivity activity = new KOMEEmergencyDefenseActivity(
                row.getString("Faction"), row.getLong("ObservationStartedAtMillis"),
                row.getLong("LastQualifyingHireAtMillis"), row.getLong("UpdatedAtMillis"),
                row.getString("UpdateSource"));
            if (result.put(activity.factionId, activity) != null)
                throw new IllegalArgumentException("Duplicate emergency-defense faction activity: "
                    + activity.factionId);
        }
        requireType(root, COMMITMENTS_KEY, 9);
        NBTTagList rawCommitments = (NBTTagList) root.getTag(COMMITMENTS_KEY);
        if (rawCommitments.tagCount() > 0 && rawCommitments.func_150303_d() != 10)
            throw new IllegalArgumentException("Emergency-defense commitment list must contain compounds.");
        Map<String, KOMEEmergencyDefenseCommitment> commitments =
            new LinkedHashMap<String, KOMEEmergencyDefenseCommitment>();
        NBTTagList rows = root.getTagList(COMMITMENTS_KEY, 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            for (String key : new String[] {"ConflictId", "Tile", "NativeFaction", "TemplateId",
                    "State", "Diagnostic"}) requireType(row, key, 8);
            for (String key : new String[] {"AttackedTileProductionRate", "TotalFactionProductionRate",
                    "AvailablePopulationBasisCenti", "PopulationCommittedCenti",
                    "CreatedAtMillis"})
                requireType(row, key, 4);
            for (String key : new String[] {"DefenderPopulationCost", "CalculatedUnitCount"})
                requireType(row, key, 3);
            requireType(row, "Defenders", 9);
            Map<String, KOMEEmergencyDefenseCommitment.Defender> defenders =
                new LinkedHashMap<String, KOMEEmergencyDefenseCommitment.Defender>();
            NBTTagList units = row.getTagList("Defenders", 10);
            NBTTagList rawUnits = (NBTTagList) row.getTag("Defenders");
            if (rawUnits.tagCount() > 0 && rawUnits.func_150303_d() != 10)
                throw new IllegalArgumentException("Emergency defender list must contain compounds.");
            for (int j = 0; j < units.tagCount(); j++) {
                NBTTagCompound unit = units.getCompoundTagAt(j);
                for (String key : new String[] {"IntentId", "EntityUuid", "Disposition", "Diagnostic"})
                    requireType(unit, key, 8);
                requireType(unit, "PopulationCost", 3);
                requireType(unit, "CreatedAtMillis", 4);
                requireType(unit, "PopulationRefunded", 1);
                KOMEEmergencyDefenseCommitment.Defender defender;
                try {
                    defender = new KOMEEmergencyDefenseCommitment.Defender(
                        unit.getString("IntentId"), java.util.UUID.fromString(unit.getString("EntityUuid")),
                        unit.getInteger("PopulationCost"), unit.getLong("CreatedAtMillis"),
                        KOMEEmergencyDefenseCommitment.Disposition.valueOf(unit.getString("Disposition")),
                        unit.getBoolean("PopulationRefunded"), unit.getString("Diagnostic"));
                } catch (RuntimeException invalid) {
                    throw new IllegalArgumentException("Invalid emergency defender row.", invalid);
                }
                if (defenders.put(defender.intentId, defender) != null)
                    throw new IllegalArgumentException("Duplicate emergency defender intent.");
            }
            KOMEEmergencyDefenseCommitment commitment;
            try {
                commitment = new KOMEEmergencyDefenseCommitment(row.getString("ConflictId"),
                    row.getString("Tile"), row.getString("NativeFaction"),
                    row.getLong("AttackedTileProductionRate"),
                    row.getLong("TotalFactionProductionRate"),
                    row.getLong("AvailablePopulationBasisCenti"), row.getString("TemplateId"),
                    row.getInteger("DefenderPopulationCost"), row.getInteger("CalculatedUnitCount"),
                    row.getLong("PopulationCommittedCenti"),
                    row.getLong("CreatedAtMillis"),
                    KOMEEmergencyDefenseCommitment.State.valueOf(row.getString("State")),
                    defenders, row.getString("Diagnostic"));
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Invalid emergency-defense commitment row.", invalid);
            }
            if (commitments.put(commitment.conflictId, commitment) != null)
                throw new IllegalArgumentException("Duplicate emergency-defense conflict commitment.");
        }
        requireType(root, OBSERVATIONS_KEY, 9);
        NBTTagList rawObservations = (NBTTagList) root.getTag(OBSERVATIONS_KEY);
        if (rawObservations.tagCount() > 0 && rawObservations.func_150303_d() != 10)
            throw new IllegalArgumentException("Emergency-defense observation list must contain compounds.");
        Map<String, KOMEEmergencyDefenseObservation> observations =
            new LinkedHashMap<String, KOMEEmergencyDefenseObservation>();
        NBTTagList observationRows = root.getTagList(OBSERVATIONS_KEY, 10);
        for (int i = 0; i < observationRows.tagCount(); i++) {
            NBTTagCompound row = observationRows.getCompoundTagAt(i);
            for (String key : new String[] {"ConflictId", "Tile", "NativeFaction", "Reason"})
                requireType(row, key, 8);
            requireType(row, "ObservedAtMillis", 4);
            KOMEEmergencyDefenseObservation observation;
            try {
                observation = new KOMEEmergencyDefenseObservation(row.getString("ConflictId"),
                    row.getString("Tile"), row.getString("NativeFaction"),
                    row.getLong("ObservedAtMillis"), row.getString("Reason"));
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Invalid emergency-defense observation row.", invalid);
            }
            if (observations.put(observation.conflictId, observation) != null)
                throw new IllegalArgumentException("Duplicate deferred Emergency Defense conflict.");
        }
        return new Loaded(result, commitments, observations);
    }

    private static void requireType(NBTTagCompound tag, String key, int type) {
        if (tag == null || !tag.hasKey(key, type))
            throw new IllegalArgumentException("Missing or mistyped emergency-defense field: " + key);
    }
}
