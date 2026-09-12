package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.edit.GateEditRequestManager;
import com.enovak.lotrmoremobs.siege.edit.GateEditStatus;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Empty C2S request; all inspection/world/session work is server-tick-owned. */
public final class GateEditStartPacket implements IMessage {
    public GateEditStartPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
    }

    @Override
    public void toBytes(ByteBuf buffer) {
    }

    public static final class Handler
            implements IMessageHandler<GateEditStartPacket, IMessage> {
        @Override
        public IMessage onMessage(
                GateEditStartPacket message,
                MessageContext context
        ) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!MumakilConfig.enableSiegeGates || player == null) {
                return null;
            }

            if (!SiegeRequestLimiter.tryAcquire(
                    player.getUniqueID(),
                    SiegeRequestLimiter.RateClass.EDIT_SESSION_ACTION
            ) || !GateEditRequestManager.enqueueStart(player)) {
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
