package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Sole authority for foreign Build grants and placement authorization. */
public final class KOMEForeignConstructionService {
    private KOMEForeignConstructionService() { }
    public static final class Decision {
        public final boolean allowed; public final String reason;
        private Decision(boolean allowed, String reason) { this.allowed = allowed; this.reason = reason; }
        public static Decision allow() { return new Decision(true, ""); }
        public static Decision deny(String reason) { return new Decision(false, reason); }
    }
    public static Decision grant(KOMEWorldData data, String tileId, UUID actor, String granteeFaction, long now) {
        KOMEConquestTile tile = tile(data, tileId); if (tile == null) return Decision.deny("The selected conquest tile is not claimed.");
        String owner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()), grantee = KOMEAlliance.normalizeFactionKey(granteeFaction);
        if (grantee.length() == 0 || owner.equals(grantee)) return Decision.deny("Choose a different pledged faction for a foreign construction grant.");
        if (!KOMERulerAuthorization.canActAsRuler(data, owner, actor)) return Decision.deny("Only the recognized ruler of this tile's controlling faction may grant construction permission.");
        KOMEForeignConstructionPermission record = new KOMEForeignConstructionPermission(); record.tileId = tile.id; record.grantingFaction = owner; record.granteeFaction = grantee; record.grantedBy = actor; record.grantedAtMillis = Math.max(0L, now);
        data.foreignConstructionPermissions.put(record.key(), record);
        KOMEAuditService.record(data, now, "BUILD", "CONSTRUCTION_GRANT", actor == null ? "" : actor.toString(),
            record.key(), "Foreign construction permission granted", grantee);
        data.markDirty(); return Decision.allow();
    }
    public static Decision revoke(KOMEWorldData data, String tileId, UUID actor, String granteeFaction) {
        KOMEConquestTile tile = tile(data, tileId); if (tile == null) return Decision.deny("The selected conquest tile is not claimed.");
        String owner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()), grantee = KOMEAlliance.normalizeFactionKey(granteeFaction);
        if (!KOMERulerAuthorization.canActAsRuler(data, owner, actor)) return Decision.deny("Only the recognized ruler of this tile's controlling faction may revoke construction permission.");
        if (data.foreignConstructionPermissions.remove(KOMEForeignConstructionPermission.key(tile.id, owner, grantee)) == null) return Decision.deny("That faction has no current construction permission for this tile.");
        KOMEAuditService.record(data, System.currentTimeMillis(), "BUILD", "CONSTRUCTION_REVOKE",
            actor == null ? "" : actor.toString(), KOMEForeignConstructionPermission.key(tile.id, owner, grantee),
            "Foreign construction permission revoked for future builds", grantee);
        data.markDirty(); return Decision.allow();
    }
    public static Decision canConstruct(KOMEWorldData data, String tileId, UUID builder, String builderFaction) {
        KOMEConquestTile tile = tile(data, tileId); if (tile == null) return Decision.deny("The selected conquest tile is not claimed.");
        String owner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()), builderKey = KOMEAlliance.normalizeFactionKey(builderFaction);
        if (builderKey.length() == 0) return Decision.deny("You must be pledged to place a Build.");
        if (owner.equals(builderKey)) return Decision.allow();
        if (data.foreignConstructionPermissions.containsKey(KOMEForeignConstructionPermission.key(tile.id, owner, builderKey))) return Decision.allow();
        return Decision.deny("The tile controller has not granted your faction construction permission for this tile.");
    }
    public static List<KOMEForeignConstructionPermission> currentForTile(KOMEWorldData data, String tileId) {
        KOMEConquestTile tile = tile(data, tileId); if (tile == null) return Collections.emptyList(); String owner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
        List<KOMEForeignConstructionPermission> result = new ArrayList<KOMEForeignConstructionPermission>();
        for (KOMEForeignConstructionPermission record : data.foreignConstructionPermissions.values()) if (record != null && tile.id.equals(record.tileId) && owner.equals(record.grantingFaction)) result.add(record);
        Collections.sort(result, new Comparator<KOMEForeignConstructionPermission>() { public int compare(KOMEForeignConstructionPermission a, KOMEForeignConstructionPermission b) { return a.granteeFaction.compareTo(b.granteeFaction); } }); return result;
    }
    private static KOMEConquestTile tile(KOMEWorldData data, String tileId) { KOMEConquestTile tile = data == null ? null : data.conquestTiles.get(KOMEConquestTile.normalizeId(tileId)); return tile == null || !tile.isClaimed() ? null : tile; }
}
