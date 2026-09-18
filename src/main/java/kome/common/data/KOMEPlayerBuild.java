package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent player-created construction project and its contribution audit. */
public class KOMEPlayerBuild {
    /** Schema 3 combines KOM-54 precise Builds with KOM-10 defensive gate records. */
    public static final int DATA_SCHEMA_VERSION = 3;
    public static final int MAX_AUDIT_ENTRIES = 250;
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
    private final List<String> auditHistory = new ArrayList<String>();
    /** Accounting links only; tactical siege geometry and live gate state are intentionally separate. */
    private final List<KOMEDefensiveGateRecord> defensiveGateRecords = new ArrayList<KOMEDefensiveGateRecord>();
    /** Highest G-number ever allocated within this Build. It never decreases or becomes global. */
    private long defensiveGateRecordSequence;

    /** Approved canonical construction hours; only NORMAL produces a future population rate. */
    public long approvedCentiHours() {
        long result = 0L;
        for (KOMEBuildContribution contribution : contributions) {
            if (contribution != null && contribution.isApproved()) {
                result = KOMEBuildTime.add(result, contribution.totalCentiHours());
            }
        }
        return result;
    }

    public boolean isNormal() { return type == KOMEBuildType.NORMAL; }
    public boolean isDefensive() { return type == KOMEBuildType.DEFENSIVE; }

    /** Canonical downstream source for KOM-10; NORMAL Builds never supply defensive hours. */
    public long approvedDefensiveCentiHours() { return active && isDefensive() ? approvedCentiHours() : 0L; }

    public List<String> auditHistory() { return Collections.unmodifiableList(auditHistory); }

    public void appendAudit(String entry) {
        if (entry == null || entry.isEmpty()) throw new IllegalArgumentException("Build audit entry is required.");
        auditHistory.add(entry);
        if (auditHistory.size() > MAX_AUDIT_ENTRIES) auditHistory.subList(0, auditHistory.size() - MAX_AUDIT_ENTRIES).clear();
    }

    public void validateContributions() {
        if (type == null) throw new IllegalArgumentException("Build type is required.");
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (KOMEBuildContribution contribution : contributions) {
            if (contribution == null) throw new IllegalArgumentException("Null Build contribution.");
            contribution.validate();
            if (!ids.add(contribution.id)) throw new IllegalArgumentException("Duplicate contribution Id: " + contribution.id);
        }
        approvedCentiHours();
    }


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

    public Map<String, Long> activeCentiHoursByFaction() {
        Map<String, Long> result = new HashMap<String, Long>();
        if (!active) return result;
        for (KOMEBuildContribution contribution : contributions) {
            if (contribution == null || !contribution.isApproved()) continue;
            String faction = KOMEAlliance.normalizeFactionKey(contribution.contributorFaction);
            Long current = result.get(faction);
            result.put(faction, KOMEBuildTime.add(current == null ? 0L : current, contribution.totalCentiHours()));
        }
        return result;
    }

