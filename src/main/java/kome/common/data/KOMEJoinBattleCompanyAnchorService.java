package kome.common.data;

import kome.common.KOMEAddon;
import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.ExpectedConflict;

/**
 * Resolves a selected Campaign company's live physical anchor with a bounded,
 * temporary chunk-loading attempt. Persisted locators are addresses only; a
 * returned anchor always comes from the exact verified live entity.
 */
final class KOMEJoinBattleCompanyAnchorService {
    static final int MAX_UNIQUE_CHUNKS = 4;
    static final long LOAD_ATTEMPT_COOLDOWN_MILLIS = 5000L;
    static final KOMEJoinBattleCompanyAnchorService INSTANCE =
        new KOMEJoinBattleCompanyAnchorService(new ForgeEnvironment());

    interface ChunkLease {
        void close();
    }

    interface Environment {
        long now();
        Entity findLoaded(UUID entityId);
        ChunkLease load(int dimensionId, int chunkX, int chunkZ);
    }

    static final class Resolution {
        final KOMEJoinBattleService.Reason reason;
        final LOTREntityNPC anchor;
        private final ChunkLease chunkLease;
        private final AttemptGate.Lease attemptLease;

        private Resolution(KOMEJoinBattleService.Reason reason, LOTREntityNPC anchor,
                ChunkLease chunkLease, AttemptGate.Lease attemptLease) {
            this.reason = reason;
            this.anchor = anchor;
            this.chunkLease = chunkLease;
            this.attemptLease = attemptLease;
        }

        static Resolution denied(KOMEJoinBattleService.Reason reason) {
            return new Resolution(reason, null, null, null);
        }

        static Resolution loaded(LOTREntityNPC anchor) {
            return new Resolution(KOMEJoinBattleService.Reason.ALLOWED, anchor, null, null);
        }

        static Resolution targeted(LOTREntityNPC anchor, ChunkLease lease,
                AttemptGate.Lease attempt) {
            return new Resolution(KOMEJoinBattleService.Reason.ALLOWED, anchor, lease, attempt);
        }

        void close() {
            try {
                if (chunkLease != null) chunkLease.close();
            } finally {
                if (attemptLease != null) attemptLease.close();
            }
        }
    }

    /** Transient per-player serialization and throttling; no state is persisted. */
    static final class AttemptGate {
        private final Set<UUID> active = new HashSet<UUID>();
        private final Map<UUID, Long> lastStarted = new HashMap<UUID, Long>();

        synchronized Lease acquire(UUID playerId, long now) {
            prune(now);
            if (playerId == null || active.contains(playerId)) return null;
            Long previous = lastStarted.get(playerId);
            if (previous != null && (now < previous.longValue()
                    || now - previous.longValue() < LOAD_ATTEMPT_COOLDOWN_MILLIS)) return null;
            active.add(playerId);
            lastStarted.put(playerId, Long.valueOf(now));
            return new Lease(this, playerId);
        }

        private void prune(long now) {
            for (Iterator<Map.Entry<UUID, Long>> iterator = lastStarted.entrySet().iterator();
                    iterator.hasNext();) {
                Map.Entry<UUID, Long> entry = iterator.next();
                long started = entry.getValue().longValue();
                if (!active.contains(entry.getKey()) && now >= started
                        && now - started >= LOAD_ATTEMPT_COOLDOWN_MILLIS) iterator.remove();
            }
        }

        private synchronized void release(UUID playerId) {
            active.remove(playerId);
        }

        synchronized void clear() {
            active.clear();
            lastStarted.clear();
        }

        static final class Lease {
            private AttemptGate owner;
            private final UUID playerId;
            private Lease(AttemptGate owner, UUID playerId) {
                this.owner = owner;
                this.playerId = playerId;
            }
            void close() {
                AttemptGate value = owner;
                owner = null;
                if (value != null) value.release(playerId);
            }
        }
    }

