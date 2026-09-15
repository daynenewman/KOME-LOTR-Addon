package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent player-created construction project and its contribution audit. */
public class KOMEPlayerBuild {
    public String id = "";
    public String displayName = "";
    public String tileId = "";
    public int dimension;
    public double x;
    public double y;
    public double z;
    public UUID builderUuid;
    public String builderName = "";
    public UUID managerUuid;
    public String managerName = "";
    public String populationFaction = "";
    public String originalBuilderFaction = "";
    public long createdAtMillis;
    public long updatedAtMillis;
    public boolean active = true;
    public long deletedAtMillis;
    public UUID deletedByUuid;
    public String deletedByName = "";
    public String deletionReason = "";
    public boolean markerVisible = true;
    public String markerLabel = "";
    public KOMEBuildType type;
    public final List<KOMEBuildContribution> contributions = new ArrayList<KOMEBuildContribution>();
    /** Accounting links only; tactical siege geometry and live gate state are intentionally separate. */
    private final List<KOMEDefensiveGateRecord> defensiveGateRecords = new ArrayList<KOMEDefensiveGateRecord>();
    /** Highest G-number ever allocated within this Build. It never decreases or becomes global. */
    private long defensiveGateRecordSequence;

    /** Approved canonical construction hours; only NORMAL produces a future population rate. */
    public int approvedHalfHours() {
        int result = 0;
        for (KOMEBuildContribution contribution : contributions) {
            if (contribution != null && contribution.isApproved()) {
                result = saturatedAdd(result, contribution.totalHalfHours());
            }
        }
        return Math.max(0, result);
    }

    public boolean isNormal() { return type == KOMEBuildType.NORMAL; }
    public boolean isDefensive() { return type == KOMEBuildType.DEFENSIVE; }

    /** Exact rational rate: numerator half-hours, denominator 2 * configured hours per point. */
    public long[] originalPopulationRate(int hoursPerPopulationPoint) {
        long denominator = Math.multiplyExact(2L, Math.max(1L, (long) hoursPerPopulationPoint));
        return isNormal() ? new long[] { approvedHalfHours(), denominator }
            : new long[] { 0L, 1L };
    }

    /** Canonical downstream source for KOM-10; NORMAL Builds never supply defensive hours. */
    public int approvedDefensiveHalfHours() { return isDefensive() ? approvedHalfHours() : 0; }


    public int pendingCount() {
        int count = 0;
        for (KOMEBuildContribution contribution : contributions) {
            if (contribution != null && contribution.isPending()) {
                count++;
            }
        }
        return count;
    }

    public KOMEBuildContribution getContribution(String contributionId) {
        String key = contributionId == null ? "" : contributionId.trim();
        for (KOMEBuildContribution contribution : contributions) {
            if (contribution != null && key.equals(contribution.id)) {
                return contribution;
            }
        }
        return null;
    }

    public KOMEDefensiveGateRecord getDefensiveGateRecord(String recordId) {
        String key = recordId == null ? "" : recordId.trim();
        for (KOMEDefensiveGateRecord record : defensiveGateRecords) {
            if (record != null && key.equals(record.id)) return record;
        }
        return null;
    }

    public List<KOMEDefensiveGateRecord> getDefensiveGateRecords() {
        return Collections.unmodifiableList(defensiveGateRecords);
    }

    public long getDefensiveGateRecordSequence() {
        return defensiveGateRecordSequence;
    }

    /**
     * Reserved for an authoritative same-package service that atomically creates a real linkage.
     * Merely allocating an ID burns it intentionally so historical IDs can never be reused.
     */
    String allocateDefensiveGateRecordId() {
        if (!isDefensive()) {
            throw new IllegalStateException("Only a DEFENSIVE Build may allocate defensive gate record IDs.");
        }
        if (defensiveGateRecordSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Defensive gate record ID sequence is exhausted for Build " + safe(id) + ".");
        }
        String candidate;
        do {
            defensiveGateRecordSequence++;
            candidate = "G" + defensiveGateRecordSequence;
            if (defensiveGateRecordSequence == Long.MAX_VALUE
                    && getDefensiveGateRecord(candidate) != null) {
                throw new IllegalStateException("Defensive gate record ID sequence is exhausted for Build " + safe(id) + ".");
            }
        } while (getDefensiveGateRecord(candidate) != null);
        return candidate;
    }

