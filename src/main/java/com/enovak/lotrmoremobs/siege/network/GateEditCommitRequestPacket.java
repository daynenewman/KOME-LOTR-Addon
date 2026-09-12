package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.edit.GateEditRequestManager;
import com.enovak.lotrmoremobs.siege.edit.GateEditSessionManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/** C2S intent only; the server derives every gate and draft fact from its session. */
public final class GateEditCommitRequestPacket implements IMessage {
    private UUID token;
    private long expectedDraftSequence;
    private boolean valid = true;

    public GateEditCommitRequestPacket() {
    }

    public GateEditCommitRequestPacket(UUID token, long expectedDraftSequence) {
        this.token = token;
        this.expectedDraftSequence = expectedDraftSequence;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            token = new UUID(buffer.readLong(), buffer.readLong());
            expectedDraftSequence = buffer.readLong();
            if (expectedDraftSequence < 0L
                    || (token.getMostSignificantBits() == 0L
                    && token.getLeastSignificantBits() == 0L)) {
                throw new IllegalArgumentException();
            }
            valid = true;
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(token == null ? 0L : token.getMostSignificantBits());
        buffer.writeLong(token == null ? 0L : token.getLeastSignificantBits());
        buffer.writeLong(expectedDraftSequence);
    }

    public static final class Handler implements IMessageHandler<GateEditCommitRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(
                GateEditCommitRequestPacket message,
                MessageContext context
        ) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;

            if (!MumakilConfig.enableSiegeGates || player == null) {
                return null;
            }

            if (!message.valid
                    || !SiegeRequestLimiter.tryAcquire(
                    player.getUniqueID(),
                    SiegeRequestLimiter.RateClass.EDIT_COMMIT
            )) {
                Main.network.sendTo(
                        new GateEditCommitResultPacket(
                                GateEditSessionManager.EditCommitAdmissionResult.State.INTERNAL_REJECTED
                        ),
                        player
                );
                GateEditRequestManager.enqueueDraftAndPreflightRefresh(
                        player,
                        message.token
                );
                return null;
            }

            /*
             * Put successful commit intent into the same ordered transient
             * request stream as draft actions. When this barrier reaches the
             * server tick it delegates to the existing durable commit queue;
             * the durable transaction itself remains unchanged.
             */
            if (!GateEditRequestManager.enqueueCommit(
                    player,
                    message.token,
                    message.expectedDraftSequence
            )) {
                Main.network.sendTo(
                        new GateEditCommitResultPacket(
                                GateEditSessionManager.EditCommitAdmissionResult.State.INTERNAL_REJECTED
                        ),
                        player
                );
                GateEditRequestManager.enqueueDraftAndPreflightRefresh(
                        player,
                        message.token
                );
            }

            return null;
        }
    }
}
