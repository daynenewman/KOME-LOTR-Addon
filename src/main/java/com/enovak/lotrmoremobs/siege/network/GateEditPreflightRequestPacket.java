package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.edit.GateEditRequestManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/** Token-only C2S refresh; preflight/world work is server-tick-owned. */
public final class GateEditPreflightRequestPacket implements IMessage {
    private UUID token;
    private boolean valid = true;

    public GateEditPreflightRequestPacket() {
    }

    public GateEditPreflightRequestPacket(UUID token) {
        this.token = token;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            token = new UUID(buffer.readLong(), buffer.readLong());
            valid = true;
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(token == null ? 0L : token.getMostSignificantBits());
        buffer.writeLong(token == null ? 0L : token.getLeastSignificantBits());
    }

    public static final class Handler
            implements IMessageHandler<GateEditPreflightRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(
                GateEditPreflightRequestPacket message,
                MessageContext context
        ) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!MumakilConfig.enableSiegeGates || player == null
                    || message == null
                    || !message.valid
                    || !SiegeRequestLimiter.tryAcquire(
                    player.getUniqueID(),
                    SiegeRequestLimiter.RateClass.EDIT_PREFLIGHT
            )) {
                return null;
            }

            GateEditRequestManager.enqueuePreflight(
                    player,
                    message.token
            );
            return null;
        }
    }
}
