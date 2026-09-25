package kome.common.data;

import kome.common.KOMEReflection;
import lotr.common.LOTRDimension;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sole normal read/mutation boundary for authoritative capitals.
 * This service is neutral about muster timing: KOM-11/siege logic must choose normal
 * deployment before Encirclement and exterior relief deployment after Encirclement starts.
 */
public final class KOMEFactionCapitalService {
    private KOMEFactionCapitalService() { }

    public static Map<String, KOMEFactionCapitalRecord> prepareFreshDefaults(
            World contextWorld, long nowMillis) {
        World middleEarth = contextWorld;
        if (middleEarth == null || middleEarth.provider == null
                || middleEarth.provider.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            MinecraftServer server = MinecraftServer.getServer();
            middleEarth = server == null ? null
                : server.worldServerForDimension(LOTRDimension.MIDDLE_EARTH.dimensionID);
        }
        return KOMEFactionCapitalDefaults.resolveLive(middleEarth, nowMillis);
    }

    static void initializeFresh(KOMEWorldData data,
            Map<String, KOMEFactionCapitalRecord> prepared) {
        if (data == null) throw new IllegalArgumentException("World data is required.");
        if (!data.factionCapitals.isEmpty())
            throw new IllegalStateException("Fresh capital initialization requires an empty capital map.");
        Map<String, KOMEFactionCapitalRecord> complete = validateCompleteSet(prepared);
        List<KOMEAuditEntry> audits = new ArrayList<KOMEAuditEntry>();
        for (KOMEFactionCapitalRecord record : complete.values()) {
            audits.add(new KOMEAuditEntry(record.getUpdatedAtMillis(), "CAPITAL", "INITIALIZE",
                "SERVER", record.getFactionId(), "Fresh schema-4 capital default",
                auditDetails(null, record)));
        }
        data.publishFactionCapitals(complete, audits);
    }

    static void initializeMetadataFixture(KOMEWorldData data) {
        initializeFresh(data, KOMEFactionCapitalDefaults.metadataFixture(0L));
    }

    public static KOMEFactionCapitalRecord getCapital(KOMEWorldData data, String faction) {
        if (data == null) return null;
        return data.factionCapitals.get(KOMEAlliance.normalizeFactionKey(faction));
    }

    public static String getCapitalTileId(KOMEWorldData data, String faction) {
        KOMEFactionCapitalRecord record = getCapital(data, faction);
        return record == null ? "" : record.getCapitalTileId();
    }

    public static boolean isCapitalTile(KOMEWorldData data, String faction, String tileId) {
        KOMEFactionCapitalRecord record = getCapital(data, faction);
        return record != null && record.getCapitalTileId().equals(
            KOMEConquestTile.normalizeId(tileId));
    }

    public static List<String> getCapitalFactionsForTile(KOMEWorldData data, String tileId) {
        if (data == null) return Collections.emptyList();
        String tile = KOMEConquestTile.normalizeId(tileId);
        List<String> result = new ArrayList<String>();
        for (String faction : KOMEAlliance.allFactionKeys()) {
            KOMEFactionCapitalRecord record = data.factionCapitals.get(faction);
            if (record != null && tile.equals(record.getCapitalTileId())) result.add(faction);
        }
        return Collections.unmodifiableList(result);
    }

    public static RelocationBlock relocationBlock(KOMEWorldData data, String faction) {
        String key = KOMEAlliance.normalizeFactionKey(faction);
        if (data == null || !KOMEAlliance.allFactionKeys().contains(key))
            return RelocationBlock.blocked("Capital relocation state is unavailable.");
        try {
            for (Map.Entry<String, KOMEWar> entry : data.wars.entrySet()) {
                KOMEWar war = entry.getValue();
                if (war == null || war.id == null || war.id.trim().length() == 0
                        || !(KOMEWar.ACTIVE.equals(war.status) || KOMEWar.ENDING.equals(war.status)
                            || KOMEWar.ENDED.equals(war.status)))
                    return RelocationBlock.blocked("War state is malformed; relocation fails closed.");
                boolean sideOne = war.sideOneFactions.contains(key);
                boolean sideTwo = war.sideTwoFactions.contains(key);
                if (sideOne && sideTwo)
                    return RelocationBlock.blocked("War membership is contradictory; relocation fails closed.");
                if ((KOMEWar.ACTIVE.equals(war.status) || KOMEWar.ENDING.equals(war.status))
                        && war.sideOf(key) > 0)
                    return RelocationBlock.blocked("Faction participates in "
                        + war.status + " war " + war.id + ".");
            }
        } catch (RuntimeException unreadable) {
            return RelocationBlock.blocked("War state could not be read safely.");
        }
        return RelocationBlock.allowed();
    }

