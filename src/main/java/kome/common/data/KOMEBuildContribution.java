package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * One Build contribution. Its reviewed duration has one centi-hour authority;
 * the Build lifecycle audit records submission and adjustment amounts.
 */
public class KOMEBuildContribution {
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String REMOVED = "REMOVED";

    public String id = "";
    public UUID contributorUuid;
    public String contributorName = "";
    public String contributorFaction = "";
    /** Canonical contribution duration: 100 centi-hours = 1.00 hour. */
    public long centiHours;
    public String status = PENDING;
    public long submittedAtMillis;
    public long decidedAtMillis;
    public UUID decidedByUuid;
    public String decidedByName = "";
    public String decisionReason = "";

    public long totalCentiHours() {
        return KOMEBuildTime.requireNonnegative(centiHours);
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    public boolean isApproved() {
        return APPROVED.equals(status);
    }

    public boolean isRemoved() {
        return REMOVED.equals(status);
    }

    public NBTTagCompound writeToNBT() {
        validate();
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", safe(id));
        nbt.setString("ContributorUuid", contributorUuid == null ? "" : contributorUuid.toString());
        nbt.setString("ContributorName", safe(contributorName));
        nbt.setString("ContributorFaction", KOMEAlliance.normalizeFactionKey(contributorFaction));
        nbt.setLong("CentiHours", centiHours);
        nbt.setString("Status", normalizeStatus(status));
        nbt.setLong("SubmittedAtMillis", Math.max(0L, submittedAtMillis));
        nbt.setLong("DecidedAtMillis", Math.max(0L, decidedAtMillis));
        nbt.setString("DecidedByUuid", decidedByUuid == null ? "" : decidedByUuid.toString());
        nbt.setString("DecidedByName", safe(decidedByName));
        nbt.setString("DecisionReason", safe(decisionReason));
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        if (!nbt.hasKey("CentiHours", 4) || nbt.hasKey("HalfHours")
                || nbt.hasKey("OffensiveHalfHours") || nbt.hasKey("DefensiveHalfHours")) {
            throw new IllegalArgumentException("Contribution requires canonical long CentiHours; development Builds require reset.");
        }
        KOMEPlayerBuild.requireFields(nbt, 8, "Id", "ContributorUuid", "ContributorName", "ContributorFaction",
            "Status", "DecidedByUuid", "DecidedByName", "DecisionReason");
        KOMEPlayerBuild.requireFields(nbt, 4, "SubmittedAtMillis", "DecidedAtMillis");
        for (String key : new String[] {"ContributorUuid", "DecidedByUuid"}) {
            if (!nbt.getString(key).isEmpty()) UUID.fromString(nbt.getString(key));
        }
        long savedHours = KOMEBuildTime.requireNonnegative(nbt.getLong("CentiHours"));
        if (!nbt.hasKey("Status", 8)) throw new IllegalArgumentException("Contribution Status is required.");
        String savedStatus = normalizeStatus(nbt.getString("Status"));
        if (nbt.getString("Id").trim().isEmpty()) throw new IllegalArgumentException("Contribution Id is required.");
        id = safe(nbt.getString("Id"));
        contributorUuid = parseUuid(nbt.getString("ContributorUuid"));
        contributorName = safe(nbt.getString("ContributorName"));
        contributorFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("ContributorFaction"));
        centiHours = savedHours;
        status = savedStatus;
        submittedAtMillis = Math.max(0L, nbt.getLong("SubmittedAtMillis"));
        decidedAtMillis = Math.max(0L, nbt.getLong("DecidedAtMillis"));
        decidedByUuid = parseUuid(nbt.getString("DecidedByUuid"));
        decidedByName = safe(nbt.getString("DecidedByName"));
        decisionReason = safe(nbt.getString("DecisionReason"));
    }

    public static String normalizeStatus(String value) {
        if (PENDING.equals(value) || APPROVED.equals(value) || REJECTED.equals(value) || REMOVED.equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("Invalid Build contribution status: " + value);
    }

    public void validate() {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Contribution Id is required.");
        totalCentiHours();
        normalizeStatus(status);
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
