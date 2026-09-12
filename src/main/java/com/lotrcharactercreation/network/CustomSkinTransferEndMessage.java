package com.lotrcharactercreation.network;

import com.lotrcharactercreation.LOTRCharacterCreation;
import com.lotrcharactercreation.appearance.CustomSkinHashing;
import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CustomSkinTransferEndMessage implements IMessage {

    private long transferId;
    private long epoch;
    private String presetId;
    private String sha256;
    private boolean valid;

    public CustomSkinTransferEndMessage() {}

    public CustomSkinTransferEndMessage(long transferId, long epoch, String presetId, String sha256) {
        validate(transferId, epoch, presetId, sha256);
        this.transferId = transferId;
        this.epoch = epoch;
        this.presetId = presetId;
        this.sha256 = sha256;
        valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            CustomSkinSyncProtocol.requirePacketSize(buffer);
            transferId = buffer.readLong();
            epoch = buffer.readLong();
            String[] identity = CustomSkinSyncProtocol.readIdentity(buffer);
            presetId = identity[0];
            sha256 = identity[1];
            CustomSkinSyncProtocol.requireFullyRead(buffer);
            validate(transferId, epoch, presetId, sha256);
            valid = true;
        } catch (RuntimeException exception) {
            CustomSkinSyncProtocol.warnMalformedOnce("SkinTransferEnd", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) {
            throw new IllegalStateException("invalid custom skin transfer end message");
        }
        buffer.writeLong(transferId);
        buffer.writeLong(epoch);
        CustomSkinSyncProtocol.writeIdentity(buffer, presetId, sha256);
    }

    private static void validate(long transferId, long epoch, String presetId, String sha256) {
        if (transferId <= 0L || epoch <= 0L || !CustomSkinManifestEntry.isPossibleExternalPresetId(presetId)
            || !CustomSkinHashing.isCanonicalSha256(sha256)) {
            throw new IllegalArgumentException("custom skin transfer end metadata is invalid");
        }
    }

    public long getTransferId() { return transferId; }
    public long getEpoch() { return epoch; }
    public String getPresetId() { return presetId; }
    public String getSha256() { return sha256; }
    public boolean isValid() { return valid; }

    public static final class Handler implements IMessageHandler<CustomSkinTransferEndMessage, IMessage> {

        @Override
        public IMessage onMessage(CustomSkinTransferEndMessage message, MessageContext context) {
            if (message.isValid()) {
                LOTRCharacterCreation.proxy.handleCustomSkinTransferEnd(
                    message.transferId,
                    message.epoch,
                    message.presetId,
                    message.sha256);
            }
            return null;
        }
    }
}
