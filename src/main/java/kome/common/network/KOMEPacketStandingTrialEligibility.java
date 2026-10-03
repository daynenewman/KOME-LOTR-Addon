package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.UUID;

/** Server-authored Trial eligibility and native LOTR offer-indicator state for one NPC. */
public final class KOMEPacketStandingTrialEligibility implements IMessage {
    public int entityId;
    public long entityUuidMost;
    public long entityUuidLeast;
    public boolean eligible;
    public boolean passiveOffer;
    public boolean offering;
    public int offerColor;

    public KOMEPacketStandingTrialEligibility() {
    }

    public KOMEPacketStandingTrialEligibility(
            int entityId,
            UUID entityUuid,
            boolean eligible,
            boolean passiveOffer,
            boolean offering,
            int offerColor) {
        this.entityId = entityId;
        this.entityUuidMost = entityUuid == null ? 0L : entityUuid.getMostSignificantBits();
        this.entityUuidLeast = entityUuid == null ? 0L : entityUuid.getLeastSignificantBits();
        this.eligible = eligible;
        this.passiveOffer = passiveOffer;
        this.offering = offering;
        this.offerColor = offerColor;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        entityId = buffer.readInt();
        entityUuidMost = buffer.readLong();
        entityUuidLeast = buffer.readLong();
        eligible = buffer.readBoolean();
        passiveOffer = buffer.readBoolean();
        offering = buffer.readBoolean();
        offerColor = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(entityId);
        buffer.writeLong(entityUuidMost);
        buffer.writeLong(entityUuidLeast);
        buffer.writeBoolean(eligible);
        buffer.writeBoolean(passiveOffer);
        buffer.writeBoolean(offering);
        buffer.writeInt(offerColor);
    }

    public static final class Handler
            implements IMessageHandler<KOMEPacketStandingTrialEligibility, IMessage> {
        @Override
        public IMessage onMessage(
                final KOMEPacketStandingTrialEligibility message,
                MessageContext context) {
            KOMEAddon.proxy.enqueueClientTask(new Runnable() {
                @Override
                public void run() {
                    KOMEAddon.proxy.updateStandingTrialEligibility(
                        message.entityId,
                        message.entityUuidMost,
                        message.entityUuidLeast,
                        message.eligible,
                        message.passiveOffer,
                        message.offering,
                        message.offerColor);
                }
            });
            return null;
        }
    }
}
