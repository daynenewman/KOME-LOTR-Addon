package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.siege.creation.GateCreationManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class GateCreationSelectPacket implements IMessage {

    private int x;
    private int y;
    private int z;
    private int dimensionId;
    private int controllerX;
    private int controllerY;
    private int controllerZ;
    private boolean fillEnclosed;

    public GateCreationSelectPacket() {
    }

    public GateCreationSelectPacket(
            int x,
            int y,
            int z,
            int dimensionId,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        this(
                x,
                y,
                z,
                dimensionId,
                controllerX,
                controllerY,
                controllerZ,
                false
        );
    }

    public GateCreationSelectPacket(
            int x,
            int y,
            int z,
            int dimensionId,
            int controllerX,
            int controllerY,
            int controllerZ,
            boolean fillEnclosed
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimensionId = dimensionId;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
        this.fillEnclosed = fillEnclosed;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
        dimensionId = buffer.readInt();
        controllerX = buffer.readInt();
        controllerY = buffer.readInt();
        controllerZ = buffer.readInt();
        fillEnclosed = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(dimensionId);
        buffer.writeInt(controllerX);
        buffer.writeInt(controllerY);
        buffer.writeInt(controllerZ);
        buffer.writeBoolean(fillEnclosed);
    }

    public static class Handler implements IMessageHandler<
            GateCreationSelectPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                GateCreationSelectPacket message,
                MessageContext context
        ) {
            EntityPlayerMP player =
                    context.getServerHandler().playerEntity;
            if (player != null
                    && SiegeRequestLimiter.isSaneBlockPosition(
                            message.x,
                            message.y,
                            message.z
                    )
                    && SiegeRequestLimiter.isSaneBlockPosition(
                            message.controllerX,
                            message.controllerY,
                            message.controllerZ
                    )) {
                GateCreationManager.queueSelection(
                        player,
                        message.x,
                        message.y,
                        message.z,
                        message.dimensionId,
                        message.controllerX,
                        message.controllerY,
                        message.controllerZ,
                        message.fillEnclosed
                );
            }
            return null;
        }
    }
}
