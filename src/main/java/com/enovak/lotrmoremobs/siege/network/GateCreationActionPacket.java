package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.siege.creation.GateCreationManager;
import com.enovak.lotrmoremobs.siege.creation.GateBlockPosition;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class GateCreationActionPacket implements IMessage {

    public static final int SELECT_LEFT = 0;
    public static final int SELECT_RIGHT = 1;
    public static final int SET_LEFT_HINGE = 2;
    public static final int SET_RIGHT_HINGE = 3;
    public static final int TOGGLE_DIRECTION = 4;
    public static final int FINALIZE = 5;
    public static final int CANCEL = 6;
    public static final int STOP_SELECTING = 7;
    public static final int SELECT_CENTER_SPLIT = 8;
    public static final int TOGGLE_BORDER_TEXTURE = 9;

    private int action;
    private int dimensionId;
    private int controllerX;
    private int controllerY;
    private int controllerZ;

    public GateCreationActionPacket() {
    }

    public GateCreationActionPacket(
            int action,
            int dimensionId,
            GateBlockPosition controller
    ) {
        this.action = action;
        this.dimensionId = dimensionId;
        this.controllerX = controller.getX();
        this.controllerY = controller.getY();
        this.controllerZ = controller.getZ();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        action = buffer.readUnsignedByte();
        dimensionId = buffer.readInt();
        controllerX = buffer.readInt();
        controllerY = buffer.readInt();
        controllerZ = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(action);
        buffer.writeInt(dimensionId);
        buffer.writeInt(controllerX);
        buffer.writeInt(controllerY);
        buffer.writeInt(controllerZ);
    }

    public static class Handler implements IMessageHandler<
            GateCreationActionPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                GateCreationActionPacket message,
                MessageContext context
        ) {
            EntityPlayerMP player =
                    context.getServerHandler().playerEntity;
            if (player != null
                    && isKnownAction(message.action)
                    && SiegeRequestLimiter.isSaneBlockPosition(
                            message.controllerX,
                            message.controllerY,
                            message.controllerZ
                    )) {
                GateCreationManager.queueAction(
                        player,
                        message.action,
                        message.dimensionId,
                        message.controllerX,
                        message.controllerY,
                        message.controllerZ
                );
            }
            return null;
        }
    }

    public static boolean isKnownAction(int action) {
        return action >= SELECT_LEFT && action <= TOGGLE_BORDER_TEXTURE;
    }
}
