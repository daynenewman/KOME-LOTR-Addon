package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.siege.ram.RamControlManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class RamControlActionPacket implements IMessage {

    private int action;
    private int dimensionId;
    private int entityId;

    public RamControlActionPacket() {
    }

    public RamControlActionPacket(
            int action,
            int dimensionId,
            int entityId
    ) {
        this.action = action;
        this.dimensionId = dimensionId;
        this.entityId = entityId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        action = buffer.readByte();
        dimensionId = buffer.readInt();
        entityId = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(action);
        buffer.writeInt(dimensionId);
        buffer.writeInt(entityId);
    }

    public static class Handler implements IMessageHandler<
            RamControlActionPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                RamControlActionPacket message,
                MessageContext context
        ) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (player != null
                    && RamControlManager.isKnownAction(message.action)
                    && message.entityId > 0) {
                RamControlManager.queueAction(
                        player,
                        message.action,
                        message.dimensionId,
                        message.entityId
                );
            }
            return null;
        }
    }
}
