package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import kome.common.tactical.edit.*;

/** Decode primitive intent only on Netty; expensive draft parsing/world work belongs to the bounded server tick. */
public final class KOMEPacketTacticalEditRequest implements IMessage {
    private KOMETacticalEditRequest request;
    public KOMEPacketTacticalEditRequest() { }
    public KOMEPacketTacticalEditRequest(KOMETacticalEditRequest request) {
        if (request == null) throw new IllegalArgumentException("Editor request required."); this.request = request;
    }
    public boolean isValid() { return request != null; }
    public KOMETacticalEditRequest getRequest() { return request; }
    @Override public void fromBytes(ByteBuf buffer) {
        request = null;
        ByteBuf decoded = null;
        try {
            decoded = KOMETacticalPacketEnvelope.read(buffer, KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES);
            buffer = decoded;
            if (buffer.readableBytes() > KOMETacticalEditWire.MAX_DRAFT_BYTES + 2048 || buffer.readUnsignedByte() != 1) {
                throw new IllegalArgumentException("Invalid tactical editor protocol/size.");
            }
            int action = buffer.readUnsignedByte();
            if (action >= KOMETacticalEditRequest.Action.values().length) throw new IllegalArgumentException("Invalid editor action.");
            KOMETacticalEditScope scope = KOMETacticalEditWire.readScope(buffer);
            UUID token = buffer.readBoolean() ? new UUID(buffer.readLong(), buffer.readLong()) : null;
            long sequence = buffer.readLong();
            byte[] payload = KOMETacticalEditWire.readPayload(buffer);
            KOMEPopulationWire.requireFullyRead(buffer);
            request = new KOMETacticalEditRequest(KOMETacticalEditRequest.Action.values()[action], scope, token, sequence, payload);
        } catch (RuntimeException invalid) { request = null; }
        finally { if (decoded != null) decoded.release(); }
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (request == null) throw new IllegalStateException("Invalid editor packet.");
        KOMETacticalPacketEnvelope.write(buffer, KOMETacticalPacketEnvelope.MAX_REQUEST_BYTES, out -> {
            out.writeByte(1); out.writeByte(request.getAction().ordinal()); KOMETacticalEditWire.writeScope(out, request.getScope());
            out.writeBoolean(request.getToken() != null);
            if (request.getToken() != null) { out.writeLong(request.getToken().getMostSignificantBits()); out.writeLong(request.getToken().getLeastSignificantBits()); }
            out.writeLong(request.getExpectedSequence()); KOMETacticalEditWire.writePayload(out, request.getPayload());
        });
    }
    public static final class Handler implements IMessageHandler<KOMEPacketTacticalEditRequest, IMessage> {
        @Override public IMessage onMessage(KOMEPacketTacticalEditRequest message, MessageContext context) {
            if (message != null && message.isValid()
                    && !KOMETacticalEditRuntime.enqueue(context.getServerHandler().playerEntity, message.getRequest())) {
                return new KOMEPacketTacticalEditSnapshot(KOMETacticalEditSessionManager.Status.RATE_LIMITED, null);
            }
            return null;
        }
    }
}