    /** Same-package persistence/service hook; callers remain responsible for dirty marking and audit. */
    void addDefensiveGateRecord(KOMEDefensiveGateRecord record) {
        if (!isDefensive()) {
            throw new IllegalStateException("Only a DEFENSIVE Build may own defensive gate records.");
        }
        if (record == null || safe(record.id).trim().length() == 0) {
            throw new IllegalArgumentException("Defensive gate record ID is required.");
        }
        record.id = record.id.trim();
        if (getDefensiveGateRecord(record.id) != null) {
            throw new IllegalArgumentException("Duplicate defensive gate record ID " + record.id + ".");
        }
        defensiveGateRecords.add(record);
        repairDefensiveGateRecordSequence(record.id);
    }

    /** Same-package persistence/service hook; deleting a record never decreases the high-water value. */
    boolean removeDefensiveGateRecord(String recordId) {
        KOMEDefensiveGateRecord record = getDefensiveGateRecord(recordId);
        return record != null && defensiveGateRecords.remove(record);
    }

    public Map<String, Integer> activeHalfHoursByFaction() {
        Map<String, Integer> result = new HashMap<String, Integer>();
        for (KOMEBuildContribution contribution : contributions) {
            if (contribution == null || !contribution.isApproved()) continue;
            String faction = KOMEAlliance.normalizeFactionKey(contribution.contributorFaction);
            Integer current = result.get(faction);
            result.put(faction, Integer.valueOf((current == null ? 0 : current.intValue()) + contribution.totalHalfHours()));
        }
        return result;
    }

    public Map<UUID, Integer> activeHalfHoursByPlayer() {
        Map<UUID, Integer> result = new HashMap<UUID, Integer>();
        for (KOMEBuildContribution contribution : contributions) {
            if (contribution == null || !contribution.isApproved() || contribution.contributorUuid == null) continue;
            Integer current = result.get(contribution.contributorUuid);
            result.put(contribution.contributorUuid,
                Integer.valueOf((current == null ? 0 : current.intValue()) + contribution.totalHalfHours()));
        }
        return result;
    }

