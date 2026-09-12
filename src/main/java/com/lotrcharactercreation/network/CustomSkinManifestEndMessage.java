package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CustomSkinManifestEndMessage implements IMessage {

    private long epoch;
    private long revision;
    private String digest;
    private boolean valid;

    public CustomSkinManifestEndMessage() {}

    public CustomSkinManifestEndMessage(long epoch, long revision, String digest) {
        if (epoch <= 0L || revision < 0L || !com.lotrcharactercreation.appearance.CustomSkinHashing
            .isCanonicalSha256(digest)) {
            throw new IllegalArgumentException("custom skin manifest end is invalid");
        }
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
            epoch = buffer.readLong();
            revision = buffer.readLong();
            digest = CustomSkinSyncProtocol.readRequiredString(buffer, CustomSkinSyncProtocol.SHA_256_BYTES);
            CustomSkinSyncProtocol.requireFullyRead(buffer);
            if (epoch <= 0L || revision < 0L || !com.lotrcharactercreation.appearance.CustomSkinHashing
                .isCanonicalSha256(digest)) {
                throw new IllegalArgumentException("custom skin manifest end is invalid");
            }
            valid = true;
        } catch (RuntimeException exception) {
            CustomSkinSyncProtocol.warnMalformedOnce("ManifestEnd", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) {
            throw new IllegalStateException("invalid custom skin manifest end message");
        }
        buffer.writeLong(epoch);
        buffer.writeLong(revision);
        CustomSkinSyncProtocol.writeRequiredString(buffer, digest, CustomSkinSyncProtocol.SHA_256_BYTES);
    }

    public long getEpoch() { return epoch; }
    public long getRevision() { return revision; }
    public String getDigest() { return digest; }
    public boolean isValid() { return valid; }

    public static final class Handler implements IMessageHandler<CustomSkinManifestEndMessage, IMessage> {

        @Override
        public IMessage onMessage(CustomSkinManifestEndMessage message, MessageContext context) {
            if (message.isValid()) {
                LOTRCharacterCreation.proxy.handleCustomSkinManifestEnd(message.epoch, message.revision, message.digest);
            }
            return null;
        }
    }
}
