package com.lotrcharactercreation.client.appearance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;
import com.lotrcharactercreation.network.CustomSkinManifestAssembly;
import com.lotrcharactercreation.network.CustomSkinRequestIdentity;
import com.lotrcharactercreation.network.CustomSkinSyncProtocol;
import com.lotrcharactercreation.network.CustomSkinTransferResultMessage;
import com.lotrcharactercreation.network.ModNetwork;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-main-thread manifest, cache comparison, transfer, and retry coordinator. */
@SideOnly(Side.CLIENT)
public final class ClientCustomSkinSyncService {

    private static final Logger LOGGER = LogManager.getLogger("lotrcharactercreation");
    private static final ClientCustomSkinSyncService INSTANCE = new ClientCustomSkinSyncService();

    private final Map<Long, ClientCustomSkinTransferAssembly> activeTransfers =
        new HashMap<Long, ClientCustomSkinTransferAssembly>();
    private final Set<CustomSkinRequestIdentity> requestedIdentities =
        new HashSet<CustomSkinRequestIdentity>();
    private final ClientCustomSkinRetryState retryState = new ClientCustomSkinRetryState();
    private final Set<ClientCustomSkinIdentity> exhaustedFailures =
        new HashSet<ClientCustomSkinIdentity>();
    private final Set<String> loggedProtocolFailures = new HashSet<String>();

    private CustomSkinManifestAssembly manifestAssembly;
    private long activeEpoch = -1L;
    private long activeRevision = -1L;
    private String activeDigest;

    private ClientCustomSkinSyncService() {}

    public static ClientCustomSkinSyncService getInstance() {
        return INSTANCE;
    }

    public void handleManifestBegin(int schemaVersion, long epoch, long revision, String digest, int entryCount,
        long totalBytes, int pageCount) {
        if (isSupersededRevision(revision, activeRevision, manifestAssembly)) {
            logProtocolFailureOnce("Ignoring superseded custom skin manifest revision " + revision);
            return;
        }
        try {
            manifestAssembly = new CustomSkinManifestAssembly(
                schemaVersion,
                epoch,
                revision,
                digest,
                entryCount,
                totalBytes,
                pageCount);
            clearTransfersAndRetries();
        } catch (IllegalArgumentException exception) {
            manifestAssembly = null;
            logProtocolFailureOnce("Ignoring invalid custom skin manifest header: " + exception.getMessage());
        }
    }

    public void handleManifestPage(long epoch, int pageIndex, int pageCount,
        List<CustomSkinManifestEntry> entries) {
        if (manifestAssembly == null || manifestAssembly.getEpoch() != epoch) {
            logProtocolFailureOnce("Ignoring custom skin manifest page for an unknown epoch");
            return;
        }
        try {
            manifestAssembly.acceptPage(epoch, pageIndex, pageCount, entries);
        } catch (IllegalArgumentException exception) {
            manifestAssembly = null;
            logProtocolFailureOnce("Discarding invalid custom skin manifest: " + exception.getMessage());
        }
    }

    public void handleManifestEnd(long epoch, long revision, String digest) {
        if (manifestAssembly == null || manifestAssembly.getEpoch() != epoch) {
            logProtocolFailureOnce("Ignoring custom skin manifest end for an unknown epoch");
            return;
        }
        List<CustomSkinManifestEntry> entries;
        try {
            entries = manifestAssembly.finish(epoch, revision, digest);
        } catch (IllegalArgumentException exception) {
            manifestAssembly = null;
            logProtocolFailureOnce("Discarding incomplete custom skin manifest: " + exception.getMessage());
            return;
        }

        List<ClientExternalSkinDefinition> definitions = new ArrayList<ClientExternalSkinDefinition>(entries.size());
        try {
            for (CustomSkinManifestEntry entry : entries) {
                definitions.add(ClientExternalSkinDefinition.fromManifestEntry(entry));
            }
            ClientCustomSkinManager.getInstance().activateExternalCatalog(definitions);
        } catch (RuntimeException exception) {
            manifestAssembly = null;
            logProtocolFailureOnce("Could not activate the custom skin manifest: " + exception.getMessage());
            return;
        }

        activeEpoch = epoch;
        activeRevision = revision;
        activeDigest = digest;
        manifestAssembly = null;

        final ClientCustomSkinManager manager = ClientCustomSkinManager.getInstance();
        List<CustomSkinRequestIdentity> missing = ClientCustomSkinRequestPlanner.findMissing(
            definitions,
            new ClientCustomSkinRequestPlanner.ContentAvailability() {

                @Override
                public boolean isAvailable(ClientExternalSkinDefinition definition) {
                    boolean available = manager.hasCachedContent(
                        definition.getPresetId(),
                        definition.getSha256());
                    if (available) {
                        manager.contentAvailable(definition.getPresetId(), definition.getSha256());
                    }
                    return available;
                }
            });

        LOGGER.info("Activated server custom skin manifest revision " + activeRevision + " (epoch "
            + activeEpoch + ", " + definitions.size() + " external preset(s), " + missing.size()
            + " download(s) required)");

        ModNetwork.sendCustomSkinManifestReady(
            CustomSkinSyncProtocol.SCHEMA_VERSION,
            activeEpoch,
            activeRevision,
            activeDigest);
        requestMissing(missing);
    }