    public List<KOMEBuildContribution> sortedContributions() {
        List<KOMEBuildContribution> result = new ArrayList<KOMEBuildContribution>(contributions);
        Collections.sort(result, new Comparator<KOMEBuildContribution>() {
            @Override
            public int compare(KOMEBuildContribution left, KOMEBuildContribution right) {
                int byTime = left.submittedAtMillis < right.submittedAtMillis ? -1
                    : left.submittedAtMillis == right.submittedAtMillis ? 0 : 1;
                return byTime != 0 ? byTime : safe(left.id).compareTo(safe(right.id));
            }
        });
        return result;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", safe(id));
        nbt.setString("DisplayName", sanitizeName(displayName));
        nbt.setString("TileId", KOMEConquestTile.normalizeId(tileId));
        nbt.setInteger("Dimension", dimension);
        nbt.setDouble("X", x);
        nbt.setDouble("Y", y);
        nbt.setDouble("Z", z);
        nbt.setString("BuilderUuid", builderUuid == null ? "" : builderUuid.toString());
        nbt.setString("BuilderName", safe(builderName));
        nbt.setString("ManagerUuid", managerUuid == null ? "" : managerUuid.toString());
        nbt.setString("ManagerName", safe(managerName));
        nbt.setString("PopulationFaction", KOMEAlliance.normalizeFactionKey(populationFaction));
        nbt.setString("OriginalBuilderFaction", KOMEAlliance.normalizeFactionKey(originalBuilderFaction));
        nbt.setLong("CreatedAtMillis", Math.max(0L, createdAtMillis));
        nbt.setLong("UpdatedAtMillis", Math.max(0L, updatedAtMillis));
        nbt.setBoolean("Active", active);
        nbt.setLong("DeletedAtMillis", Math.max(0L, deletedAtMillis));
        nbt.setString("DeletedByUuid", deletedByUuid == null ? "" : deletedByUuid.toString());
        nbt.setString("DeletedByName", safe(deletedByName));
        nbt.setString("DeletionReason", safe(deletionReason));
        nbt.setBoolean("MarkerVisible", markerVisible);
        nbt.setString("MarkerLabel", sanitizeName(markerLabel));
        if (type == null) throw new IllegalStateException("Build type is required.");
        nbt.setString("BuildType", type.key);
        NBTTagList contributionList = new NBTTagList();
        for (KOMEBuildContribution contribution : contributions) {
            if (contribution != null && contribution.id != null && contribution.id.length() > 0) {
                contributionList.appendTag(contribution.writeToNBT());
            }
        }
        nbt.setTag("Contributions", contributionList);
        NBTTagList defensiveGateList = new NBTTagList();
        if (isDefensive()) {
            nbt.setLong("DefensiveGateRecordSequence", Math.max(0L, defensiveGateRecordSequence));
            for (KOMEDefensiveGateRecord record : defensiveGateRecords) {
                if (record != null && record.id != null && record.id.trim().length() > 0) {
                    defensiveGateList.appendTag(record.writeToNBT());
                }
            }
        }
        nbt.setTag("DefensiveGateRecords", defensiveGateList);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        id = safe(nbt.getString("Id"));
        displayName = sanitizeName(nbt.getString("DisplayName"));
        tileId = KOMEConquestTile.normalizeId(nbt.getString("TileId"));
        dimension = nbt.getInteger("Dimension");
        x = nbt.getDouble("X");
        y = nbt.getDouble("Y");
        z = nbt.getDouble("Z");
        builderUuid = parseUuid(nbt.getString("BuilderUuid"));
        builderName = safe(nbt.getString("BuilderName"));
        managerUuid = parseUuid(nbt.getString("ManagerUuid"));
        managerName = safe(nbt.getString("ManagerName"));
        populationFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("PopulationFaction"));
        originalBuilderFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("OriginalBuilderFaction"));
        createdAtMillis = Math.max(0L, nbt.getLong("CreatedAtMillis"));
        updatedAtMillis = Math.max(0L, nbt.getLong("UpdatedAtMillis"));
        active = !nbt.hasKey("Active") || nbt.getBoolean("Active");
        deletedAtMillis = Math.max(0L, nbt.getLong("DeletedAtMillis"));
        deletedByUuid = parseUuid(nbt.getString("DeletedByUuid"));
        deletedByName = safe(nbt.getString("DeletedByName"));
        deletionReason = safe(nbt.getString("DeletionReason"));
        markerVisible = !nbt.hasKey("MarkerVisible") || nbt.getBoolean("MarkerVisible");
        markerLabel = sanitizeName(nbt.getString("MarkerLabel"));
        if (!nbt.hasKey("BuildType")) throw new IllegalArgumentException("Build is missing BuildType.");
        type = KOMEBuildType.forKey(nbt.getString("BuildType"));
        contributions.clear();
        NBTTagList contributionList = nbt.getTagList("Contributions", 10);
        for (int i = 0; i < contributionList.tagCount(); i++) {
            KOMEBuildContribution contribution = new KOMEBuildContribution();
            contribution.readFromNBT(contributionList.getCompoundTagAt(i));
            if (contribution.id.length() > 0) contributions.add(contribution);
        }
        defensiveGateRecords.clear();
        defensiveGateRecordSequence = isDefensive()
            ? Math.max(0L, nbt.getLong("DefensiveGateRecordSequence")) : 0L;
        if (isDefensive()) {
            NBTTagList defensiveGateList = nbt.getTagList("DefensiveGateRecords", 10);
            for (int i = 0; i < defensiveGateList.tagCount(); i++) {
                KOMEDefensiveGateRecord record = new KOMEDefensiveGateRecord();
                record.readFromNBT(defensiveGateList.getCompoundTagAt(i));
                if (record.id.length() > 0 && getDefensiveGateRecord(record.id) == null) {
                    addDefensiveGateRecord(record);
                }
            }
        }
    }

    private void repairDefensiveGateRecordSequence(String recordId) {
        long suffix = validDefensiveGateRecordSuffix(recordId);
        if (suffix > defensiveGateRecordSequence) defensiveGateRecordSequence = suffix;
    }

    private static long validDefensiveGateRecordSuffix(String recordId) {
        String value = safe(recordId).trim();
        if (value.length() < 2 || value.charAt(0) != 'G' || value.charAt(1) == '0') return 0L;
        long suffix = 0L;
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return 0L;
            int digit = c - '0';
            if (suffix > (Long.MAX_VALUE - digit) / 10L) return 0L;
            suffix = suffix * 10L + digit;
        }
        return suffix;
    }

    public static String sanitizeName(String value) {
        String source = safe(value).trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < source.length() && out.length() < 40; i++) {
            char c = source.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'' || c == '&') {
                out.append(c);
            }
        }
        String result = out.toString().trim().replaceAll(" +", " ");
        return result.length() == 0 ? "New Build" : result;
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.length() == 0 ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int saturatedAdd(int left, int right) {
        long total = (long) Math.max(0, left) + Math.max(0, right);
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
