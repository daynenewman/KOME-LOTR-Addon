package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import lotr.common.entity.npc.LOTREntityNPC;

/**
 * Synchronizes only the stable ram/crew relationship. Carrier positions remain
 * derived locally from the shared formation transform.
 */
public class RamCrewAttachmentPacket implements IMessage {

    private int dimensionId;
    private int ramEntityId;
    private long ramUuidMost;
    private long ramUuidLeast;
    private int crewEntityId;
    private long crewUuidMost;
    private long crewUuidLeast;
    private int slot;
    private boolean attached;

    public RamCrewAttachmentPacket() {
    }

    public RamCrewAttachmentPacket(
            EntityBattleRam ram,
            LOTREntityNPC crew,
            int slot,
            boolean attached
    ) {
        this(
                crew.worldObj.provider.dimensionId,
                ram == null ? -1 : ram.getEntityId(),
                ram == null
                        ? EntityBattleRam.getTaggedRamUuid(crew)
                        : ram.getUniqueID(),
                crew.getEntityId(),
                crew.getUniqueID(),
                slot,
                attached
        );
    }

    private RamCrewAttachmentPacket(
            int dimensionId,
            int ramEntityId,
            UUID ramUuid,
            int crewEntityId,
            UUID crewUuid,
            int slot,
            boolean attached
    ) {
        this.dimensionId = dimensionId;
        this.ramEntityId = ramEntityId;
        ramUuidMost = ramUuid == null ? 0L : ramUuid.getMostSignificantBits();
        ramUuidLeast = ramUuid == null ? 0L : ramUuid.getLeastSignificantBits();
        this.crewEntityId = crewEntityId;
        crewUuidMost = crewUuid == null
                ? 0L
                : crewUuid.getMostSignificantBits();
        crewUuidLeast = crewUuid == null
                ? 0L
                : crewUuid.getLeastSignificantBits();
        this.slot = slot;
        this.attached = attached;
    }

    public static RamCrewAttachmentPacket detached(LOTREntityNPC crew) {
        return new RamCrewAttachmentPacket(
                null,
                crew,
                EntityBattleRam.getTaggedCrewSlot(crew),
                false
        );
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimensionId = buffer.readInt();
        ramEntityId = buffer.readInt();
        ramUuidMost = buffer.readLong();
        ramUuidLeast = buffer.readLong();
        crewEntityId = buffer.readInt();
        crewUuidMost = buffer.readLong();
        crewUuidLeast = buffer.readLong();
        slot = buffer.readInt();
        attached = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimensionId);
        buffer.writeInt(ramEntityId);
        buffer.writeLong(ramUuidMost);
        buffer.writeLong(ramUuidLeast);
        buffer.writeInt(crewEntityId);
        buffer.writeLong(crewUuidMost);
        buffer.writeLong(crewUuidLeast);
        buffer.writeInt(slot);
        buffer.writeBoolean(attached);
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getRamEntityId() {
        return ramEntityId;
    }

    public UUID getRamUuid() {
        return new UUID(ramUuidMost, ramUuidLeast);
    }

    public int getCrewEntityId() {
        return crewEntityId;
    }

    public UUID getCrewUuid() {
        return new UUID(crewUuidMost, crewUuidLeast);
    }

    public int getSlot() {
        return slot;
    }

    public boolean isAttached() {
        return attached;
    }

    public static class Handler implements IMessageHandler<
            RamCrewAttachmentPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                RamCrewAttachmentPacket message,
                MessageContext context
        ) {
            Main.proxy.handleRamCrewAttachment(message);
            return null;
        }
    }
}
