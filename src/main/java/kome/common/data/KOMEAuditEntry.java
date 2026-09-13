package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

/** Immutable structured audit row; gameplay state never depends on this record. */
public final class KOMEAuditEntry {
    public final long timestamp;
    public final String domain;
    public final String action;
    public final String actor;
    public final String subject;
    public final String reason;
    public final String details;

    public KOMEAuditEntry(long timestamp, String domain, String action, String actor,
            String subject, String reason, String details) {
        this.timestamp = Math.max(0L, timestamp);
        this.domain = clean(domain); this.action = clean(action); this.actor = clean(actor);
        this.subject = clean(subject); this.reason = clean(reason); this.details = clean(details);
    }
    private static String clean(String value) {
        return value == null ? "" : value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound(); nbt.setLong("Timestamp", timestamp);
        nbt.setString("Domain", domain); nbt.setString("Action", action); nbt.setString("Actor", actor);
        nbt.setString("Subject", subject); nbt.setString("Reason", reason); nbt.setString("Details", details); return nbt;
    }
    public static KOMEAuditEntry readFromNBT(NBTTagCompound nbt) {
        return new KOMEAuditEntry(nbt.getLong("Timestamp"), nbt.getString("Domain"), nbt.getString("Action"),
            nbt.getString("Actor"), nbt.getString("Subject"), nbt.getString("Reason"), nbt.getString("Details"));
    }
    public String compact() {
        return timestamp + " [" + domain + "/" + action + "] " + subject + " by " + actor + ": " + reason
            + (details.length() == 0 ? "" : " (" + details + ")");
    }
}