    public static boolean hasRelocationBlockingWar(KOMEWorldData data, String faction) {
        return relocationBlock(data, faction).blocked;
    }

    public static KOMEStrategicDeploymentResolver.Anchor requireCapitalDeployment(
            KOMEWorldData data, String faction, World deploymentWorld) {
        KOMEFactionCapitalRecord record = getCapital(data, faction);
        if (record == null)
            throw new IllegalStateException("No authoritative capital exists for faction: "
                + KOMEAlliance.normalizeFactionKey(faction));
        KOMEStrategicDeploymentResolver.Validation validation =
            KOMEStrategicDeploymentResolver.validateStored(deploymentWorld,
                record.getCapitalTileId(), record.getDeploymentDimensionId(),
                record.getDeploymentX(), record.getDeploymentY(), record.getDeploymentZ());
        if (!validation.valid)
            throw new IllegalStateException("Capital deployment is unavailable for "
                + record.getFactionId() + ": " + validation.reason);
        return validation.anchor;
    }

    public static String readiness(KOMEWorldData data, String faction, World deploymentWorld) {
        try {
            requireCapitalDeployment(data, faction, deploymentWorld);
            return "READY";
        } catch (RuntimeException unavailable) {
            return "NOT_READY: " + KOMEFactionCapitalRecord.clean(unavailable.getMessage());
        }
    }

    public static RelocationResult relocateHere(KOMEWorldData data, EntityPlayerMP actor,
            String faction, long nowMillis) {
        if (data == null || actor == null || !actor.canCommandSenderUseCommand(2, "kome"))
            return RelocationResult.failure("Only an authorized operator may relocate a capital.");
        String key = KOMEAlliance.normalizeFactionKey(faction);
        if (!KOMEAlliance.allFactionKeys().contains(key))
            return RelocationResult.failure("Unknown supported faction: " + faction);
        KOMEFactionCapitalRecord old = getCapital(data, key);
        if (old == null) return RelocationResult.failure("Faction capital is missing: " + key);
        RelocationBlock block = relocationBlock(data, key);
        if (block.blocked) return RelocationResult.failure(block.reason);
        World world = KOMEReflection.getWorld(actor);
        if (world == null || world.provider == null
                || world.provider.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID)
            return RelocationResult.failure("Stand in the live Middle-earth dimension to relocate a capital.");
        KOMETileResolution location = KOMEBuildService.tileAtWorldCoordinates(
            world.provider.dimensionId, actor.posX, actor.posZ);
        if (location.status != KOMETileResolution.Status.RESOLVED)
            return RelocationResult.failure("Current position has no resolved tile: " + location);
        String tile = location.tileId;
        if (!KOMEConquestTile.isCanonicalTileId(tile)
                || KOMEConquestTileDefaults.isRetiredTile(tile)
                || !KOMEConquestTileDefaults.getKnownTileIds().contains(tile))
            return RelocationResult.failure("Current position is not inside a valid canonical conquest tile.");
        KOMEStrategicDeploymentResolver.Validation safe =
            KOMEStrategicDeploymentResolver.resolveAround(world, tile,
                actor.posX, actor.posY, actor.posZ,
                KOMEStrategicDeploymentResolver.DEFAULT_SEARCH_RADIUS);
        if (!safe.valid) return RelocationResult.failure(safe.reason);
        String actorName = KOMEFactionCapitalRecord.clean(actor.getCommandSenderName());
        String actorAudit = actorName + " (" + KOMEReflection.getEntityUUID(actor) + ")";
        KOMEStrategicDeploymentResolver.Anchor anchor = safe.anchor;
        RelocationResult result = relocateValidated(data, key, tile, anchor, true,
            actorAudit, "ADMIN_RELOCATION_HERE", nowMillis);
        if (result.success) data.syncConquestTiles();
        return result;
    }

