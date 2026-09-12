package com.lotrcharactercreation.client.appearance;

import java.io.ByteArrayOutputStream;

import com.lotrcharactercreation.network.CustomSkinSyncProtocol;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Strict in-order bounded assembly for one client-side PNG transfer. */
@SideOnly(Side.CLIENT)
public final class ClientCustomSkinTransferAssembly {

    private final long transferId;
    private final long epoch;
    private final long revision;
    private final ClientExternalSkinDefinition definition;
    private final int expectedChunkCount;
    private final ByteArrayOutputStream bytes;
    private int nextChunkIndex;

    public ClientCustomSkinTransferAssembly(long transferId, long epoch, long revision,
        ClientExternalSkinDefinition definition, int expectedChunkCount) {
        if (transferId <= 0L || epoch <= 0L || revision < 0L || definition == null
            || expectedChunkCount != CustomSkinSyncProtocol.chunkCountForSize(definition.getByteSize())) {
            throw new IllegalArgumentException("custom skin client transfer metadata is invalid");
        }
        this.transferId = transferId;
        this.epoch = epoch;
        this.revision = revision;
        this.definition = definition;
        this.expectedChunkCount = expectedChunkCount;
        bytes = new ByteArrayOutputStream(definition.getByteSize());
    }

    public void acceptChunk(long chunkTransferId, int chunkIndex, byte[] chunk) {
        if (chunkTransferId != transferId || chunkIndex != nextChunkIndex || chunk == null
            || chunk.length <= 0 || chunk.length > CustomSkinSyncProtocol.MAX_CHUNK_BYTES
            || chunkIndex >= expectedChunkCount) {
            throw new IllegalArgumentException("custom skin transfer chunk is duplicate, unknown, or out of order");
        }
        int remaining = definition.getByteSize() - bytes.size();
        int expectedLength = Math.min(CustomSkinSyncProtocol.MAX_CHUNK_BYTES, remaining);
        if (chunk.length != expectedLength || bytes.size() + chunk.length > definition.getByteSize()) {
            throw new IllegalArgumentException("custom skin transfer chunk length exceeds the declaration");
        }
        bytes.write(chunk, 0, chunk.length);
        nextChunkIndex++;
    }

    public byte[] finish(long endTransferId, long endEpoch, String presetId, String sha256) {
        if (endTransferId != transferId || endEpoch != epoch
            || !definition.getPresetId().equals(presetId) || !definition.getSha256().equals(sha256)
            || nextChunkIndex != expectedChunkCount || bytes.size() != definition.getByteSize()) {
            throw new IllegalArgumentException("custom skin transfer is incomplete or has the wrong identity");
        }
        return bytes.toByteArray();
    }

    public long getTransferId() {
        return transferId;
    }

    public long getEpoch() {
        return epoch;
    }

    public long getRevision() {
        return revision;
    }

    public ClientExternalSkinDefinition getDefinition() {
        return definition;
    }
}