    public void handleTransferStart(long transferId, long epoch, long revision, String presetId, String sha256,
        int byteSize, int chunkCount, int width, int height) {
        if (epoch != activeEpoch || revision != activeRevision) {
            logProtocolFailureOnce("Ignoring custom skin transfer start for a superseded manifest");
            return;
        }
        ClientExternalSkinDefinition definition = ClientCustomSkinManager.getInstance()
            .getExternalDefinition(presetId);
        if (!matches(definition, sha256, byteSize, width, height)) {
            failStart(transferId, epoch, presetId, sha256, definition, "transfer metadata does not match manifest");
            return;
        }
        if (activeTransfers.size() >= CustomSkinSyncProtocol.MAX_ACTIVE_TRANSFERS
            || activeTransfers.containsKey(Long.valueOf(transferId))
            || containsActiveIdentity(definition.getIdentity())) {
            failStart(transferId, epoch, presetId, sha256, definition, "transfer window or identity is invalid");
            return;
        }
        try {
            ClientCustomSkinTransferAssembly assembly = new ClientCustomSkinTransferAssembly(
                transferId,
                epoch,
                revision,
                definition,
                chunkCount);
            activeTransfers.put(Long.valueOf(transferId), assembly);
            requestedIdentities.remove(new CustomSkinRequestIdentity(presetId, sha256));
        } catch (IllegalArgumentException exception) {
            failStart(transferId, epoch, presetId, sha256, definition, exception.getMessage());
        }
    }

    public void handleTransferChunk(long transferId, int chunkIndex, byte[] data) {
        ClientCustomSkinTransferAssembly assembly = activeTransfers.get(Long.valueOf(transferId));
        if (assembly == null) {
            logProtocolFailureOnce("Ignoring a custom skin chunk for an unknown transfer");
            return;
        }
        try {
            assembly.acceptChunk(transferId, chunkIndex, data);
        } catch (IllegalArgumentException exception) {
            activeTransfers.remove(Long.valueOf(transferId));
            failTransfer(assembly, CustomSkinTransferResultMessage.RESULT_PROTOCOL_FAILED, exception.getMessage());
        }
    }

    public void handleTransferEnd(long transferId, long epoch, String presetId, String sha256) {
        ClientCustomSkinTransferAssembly assembly = activeTransfers.remove(Long.valueOf(transferId));
        if (assembly == null) {
            logProtocolFailureOnce("Ignoring a custom skin end for an unknown transfer");
            return;
        }
        byte[] bytes;
        try {
            bytes = assembly.finish(transferId, epoch, presetId, sha256);
        } catch (IllegalArgumentException exception) {
            failTransfer(assembly, CustomSkinTransferResultMessage.RESULT_PROTOCOL_FAILED, exception.getMessage());
            return;
        }

        ClientExternalSkinDefinition definition = assembly.getDefinition();
        if (assembly.getEpoch() != activeEpoch || assembly.getRevision() != activeRevision
            || !ClientCustomSkinManager.getInstance().acceptDownloadedContent(
                definition.getPresetId(),
                definition.getSha256(),
                bytes)) {
            failTransfer(
                assembly,
                CustomSkinTransferResultMessage.RESULT_VALIDATION_FAILED,
                "completed bytes failed independent cache validation");
            return;
        }

        retryState.succeeded(definition.getIdentity());
        exhaustedFailures.remove(definition.getIdentity());
        ModNetwork.sendCustomSkinTransferResult(
            transferId,
            activeEpoch,
            definition.getPresetId(),
            definition.getSha256(),
            CustomSkinTransferResultMessage.RESULT_SUCCESS);
    }

