package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Canonical global KOM-71 ceiling, live-boundary cursor, and exact faction remainders. */
public final class KOMEPopulationDevelopmentState {
    public static final int DATA_SCHEMA_VERSION = 1;

    boolean initialized;
    BigInteger rateCeilingUnits = BigInteger.ZERO;
    long lastLiveBoundaryMillis = -1L;
    String timezone = "";
    String localTime = "";
    long hoursPerPopulationPointCentiHours;
    final Map<String, Long> factionCentiHourRemainders = new HashMap<String, Long>();

    public boolean isInitialized() { return initialized; }
    public BigInteger getRateCeilingUnits() { return rateCeilingUnits; }
    public long getLastLiveBoundaryMillis() { return lastLiveBoundaryMillis; }
    public String getTimezone() { return timezone; }
    public String getLocalTime() { return localTime; }
    public long getHoursPerPopulationPointCentiHours() {
        return hoursPerPopulationPointCentiHours;
    }
    public Map<String, Long> getFactionCentiHourRemainders() {
        return Collections.unmodifiableMap(new HashMap<String, Long>(factionCentiHourRemainders));
    }

    KOMEDailyBoundary schedule() {
        return KOMEDailyBoundary.persisted(timezone, localTime);
    }

    KOMEPopulationDevelopmentState copy() {
        KOMEPopulationDevelopmentState result = new KOMEPopulationDevelopmentState();
        result.copyFrom(this);
        return result;
    }

    void copyFrom(KOMEPopulationDevelopmentState source) {
        initialized = source.initialized;
        rateCeilingUnits = source.rateCeilingUnits;
        lastLiveBoundaryMillis = source.lastLiveBoundaryMillis;
        timezone = source.timezone;
        localTime = source.localTime;
        hoursPerPopulationPointCentiHours = source.hoursPerPopulationPointCentiHours;
        factionCentiHourRemainders.clear();
        factionCentiHourRemainders.putAll(source.factionCentiHourRemainders);
    }

    void validate() {
        if (rateCeilingUnits == null || rateCeilingUnits.signum() < 0)
            throw new IllegalArgumentException("Population Rate Ceiling must be nonnegative.");
        if (!initialized) {
            if (rateCeilingUnits.signum() != 0 || lastLiveBoundaryMillis != -1L
                    || timezone.length() != 0 || localTime.length() != 0
                    || hoursPerPopulationPointCentiHours != 0L
                    || !factionCentiHourRemainders.isEmpty())
                throw new IllegalArgumentException("Uninitialized population development state must be empty.");
        } else {
            if (hoursPerPopulationPointCentiHours <= 0L)
                throw new IllegalArgumentException("Population development hours-per-point identity is invalid.");
            KOMEDailyBoundary persisted = schedule();
            Instant boundary = Instant.ofEpochMilli(lastLiveBoundaryMillis);
            if (!persisted.latestBoundaryAtOrBefore(boundary).equals(boundary))
                throw new IllegalArgumentException("Population development cursor is not a persisted schedule boundary.");
            persisted.nextBoundary(boundary).toEpochMilli();
        }
        for (Map.Entry<String, Long> entry : factionCentiHourRemainders.entrySet()) {
            String faction = entry.getKey();
            Long remainder = entry.getValue();
            if (faction == null || !faction.equals(KOMEAlliance.normalizeFactionKey(faction))
                    || !KOMEAlliance.allFactionKeys().contains(faction))
                throw new IllegalArgumentException("Population development remainder has unsupported faction.");
            if (remainder == null || remainder.longValue() < 0L
                    || remainder.longValue() >= KOMEPopulationRate.SCALE)
                throw new IllegalArgumentException("Population development remainder is outside the exact sub-centi range.");
        }
    }

    NBTTagCompound writeToNBT() {
        validate();
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setBoolean("Initialized", initialized);
        nbt.setString("RateCeilingUnits", rateCeilingUnits.toString());
        nbt.setLong("LastLiveBoundaryMillis", lastLiveBoundaryMillis);
        nbt.setString("Timezone", timezone);
        nbt.setString("LocalTime", localTime);
        nbt.setLong("HoursPerPopulationPointCentiHours",
            hoursPerPopulationPointCentiHours);
        NBTTagList remainders = new NBTTagList();
        java.util.List<String> factions = new java.util.ArrayList<String>(factionCentiHourRemainders.keySet());
        java.util.Collections.sort(factions);
        for (String faction : factions) {
            long value = factionCentiHourRemainders.get(faction).longValue();
            if (value == 0L) continue;
            NBTTagCompound row = new NBTTagCompound();
            row.setString("Faction", faction);
            row.setLong("Remainder", value);
            remainders.appendTag(row);
        }
        nbt.setTag("FactionCentiHourRemainders", remainders);
        return nbt;
    }

    static KOMEPopulationDevelopmentState readFromNBT(NBTTagCompound nbt) {
        if (nbt == null || !nbt.hasKey("Initialized", 1)
                || !nbt.hasKey("RateCeilingUnits", 8)
                || !nbt.hasKey("LastLiveBoundaryMillis", 4)
                || !nbt.hasKey("Timezone", 8) || !nbt.hasKey("LocalTime", 8)
                || !nbt.hasKey("HoursPerPopulationPointCentiHours", 4)
                || !nbt.hasKey("FactionCentiHourRemainders", 9))
            throw new IllegalArgumentException("Mandatory population development fields are missing or malformed.");
        KOMEPopulationDevelopmentState result = new KOMEPopulationDevelopmentState();
        result.initialized = nbt.getBoolean("Initialized");
        result.rateCeilingUnits = new BigInteger(nbt.getString("RateCeilingUnits"));
        result.lastLiveBoundaryMillis = nbt.getLong("LastLiveBoundaryMillis");
        result.timezone = nbt.getString("Timezone");
        result.localTime = nbt.getString("LocalTime");
        result.hoursPerPopulationPointCentiHours =
            nbt.getLong("HoursPerPopulationPointCentiHours");
        NBTTagList rows = nbt.getTagList("FactionCentiHourRemainders", 10);
        if (((NBTTagList) nbt.getTag("FactionCentiHourRemainders")).tagCount() != rows.tagCount())
            throw new IllegalArgumentException("Population development remainders must be compound records.");
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            if (!row.hasKey("Faction", 8) || !row.hasKey("Remainder", 4))
                throw new IllegalArgumentException("Malformed population development remainder.");
            String faction = row.getString("Faction");
            if (result.factionCentiHourRemainders.put(faction, row.getLong("Remainder")) != null)
                throw new IllegalArgumentException("Duplicate population development remainder faction.");
        }
        result.validate();
        return result;
    }
}
