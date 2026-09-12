package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class GateManagementOpenPacket implements IMessage {

    private int dimensionId;
    private int x;
    private int y;
    private int z;
    private boolean canManage;
    private boolean canManagePlayerAccess;
    private boolean canAdminister;

    public GateManagementOpenPacket() {
    }

    public GateManagementOpenPacket(
            int dimensionId,
            int x,
            int y,
            int z,
            boolean canManage,
            boolean canManagePlayerAccess,
            boolean canAdminister
    ) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.canManage = canManage;
        this.canManagePlayerAccess = canManagePlayerAccess;
        this.canAdminister = canAdminister;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimensionId = buffer.readInt();
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
        canManage = buffer.readBoolean();
        canManagePlayerAccess = buffer.readBoolean();
        canAdminister = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimensionId);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeBoolean(canManage);
        buffer.writeBoolean(canManagePlayerAccess);
        buffer.writeBoolean(canAdminister);
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public boolean canManage() {
        return canManage;
    }

    public boolean canManagePlayerAccess() {
        return canManagePlayerAccess;
    }

    public boolean canAdminister() {
        return canAdminister;
    }

    public static class Handler implements IMessageHandler<
            GateManagementOpenPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                GateManagementOpenPacket message,
                MessageContext context
        ) {
            Main.proxy.handleGateManagementOpen(message);
            return null;
        }
    }
}
