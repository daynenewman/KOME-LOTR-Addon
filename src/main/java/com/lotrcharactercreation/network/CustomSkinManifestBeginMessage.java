package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CustomSkinManifestBeginMessage implements IMessage {

    private int schemaVersion;
    private long epoch;
    private long revision;
    private String digest;
    private int entryCount;
    private long totalBytes;
    private int pageCount;
    private boolean valid;

    public CustomSkinManifestBeginMessage() {}

    public CustomSkinManifestBeginMessage(int schemaVersion, long epoch, long revision, String digest,
        int entryCount, long totalBytes, int pageCount) {
        new CustomSkinManifestAssembly(schemaVersion, epoch, revision, digest, entryCount, totalBytes, pageCount);
        this.schemaVersion = schemaVersion;
        this.epoch = epoch;
        this.revision = revision;
        this.digest = digest;
        this.entryCount = entryCount;
        this.totalBytes = totalBytes;
        this.pageCount = pageCount;
        valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            CustomSkinSyncProtocol.requirePacketSize(buffer);
            schemaVersion = buffer.readInt();
            epoch = buffer.readLong();
            revision = buffer.readLong();
            digest = CustomSkinSyncProtocol.readRequiredString(buffer, CustomSkinSyncProtocol.SHA_256_BYTES);
            entryCount = buffer.readInt();
            totalBytes = buffer.readLong();
            pageCount = buffer.readInt();
            CustomSkinSyncProtocol.requireFullyRead(buffer);
            new CustomSkinManifestAssembly(
                schemaVersion,
                epoch,
                revision,
                digest,
                entryCount,
                totalBytes,
                pageCount);
            valid = true;
        } catch (RuntimeException exception) {
            CustomSkinSyncProtocol.warnMalformedOnce("ManifestBegin", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) {
            throw new IllegalStateException("invalid custom skin manifest begin message");
        }
        buffer.writeInt(schemaVersion);
        buffer.writeLong(epoch);
        buffer.writeLong(revision);
        CustomSkinSyncProtocol.writeRequiredString(buffer, digest, CustomSkinSyncProtocol.SHA_256_BYTES);
        buffer.writeInt(entryCount);
        buffer.writeLong(totalBytes);
        buffer.writeInt(pageCount);
    }

    public int getSchemaVersion() { return schemaVersion; }
    public long getEpoch() { return epoch; }
    public long getRevision() { return revision; }
    public String getDigest() { return digest; }
    public int getEntryCount() { return entryCount; }
    public long getTotalBytes() { return totalBytes; }
    public int getPageCount() { return pageCount; }
    public boolean isValid() { return valid; }

    public static final class Handler implements IMessageHandler<CustomSkinManifestBeginMessage, IMessage> {

        @Override
        public IMessage onMessage(CustomSkinManifestBeginMessage message, MessageContext context) {
            if (message.isValid()) {
                LOTRCharacterCreation.proxy.handleCustomSkinManifestBegin(
                    message.schemaVersion,
                    message.epoch,
                    message.revision,
                    message.digest,
                    message.entryCount,
                    message.totalBytes,
                    message.pageCount);
            }
            return null;
        }
    }
}