    public Map<UUID, Long> activeCentiHoursByPlayer() {
        Map<UUID, Long> result = new HashMap<UUID, Long>();
        if (!active) return result;
        for (KOMEBuildContribution contribution : contributions) {
            if (contribution == null || !contribution.isApproved() || contribution.contributorUuid == null) continue;
            Long current = result.get(contribution.contributorUuid);
            result.put(contribution.contributorUuid,
                KOMEBuildTime.add(current == null ? 0L : current, contribution.totalCentiHours()));
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
        validateContributions();
        validateDefensiveGateRecords();
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("BuildSchemaVersion", DATA_SCHEMA_VERSION);
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
        NBTTagList audit = new NBTTagList();
        for (String entry : auditHistory) audit.appendTag(new NBTTagString(entry));
        nbt.setTag("AuditHistory", audit);
        nbt.setLong("DefensiveGateRecordSequence", defensiveGateRecordSequence);
        NBTTagList defensiveGateList = new NBTTagList();
        if (isDefensive()) {
            for (KOMEDefensiveGateRecord record : defensiveGateRecords) {
                defensiveGateList.appendTag(record.writeToNBT());
            }
        }
        nbt.setTag("DefensiveGateRecords", defensiveGateList);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        if (!nbt.hasKey("BuildSchemaVersion", 3) || nbt.getInteger("BuildSchemaVersion") != DATA_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported BuildSchemaVersion; expected " + DATA_SCHEMA_VERSION + ". Development Builds require reset.");
        }
        requireFields(nbt, 8, "Id", "DisplayName", "TileId", "BuildType", "BuilderUuid", "BuilderName",
            "ManagerUuid", "ManagerName", "PopulationFaction", "OriginalBuilderFaction",
            "DeletedByUuid", "DeletedByName", "DeletionReason", "MarkerLabel");
        requireFields(nbt, 3, "Dimension");
        requireFields(nbt, 6, "X", "Y", "Z");
        requireFields(nbt, 4, "CreatedAtMillis", "UpdatedAtMillis", "DeletedAtMillis");
        requireFields(nbt, 1, "Active", "MarkerVisible");
        for (String key : new String[] {"BuilderUuid", "ManagerUuid", "DeletedByUuid"}) {
            if (!nbt.getString(key).isEmpty()) UUID.fromString(nbt.getString(key));
        }
        if (!nbt.hasKey("BuildType", 8)) throw new IllegalArgumentException("Build is missing BuildType.");
        KOMEBuildType savedType = KOMEBuildType.forKey(nbt.getString("BuildType"));
        if (!nbt.hasKey("Contributions", 9) || !nbt.hasKey("AuditHistory", 9)
                || !nbt.hasKey("DefensiveGateRecords", 9)) {
            throw new IllegalArgumentException(
                "Build Contributions, AuditHistory and DefensiveGateRecords are required.");
        }
        requireFields(nbt, 4, "DefensiveGateRecordSequence");
        NBTTagList contributionList = nbt.getTagList("Contributions", 10);
        if (((NBTTagList) nbt.getTag("Contributions")).tagCount() != contributionList.tagCount()) {
            throw new IllegalArgumentException("Build Contributions must contain compound records.");
        }
        List<KOMEBuildContribution> loaded = new ArrayList<KOMEBuildContribution>();
        java.util.Set<String> ids = new java.util.HashSet<String>();
        long approved = 0L;
        for (int i = 0; i < contributionList.tagCount(); i++) {
            KOMEBuildContribution contribution = new KOMEBuildContribution();
            contribution.readFromNBT(contributionList.getCompoundTagAt(i));
            if (!ids.add(contribution.id)) throw new IllegalArgumentException("Duplicate contribution Id: " + contribution.id);
            if (contribution.isApproved()) approved = KOMEBuildTime.add(approved, contribution.totalCentiHours());
            loaded.add(contribution);
        }
        NBTTagList savedAudit = nbt.getTagList("AuditHistory", 8);
        if (((NBTTagList) nbt.getTag("AuditHistory")).tagCount() != savedAudit.tagCount()) {
            throw new IllegalArgumentException("Build AuditHistory must contain strings.");
        }
        long savedGateSequence = nbt.getLong("DefensiveGateRecordSequence");
        if (savedGateSequence < 0L) {
            throw new IllegalArgumentException("Defensive gate record sequence must not be negative.");
        }
        NBTTagList savedGateRecords = nbt.getTagList("DefensiveGateRecords", 10);
        if (((NBTTagList) nbt.getTag("DefensiveGateRecords")).tagCount() != savedGateRecords.tagCount()) {
            throw new IllegalArgumentException("Build DefensiveGateRecords must contain compound records.");
        }
        List<KOMEDefensiveGateRecord> loadedGateRecords = new ArrayList<KOMEDefensiveGateRecord>();
        java.util.Set<String> gateIds = new java.util.HashSet<String>();
        long highestGateSuffix = 0L;
        for (int i = 0; i < savedGateRecords.tagCount(); i++) {
            KOMEDefensiveGateRecord record = new KOMEDefensiveGateRecord();
            record.readFromNBT(savedGateRecords.getCompoundTagAt(i));
            record.id = safe(record.id).trim();
            if (record.id.length() == 0) {
                throw new IllegalArgumentException("Defensive gate record ID is required at index " + i + ".");
            }
            if (!gateIds.add(record.id)) {
                throw new IllegalArgumentException("Duplicate defensive gate record ID " + record.id + ".");
            }
            highestGateSuffix = Math.max(highestGateSuffix, validDefensiveGateRecordSuffix(record.id));
            loadedGateRecords.add(record);
        }
        if (savedType != KOMEBuildType.DEFENSIVE
                && (savedGateSequence != 0L || !loadedGateRecords.isEmpty())) {
            throw new IllegalArgumentException("Only a DEFENSIVE Build may persist defensive gate records.");
        }
        if (savedGateSequence < highestGateSuffix) {
            throw new IllegalArgumentException("Defensive gate record sequence is below an existing gate ID.");
        }
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
        active = nbt.getBoolean("Active");
        deletedAtMillis = Math.max(0L, nbt.getLong("DeletedAtMillis"));
        deletedByUuid = parseUuid(nbt.getString("DeletedByUuid"));
        deletedByName = safe(nbt.getString("DeletedByName"));
        deletionReason = safe(nbt.getString("DeletionReason"));
        markerVisible = nbt.getBoolean("MarkerVisible");
        markerLabel = sanitizeName(nbt.getString("MarkerLabel"));
        type = savedType;
        contributions.clear();
        contributions.addAll(loaded);
        auditHistory.clear();
        for (int i = Math.max(0, savedAudit.tagCount() - MAX_AUDIT_ENTRIES); i < savedAudit.tagCount(); i++)
            auditHistory.add(savedAudit.getStringTagAt(i));
        defensiveGateRecords.clear();
        defensiveGateRecords.addAll(loadedGateRecords);
        defensiveGateRecordSequence = savedType == KOMEBuildType.DEFENSIVE ? savedGateSequence : 0L;
    }

    private void validateDefensiveGateRecords() {
        if (defensiveGateRecordSequence < 0L) {
            throw new IllegalArgumentException("Defensive gate record sequence must not be negative.");
        }
        if (!isDefensive()) {
            if (defensiveGateRecordSequence != 0L || !defensiveGateRecords.isEmpty()) {
                throw new IllegalArgumentException("Only a DEFENSIVE Build may persist defensive gate records.");
            }
            return;
        }
        java.util.Set<String> ids = new java.util.HashSet<String>();
        long highestSuffix = 0L;
        for (KOMEDefensiveGateRecord record : defensiveGateRecords) {
            if (record == null || safe(record.id).trim().length() == 0) {
                throw new IllegalArgumentException("Defensive gate record ID is required.");
            }
            record.id = record.id.trim();
            if (!ids.add(record.id)) {
                throw new IllegalArgumentException("Duplicate defensive gate record ID " + record.id + ".");
            }
            highestSuffix = Math.max(highestSuffix, validDefensiveGateRecordSuffix(record.id));
        }
        if (defensiveGateRecordSequence < highestSuffix) {
            throw new IllegalArgumentException("Defensive gate record sequence is below an existing gate ID.");
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

    static void requireFields(NBTTagCompound nbt, int type, String... keys) {
        for (String key : keys) if (!nbt.hasKey(key, type))
            throw new IllegalArgumentException("Canonical Build field missing or wrong NBT type: " + key);
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
