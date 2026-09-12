package com.enovak.lotrmoremobs.network;

import com.enovak.lotrmoremobs.Main;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server -> client gameplay settings controlled by the world/server. */
public final class ServerGameplaySyncPacket implements IMessage {

    private boolean modernPlayerAnimations;

    public ServerGameplaySyncPacket() {
    }

    public ServerGameplaySyncPacket(boolean modernPlayerAnimations) {
        this.modernPlayerAnimations = modernPlayerAnimations;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        modernPlayerAnimations = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(modernPlayerAnimations);
    }

    public boolean isModernPlayerAnimations() {
        return modernPlayerAnimations;
    }

    public static final class Handler implements IMessageHandler<
            ServerGameplaySyncPacket,
            IMessage> {
        @Override
        public IMessage onMessage(
                ServerGameplaySyncPacket message,
                MessageContext context
        ) {
            if (message != null) {
                Main.proxy.handleServerGameplaySync(message);
            }
            return null;
        }
    }
}
