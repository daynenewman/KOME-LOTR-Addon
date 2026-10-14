package kome.common.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Persisted reset journal; entity receipts use the same season/company token. */
public final class KOMESeasonResetState {
    public long seasonId;
    public boolean ownershipReset;
    public boolean prepared;
    public String failure = "";
    public final Map<String, Return> companies = new LinkedHashMap<String, Return>();

    public static final class Return {
        public String companyId = "", nativeFaction = "", origin = "", destination = "", reason = "";
        public UUID owner;
        public boolean returnRequired, cleared, complete;
        public KOMEFactionCapitalRecord capital;
        public final Set<Long> originChunks = new LinkedHashSet<Long>();
        public final Set<UUID> units = new LinkedHashSet<UUID>();
        public final Set<UUID> virtualUnits = new LinkedHashSet<UUID>();
    }

    public boolean complete() {
        if (!ownershipReset || !prepared || !failure.isEmpty()) return false;
        for (Return entry : companies.values()) if (!entry.complete) return false;
        return true;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Schema", 1);
        tag.setLong("Season", seasonId);
        tag.setBoolean("OwnershipReset", ownershipReset);
        tag.setBoolean("Prepared", prepared);
        tag.setString("Failure", failure);
        NBTTagList entries = new NBTTagList();
        for (Return entry : companies.values()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("Company", entry.companyId);
            row.setString("Owner", entry.owner.toString());
            row.setString("NativeFaction", entry.nativeFaction);
            row.setString("Origin", entry.origin);
            row.setString("Destination", entry.destination);
            row.setString("Reason", entry.reason);
            row.setBoolean("ReturnRequired", entry.returnRequired);
            row.setBoolean("Cleared", entry.cleared);
            row.setBoolean("Complete", entry.complete);
            if (entry.capital != null) row.setTag("Capital", entry.capital.writeToNBT());
            NBTTagList members = new NBTTagList();
            for (UUID id : entry.units) {
                NBTTagCompound unit = new NBTTagCompound();
                unit.setString("Id", id.toString());
                unit.setBoolean("Virtual", entry.virtualUnits.contains(id));
                members.appendTag(unit);
            }
            row.setTag("Units", members);
            NBTTagList chunks = new NBTTagList();
            for (Long key : entry.originChunks) { NBTTagCompound chunk = new NBTTagCompound(); chunk.setLong("Key", key); chunks.appendTag(chunk); }
            row.setTag("OriginChunks", chunks);
            entries.appendTag(row);
        }
        tag.setTag("Companies", entries);
        return tag;
    }

    public static KOMESeasonResetState read(NBTTagCompound tag, long currentSeason) {
        if (!tag.hasKey("Schema", 3) || tag.getInteger("Schema") != 1
                || !tag.hasKey("Companies", 9)) throw new IllegalArgumentException("Invalid season reset journal schema");
        KOMESeasonResetState state = new KOMESeasonResetState();
        state.seasonId = tag.getLong("Season");
        if (state.seasonId < 0 || state.seasonId > currentSeason)
            throw new IllegalArgumentException("Invalid reset season");
        state.ownershipReset = tag.getBoolean("OwnershipReset");
        state.prepared = tag.getBoolean("Prepared");
        state.failure = tag.getString("Failure");
        NBTTagList rows = compounds(tag, "Companies");
        Set<UUID> uniqueUnits = new LinkedHashSet<UUID>();
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            Return entry = new Return();
            entry.companyId = row.getString("Company");
            entry.owner = UUID.fromString(row.getString("Owner"));
            entry.nativeFaction = row.getString("NativeFaction");
            entry.origin = row.getString("Origin");
            entry.destination = row.getString("Destination");
            entry.reason = row.getString("Reason");
            entry.returnRequired = row.getBoolean("ReturnRequired");
            entry.cleared = row.getBoolean("Cleared");
            entry.complete = row.getBoolean("Complete");
            if (row.hasKey("Capital") && !row.hasKey("Capital", 10)) throw new IllegalArgumentException("Invalid reset capital");
            if (row.hasKey("Capital", 10)) entry.capital = KOMEFactionCapitalRecord.readFromNBT(row.getCompoundTag("Capital"));
            NBTTagList chunks = compounds(row, "OriginChunks");
            for (int c = 0; c < chunks.tagCount(); c++) entry.originChunks.add(chunks.getCompoundTagAt(c).getLong("Key"));
            NBTTagList members = compounds(row, "Units");
            for (int u = 0; u < members.tagCount(); u++) {
                NBTTagCompound member = members.getCompoundTagAt(u);
                UUID id = UUID.fromString(member.getString("Id"));
                if (!uniqueUnits.add(id)) throw new IllegalArgumentException("Duplicate reset unit");
                entry.units.add(id);
                if (member.getBoolean("Virtual")) entry.virtualUnits.add(id);
            }
            if (entry.companyId.isEmpty() || entry.units.isEmpty()
                    || !KOMEAlliance.allFactionKeys().contains(entry.nativeFaction)
                    || !KOMEConquestTile.normalizeId(entry.origin).equals(entry.origin) || entry.origin.isEmpty()
                    || !KOMEConquestTile.isCanonicalTileId(entry.origin)
                    || !entry.destination.isEmpty() && !KOMEConquestTile.isCanonicalTileId(entry.destination)
                    || !entry.returnRequired && !entry.origin.equals(entry.destination)
                    || entry.capital != null && (!entry.nativeFaction.equals(entry.capital.getFactionId())
                        || !entry.destination.equals(entry.capital.getCapitalTileId()))
                    || entry.complete && (!entry.cleared || entry.destination.isEmpty() || entry.returnRequired && entry.capital == null)
                    || state.companies.put(entry.companyId, entry) != null)
                throw new IllegalArgumentException("Invalid reset company entry");
        }
        if (state.seasonId == 0 && (state.ownershipReset || state.prepared || !state.companies.isEmpty())
                || state.prepared && !state.ownershipReset || !state.prepared && !state.companies.isEmpty())
            throw new IllegalArgumentException("Invalid reset checkpoint ordering");
        return state;
    }

    private static NBTTagList compounds(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, 9)) throw new IllegalArgumentException("Missing reset " + key);
        NBTTagList list = (NBTTagList) tag.getTag(key);
        if (list.tagCount() > 0 && list.func_150303_d() != 10)
            throw new IllegalArgumentException("Invalid reset " + key + " entries");
        return list;
    }
}
