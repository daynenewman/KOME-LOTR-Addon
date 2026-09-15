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
    public static final int DATA_SCHEMA_VERSION = 2;
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
        if (!nbt.hasKey("Contributions", 9) || !nbt.hasKey("AuditHistory", 9)) {
            throw new IllegalArgumentException("Build Contributions and AuditHistory are required.");
        }
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
