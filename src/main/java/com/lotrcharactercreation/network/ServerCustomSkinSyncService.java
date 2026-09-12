package com.lotrcharactercreation.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.appearance.CustomSkinEntry;
import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;
import com.lotrcharactercreation.appearance.CustomSkinScanLimits;
import com.lotrcharactercreation.appearance.CustomSkinSnapshot;
import com.lotrcharactercreation.appearance.ServerCustomSkinLibrary;

/** Server-authoritative per-connection manifest and bounded transfer coordinator. */
public final class ServerCustomSkinSyncService {

    private static final Logger LOGGER = LogManager.getLogger("lotrcharactercreation");
    private static final ServerCustomSkinSyncService INSTANCE = new ServerCustomSkinSyncService();
    private static final AtomicLong NEXT_EPOCH = new AtomicLong(Math.max(1L, System.currentTimeMillis()));
    private static final AtomicLong NEXT_TRANSFER_ID = new AtomicLong(1L);

    private final Map<UUID, ManifestSession> sessions = new ConcurrentHashMap<UUID, ManifestSession>();
    private final ConcurrentLinkedQueue<PendingAction> pendingActions =
        new ConcurrentLinkedQueue<PendingAction>();
    private final Map<UUID, AtomicInteger> pendingCounts = new ConcurrentHashMap<UUID, AtomicInteger>();

    private ServerCustomSkinSyncService() {}

    public static ServerCustomSkinSyncService getInstance() {
        return INSTANCE;
    }

