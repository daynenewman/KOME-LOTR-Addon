package com.lotrcharactercreation.network;

import com.lotrcharactercreation.appearance.CustomSkinHashing;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CustomSkinManifestReadyMessage implements IMessage {

    private int schemaVersion;
    private long epoch;
    private long revision;
    private String digest;
    private boolean valid;

    public CustomSkinManifestReadyMessage() {}

    public CustomSkinManifestReadyMessage(int schemaVersion, long epoch, long revision, String digest) {
        validate(schemaVersion, epoch, revision, digest);
        this.schemaVersion = schemaVersion;
        this.epoch = epoch;
        this.revision = revision;
        this.digest = digest;
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
            CustomSkinSyncProtocol.requireFullyRead(buffer);
            validate(schemaVersion, epoch, revision, digest);
            valid = true;
        } catch (RuntimeException exception) {
            CustomSkinSyncProtocol.warnMalformedOnce("ManifestReady", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) {
            throw new IllegalStateException("invalid custom skin manifest ready message");
        }
        buffer.writeInt(schemaVersion);
        buffer.writeLong(epoch);
        buffer.writeLong(revision);
        CustomSkinSyncProtocol.writeRequiredString(buffer, digest, CustomSkinSyncProtocol.SHA_256_BYTES);
    }

    private static void validate(int schemaVersion, long epoch, long revision, String digest) {
        if (schemaVersion != CustomSkinSyncProtocol.SCHEMA_VERSION || epoch <= 0L || revision < 0L
            || !CustomSkinHashing.isCanonicalSha256(digest)) {
            throw new IllegalArgumentException("custom skin manifest ready metadata is invalid");
        }
    }

    public int getSchemaVersion() { return schemaVersion; }
    public long getEpoch() { return epoch; }
    public long getRevision() { return revision; }
    public String getDigest() { return digest; }
    public boolean isValid() { return valid; }

    public static final class Handler implements IMessageHandler<CustomSkinManifestReadyMessage, IMessage> {

        @Override
        public IMessage onMessage(CustomSkinManifestReadyMessage message, MessageContext context) {
            if (message.isValid()) {
                ServerCustomSkinSyncService.getInstance().enqueueManifestReady(
                    context.getServerHandler().playerEntity,
                    message.schemaVersion,
                    message.epoch,
                    message.revision,
                    message.digest);
            }
            return null;
        }
    }
}