    static RelocationResult relocateValidated(KOMEWorldData data, String faction, String tile,
            KOMEStrategicDeploymentResolver.Anchor anchor, boolean authorized,
            String actorAudit, String source, long nowMillis) {
        if (!authorized) return RelocationResult.failure(
            "Only an authorized operator may relocate a capital.");
        String key = KOMEAlliance.normalizeFactionKey(faction);
        KOMEFactionCapitalRecord old = getCapital(data, key);
        if (old == null) return RelocationResult.failure("Faction capital is missing: " + key);
        RelocationBlock block = relocationBlock(data, key);
        if (block.blocked) return RelocationResult.failure(block.reason);
        if (anchor == null) return RelocationResult.failure("Validated deployment anchor is required.");
        KOMEStrategicDeploymentResolver.Validation metadata =
            KOMEStrategicDeploymentResolver.validateMetadata(tile, anchor.dimensionId,
                anchor.x, anchor.y, anchor.z);
        if (!metadata.valid) return RelocationResult.failure(metadata.reason);
        KOMEFactionCapitalRecord replacement = new KOMEFactionCapitalRecord(key, tile,
            anchor.dimensionId, anchor.x, anchor.y, anchor.z, nowMillis,
            source, actorAudit);
        Map<String, KOMEFactionCapitalRecord> candidate =
            new LinkedHashMap<String, KOMEFactionCapitalRecord>(data.factionCapitals);
        candidate.put(key, replacement);
        List<KOMEAuditEntry> audit = Collections.singletonList(new KOMEAuditEntry(nowMillis,
            "CAPITAL", "RELOCATE", actorAudit, key, "Administrative relocation",
            auditDetails(old, replacement)));
        data.publishFactionCapitals(validateCompleteSet(candidate), audit);
        return RelocationResult.success(old, replacement);
    }

    public static final class RelocationBlock {
        public final boolean blocked;
        public final String reason;
        private RelocationBlock(boolean blocked, String reason) {
            this.blocked = blocked; this.reason = reason;
        }
        static RelocationBlock allowed() { return new RelocationBlock(false, "Relocation allowed."); }
        static RelocationBlock blocked(String reason) { return new RelocationBlock(true, reason); }
    }

    public static final class RelocationResult {
        public final boolean success;
        public final String reason;
        public final KOMEFactionCapitalRecord oldRecord;
        public final KOMEFactionCapitalRecord newRecord;
        private RelocationResult(boolean success, String reason,
                KOMEFactionCapitalRecord oldRecord, KOMEFactionCapitalRecord newRecord) {
            this.success = success; this.reason = reason;
            this.oldRecord = oldRecord; this.newRecord = newRecord;
        }
        static RelocationResult failure(String reason) {
            return new RelocationResult(false, reason, null, null);
        }
        static RelocationResult success(KOMEFactionCapitalRecord oldRecord,
                KOMEFactionCapitalRecord newRecord) {
            return new RelocationResult(true, "Capital relocated.", oldRecord, newRecord);
        }
    }

    private static String auditDetails(KOMEFactionCapitalRecord oldRecord,
            KOMEFactionCapitalRecord newRecord) {
        String oldValue = oldRecord == null ? "none"
            : oldRecord.getCapitalTileId() + "@" + oldRecord.getDeploymentDimensionId()
                + ":" + oldRecord.getDeploymentX() + "," + oldRecord.getDeploymentY()
                + "," + oldRecord.getDeploymentZ();
        return "old=" + oldValue + ";new=" + newRecord.getCapitalTileId() + "@"
            + newRecord.getDeploymentDimensionId() + ":" + newRecord.getDeploymentX()
            + "," + newRecord.getDeploymentY() + "," + newRecord.getDeploymentZ()
            + ";source=" + newRecord.getSource();
    }

    static Map<String, KOMEFactionCapitalRecord> validateCompleteSet(
            Map<String, KOMEFactionCapitalRecord> candidate) {
        if (candidate == null) throw new IllegalArgumentException("Faction capital set is missing.");
        List<String> supported = KOMEAlliance.allFactionKeys();
        if (candidate.size() != supported.size())
            throw new IllegalArgumentException("Faction capital set must contain exactly "
                + supported.size() + " records; found " + candidate.size() + ".");
        Map<String, KOMEFactionCapitalRecord> ordered =
            new LinkedHashMap<String, KOMEFactionCapitalRecord>();
        for (String faction : supported) {
            KOMEFactionCapitalRecord record = candidate.get(faction);
            if (record == null)
                throw new IllegalArgumentException("Missing faction capital: " + faction);
            if (!faction.equals(record.getFactionId()))
                throw new IllegalArgumentException("Capital map key does not match record faction: "
                    + faction + " / " + record.getFactionId());
            ordered.put(faction, record);
        }
        for (String key : candidate.keySet()) if (!supported.contains(key))
            throw new IllegalArgumentException("Unsupported faction capital: " + key);
        return ordered;
    }
}