    public void beginSession(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        CustomSkinSnapshot snapshot = ServerCustomSkinLibrary.getInstance().getCurrentSnapshot();
        List<CustomSkinManifestEntry> entries = new ArrayList<CustomSkinManifestEntry>(
            snapshot.getEntries().size());
        for (CustomSkinEntry entry : snapshot.getEntries()) {
            entries.add(CustomSkinManifestEntry.fromServerEntry(entry));
        }
        String calculatedDigest = CustomSkinManifestEntry.calculateLibraryDigest(entries);
        if (!snapshot.getLibraryDigest().equals(calculatedDigest)) {
            LOGGER.error("Custom skin snapshot digest is incompatible with its manifest metadata; sending an empty library");
            entries = Collections.emptyList();
            calculatedDigest = CustomSkinManifestEntry.calculateLibraryDigest(entries);
        }
        List<List<CustomSkinManifestEntry>> pages = CustomSkinManifestPages.paginate(entries);
        long epoch = nextPositive(NEXT_EPOCH);
        ManifestSession session = new ManifestSession(player, snapshot, entries, calculatedDigest, pages, epoch);
        sessions.put(player.getUniqueID(), session);
        clearPendingCount(player.getUniqueID());

        LOGGER.info("Sending custom skin manifest revision " + snapshot.getRevision() + " (epoch " + epoch
            + ", " + entries.size() + " external preset(s)) to " + player.getCommandSenderName());

        ModNetwork.sendTo(
            new CustomSkinManifestBeginMessage(
                CustomSkinSyncProtocol.SCHEMA_VERSION,
                epoch,
                snapshot.getRevision(),
                calculatedDigest,
                entries.size(),
                totalBytes(entries),
                pages.size()),
            player);
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            ModNetwork.sendTo(
                new CustomSkinManifestPageMessage(epoch, pageIndex, pages.size(), pages.get(pageIndex)),
                player);
        }
        ModNetwork.sendTo(
            new CustomSkinManifestEndMessage(epoch, snapshot.getRevision(), calculatedDigest),
            player);
    }

    public void clearPlayer(EntityPlayerMP player) {
        if (player != null) {
            UUID playerId = player.getUniqueID();
            ManifestSession removed = sessions.remove(playerId);
            if (removed != null) {
                removed.transfers.clear();
            }
            clearPendingCount(playerId);
        }
    }

    public void enqueueManifestReady(EntityPlayerMP player, int schemaVersion, long epoch, long revision,
        String digest) {
        enqueue(new ManifestReadyAction(player, schemaVersion, epoch, revision, digest));
    }

    public void enqueueRequests(EntityPlayerMP player, long epoch, long revision,
        List<CustomSkinRequestIdentity> identities) {
        if (identities == null || identities.isEmpty()
            || identities.size() > CustomSkinSyncProtocol.MAX_REQUEST_IDENTITIES_PER_PAGE) {
            return;
        }
        enqueue(new RequestAction(player, epoch, revision, identities));
    }

    public void enqueueTransferResult(EntityPlayerMP player, long transferId, long epoch, String presetId,
        String sha256, int resultCode) {
        enqueue(new TransferResultAction(
            player,
            transferId,
            epoch,
            new CustomSkinRequestIdentity(presetId, sha256),
            resultCode));
    }

    public void processPending(MinecraftServer server) {
        PendingAction action;
        while ((action = pendingActions.poll()) != null) {
            decrementPending(action.player.getUniqueID());
            if (isConnected(server, action.player)) {
                action.apply(this);
            }
        }
        for (ManifestSession session : new ArrayList<ManifestSession>(sessions.values())) {
            if (isConnected(server, session.player)) {
                pumpTransfers(session);
            } else {
                clearPlayer(session.player);
            }
        }
    }

    private void handleReady(ManifestReadyAction action) {
        ManifestSession session = sessions.get(action.player.getUniqueID());
        if (session == null || action.schemaVersion != CustomSkinSyncProtocol.SCHEMA_VERSION
            || action.epoch != session.epoch || action.revision != session.snapshot.getRevision()
            || !session.digest.equals(action.digest)) {
            return;
        }
        if (!session.ready) {
            session.ready = true;
            ModNetwork.refreshAppearanceStateAfterManifestReady(action.player);
        }
    }

    private void handleRequests(RequestAction action) {
        ManifestSession session = sessions.get(action.player.getUniqueID());
        if (session == null || !session.ready || action.epoch != session.epoch
            || action.revision != session.snapshot.getRevision()) {
            return;
        }
        for (CustomSkinRequestIdentity identity : action.identities) {
            if (session.completedIdentities.contains(identity)) {
                continue;
            }
            CustomSkinEntry entry = findRequestedEntry(session.snapshot, session.manifestPresetIds, identity);
            if (entry == null) {
                if (!session.invalidRequestLogged) {
                    session.invalidRequestLogged = true;
                    LOGGER.warn("Ignoring custom skin request not present in the active manifest from "
                        + action.player.getCommandSenderName());
                }
                continue;
            }
            session.transfers.request(identity);
        }
    }

    private void handleResult(TransferResultAction action) {
        ManifestSession session = sessions.get(action.player.getUniqueID());
        if (session == null || action.epoch != session.epoch) {
            return;
        }
        if (session.transfers.complete(action.transferId, action.identity)
            && action.resultCode == CustomSkinTransferResultMessage.RESULT_SUCCESS) {
            session.completedIdentities.add(action.identity);
        }
    }

    private void pumpTransfers(ManifestSession session) {
        while (session.transfers.getActiveCount() < CustomSkinSyncProtocol.MAX_ACTIVE_TRANSFERS
            && session.transfers.getQueuedCount() > 0) {
            long transferId = nextPositive(NEXT_TRANSFER_ID);
            CustomSkinRequestIdentity identity = session.transfers.beginNext(transferId);
            if (identity == null) {
                return;
            }
            CustomSkinEntry entry = session.snapshot.findByPresetId(identity.getPresetId());
            if (entry == null || !entry.getSha256().equals(identity.getSha256())) {
                session.transfers.complete(transferId, identity);
                continue;
            }
            sendTransfer(session, transferId, entry);
        }
    }

    private static void sendTransfer(ManifestSession session, long transferId, CustomSkinEntry entry) {
        byte[] bytes = entry.copyPngBytes();
        int chunkCount = CustomSkinSyncProtocol.chunkCountForSize(bytes.length);
        ModNetwork.sendTo(
            new CustomSkinTransferStartMessage(
                transferId,
                session.epoch,
                session.snapshot.getRevision(),
                entry.getPresetId(),
                entry.getSha256(),
                bytes.length,
                chunkCount,
                entry.getWidth(),
                entry.getHeight()),
            session.player);
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int offset = chunkIndex * CustomSkinSyncProtocol.MAX_CHUNK_BYTES;
            int length = Math.min(CustomSkinSyncProtocol.MAX_CHUNK_BYTES, bytes.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(bytes, offset, chunk, 0, length);
            ModNetwork.sendTo(new CustomSkinTransferChunkMessage(transferId, chunkIndex, chunk), session.player);
        }
        ModNetwork.sendTo(
            new CustomSkinTransferEndMessage(
                transferId,
                session.epoch,
                entry.getPresetId(),
                entry.getSha256()),
            session.player);
    }

    private void enqueue(PendingAction action) {
        if (action == null || action.player == null) {
            return;
        }
        UUID playerId = action.player.getUniqueID();
        AtomicInteger count = pendingCounts.get(playerId);
        if (count == null) {
            AtomicInteger candidate = new AtomicInteger();
            AtomicInteger existing = pendingCounts.putIfAbsent(playerId, candidate);
            count = existing == null ? candidate : existing;
        }
        if (count.incrementAndGet() > CustomSkinSyncProtocol.MAX_PENDING_SERVER_ACTIONS_PER_PLAYER) {
            count.decrementAndGet();
            return;
        }
        pendingActions.add(action);
    }

    private void decrementPending(UUID playerId) {
        AtomicInteger count = pendingCounts.get(playerId);
        if (count != null && count.decrementAndGet() <= 0) {
            pendingCounts.remove(playerId, count);
        }
    }

    private void clearPendingCount(UUID playerId) {
        pendingCounts.remove(playerId);
    }

    private static long totalBytes(List<CustomSkinManifestEntry> entries) {
        long total = 0L;
        for (CustomSkinManifestEntry entry : entries) {
            total += entry.getByteSize();
        }
        return total;
    }

    static CustomSkinEntry findRequestedEntry(CustomSkinSnapshot snapshot, java.util.Set<String> manifestPresetIds,
        CustomSkinRequestIdentity identity) {
        if (snapshot == null || manifestPresetIds == null || identity == null
            || !manifestPresetIds.contains(identity.getPresetId())) {
            return null;
        }
        CustomSkinEntry entry = snapshot.findByPresetId(identity.getPresetId());
        return entry != null && entry.getSha256().equals(identity.getSha256()) ? entry : null;
    }

    private static boolean isConnected(MinecraftServer server, EntityPlayerMP player) {
        return server != null && server.getConfigurationManager().playerEntityList.contains(player);
    }

    private static long nextPositive(AtomicLong sequence) {
        long value = sequence.getAndIncrement();
        if (value <= 0L) {
            synchronized (sequence) {
                if (sequence.get() <= 0L) {
                    sequence.set(2L);
                }
                value = 1L;
            }
        }
        return value;
    }

    private static final class ManifestSession {

        final EntityPlayerMP player;
        private final CustomSkinSnapshot snapshot;
        private final String digest;
        private final long epoch;
        private final java.util.Set<String> manifestPresetIds;
        private final CustomSkinTransferWindow transfers = new CustomSkinTransferWindow();
        private final java.util.Set<CustomSkinRequestIdentity> completedIdentities =
            new java.util.HashSet<CustomSkinRequestIdentity>();
        private boolean ready;
        private boolean invalidRequestLogged;

        private ManifestSession(EntityPlayerMP player, CustomSkinSnapshot snapshot,
            List<CustomSkinManifestEntry> entries, String digest, List<List<CustomSkinManifestEntry>> pages,
            long epoch) {
            this.player = player;
            this.snapshot = snapshot;
            this.digest = digest;
            this.epoch = epoch;
            java.util.Set<String> ids = new java.util.HashSet<String>();
            for (CustomSkinManifestEntry entry : entries) {
                ids.add(entry.getPresetId());
            }
            manifestPresetIds = Collections.unmodifiableSet(ids);
            if (pages.size() > CustomSkinScanLimits.DEFAULT_MAX_ENTRIES) {
                throw new IllegalArgumentException("custom skin manifest has too many pages");
            }
        }
    }

    private abstract static class PendingAction {

        final EntityPlayerMP player;

        private PendingAction(EntityPlayerMP player) {
            this.player = player;
        }

        abstract void apply(ServerCustomSkinSyncService service);
    }

    private static final class ManifestReadyAction extends PendingAction {

        private final int schemaVersion;
        private final long epoch;
        private final long revision;
        private final String digest;

        private ManifestReadyAction(EntityPlayerMP player, int schemaVersion, long epoch, long revision,
            String digest) {
            super(player);
            this.schemaVersion = schemaVersion;
            this.epoch = epoch;
            this.revision = revision;
            this.digest = digest;
        }

        @Override
        void apply(ServerCustomSkinSyncService service) {
            service.handleReady(this);
        }
    }

    private static final class RequestAction extends PendingAction {

        private final long epoch;
        private final long revision;
        private final List<CustomSkinRequestIdentity> identities;

        private RequestAction(EntityPlayerMP player, long epoch, long revision,
            List<CustomSkinRequestIdentity> identities) {
            super(player);
            this.epoch = epoch;
            this.revision = revision;
            this.identities = Collections.unmodifiableList(new ArrayList<CustomSkinRequestIdentity>(identities));
        }

        @Override
        void apply(ServerCustomSkinSyncService service) {
            service.handleRequests(this);
        }
    }

    private static final class TransferResultAction extends PendingAction {

        private final long transferId;
        private final long epoch;
        private final CustomSkinRequestIdentity identity;
        private final int resultCode;

        private TransferResultAction(EntityPlayerMP player, long transferId, long epoch,
            CustomSkinRequestIdentity identity, int resultCode) {
            super(player);
            this.transferId = transferId;
            this.epoch = epoch;
            this.identity = identity;
            this.resultCode = resultCode;
        }

        @Override
        void apply(ServerCustomSkinSyncService service) {
            service.handleResult(this);
        }
    }
}
