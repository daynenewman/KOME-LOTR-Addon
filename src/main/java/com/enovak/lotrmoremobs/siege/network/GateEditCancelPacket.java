package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.edit.GateEditRequestManager;
import com.enovak.lotrmoremobs.siege.edit.GateEditStatus;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/** Token-bound C2S cancellation; session mutation is server-tick-owned. */
public final class GateEditCancelPacket implements IMessage {
    private UUID token;
    private boolean valid = true;

    public GateEditCancelPacket() {
    }

    public GateEditCancelPacket(UUID token) {
        this.token = token;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            token = new UUID(buffer.readLong(), buffer.readLong());
            valid = token.getMostSignificantBits() != 0L
                    || token.getLeastSignificantBits() != 0L;
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(token == null ? 0L : token.getMostSignificantBits());
        buffer.writeLong(token == null ? 0L : token.getLeastSignificantBits());
    }

    public static final class Handler
            implements IMessageHandler<GateEditCancelPacket, IMessage> {
        @Override
        public IMessage onMessage(
                GateEditCancelPacket message,
                MessageContext context
        ) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!MumakilConfig.enableSiegeGates
                    || player == null
                    || message == null
                    || !message.valid
                    || message.token == null) {
                return null;
            }

            if (!SiegeRequestLimiter.tryAcquire(
                    player.getUniqueID(),
                    SiegeRequestLimiter.RateClass.EDIT_SESSION_ACTION
            ) || !GateEditRequestManager.enqueueCancel(
                    player,
                    message.token
            )) {
                Main.network.sendTo(
                        new GateEditSessionStatusPacket(
                                GateEditStatus.RATE_LIMITED,
                                null
                        ),
                        player
                );
            }
            return null;
        }
    }
}
