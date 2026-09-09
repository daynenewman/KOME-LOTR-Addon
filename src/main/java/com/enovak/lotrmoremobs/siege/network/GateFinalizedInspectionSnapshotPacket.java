package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateHingeSide;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GateState;
import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;
import com.enovak.lotrmoremobs.siege.management.FinalizedGateSnapshot;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded server-to-client receipt for one immutable INSPECT_EXISTING snapshot. */
public final class GateFinalizedInspectionSnapshotPacket implements IMessage {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_GATE_NAME_BYTES =
            TileEntitySiegeGate.MAX_GATE_NAME_LENGTH * 4;
    private static final int MAX_FACTION_NAME_BYTES =
            FinalizedGateSnapshot.MAX_FACTION_NAME_LENGTH * 4;

    private FinalizedGateSnapshot snapshot;
    private boolean valid;

    public GateFinalizedInspectionSnapshotPacket() {
    }

    public GateFinalizedInspectionSnapshotPacket(
            FinalizedGateSnapshot snapshot
    ) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Inspection snapshot is required.");
        }
        this.snapshot = snapshot;
        valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        snapshot = null;
        try {
            UUID gateUuid = new UUID(buffer.readLong(), buffer.readLong());
            int revision = buffer.readInt();
            int dimension = buffer.readInt();
            int controllerX = buffer.readInt();
            int controllerY = buffer.readInt();
            int controllerZ = buffer.readInt();
            GateState state = readRequiredGateState(buffer);
            int currentHealth = buffer.readInt();
            int maxHealth = buffer.readInt();
            boolean repairActive = buffer.readBoolean();
            boolean ramReserved = buffer.readBoolean();
            String gateName = readBoundedString(buffer, MAX_GATE_NAME_BYTES);
            String factionName = readBoundedString(
                    buffer,
                    MAX_FACTION_NAME_BYTES
            );
            int requiredAlignment = buffer.readInt();
            GateOrientation orientation = readOptionalOrientation(buffer);
            GateOpeningDirection openingDirection =
                    readOptionalOpeningDirection(buffer);
            GateHinge leftHinge = readOptionalHinge(buffer);
            GateHinge rightHinge = readOptionalHinge(buffer);
            int count = buffer.readUnsignedShort();
            if (count > GateStructureValidator.MAX_GATE_PARTS) {
                throw new IllegalArgumentException("Too many inspection parts.");
            }
            List<FinalizedGateSnapshot.PartEntry> parts =
                    new ArrayList<FinalizedGateSnapshot.PartEntry>(count);
            for (int i = 0; i < count; ++i) {
                int relativeX = buffer.readInt();
                int relativeY = buffer.readInt();
                int relativeZ = buffer.readInt();
                GateLeaf leaf = GateLeaf.fromWireId(buffer.readUnsignedByte());
                if (leaf == null) {
                    throw new IllegalArgumentException(
                            "Invalid inspection GateLeaf value."
                    );
                }
                parts.add(new FinalizedGateSnapshot.PartEntry(
                        relativeX,
                        relativeY,
                        relativeZ,
                        leaf
                ));
            }
            snapshot = new FinalizedGateSnapshot(
                    gateUuid,
                    dimension,
                    controllerX,
                    controllerY,
                    controllerZ,
                    revision,
                    state,
                    currentHealth,
                    maxHealth,
                    repairActive,
                    ramReserved,
                    gateName,
                    factionName,
                    requiredAlignment,
                    orientation,
                    openingDirection,
                    leftHinge,
                    rightHinge,
                    parts
            );
            valid = true;
        } catch (RuntimeException ignored) {
            snapshot = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (snapshot == null) {
            throw new IllegalStateException("Inspection snapshot is unavailable.");
        }
        UUID gateUuid = snapshot.getGateUuid();
        buffer.writeLong(gateUuid.getMostSignificantBits());
        buffer.writeLong(gateUuid.getLeastSignificantBits());
        buffer.writeInt(snapshot.getBaseStructureRevision());
        buffer.writeInt(snapshot.getDimensionId());
        buffer.writeInt(snapshot.getControllerX());
        buffer.writeInt(snapshot.getControllerY());
        buffer.writeInt(snapshot.getControllerZ());
        buffer.writeByte(snapshot.getGateState().ordinal());
        buffer.writeInt(snapshot.getCurrentHealth());
        buffer.writeInt(snapshot.getMaxHealth());
        buffer.writeBoolean(snapshot.isRepairActive());
        buffer.writeBoolean(snapshot.isRamReserved());
        writeBoundedString(
                buffer,
                snapshot.getGateName(),
                MAX_GATE_NAME_BYTES
        );
        writeBoundedString(
                buffer,
                snapshot.getFactionName(),
                MAX_FACTION_NAME_BYTES
        );
        buffer.writeInt(snapshot.getRequiredAlignment());
        writeOptionalEnum(buffer, snapshot.getOrientation());
        writeOptionalEnum(buffer, snapshot.getOpeningDirection());
        writeOptionalHinge(buffer, snapshot.getLeftHinge());
        writeOptionalHinge(buffer, snapshot.getRightHinge());
        List<FinalizedGateSnapshot.PartEntry> parts = snapshot.getParts();
        if (parts.size() > GateStructureValidator.MAX_GATE_PARTS) {
            throw new IllegalStateException("Too many inspection parts.");
        }
        buffer.writeShort(parts.size());
        for (FinalizedGateSnapshot.PartEntry part : parts) {
            buffer.writeInt(part.getRelativeX());
            buffer.writeInt(part.getRelativeY());
            buffer.writeInt(part.getRelativeZ());
            buffer.writeByte(part.getLeaf().getWireId());
        }
    }

    public boolean isValid() {
        return valid && snapshot != null;
    }

    public FinalizedGateSnapshot getSnapshot() {
        return snapshot;
    }

    private static GateState readRequiredGateState(ByteBuf buffer) {
        int value = buffer.readUnsignedByte();
        GateState[] values = GateState.values();
        if (value >= values.length) {
            throw new IllegalArgumentException("Invalid inspection GateState.");
        }
        return values[value];
    }

    private static GateOrientation readOptionalOrientation(ByteBuf buffer) {
        int value = buffer.readByte();
        if (value == -1) {
            return null;
        }
        GateOrientation[] values = GateOrientation.values();
        if (value < 0 || value >= values.length) {
            throw new IllegalArgumentException("Invalid inspection orientation.");
        }
        return values[value];
    }

    private static GateOpeningDirection readOptionalOpeningDirection(
            ByteBuf buffer
    ) {
        int value = buffer.readByte();
        if (value == -1) {
            return null;
        }
        GateOpeningDirection[] values = GateOpeningDirection.values();
        if (value < 0 || value >= values.length) {
            throw new IllegalArgumentException("Invalid inspection direction.");
        }
        return values[value];
    }

    private static GateHinge readOptionalHinge(ByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        int relativeX = buffer.readInt();
        int relativeZ = buffer.readInt();
        int sideValue = buffer.readByte();
        GateHingeSide side = null;
        if (sideValue != -1) {
            GateHingeSide[] sides = GateHingeSide.values();
            if (sideValue < 0 || sideValue >= sides.length) {
                throw new IllegalArgumentException("Invalid inspection hinge side.");
            }
            side = sides[sideValue];
        }
        return new GateHinge(relativeX, relativeZ, side);
    }

    private static void writeOptionalHinge(ByteBuf buffer, GateHinge hinge) {
        buffer.writeBoolean(hinge != null);
        if (hinge != null) {
            buffer.writeInt(hinge.getRelativeX());
            buffer.writeInt(hinge.getRelativeZ());
            writeOptionalEnum(buffer, hinge.getSide());
        }
    }

    private static void writeOptionalEnum(ByteBuf buffer, Enum<?> value) {
        buffer.writeByte(value == null ? -1 : value.ordinal());
    }

    private static String readBoundedString(ByteBuf buffer, int maximumBytes) {
        int length = buffer.readUnsignedShort();
        if (length > maximumBytes || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("Oversized inspection string.");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, UTF8);
    }

    private static void writeBoundedString(
            ByteBuf buffer,
            String value,
            int maximumBytes
    ) {
        byte[] bytes = (value == null ? "" : value).getBytes(UTF8);
        if (bytes.length > maximumBytes) {
            throw new IllegalStateException("Oversized inspection string.");
        }
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    public static final class Handler implements IMessageHandler<
            GateFinalizedInspectionSnapshotPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                GateFinalizedInspectionSnapshotPacket message,
                MessageContext context
        ) {
            if (message.isValid()) {
                Main.proxy.handleGateFinalizedInspectionSnapshot(message);
            }
            return null;
        }
    }
}
