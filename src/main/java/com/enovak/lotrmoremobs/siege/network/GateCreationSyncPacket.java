package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.creation.GateBlockPosition;
import com.enovak.lotrmoremobs.siege.creation.GateSelectionMode;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GateCreationSyncPacket implements IMessage {

    public static final int START = 0;
    public static final int CONFIGURATION = 1;
    public static final int PART_UPDATE = 2;
    public static final int END = 3;
    private static final int MAX_PARTS = GateStructureValidator.MAX_GATE_PARTS;

    private int operation;
    private int dimensionId;
    private GateBlockPosition controllerPosition;
    private GateLeaf activeLeaf;
    private GateSelectionMode selectionMode = GateSelectionMode.NONE;
    private GateOpeningDirection openingDirection =
            GateOpeningDirection.FORWARD;
    private boolean borderTextureEnabled = true;
    private GateBlockPosition leftHingePosition;
    private GateBlockPosition rightHingePosition;
    private boolean openControls;
    private List<GateBlockPosition> positions =
            Collections.emptyList();
    private List<GateLeaf> leaves = Collections.emptyList();
    private GateBlockPosition changedPosition;
    private GateLeaf changedLeaf;

    public GateCreationSyncPacket() {
    }

    public static GateCreationSyncPacket start(
            int dimensionId,
            GateBlockPosition controllerPosition,
            GateLeaf activeLeaf,
            List<GateBlockPosition> positions,
            List<GateLeaf> leaves,
            GateSelectionMode selectionMode,
            GateOpeningDirection openingDirection,
            boolean borderTextureEnabled,
            GateBlockPosition leftHingePosition,
            GateBlockPosition rightHingePosition,
            boolean openControls
    ) {
        GateCreationSyncPacket packet = new GateCreationSyncPacket();
        packet.operation = START;
        packet.dimensionId = dimensionId;
        packet.controllerPosition = controllerPosition;
        packet.activeLeaf = activeLeaf;
        packet.positions = positions;
        packet.leaves = leaves;
        packet.selectionMode = selectionMode;
        packet.openingDirection = openingDirection;
        packet.borderTextureEnabled = borderTextureEnabled;
        packet.leftHingePosition = leftHingePosition;
        packet.rightHingePosition = rightHingePosition;
        packet.openControls = openControls;
        return packet;
    }

    public static GateCreationSyncPacket configuration(
            GateLeaf activeLeaf,
            GateSelectionMode selectionMode,
            GateOpeningDirection openingDirection,
            boolean borderTextureEnabled,
            GateBlockPosition leftHingePosition,
            GateBlockPosition rightHingePosition
    ) {
        GateCreationSyncPacket packet = new GateCreationSyncPacket();
        packet.operation = CONFIGURATION;
        packet.activeLeaf = activeLeaf;
        packet.selectionMode = selectionMode;
        packet.openingDirection = openingDirection;
        packet.borderTextureEnabled = borderTextureEnabled;
        packet.leftHingePosition = leftHingePosition;
        packet.rightHingePosition = rightHingePosition;
        return packet;
    }

    public static GateCreationSyncPacket partUpdate(
            GateBlockPosition position,
            GateLeaf leaf
    ) {
        GateCreationSyncPacket packet = new GateCreationSyncPacket();
        packet.operation = PART_UPDATE;
        packet.changedPosition = position;
        packet.changedLeaf = leaf;
        return packet;
    }

    public static GateCreationSyncPacket end() {
        GateCreationSyncPacket packet = new GateCreationSyncPacket();
        packet.operation = END;
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        operation = buffer.readUnsignedByte();
        if (operation == START) {
            dimensionId = buffer.readInt();
            controllerPosition = readPosition(buffer);
            activeLeaf = readRequiredLeaf(buffer.readByte());
            readConfiguration(buffer);
            openControls = buffer.readBoolean();
            int count = buffer.readUnsignedShort();
            if (count > MAX_PARTS) {
                throw new IllegalArgumentException(
                        "Too many gate creation selections"
                );
            }
            positions = new ArrayList<GateBlockPosition>(count);
            leaves = new ArrayList<GateLeaf>(count);
            for (int i = 0; i < count; ++i) {
                GateBlockPosition position = readPosition(buffer);
                GateLeaf leaf = readRequiredLeaf(buffer.readByte());
                positions.add(position);
                leaves.add(leaf);
            }
        } else if (operation == CONFIGURATION) {
            activeLeaf = readRequiredLeaf(buffer.readByte());
            readConfiguration(buffer);
        } else if (operation == PART_UPDATE) {
            changedPosition = readPosition(buffer);
            int wireId = buffer.readByte();
            changedLeaf = wireId == -1
                    ? null
                    : readRequiredLeaf(wireId);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(operation);
        if (operation == START) {
            buffer.writeInt(dimensionId);
            writePosition(buffer, controllerPosition);
            buffer.writeByte(writeLeaf(activeLeaf));
            writeConfiguration(buffer);
            buffer.writeBoolean(openControls);
            int count = Math.min(
                    Math.min(positions.size(), leaves.size()),
                    MAX_PARTS
            );
            buffer.writeShort(count);
            for (int i = 0; i < count; ++i) {
                writePosition(buffer, positions.get(i));
                buffer.writeByte(writeLeaf(leaves.get(i)));
            }
        } else if (operation == CONFIGURATION) {
            buffer.writeByte(writeLeaf(activeLeaf));
            writeConfiguration(buffer);
        } else if (operation == PART_UPDATE) {
            writePosition(buffer, changedPosition);
            buffer.writeByte(changedLeaf == null
                    ? -1
                    : writeLeaf(changedLeaf));
        }
    }

    public int getOperation() {
        return operation;
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public GateBlockPosition getControllerPosition() {
        return controllerPosition;
    }

    public GateLeaf getActiveLeaf() {
        return activeLeaf;
    }

    public GateSelectionMode getSelectionMode() {
        return selectionMode;
    }

    public GateOpeningDirection getOpeningDirection() {
        return openingDirection;
    }

    public boolean isBorderTextureEnabled() {
        return borderTextureEnabled;
    }

    public GateBlockPosition getLeftHingePosition() {
        return leftHingePosition;
    }

    public GateBlockPosition getRightHingePosition() {
        return rightHingePosition;
    }

    public boolean shouldOpenControls() {
        return openControls;
    }

    public List<GateBlockPosition> getPositions() {
        return positions;
    }

    public List<GateLeaf> getLeaves() {
        return leaves;
    }

    public GateBlockPosition getChangedPosition() {
        return changedPosition;
    }

    public GateLeaf getChangedLeaf() {
        return changedLeaf;
    }

    private void readConfiguration(ByteBuf buffer) {
        selectionMode = GateSelectionMode.fromOrdinal(buffer.readByte());
        openingDirection = readOpeningDirection(buffer.readByte());
        borderTextureEnabled = buffer.readBoolean();
        leftHingePosition = readNullablePosition(buffer);
        rightHingePosition = readNullablePosition(buffer);
    }

    private void writeConfiguration(ByteBuf buffer) {
        buffer.writeByte(selectionMode.ordinal());
        buffer.writeByte(openingDirection.ordinal());
        buffer.writeBoolean(borderTextureEnabled);
        writeNullablePosition(buffer, leftHingePosition);
        writeNullablePosition(buffer, rightHingePosition);
    }

    private static GateBlockPosition readPosition(ByteBuf buffer) {
        return new GateBlockPosition(
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt()
        );
    }

    private static void writePosition(
            ByteBuf buffer,
            GateBlockPosition position
    ) {
        buffer.writeInt(position.getX());
        buffer.writeInt(position.getY());
        buffer.writeInt(position.getZ());
    }

    private static GateBlockPosition readNullablePosition(ByteBuf buffer) {
        return buffer.readBoolean() ? readPosition(buffer) : null;
    }

    private static void writeNullablePosition(
            ByteBuf buffer,
            GateBlockPosition position
    ) {
        buffer.writeBoolean(position != null);
        if (position != null) {
            writePosition(buffer, position);
        }
    }

    private static int writeLeaf(GateLeaf leaf) {
        return leaf == null ? -1 : leaf.getWireId();
    }

    private static GateLeaf readLeaf(int serializedLeaf) {
        return GateLeaf.fromWireId(serializedLeaf);
    }

    private static GateLeaf readRequiredLeaf(int serializedLeaf) {
        GateLeaf leaf = readLeaf(serializedLeaf);
        if (leaf == null) {
            throw new IllegalArgumentException("Unknown gate leaf wire ID");
        }
        return leaf;
    }

    private static GateOpeningDirection readOpeningDirection(int ordinal) {
        return ordinal >= 0
                && ordinal < GateOpeningDirection.values().length
                ? GateOpeningDirection.values()[ordinal]
                : GateOpeningDirection.FORWARD;
    }

    public static class Handler implements IMessageHandler<
            GateCreationSyncPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                final GateCreationSyncPacket message,
                MessageContext context
        ) {
            Main.proxy.handleGateCreationSync(message);
            return null;
        }
    }
}
