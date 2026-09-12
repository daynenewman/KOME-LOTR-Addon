package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.edit.GateEditSessionManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;

/** Bounded S2C durable-admission receipt; rejected results carry no transaction identity. */
public final class GateEditCommitResultPacket implements IMessage {
    private GateEditSessionManager.EditCommitAdmissionResult.State state;
    private UUID jobUuid;
    private UUID gateUuid;
    private int baseRevision;
    private int targetRevision;
    private boolean valid;

    public GateEditCommitResultPacket() {
    }

    public GateEditCommitResultPacket(GateEditSessionManager.EditCommitAdmissionResult result) {
        this(result == null ? GateEditSessionManager.EditCommitAdmissionResult.State.INTERNAL_REJECTED
                : result.getState(), result == null ? null : result.getJobUuid(),
                result == null ? null : result.getGateUuid(),
                result == null ? 0 : result.getBaseRevision(),
                result == null ? 0 : result.getTargetRevision());
    }

    public GateEditCommitResultPacket(GateEditSessionManager.EditCommitAdmissionResult.State state) {
        this(state, null, null, 0, 0);
    }

    private GateEditCommitResultPacket(
            GateEditSessionManager.EditCommitAdmissionResult.State state,
            UUID jobUuid, UUID gateUuid, int baseRevision, int targetRevision
    ) {
        this.state = state;
        this.jobUuid = jobUuid;
        this.gateUuid = gateUuid;
        this.baseRevision = baseRevision;
        this.targetRevision = targetRevision;
        valid = isWellFormed();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            int stateId = buffer.readUnsignedByte();
            if (stateId >= GateEditSessionManager.EditCommitAdmissionResult.State.values().length) {
                throw new IllegalArgumentException();
            }
            state = GateEditSessionManager.EditCommitAdmissionResult.State.values()[stateId];
            if (state == GateEditSessionManager.EditCommitAdmissionResult.State.PREPARED) {
                jobUuid = new UUID(buffer.readLong(), buffer.readLong());
                gateUuid = new UUID(buffer.readLong(), buffer.readLong());
                baseRevision = buffer.readInt();
                targetRevision = buffer.readInt();
            }
            valid = isWellFormed();
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!isWellFormed()) {
            throw new IllegalStateException("Invalid edit commit result.");
        }
        buffer.writeByte(state.ordinal());
        if (state == GateEditSessionManager.EditCommitAdmissionResult.State.PREPARED) {
            buffer.writeLong(jobUuid.getMostSignificantBits());
            buffer.writeLong(jobUuid.getLeastSignificantBits());
            buffer.writeLong(gateUuid.getMostSignificantBits());
            buffer.writeLong(gateUuid.getLeastSignificantBits());
            buffer.writeInt(baseRevision);
            buffer.writeInt(targetRevision);
        }
    }

    private boolean isWellFormed() {
        if (state == null) return false;
        if (state != GateEditSessionManager.EditCommitAdmissionResult.State.PREPARED) {
            return jobUuid == null && gateUuid == null && baseRevision == 0 && targetRevision == 0;
        }
        return jobUuid != null && gateUuid != null && baseRevision > 0
                && targetRevision == baseRevision + 1;
    }

    public boolean isValid() { return valid; }
    public GateEditSessionManager.EditCommitAdmissionResult.State getState() { return state; }
    public UUID getJobUuid() { return jobUuid; }
    public UUID getGateUuid() { return gateUuid; }
    public int getBaseRevision() { return baseRevision; }
    public int getTargetRevision() { return targetRevision; }

    public static final class Handler implements IMessageHandler<GateEditCommitResultPacket, IMessage> {
        @Override
        public IMessage onMessage(GateEditCommitResultPacket message, MessageContext context) {
            if (message.isValid()) Main.proxy.handleGateEditCommitResult(message);
            return null;
        }
    }
}
