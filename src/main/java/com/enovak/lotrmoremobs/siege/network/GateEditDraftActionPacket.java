package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.edit.GateEditDraftAction;
import com.enovak.lotrmoremobs.siege.edit.GateEditRequestManager;
import com.enovak.lotrmoremobs.siege.edit.GateEditStatus;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/** Bounded coordinate intent; world/session work is server-tick-owned. */
public final class GateEditDraftActionPacket implements IMessage {
    private UUID token;
    private GateEditDraftAction action;
    private int x;
    private int y;
    private int z;
    private boolean fillEnclosed;
    private boolean valid = true;

    public GateEditDraftActionPacket() {
    }

    public GateEditDraftActionPacket(
            UUID token,
            GateEditDraftAction action,
            int x,
            int y,
            int z
    ) {
        this(token, action, x, y, z, false);
    }

    public GateEditDraftActionPacket(
            UUID token,
            GateEditDraftAction action,
            int x,
            int y,
            int z,
            boolean fillEnclosed
    ) {
        this.token = token;
        this.action = action;
        this.x = x;
        this.y = y;
        this.z = z;
        this.fillEnclosed = fillEnclosed;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            token = new UUID(buffer.readLong(), buffer.readLong());
            int id = buffer.readUnsignedByte();
            if (id >= GateEditDraftAction.values().length) {
                throw new IllegalArgumentException();
            }
            action = GateEditDraftAction.values()[id];
            x = buffer.readInt();
            y = buffer.readInt();
            z = buffer.readInt();
            fillEnclosed = buffer.readBoolean();
            valid = true;
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(token == null ? 0L : token.getMostSignificantBits());
        buffer.writeLong(token == null ? 0L : token.getLeastSignificantBits());
        buffer.writeByte(action == null ? 255 : action.ordinal());
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeBoolean(fillEnclosed);
    }

    public static final class Handler
            implements IMessageHandler<GateEditDraftActionPacket, IMessage> {
        @Override
        public IMessage onMessage(
                GateEditDraftActionPacket message,
                MessageContext context
        ) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!MumakilConfig.enableSiegeGates || player == null || message == null || !message.valid) {
                return null;
            }

            if (!SiegeRequestLimiter.tryAcquire(
                    player.getUniqueID(),
                    SiegeRequestLimiter.RateClass.EDIT_DRAFT_ACTION
            ) || !GateEditRequestManager.enqueueDraft(
                    player,
                    message.token,
                    message.action,
                    message.x,
                    message.y,
                    message.z,
                    message.fillEnclosed
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
