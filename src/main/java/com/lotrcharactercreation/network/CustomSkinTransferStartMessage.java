package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;
import com.lotrcharactercreation.appearance.CustomSkinHashing;
import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;
import com.lotrcharactercreation.appearance.CustomSkinScanLimits;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CustomSkinTransferStartMessage implements IMessage {

    private long transferId;
    private long epoch;
    private long revision;
    private String presetId;
    private String sha256;
    private int byteSize;
    private int chunkCount;
    private int width;
    private int height;
    private boolean valid;

    public CustomSkinTransferStartMessage() {}

    public CustomSkinTransferStartMessage(long transferId, long epoch, long revision, String presetId,
        String sha256, int byteSize, int chunkCount, int width, int height) {
        validate(transferId, epoch, revision, presetId, sha256, byteSize, chunkCount, width, height);
        this.transferId = transferId;
        this.epoch = epoch;
        this.revision = revision;
        this.presetId = presetId;
        this.sha256 = sha256;
        this.byteSize = byteSize;
        this.chunkCount = chunkCount;
        this.width = width;
        this.height = height;
        valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            CustomSkinSyncProtocol.requirePacketSize(buffer);
            transferId = buffer.readLong();
            epoch = buffer.readLong();
            revision = buffer.readLong();
            presetId = CustomSkinSyncProtocol.readRequiredString(
                buffer,
                CustomSkinSyncProtocol.MAX_PRESET_ID_BYTES);
            sha256 = CustomSkinSyncProtocol.readRequiredString(buffer, CustomSkinSyncProtocol.SHA_256_BYTES);
            byteSize = buffer.readInt();
            chunkCount = buffer.readInt();
            width = buffer.readInt();
            height = buffer.readInt();
            CustomSkinSyncProtocol.requireFullyRead(buffer);
            validate(transferId, epoch, revision, presetId, sha256, byteSize, chunkCount, width, height);
            valid = true;
        } catch (RuntimeException exception) {
            CustomSkinSyncProtocol.warnMalformedOnce("SkinTransferStart", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) {
            throw new IllegalStateException("invalid custom skin transfer start message");
        }
        buffer.writeLong(transferId);
        buffer.writeLong(epoch);
        buffer.writeLong(revision);
        CustomSkinSyncProtocol.writeIdentity(buffer, presetId, sha256);
        buffer.writeInt(byteSize);
        buffer.writeInt(chunkCount);
        buffer.writeInt(width);
        buffer.writeInt(height);
    }

    private static void validate(long transferId, long epoch, long revision, String presetId, String sha256,
        int byteSize, int chunkCount, int width, int height) {
        if (transferId <= 0L || epoch <= 0L || revision < 0L
            || !CustomSkinManifestEntry.isPossibleExternalPresetId(presetId)
            || !CustomSkinHashing.isCanonicalSha256(sha256)
            || byteSize <= 0 || byteSize > CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES
            || chunkCount != CustomSkinSyncProtocol.chunkCountForSize(byteSize)
            || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("custom skin transfer start metadata is invalid");
        }
    }

    public long getTransferId() { return transferId; }
    public long getEpoch() { return epoch; }
    public long getRevision() { return revision; }
    public String getPresetId() { return presetId; }
    public String getSha256() { return sha256; }
    public int getByteSize() { return byteSize; }
    public int getChunkCount() { return chunkCount; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isValid() { return valid; }

    public static final class Handler implements IMessageHandler<CustomSkinTransferStartMessage, IMessage> {

        @Override
        public IMessage onMessage(CustomSkinTransferStartMessage message, MessageContext context) {
            if (message.isValid()) {
                LOTRCharacterCreation.proxy.handleCustomSkinTransferStart(
                    message.transferId,
                    message.epoch,
                    message.revision,
                    message.presetId,
                    message.sha256,
                    message.byteSize,
                    message.chunkCount,
                    message.width,
                    message.height);
            }
            return null;
        }
    }
}
