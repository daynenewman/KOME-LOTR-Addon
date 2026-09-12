package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RamTargetModePacket implements IMessage {

    private static final int MAX_TARGETS = 64;

    private int dimensionId;
    private int ramEntityId;
    private boolean active;
    private boolean queueRefreshOnly;
    private String ramUuid = "";
    private String ramFaction = "";
    private final List<TargetEntry> targetQueue =
            new ArrayList<TargetEntry>();

    public RamTargetModePacket() {
    }

    public RamTargetModePacket(
            int dimensionId,
            int ramEntityId,
            boolean active
    ) {
        this.dimensionId = dimensionId;
        this.ramEntityId = ramEntityId;
        this.active = active;
    }

    public RamTargetModePacket(
            int dimensionId,
            EntityBattleRam ram,
            boolean active
    ) {
        this.dimensionId = dimensionId;
        this.ramEntityId = ram == null ? 0 : ram.getEntityId();
        this.active = active;

        if (ram == null) {
            return;
        }

        UUID uuid = ram.getUniqueID();
        ramUuid = uuid == null ? "" : uuid.toString();
        ramFaction = ram.getRamFaction() == null
                ? ""
                : ram.getRamFaction().codeName();

        for (EntityBattleRam.TargetQueueSnapshot snapshot
                : ram.getTargetQueueSnapshot()) {
            if (snapshot == null || targetQueue.size() >= MAX_TARGETS) {
                break;
            }
            targetQueue.add(new TargetEntry(
                    snapshot.getDimensionId(),
                    snapshot.getControllerX(),
                    snapshot.getControllerY(),
                    snapshot.getControllerZ()
            ));
        }
    }

    public static RamTargetModePacket queueRefresh(
            int dimensionId,
            EntityBattleRam ram
    ) {
        RamTargetModePacket packet = new RamTargetModePacket(
                dimensionId,
                ram,
                false
        );
        packet.queueRefreshOnly = true;
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimensionId = buffer.readInt();
        ramEntityId = buffer.readInt();
        active = buffer.readBoolean();
        queueRefreshOnly = buffer.readBoolean();
        ramUuid = ByteBufUtils.readUTF8String(buffer);
        ramFaction = ByteBufUtils.readUTF8String(buffer);

        targetQueue.clear();
        int count = Math.min(buffer.readUnsignedByte(), MAX_TARGETS);
        for (int i = 0; i < count; ++i) {
            targetQueue.add(new TargetEntry(
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt()
            ));
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimensionId);
        buffer.writeInt(ramEntityId);
        buffer.writeBoolean(active);
        buffer.writeBoolean(queueRefreshOnly);
        ByteBufUtils.writeUTF8String(buffer, ramUuid == null ? "" : ramUuid);
        ByteBufUtils.writeUTF8String(
                buffer,
                ramFaction == null ? "" : ramFaction
        );

        int count = Math.min(targetQueue.size(), MAX_TARGETS);
        buffer.writeByte(count);
        for (int i = 0; i < count; ++i) {
            TargetEntry entry = targetQueue.get(i);
            buffer.writeInt(entry.dimensionId);
            buffer.writeInt(entry.controllerX);
            buffer.writeInt(entry.controllerY);
            buffer.writeInt(entry.controllerZ);
        }
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getRamEntityId() {
        return ramEntityId;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isQueueRefreshOnly() {
        return queueRefreshOnly;
    }

    public String getRamUuid() {
        return ramUuid;
    }

    public String getRamFaction() {
        return ramFaction;
    }

    public List<TargetEntry> getTargetQueue() {
        return Collections.unmodifiableList(targetQueue);
    }

    public static final class TargetEntry {
        private final int dimensionId;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;

        public TargetEntry(
                int dimensionId,
                int controllerX,
                int controllerY,
                int controllerZ
        ) {
            this.dimensionId = dimensionId;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
        }

        public int getDimensionId() {
            return dimensionId;
        }

        public int getControllerX() {
            return controllerX;
        }

        public int getControllerY() {
            return controllerY;
        }

        public int getControllerZ() {
            return controllerZ;
        }
    }

    public static class Handler implements IMessageHandler<
            RamTargetModePacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                RamTargetModePacket message,
                MessageContext context
        ) {
            Main.proxy.handleRamTargetMode(message);
            return null;
        }
    }
}
