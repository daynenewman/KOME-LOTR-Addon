package com.enovak.lotrmoremobs.siege.creation;

import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.World;

final class GateCreationSession {

    private final UUID creatorUuid;
    private final UUID gateUuid;
    private final World world;
    private final int dimensionId;
    private final GateBlockPosition controllerPosition;
    private final Map<GateBlockPosition, GateSelectionData> selections =
            new LinkedHashMap<GateBlockPosition, GateSelectionData>();
    private GateLeaf activeLeaf = GateLeaf.LEFT;
    private GateSelectionMode selectionMode = GateSelectionMode.NONE;
    private GateOpeningDirection openingDirection =
            GateOpeningDirection.FORWARD;
    private boolean borderTextureEnabled = true;
    private GateBlockPosition leftHingePosition;
    private GateBlockPosition rightHingePosition;

    GateCreationSession(
            UUID creatorUuid,
            UUID gateUuid,
            World world,
            int dimensionId,
            GateBlockPosition controllerPosition
    ) {
        this.creatorUuid = creatorUuid;
        this.gateUuid = gateUuid;
        this.world = world;
        this.dimensionId = dimensionId;
        this.controllerPosition = controllerPosition;
    }

    UUID getCreatorUuid() {
        return creatorUuid;
    }

    UUID getGateUuid() {
        return gateUuid;
    }

    World getWorld() {
        return world;
    }

    int getDimensionId() {
        return dimensionId;
    }

    GateBlockPosition getControllerPosition() {
        return controllerPosition;
    }

    GateLeaf getActiveLeaf() {
        return activeLeaf;
    }

    void setActiveLeaf(GateLeaf activeLeaf) {
        if (activeLeaf != null) {
            this.activeLeaf = activeLeaf;
        }
    }

    GateSelectionMode getSelectionMode() {
        return selectionMode;
    }

    void setSelectionMode(GateSelectionMode selectionMode) {
        if (selectionMode != null) {
            this.selectionMode = selectionMode;
        }
    }

    GateOpeningDirection getOpeningDirection() {
        return openingDirection;
    }

    void toggleOpeningDirection() {
        openingDirection = openingDirection.opposite();
    }

    boolean isBorderTextureEnabled() {
        return borderTextureEnabled;
    }

    void toggleBorderTexture() {
        borderTextureEnabled = !borderTextureEnabled;
    }

    GateBlockPosition getLeftHingePosition() {
        return leftHingePosition;
    }

    GateBlockPosition getRightHingePosition() {
        return rightHingePosition;
    }

    void setHingePosition(
            GateLeaf leaf,
            GateBlockPosition position
    ) {
        if (leaf == GateLeaf.LEFT) {
            leftHingePosition = position;
        } else if (leaf == GateLeaf.RIGHT) {
            rightHingePosition = position;
        }
    }

    GateSelectionData getSelection(GateBlockPosition position) {
        return selections.get(position);
    }

    void putSelection(GateSelectionData selection) {
        selections.put(selection.getPosition(), selection);
        clearInvalidHinge(selection.getPosition(), selection.getLeaf());
    }

    GateSelectionData removeSelection(GateBlockPosition position) {
        GateSelectionData removed = selections.remove(position);
        clearInvalidHinge(position, null);
        return removed;
    }

    int getSelectionCount() {
        return selections.size();
    }

    Collection<GateSelectionData> getSelections() {
        return selections.values();
    }

    private void clearInvalidHinge(
            GateBlockPosition position,
            GateLeaf selectedLeaf
    ) {
        if (position.equals(leftHingePosition)
                && selectedLeaf != GateLeaf.LEFT) {
            leftHingePosition = null;
        }
        if (position.equals(rightHingePosition)
                && selectedLeaf != GateLeaf.RIGHT) {
            rightHingePosition = null;
        }
    }
}
