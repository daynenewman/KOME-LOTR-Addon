package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Server-only pre-placement seam. EXTERIOR is a constraint, not a polygon or deployment-area ID. */
public final class KOMEStrategicArrivalPlacement {
    private KOMEStrategicArrivalPlacement() { }
    public enum Directive { ORDINARY, EXTERIOR_REQUIRED }
    public enum Code { ORDINARY_PLACEMENT, EXTERIOR_PLACEMENT_UNAVAILABLE, POLICY_UNAVAILABLE, INVALID_ARRIVAL }
    public interface Provider { Directive placement(Context arrival); }
    public static final Provider ORDINARY = arrival -> Directive.ORDINARY;

    /** Immutable authoritative event facts; a provider can constrain placement, never rewrite identity. */
    public static final class Context {
        public final String eventId, orderId, companyId, tileId, factionId, conflictId;
        public final int routeIndex, dimensionId;
        public final long acceptedAtMillis, conflictRevision;
        public final KOMEConflictRecord.EntryOrigin origin;
        public final boolean createsConflict;
        public final List<String> originalGarrisonIds;
        private Context(String order, String company, String tile, String faction, int index,
                int dimension, long at, String conflict, long revision,
                KOMEConflictRecord.EntryOrigin origin, boolean creates, List<String> garrison) {
            if (order.isEmpty() || tile.isEmpty() || index < 0 || at < 0 || revision < 0
                    || origin == null || !conflict.isEmpty() && !conflict.matches("CF[1-9][0-9]*")
                    || conflict.isEmpty() != (revision == 0))
                throw new IllegalArgumentException("Invalid strategic arrival event");
            orderId = order; companyId = company; tileId = tile; factionId = faction;
            routeIndex = index; dimensionId = dimension; acceptedAtMillis = at;
            eventId = order + ":ARRIVAL:" + index + ":" + at;
            conflictId = conflict; conflictRevision = revision; this.origin = origin;
            createsConflict = creates;
            originalGarrisonIds = Collections.unmodifiableList(new ArrayList<String>(garrison));
        }
        public NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("SchemaVersion", 1);
            tag.setString("OrderId", orderId); tag.setString("CompanyId", companyId);
            tag.setString("TileId", tileId); tag.setString("FactionId", factionId);
            tag.setInteger("RouteIndex", routeIndex); tag.setInteger("DimensionId", dimensionId);
            tag.setLong("AcceptedAtMillis", acceptedAtMillis); tag.setString("ConflictId", conflictId);
            tag.setLong("ConflictRevision", conflictRevision); tag.setString("Origin", origin.name());
            tag.setBoolean("CreatesConflict", createsConflict);
            NBTTagList list = new NBTTagList();
            for (String id : originalGarrisonIds) { NBTTagCompound row = new NBTTagCompound(); row.setString("Id", id); list.appendTag(row); }
            tag.setTag("OriginalGarrisonIds", list); return tag;
        }
        public static Context read(NBTTagCompound tag) {
            for (String key : new String[]{"OrderId", "CompanyId", "TileId", "FactionId", "ConflictId", "Origin"})
                require(tag, key, 8);
            require(tag, "SchemaVersion", 3); require(tag, "RouteIndex", 3); require(tag, "DimensionId", 3);
            require(tag, "AcceptedAtMillis", 4); require(tag, "ConflictRevision", 4);
            require(tag, "CreatesConflict", 1); require(tag, "OriginalGarrisonIds", 9);
            if (tag.getInteger("SchemaVersion") != 1) throw new IllegalArgumentException("Unsupported strategic arrival schema");
            NBTTagList list = tag.getTagList("OriginalGarrisonIds", 10);
            if (((NBTTagList) tag.getTag("OriginalGarrisonIds")).tagCount() != list.tagCount())
                throw new IllegalArgumentException("Invalid original cohort list");
            List<String> ids = new ArrayList<String>();
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound row = list.getCompoundTagAt(i); require(row, "Id", 8);
                String id = row.getString("Id");
                if (id.isEmpty() || ids.contains(id)) throw new IllegalArgumentException("Invalid original cohort identity");
                ids.add(id);
            }
            return new Context(tag.getString("OrderId"), tag.getString("CompanyId"), tag.getString("TileId"),
                tag.getString("FactionId"), tag.getInteger("RouteIndex"), tag.getInteger("DimensionId"),
                tag.getLong("AcceptedAtMillis"), tag.getString("ConflictId"), tag.getLong("ConflictRevision"),
                KOMEConflictRecord.EntryOrigin.valueOf(tag.getString("Origin")), tag.getBoolean("CreatesConflict"), ids);
        }
    }
    public static final class Decision {
        public final Code code;
        public final Context context;
        public final String reason;
        private Decision(Code code, Context context, String reason) { this.code = code; this.context = context; this.reason = reason; }
        public boolean ordinaryAllowed() { return code == Code.ORDINARY_PLACEMENT; }
    }

    /** Must run before target selection/chunk tickets/spawning. Pending exterior requirements are durable. */
    public static Decision preflight(KOMEWorldData data, KOMEArmyMovementOrder order,
            String destination, KOMEConflictMovementService.LegalArrivalReceipt receipt) {
        if (data == null || order == null || data.armyMovements.get(order.id) != order)
            return new Decision(Code.INVALID_ARRIVAL, null, "Persisted server movement order is required.");
        data.ensureWritable();
        String tile = KOMEConquestTile.normalizeId(destination);
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        String faction = KOMEAlliance.normalizeFactionKey(company == null ? order.ownerFaction : company.faction);
        if (order.nextRouteIndex < 0 || order.nextRouteIndex >= order.routeTiles.size()
                || !tile.equals(KOMEConquestTile.normalizeId(order.routeTiles.get(order.nextRouteIndex)))
                || !(KOMEArmyMovementOrder.MOVING.equals(order.status) || order.isPendingSpawn()
                    || KOMEArmyMovementOrder.SPAWNING.equals(order.status))
                || receipt != null && (!order.id.equals(receipt.movementOrderId)
                    || !order.companyId.equals(receipt.detachmentId) || !tile.equals(receipt.destinationTileId)
                    || order.arrivalMillis != receipt.acceptedAtMillis || !faction.equals(receipt.detachmentFactionId)))
            return new Decision(Code.INVALID_ARRIVAL, null, "Arrival identity disagrees with the accepted strategic leg.");
        Context event = order.strategicArrival;
        if (event != null && (!event.orderId.equals(order.id) || !event.companyId.equals(order.companyId)
                || !event.tileId.equals(tile) || !event.factionId.equals(faction)
                || event.routeIndex != order.nextRouteIndex || event.acceptedAtMillis != order.arrivalMillis
                || event.dimensionId != order.arrivalDimension))
            return new Decision(Code.INVALID_ARRIVAL, event, "Pending arrival event does not match this strategic leg.");
        // Identity is durable. Conflict classification is re-read before every physical attempt:
        // a conflict may have begun while this accepted strategic leg was pending/unloaded.
        {
            KOMEConflictRecord conflict = data.getConflictService().get(tile);
            KOMEConflictRecord.EntryOrigin origin = receipt == null
                ? KOMEConflictRecord.EntryOrigin.LEGAL_ARRIVAL : receipt.origin;
            // Ordinary legal friendly arrivals can also be relief; physical presence never proves this.
            if (receipt == null && conflict != null && conflict.isActive())
                origin = KOMEConflictMovementService.placementOrigin(data, conflict, faction);
            List<String> garrison = new ArrayList<String>();
            if (receipt != null) for (KOMEConflictContracts.GarrisonParticipantSeed seed : receipt.originalGarrison)
                garrison.add(seed.cohort.detachmentId);
            Collections.sort(garrison);
            Context current = new Context(order.id, order.companyId, tile, faction, order.nextRouteIndex,
                order.arrivalDimension, order.arrivalMillis,
                conflict == null ? "" : conflict.getConflictId(), conflict == null ? 0 : conflict.getRevision(),
                origin, receipt != null && (conflict == null || !conflict.isActive()), garrison);
            if (event == null || !current.write().equals(event.write())) {
                order.strategicArrival = current; event = current; data.markDirty();
            }
        }
        Directive directive;
        try { directive = data.strategicArrivalPlacement(event); }
        catch (RuntimeException unavailable) {
            return new Decision(Code.POLICY_UNAVAILABLE, event, "Strategic placement policy is unavailable; arrival remains pending.");
        }
        if (directive == null) return new Decision(Code.POLICY_UNAVAILABLE, event, "No authoritative placement decision is available.");
        if (directive == Directive.EXTERIOR_REQUIRED && !order.exteriorPlacementRequired) {
            order.exteriorPlacementRequired = true; data.markDirty();
        }
        if (order.exteriorPlacementRequired)
            return new Decision(Code.EXTERIOR_PLACEMENT_UNAVAILABLE, event,
                "Exterior placement required; safe exterior placement is not yet supplied. Arrival remains pending.");
        return new Decision(Code.ORDINARY_PLACEMENT, event, "Ordinary legal strategic placement.");
    }
    private static void require(NBTTagCompound tag, String key, int type) {
        if (!tag.hasKey(key, type)) throw new IllegalArgumentException("Invalid strategic arrival " + key);
    }
}
