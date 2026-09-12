package com.lotrcharactercreation.network;

import com.lotrcharactercreation.appearance.CustomSkinHashing;
import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CustomSkinTransferResultMessage implements IMessage {

    public static final int RESULT_SUCCESS = 0;
    public static final int RESULT_VALIDATION_FAILED = 1;
    public static final int RESULT_PROTOCOL_FAILED = 2;
    public static final int RESULT_RETRY_EXHAUSTED = 3;

    private long transferId;
    private long epoch;
    private String presetId;
    private String sha256;
    private int resultCode;
    private boolean valid;

    public CustomSkinTransferResultMessage() {}

    public CustomSkinTransferResultMessage(long transferId, long epoch, String presetId, String sha256,
        int resultCode) {
        validate(transferId, epoch, presetId, sha256, resultCode);
        this.transferId = transferId;
        this.epoch = epoch;
        this.presetId = presetId;
        this.sha256 = sha256;
        this.resultCode = resultCode;
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
            resultCode = buffer.readInt();
            CustomSkinSyncProtocol.requireFullyRead(buffer);
            validate(transferId, epoch, presetId, sha256, resultCode);
            valid = true;
        } catch (RuntimeException exception) {
            CustomSkinSyncProtocol.warnMalformedOnce("SkinTransferResult", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) {
            throw new IllegalStateException("invalid custom skin transfer result message");
        }
        buffer.writeLong(transferId);
        buffer.writeLong(epoch);
        CustomSkinSyncProtocol.writeIdentity(buffer, presetId, sha256);
        buffer.writeInt(resultCode);
    }

    private static void validate(long transferId, long epoch, String presetId, String sha256, int resultCode) {
        if (transferId <= 0L || epoch <= 0L || !CustomSkinManifestEntry.isPossibleExternalPresetId(presetId)
            || !CustomSkinHashing.isCanonicalSha256(sha256)
            || resultCode < RESULT_SUCCESS || resultCode > RESULT_RETRY_EXHAUSTED) {
            throw new IllegalArgumentException("custom skin transfer result is invalid");
        }
    }

    public long getTransferId() { return transferId; }
    public long getEpoch() { return epoch; }
    public String getPresetId() { return presetId; }
    public String getSha256() { return sha256; }
    public int getResultCode() { return resultCode; }
    public boolean isSuccessful() { return resultCode == RESULT_SUCCESS; }
    public boolean isValid() { return valid; }

    public static final class Handler implements IMessageHandler<CustomSkinTransferResultMessage, IMessage> {

        @Override
        public IMessage onMessage(CustomSkinTransferResultMessage message, MessageContext context) {
            if (message.isValid()) {
                ServerCustomSkinSyncService.getInstance().enqueueTransferResult(
                    context.getServerHandler().playerEntity,
                    message.transferId,
                    message.epoch,
                    message.presetId,
                    message.sha256,
                    message.resultCode);
            }
            return null;
        }
    }
}