    public void clearConnectionState() {
        manifestAssembly = null;
        activeEpoch = -1L;
        activeRevision = -1L;
        activeDigest = null;
        clearTransfersAndRetries();
        loggedProtocolFailures.clear();
    }

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        clearConnectionState();
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clearConnectionState();
    }

    private void requestMissing(List<CustomSkinRequestIdentity> identities) {
        List<CustomSkinRequestIdentity> page = new ArrayList<CustomSkinRequestIdentity>();
        int pageBytes = 8 + 8 + 4;
        for (CustomSkinRequestIdentity identity : identities) {
            if (!requestedIdentities.add(identity)) {
                continue;
            }
            int identityBytes = CustomSkinSyncProtocol.encodedIdentityBytes(
                identity.getPresetId(),
                identity.getSha256());
            if (!page.isEmpty()
                && (page.size() >= CustomSkinSyncProtocol.MAX_REQUEST_IDENTITIES_PER_PAGE
                    || pageBytes + identityBytes > CustomSkinSyncProtocol.MAX_ENCODED_PACKET_BYTES)) {
                ModNetwork.sendCustomSkinRequests(activeEpoch, activeRevision, page);
                page = new ArrayList<CustomSkinRequestIdentity>();
                pageBytes = 8 + 8 + 4;
            }
            page.add(identity);
            pageBytes += identityBytes;
        }
        if (!page.isEmpty()) {
            ModNetwork.sendCustomSkinRequests(activeEpoch, activeRevision, page);
        }
    }

    private void failStart(long transferId, long epoch, String presetId, String sha256,
        ClientExternalSkinDefinition definition, String reason) {
        if (definition == null || !definition.getSha256().equals(sha256)) {
            logProtocolFailureOnce("Ignoring invalid custom skin transfer start: " + reason);
            return;
        }
        ClientCustomSkinTransferAssembly failed = new ClientCustomSkinTransferAssembly(
            transferId,
            epoch,
            activeRevision,
            definition,
            CustomSkinSyncProtocol.chunkCountForSize(definition.getByteSize()));
        failTransfer(failed, CustomSkinTransferResultMessage.RESULT_PROTOCOL_FAILED, reason);
    }

    private void failTransfer(ClientCustomSkinTransferAssembly assembly, int failureCode, String reason) {
        ClientExternalSkinDefinition definition = assembly.getDefinition();
        ClientCustomSkinIdentity identity = definition.getIdentity();
        requestedIdentities.remove(new CustomSkinRequestIdentity(
            definition.getPresetId(),
            definition.getSha256()));
        int failures = retryState.recordFailure(identity);
        boolean exhausted = !retryState.shouldRetry(failures);
        ModNetwork.sendCustomSkinTransferResult(
            assembly.getTransferId(),
            assembly.getEpoch(),
            definition.getPresetId(),
            definition.getSha256(),
            exhausted ? CustomSkinTransferResultMessage.RESULT_RETRY_EXHAUSTED : failureCode);
        if (exhausted) {
            if (exhaustedFailures.add(identity)) {
                LOGGER.warn("Custom skin download failed after " + failures + " attempts for " + identity
                    + ": " + reason);
            }
            return;
        }
        requestMissing(java.util.Collections.singletonList(
            new CustomSkinRequestIdentity(definition.getPresetId(), definition.getSha256())));
    }

    private void clearTransfersAndRetries() {
        activeTransfers.clear();
        requestedIdentities.clear();
        retryState.clear();
        exhaustedFailures.clear();
    }

    private boolean containsActiveIdentity(ClientCustomSkinIdentity identity) {
        for (ClientCustomSkinTransferAssembly assembly : activeTransfers.values()) {
            if (assembly.getDefinition().getIdentity().equals(identity)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(ClientExternalSkinDefinition definition, String sha256, int byteSize,
        int width, int height) {
        return definition != null && definition.getSha256().equals(sha256)
            && definition.getByteSize() == byteSize && definition.getWidth() == width
            && definition.getHeight() == height;
    }

    private void logProtocolFailureOnce(String message) {
        if (loggedProtocolFailures.add(message)) {
            LOGGER.warn(message);
        }
    }

    static boolean isSupersededRevision(long candidateRevision, long acceptedRevision,
        CustomSkinManifestAssembly pendingAssembly) {
        return candidateRevision <= acceptedRevision
            || pendingAssembly != null && candidateRevision < pendingAssembly.getRevision();
    }
}
