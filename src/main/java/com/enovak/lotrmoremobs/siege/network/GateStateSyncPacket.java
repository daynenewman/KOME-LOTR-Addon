package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.gate.GateState;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class GateStateSyncPacket implements IMessage {

    private int dimensionId;
    private int x;
    private int y;
    private int z;
    private GateState gateState = GateState.CLOSED;
    private long gateStateStartTick;

    public GateStateSyncPacket() {
    }

    public GateStateSyncPacket(
            int dimensionId,
            int x,
            int y,
            int z,
            GateState gateState,
            long gateStateStartTick
    ) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.gateState = gateState == null
                ? GateState.CLOSED
                : gateState;
        this.gateStateStartTick = gateStateStartTick;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimensionId = buffer.readInt();
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
        int serializedState = buffer.readByte();
        gateState = serializedState >= 0
                && serializedState < GateState.values().length
                ? GateState.values()[serializedState]
                : GateState.CLOSED;
        gateStateStartTick = buffer.readLong();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimensionId);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeByte(gateState.ordinal());
        buffer.writeLong(gateStateStartTick);
    }

    public int getX() {
        return x;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public GateState getGateState() {
        return gateState;
    }

    public long getGateStateStartTick() {
        return gateStateStartTick;
    }

    public static class Handler implements IMessageHandler<
            GateStateSyncPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                GateStateSyncPacket message,
                MessageContext context
        ) {
            Main.proxy.handleGateStateSync(message);
            return null;
        }
    }
}
