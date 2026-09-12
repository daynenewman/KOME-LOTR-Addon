package com.lotrcharactercreation.network;

import java.util.Arrays;

import com.lotrcharactercreation.LOTRCharacterCreation;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class CustomSkinTransferChunkMessage implements IMessage {

    private long transferId;
    private int chunkIndex;
    private byte[] data = new byte[0];
    private boolean valid;

    public CustomSkinTransferChunkMessage() {}

    public CustomSkinTransferChunkMessage(long transferId, int chunkIndex, byte[] data) {
        if (transferId <= 0L || chunkIndex < 0 || data == null || data.length <= 0
            || data.length > CustomSkinSyncProtocol.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("custom skin transfer chunk is invalid");
        }
        this.transferId = transferId;
        this.chunkIndex = chunkIndex;
        this.data = Arrays.copyOf(data, data.length);
        valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            CustomSkinSyncProtocol.requirePacketSize(buffer);
            transferId = buffer.readLong();
            chunkIndex = buffer.readInt();
            int length = buffer.readInt();
            if (transferId <= 0L || chunkIndex < 0 || length <= 0
                || length > CustomSkinSyncProtocol.MAX_CHUNK_BYTES || length > buffer.readableBytes()) {
                throw new IllegalArgumentException("custom skin transfer chunk framing is invalid");
            }
            data = new byte[length];
            buffer.readBytes(data);
            CustomSkinSyncProtocol.requireFullyRead(buffer);
            valid = true;
        } catch (RuntimeException exception) {
            data = new byte[0];
            CustomSkinSyncProtocol.warnMalformedOnce("SkinTransferChunk", exception);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) {
            throw new IllegalStateException("invalid custom skin transfer chunk message");
        }
        buffer.writeLong(transferId);
        buffer.writeInt(chunkIndex);
        buffer.writeInt(data.length);
        buffer.writeBytes(data);
    }

    public long getTransferId() { return transferId; }
    public int getChunkIndex() { return chunkIndex; }
    public byte[] copyData() { return Arrays.copyOf(data, data.length); }
    public boolean isValid() { return valid; }

    public static final class Handler implements IMessageHandler<CustomSkinTransferChunkMessage, IMessage> {

        @Override
        public IMessage onMessage(CustomSkinTransferChunkMessage message, MessageContext context) {
            if (message.isValid()) {
                LOTRCharacterCreation.proxy.handleCustomSkinTransferChunk(
                    message.transferId,
                    message.chunkIndex,
                    message.copyData());
            }
            return null;
        }
    }
}
