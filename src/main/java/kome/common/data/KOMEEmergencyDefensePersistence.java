package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/** Strict schema-1 codec for ruler military-recruitment activity only. */
final class KOMEEmergencyDefensePersistence {
    static final String SCHEMA_KEY = "EmergencyDefenseDataSchemaVersion";
    static final String RECORDS_KEY = "EmergencyDefenseActivities";
    static final int DATA_SCHEMA_VERSION = 1;

    private KOMEEmergencyDefensePersistence() { }

    static NBTTagCompound write(Map<String, KOMEEmergencyDefenseActivity> records) {
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
        return result;
    }

    static Map<String, KOMEEmergencyDefenseActivity> read(NBTTagCompound root) {
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
        return result;
    }

    private static void requireType(NBTTagCompound tag, String key, int type) {
        if (tag == null || !tag.hasKey(key, type))
            throw new IllegalArgumentException("Missing or mistyped emergency-defense field: " + key);
    }
}
