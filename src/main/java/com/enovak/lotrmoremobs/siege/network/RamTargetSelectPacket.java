package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.siege.ram.RamControlManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class RamTargetSelectPacket implements IMessage {

    private int dimensionId;
    private int ramEntityId;
    private int controllerX;
    private int controllerY;
    private int controllerZ;

    public RamTargetSelectPacket() {
    }

    public RamTargetSelectPacket(
            int dimensionId,
            int ramEntityId,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        this.dimensionId = dimensionId;
        this.ramEntityId = ramEntityId;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimensionId = buffer.readInt();
        ramEntityId = buffer.readInt();
        controllerX = buffer.readInt();
        controllerY = buffer.readInt();
        controllerZ = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimensionId);
        buffer.writeInt(ramEntityId);
        buffer.writeInt(controllerX);
        buffer.writeInt(controllerY);
        buffer.writeInt(controllerZ);
    }

    public static class Handler implements IMessageHandler<
            RamTargetSelectPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                RamTargetSelectPacket message,
                MessageContext context
        ) {
            if (message.ramEntityId > 0
                    && SiegeRequestLimiter.isSaneBlockPosition(
                            message.controllerX,
                            message.controllerY,
                            message.controllerZ
                    )) {
                RamControlManager.queueTargetSelection(
                        context.getServerHandler().playerEntity,
                        message.dimensionId,
                        message.ramEntityId,
                        message.controllerX,
                        message.controllerY,
                        message.controllerZ
                );
            }
            return null;
        }
    }
}
