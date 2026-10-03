package kome.common.data;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Faction-owned reserves, deliberately separate from player-owned hired companies and bank balances. */
public final class KOMEMusterRecord {
    public enum Status { SCHEDULED, PENDING_TBD, ARRIVED }
    public static final class Entry {
        public final KOMEMusterRoster.Unit unit;
        public final BigInteger count;
        public Entry(KOMEMusterRoster.Unit unit, BigInteger count) {
            if (unit == null || count == null || count.signum() <= 0)
                throw new IllegalArgumentException("Invalid muster roster count.");
            this.unit = unit; this.count = count;
        }
    }
    public final String faction;
    public final long seasonId, calledAtMillis, dueAtMillis, seed;
    public final UUID calledBy;
    public final KOMEFactionCapitalRecord capital;
    public final BigInteger rateUnits, budgetUnits, spentUnits;
    public final int budgetMultiplier, arrivalDelayHours;
    public final List<Entry> roster;
    private volatile Status status = Status.SCHEDULED;
    private volatile String pendingReason = "", arrivalReceipt = "";
    private volatile long arrivedAtMillis = -1L;

    KOMEMusterRecord(String faction, long season, UUID actor, long now, long due, long seed,
            KOMEFactionCapitalRecord capital, BigInteger rate, int multiplier, int delay,
            KOMEMusterRoster.Selection selection) {
        this.faction = faction; seasonId = season; calledBy = actor;
        calledAtMillis = now; dueAtMillis = due; this.seed = seed; this.capital = capital;
        rateUnits = rate; budgetMultiplier = multiplier; arrivalDelayHours = delay;
        budgetUnits = rate.multiply(BigInteger.valueOf(multiplier)); spentUnits = selection.spentUnits;
        List<Entry> entries = new ArrayList<Entry>();
        for (java.util.Map.Entry<KOMEMusterRoster.Unit, BigInteger> e : selection.counts.entrySet())
            entries.add(new Entry(e.getKey(), e.getValue()));
        roster = Collections.unmodifiableList(entries);
        validate();
    }
    private KOMEMusterRecord(NBTTagCompound tag) {
        faction = string(tag, "Faction"); seasonId = number(tag, "Season");
        calledBy = UUID.fromString(string(tag, "CalledBy"));
        calledAtMillis = number(tag, "CalledAt"); dueAtMillis = number(tag, "DueAt"); seed = number(tag, "Seed");
        if (!tag.hasKey("Capital", 10)) throw new IllegalArgumentException("Missing muster capital.");
        capital = KOMEFactionCapitalRecord.readFromNBT(tag.getCompoundTag("Capital"));
        rateUnits = integer(tag, "RateUnits"); budgetUnits = integer(tag, "BudgetUnits");
        spentUnits = integer(tag, "SpentUnits");
        if (!tag.hasKey("Multiplier", 3) || !tag.hasKey("DelayHours", 3))
            throw new IllegalArgumentException("Missing muster settings snapshot.");
        budgetMultiplier = tag.getInteger("Multiplier"); arrivalDelayHours = tag.getInteger("DelayHours");
        status = Status.valueOf(string(tag, "Status"));
        pendingReason = string(tag, "PendingReason"); arrivalReceipt = string(tag, "ArrivalReceipt");
        arrivedAtMillis = number(tag, "ArrivedAt");
        if (!tag.hasKey("Roster", 9)) throw new IllegalArgumentException("Missing muster roster.");
        NBTTagList list = tag.getTagList("Roster", 10);
        List<Entry> entries = new ArrayList<Entry>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound row = list.getCompoundTagAt(i);
            if (!row.hasKey("Cost", 3) || !row.hasKey("Weight", 3))
                throw new IllegalArgumentException("Missing muster unit cost/weight.");
            entries.add(new Entry(new KOMEMusterRoster.Unit(string(row, "Key"), string(row, "Faction"),
                string(row, "Entity"), string(row, "Mount"), row.getInteger("Cost"), row.getInteger("Weight")),
                integer(row, "Count")));
        }
        roster = Collections.unmodifiableList(entries);
        validate();
    }
    public String key() { return faction + "|" + seasonId; }
    public Status getStatus() { return status; }
    public String getPendingReason() { return pendingReason; }
    public String getArrivalReceipt() { return arrivalReceipt; }
    public long getArrivedAtMillis() { return arrivedAtMillis; }
    boolean pending(String reason) {
        if (status == Status.ARRIVED) return false;
        if (status == Status.PENDING_TBD && pendingReason.equals(reason)) return false;
        status = Status.PENDING_TBD; pendingReason = reason; return true;
    }
    void arrive(long now, String receipt) {
        if (status == Status.ARRIVED || now < dueAtMillis || receipt == null || receipt.trim().isEmpty())
            throw new IllegalStateException("Muster arrival requires a due, undelivered force and durable receipt.");
        pendingReason = ""; arrivalReceipt = receipt; arrivedAtMillis = now; status = Status.ARRIVED;
    }
    public String rosterSummary() {
        StringBuilder result = new StringBuilder();
        for (Entry entry : roster) {
            if (result.length() > 0) result.append(',');
            result.append(entry.unit.key).append('=').append(entry.count)
                .append('@').append(entry.unit.cost).append("[weight=").append(entry.unit.weight).append(']');
        }
        return result.toString();
    }
    void validate() {
        if (!KOMEAlliance.allFactionKeys().contains(faction) || seasonId <= 0 || calledBy == null
                || calledAtMillis < 0 || budgetMultiplier <= 0 || arrivalDelayHours <= 0
                || dueAtMillis != Math.addExact(calledAtMillis, Math.multiplyExact((long) arrivalDelayHours, 3_600_000L))
                || capital == null || !faction.equals(capital.getFactionId()) || rateUnits.signum() <= 0
                || !budgetUnits.equals(rateUnits.multiply(BigInteger.valueOf(budgetMultiplier)))
                || spentUnits.signum() <= 0 || spentUnits.compareTo(budgetUnits) > 0 || roster.isEmpty())
            throw new IllegalArgumentException("Invalid muster call snapshot.");
        BigInteger total = BigInteger.ZERO;
        java.util.Set<String> keys = new java.util.HashSet<String>();
        for (Entry entry : roster) {
            if (!keys.add(entry.unit.key) || !faction.equals(entry.unit.faction) || entry.unit.weight <= 0
                    || "rohan".equals(faction) && !entry.unit.mounted())
                throw new IllegalArgumentException("Invalid native muster roster.");
            total = total.add(KOMEMusterRoster.costUnits(entry.unit).multiply(entry.count));
        }
        if (!total.equals(spentUnits)) throw new IllegalArgumentException("Muster cost does not match roster.");
        if (status == Status.ARRIVED ? arrivedAtMillis < dueAtMillis || arrivalReceipt.trim().isEmpty() || !pendingReason.isEmpty()
                : arrivedAtMillis != -1L || !arrivalReceipt.isEmpty()
                    || (status == Status.SCHEDULED ? !pendingReason.isEmpty() : pendingReason.isEmpty()))
            throw new IllegalArgumentException("Invalid muster arrival state.");
    }
    public NBTTagCompound writeToNBT() {
        validate();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Faction", faction); tag.setLong("Season", seasonId); tag.setString("CalledBy", calledBy.toString());
        tag.setLong("CalledAt", calledAtMillis); tag.setLong("DueAt", dueAtMillis); tag.setLong("Seed", seed);
        tag.setTag("Capital", capital.writeToNBT());
        tag.setString("RateUnits", rateUnits.toString()); tag.setString("BudgetUnits", budgetUnits.toString());
        tag.setString("SpentUnits", spentUnits.toString()); tag.setInteger("Multiplier", budgetMultiplier);
        tag.setInteger("DelayHours", arrivalDelayHours); tag.setString("Status", status.name());
        tag.setString("PendingReason", pendingReason); tag.setString("ArrivalReceipt", arrivalReceipt);
        tag.setLong("ArrivedAt", arrivedAtMillis);
        NBTTagList list = new NBTTagList();
        for (Entry entry : roster) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("Key", entry.unit.key); row.setString("Faction", entry.unit.faction);
            row.setString("Entity", entry.unit.entityId); row.setString("Mount", entry.unit.mountId);
            row.setInteger("Cost", entry.unit.cost); row.setInteger("Weight", entry.unit.weight);
            row.setString("Count", entry.count.toString()); list.appendTag(row);
        }
        tag.setTag("Roster", list); return tag;
    }
    public static KOMEMusterRecord readFromNBT(NBTTagCompound tag) { return new KOMEMusterRecord(tag); }
    private static String string(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, 8)) throw new IllegalArgumentException("Missing/invalid muster " + key);
        return tag.getString(key);
    }
    private static long number(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, 4)) throw new IllegalArgumentException("Missing/invalid muster " + key);
        return tag.getLong(key);
    }
    private static BigInteger integer(NBTTagCompound tag, String key) {
        String value = string(tag, key);
        BigInteger result = new BigInteger(value);
        if (!result.toString().equals(value)) throw new IllegalArgumentException("Noncanonical muster " + key);
        return result;
    }
}
