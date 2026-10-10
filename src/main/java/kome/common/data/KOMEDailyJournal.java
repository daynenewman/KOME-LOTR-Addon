package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

/** One bounded daily transaction cursor. Domain effects and this cursor share the canonical save. */
public final class KOMEDailyJournal {
    public enum Stage { MOVEMENT, CONFLICT, DEVELOPMENT, PAYOUT, STARVATION, EVENTS, SUMMARY }
    String timezone = "", localTime = "", status = "IDLE", reason = "";
    long anchor = -1L, boundary = -1L, lastComplete = -1L, lastObserved = -1L;
    int nextStage;
    boolean summaryClaimed;

    public String status() { return status; }
    public String reason() { return reason; }
    public long boundary() { return boundary; }
    public long lastComplete() { return lastComplete; }
    public int completedStages() { return nextStage; }
    public String stage() { return nextStage < Stage.values().length ? Stage.values()[nextStage].name() : "DONE"; }

    NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Schema", 1); tag.setString("Timezone", timezone); tag.setString("LocalTime", localTime);
        tag.setString("Status", status); tag.setString("Reason", reason);
        tag.setLong("Anchor", anchor); tag.setLong("Boundary", boundary);
        tag.setLong("LastComplete", lastComplete); tag.setLong("LastObserved", lastObserved);
        tag.setInteger("NextStage", nextStage); tag.setBoolean("SummaryClaimed", summaryClaimed);
        return tag;
    }

    static KOMEDailyJournal read(NBTTagCompound tag) {
        if (!tag.hasKey("Schema", 3) || tag.getInteger("Schema") != 1)
            throw new IllegalArgumentException("Unsupported daily journal schema");
        for (String key : new String[] {"Timezone", "LocalTime", "Status", "Reason"})
            if (!tag.hasKey(key, 8)) throw new IllegalArgumentException("Missing daily journal " + key);
        for (String key : new String[] {"Anchor", "Boundary", "LastComplete", "LastObserved"})
            if (!tag.hasKey(key, 4) || tag.getLong(key) < -1L) throw new IllegalArgumentException("Invalid daily journal " + key);
        if (!tag.hasKey("NextStage", 3) || !tag.hasKey("SummaryClaimed", 1))
            throw new IllegalArgumentException("Missing daily stage receipt");
        KOMEDailyJournal j = new KOMEDailyJournal();
        j.timezone = tag.getString("Timezone"); j.localTime = tag.getString("LocalTime");
        j.status = tag.getString("Status"); j.reason = tag.getString("Reason");
        j.anchor = tag.getLong("Anchor"); j.boundary = tag.getLong("Boundary");
        j.lastComplete = tag.getLong("LastComplete"); j.lastObserved = tag.getLong("LastObserved");
        j.nextStage = tag.getInteger("NextStage"); j.summaryClaimed = tag.getBoolean("SummaryClaimed");
        if (!j.timezone.isEmpty()) KOMEDailyBoundary.persisted(j.timezone, j.localTime);
        if (j.timezone.isEmpty() != j.localTime.isEmpty() || j.nextStage < 0 || j.nextStage > Stage.values().length
                || j.reason.length() > 512 || !java.util.Arrays.asList("IDLE", "RUNNING", "BLOCKED", "COMPLETE").contains(j.status)
                || j.nextStage > 0 && j.boundary < 0
                || j.summaryClaimed != (j.nextStage == Stage.values().length)
                || "COMPLETE".equals(j.status) && (j.lastComplete != j.boundary || !j.summaryClaimed))
            throw new IllegalArgumentException("Inconsistent daily journal");
        return j;
    }
}
