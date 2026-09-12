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