    private static final class ChunkAddress {
        final int dimensionId, chunkX, chunkZ;
        ChunkAddress(int dimensionId, int chunkX, int chunkZ) {
            this.dimensionId = dimensionId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
        @Override public int hashCode() {
            int result = dimensionId;
            result = 31 * result + chunkX;
            return 31 * result + chunkZ;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof ChunkAddress)) return false;
            ChunkAddress value = (ChunkAddress) other;
            return dimensionId == value.dimensionId && chunkX == value.chunkX
                && chunkZ == value.chunkZ;
        }
    }

    private final Environment environment;
    private final AttemptGate gate = new AttemptGate();

    KOMEJoinBattleCompanyAnchorService(Environment environment) {
        if (environment == null) throw new IllegalArgumentException("Join Battle anchor environment is required");
        this.environment = environment;
    }

    Resolution resolve(KOMEWorldData data, EntityPlayerMP player, String companyId,
            String tileId, String conflictId, long conflictRevision) {
        if (data == null || player == null || companyId == null)
            return Resolution.denied(KOMEJoinBattleService.Reason.INVALID_REQUEST);
        KOMEJoinBattleService.SelectionResult initial = revalidate(data, player, tileId,
            conflictId, conflictRevision, companyId);
        if (!initial.isAllowed()) return Resolution.denied(initial.reason);

        KOMEArmyCompany company = data.armyCompanies.get(companyId);
        if (company == null) return Resolution.denied(KOMEJoinBattleService.Reason.COMPANY_INCOHERENT);
        List<UUID> members = sortedMembers(company);

        // Prefer the exact live entity and avoid any ticket/cooldown when it is already loaded.
        for (UUID memberId : members) {
            LOTREntityNPC anchor = verifiedLive(data, companyId, memberId, tileId);
            if (anchor != null) return Resolution.loaded(anchor);
        }

        LinkedHashMap<ChunkAddress, List<UUID>> candidates = locatorCandidates(
            data, companyId, members, tileId);
        if (candidates.isEmpty())
            return Resolution.denied(KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNKNOWN);

        UUID playerId = KOMEReflection.getEntityUUID(player);
        AttemptGate.Lease attempt = gate.acquire(playerId, environment.now());
        if (attempt == null)
            return Resolution.denied(KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNAVAILABLE);

        boolean transferred = false;
        int tried = 0;
        try {
            for (Map.Entry<ChunkAddress, List<UUID>> entry : candidates.entrySet()) {
                if (tried++ >= MAX_UNIQUE_CHUNKS) break;
                ChunkAddress address = entry.getKey();
                ChunkLease chunk = null;
                try {
                    chunk = environment.load(address.dimensionId, address.chunkX, address.chunkZ);
                    if (chunk == null) continue;

                    // Chunk/entity hooks may mutate company, conflict, pledge, UUID, or coherence.
                    KOMEJoinBattleService.SelectionResult current = revalidate(data, player,
                        tileId, conflictId, conflictRevision, companyId);
                    if (!current.isAllowed()) return Resolution.denied(current.reason);

                    for (UUID memberId : entry.getValue()) {
                        LOTREntityNPC anchor = verifiedLive(data, companyId, memberId, tileId);
                        if (anchor == null) continue;
                        transferred = true;
                        return Resolution.targeted(anchor, chunk, attempt);
                    }
                } catch (Throwable unavailable) {
                    // This candidate is unverified. The bounded attempt may continue.
                } finally {
                    if (!transferred && chunk != null) chunk.close();
                }
            }
            return Resolution.denied(KOMEJoinBattleService.Reason.COMPANY_LOCATION_UNAVAILABLE);
        } finally {
            if (!transferred) attempt.close();
        }
    }

    private KOMEJoinBattleService.SelectionResult revalidate(KOMEWorldData data,
            EntityPlayerMP player, String tileId, String conflictId, long revision,
            String companyId) {
        try {
            return KOMEJoinBattleService.INSTANCE.validateSelectedCompany(data, player, tileId,
                ExpectedConflict.at(conflictId, revision), companyId);
        } catch (RuntimeException invalid) {
            return KOMEJoinBattleService.INSTANCE.validateSelectedCompany(data, player, tileId,
                null, companyId);
        }
    }

    private LinkedHashMap<ChunkAddress, List<UUID>> locatorCandidates(KOMEWorldData data,
            String companyId, List<UUID> members, String tileId) {
        LinkedHashMap<ChunkAddress, List<UUID>> result =
            new LinkedHashMap<ChunkAddress, List<UUID>>();
        KOMEArmyCompany company = data.armyCompanies.get(companyId);
        if (company == null) return result;
        for (UUID memberId : members) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(memberId);
            if (!KOMEJoinBattlePhysicalAccess.validMember(company, record, memberId, tileId)) continue;
            KOMEHiredUnitPhysicalLocator locator = record.getPhysicalLocator();
            if (locator == null || !memberId.equals(locator.getEntityId())
                    || !tileId.equals(locator.getPhysicalTileId())) continue;
            ChunkAddress address = new ChunkAddress(locator.getDimensionId(),
                locator.getChunkX(), locator.getChunkZ());
            List<UUID> group = result.get(address);
            if (group == null) {
                group = new ArrayList<UUID>();
                result.put(address, group);
            }
            group.add(memberId);
        }
        return result;
    }

    private LOTREntityNPC verifiedLive(KOMEWorldData data, String companyId,
            UUID expectedEntityId, String tileId) {
        KOMEArmyCompany company = data.armyCompanies.get(companyId);
        KOMEHiredUnitRecord record = data.hiredUnits.get(expectedEntityId);
        if (!KOMEJoinBattlePhysicalAccess.validMember(company, record, expectedEntityId, tileId))
            return null;
        Entity entity = environment.findLoaded(expectedEntityId);
        if (!(entity instanceof LOTREntityNPC) || entity.isDead || !entity.isEntityAlive()
                || !expectedEntityId.equals(KOMEReflection.getEntityUUID(entity))) return null;
        KOMEHiredUnitPhysicalLocatorService.observe(data, record, entity,
            KOMEHiredUnitPhysicalLocator.CaptureKind.LIVE_OBSERVATION,
            environment.now(), true);
        KOMETileResolution physical = KOMEBuildService.tileAtWorldCoordinates(
            entity.worldObj.provider.dimensionId, entity.posX, entity.posZ);
        return physical.status == KOMETileResolution.Status.RESOLVED
            && tileId.equals(physical.tileId) ? (LOTREntityNPC) entity : null;
    }

    private static List<UUID> sortedMembers(KOMEArmyCompany company) {
        List<UUID> members = new ArrayList<UUID>(company.units);
        Collections.sort(members, new Comparator<UUID>() {
            @Override public int compare(UUID first, UUID second) {
                return first.toString().compareTo(second.toString());
            }
        });
        return members;
    }

    void clearTransientStateForTests() {
        gate.clear();
    }

    private static final class ForgeEnvironment implements Environment {
        @Override public long now() {
            return System.currentTimeMillis();
        }

        @Override public Entity findLoaded(UUID entityId) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.worldServers == null || entityId == null) return null;
            for (WorldServer world : server.worldServers) if (world != null)
                for (Object value : world.loadedEntityList) if (value instanceof Entity
                        && !((Entity) value).isDead
                        && entityId.equals(KOMEReflection.getEntityUUID((Entity) value)))
                    return (Entity) value;
            return null;
        }

        @Override public ChunkLease load(int dimensionId, int chunkX, int chunkZ) {
            MinecraftServer server = MinecraftServer.getServer();
            WorldServer world = server == null ? null : server.worldServerForDimension(dimensionId);
            if (world == null || world.provider == null
                    || world.provider.dimensionId != dimensionId) return null;
            ForgeChunkManager.Ticket ticket = null;
            try {
                ticket = ForgeChunkManager.requestTicket(KOMEAddon.instance, world,
                    ForgeChunkManager.Type.NORMAL);
                if (ticket == null) return null;
                ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(chunkX, chunkZ));
                int blockX = chunkX << 4, blockZ = chunkZ << 4;
                if (!KOMEStrategicDeploymentResolver.ensureChunkAvailable(world, blockX, blockZ)) {
                    ForgeChunkManager.releaseTicket(ticket);
                    return null;
                }
                return new ForgeLease(ticket);
            } catch (Throwable failure) {
                if (ticket != null) try { ForgeChunkManager.releaseTicket(ticket); }
                    catch (Throwable ignored) { }
                return null;
            }
        }
    }

    private static final class ForgeLease implements ChunkLease {
        private ForgeChunkManager.Ticket ticket;
        ForgeLease(ForgeChunkManager.Ticket ticket) { this.ticket = ticket; }
        @Override public void close() {
            ForgeChunkManager.Ticket value = ticket;
            ticket = null;
            if (value != null) try { ForgeChunkManager.releaseTicket(value); }
                catch (Throwable ignored) { }
        }
    }
}
