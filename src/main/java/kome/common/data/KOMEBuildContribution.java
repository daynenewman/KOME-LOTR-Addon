package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * One immutable submission to a Build. Approval state may change, while the submitted
 * hours and contributor identity remain an audit record.
 *
 * Hours are stored as half-hour units so invalid fractions cannot enter persistence.
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
    public int offensiveHalfHours;
    public int defensiveHalfHours;
    public String status = PENDING;
    public long submittedAtMillis;
    public long decidedAtMillis;
    public UUID decidedByUuid;
    public String decidedByName = "";
    public String decisionReason = "";

    public int totalHalfHours() {
        return Math.max(0, offensiveHalfHours) + Math.max(0, defensiveHalfHours);
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
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", safe(id));
        nbt.setString("ContributorUuid", contributorUuid == null ? "" : contributorUuid.toString());
        nbt.setString("ContributorName", safe(contributorName));
        nbt.setString("ContributorFaction", KOMEAlliance.normalizeFactionKey(contributorFaction));
        nbt.setInteger("OffensiveHalfHours", Math.max(0, offensiveHalfHours));
        nbt.setInteger("DefensiveHalfHours", Math.max(0, defensiveHalfHours));
        nbt.setString("Status", normalizeStatus(status));
        nbt.setLong("SubmittedAtMillis", Math.max(0L, submittedAtMillis));
        nbt.setLong("DecidedAtMillis", Math.max(0L, decidedAtMillis));
        nbt.setString("DecidedByUuid", decidedByUuid == null ? "" : decidedByUuid.toString());
        nbt.setString("DecidedByName", safe(decidedByName));
        nbt.setString("DecisionReason", safe(decisionReason));
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        id = safe(nbt.getString("Id"));
        contributorUuid = parseUuid(nbt.getString("ContributorUuid"));
        contributorName = safe(nbt.getString("ContributorName"));
        contributorFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("ContributorFaction"));
        offensiveHalfHours = Math.max(0, nbt.getInteger("OffensiveHalfHours"));
        defensiveHalfHours = Math.max(0, nbt.getInteger("DefensiveHalfHours"));
        status = normalizeStatus(nbt.getString("Status"));
        submittedAtMillis = Math.max(0L, nbt.getLong("SubmittedAtMillis"));
        decidedAtMillis = Math.max(0L, nbt.getLong("DecidedAtMillis"));
        decidedByUuid = parseUuid(nbt.getString("DecidedByUuid"));
        decidedByName = safe(nbt.getString("DecidedByName"));
        decisionReason = safe(nbt.getString("DecisionReason"));
    }

    public static String normalizeStatus(String value) {
        if (APPROVED.equals(value) || REJECTED.equals(value) || REMOVED.equals(value)) {
            return value;
        }
        return PENDING;
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
