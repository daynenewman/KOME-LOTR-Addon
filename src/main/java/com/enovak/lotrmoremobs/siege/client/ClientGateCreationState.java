package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.creation.GateBlockPosition;
import com.enovak.lotrmoremobs.siege.creation.GateSelectionMode;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientGateCreationState {

    private static final Map<GateBlockPosition, GateLeaf> SELECTIONS =
            new LinkedHashMap<GateBlockPosition, GateLeaf>();

    private static boolean active;
    private static int dimensionId;
    private static GateBlockPosition controllerPosition;
    private static GateLeaf activeLeaf = GateLeaf.LEFT;
    private static GateSelectionMode selectionMode =
            GateSelectionMode.NONE;
    private static GateOpeningDirection openingDirection =
            GateOpeningDirection.FORWARD;
    private static boolean borderTextureEnabled = true;
    private static GateBlockPosition leftHingePosition;
    private static GateBlockPosition rightHingePosition;

    private ClientGateCreationState() {
    }

    public static void start(
            int dimensionId,
            GateBlockPosition controllerPosition,
            GateLeaf activeLeaf,
            List<GateBlockPosition> positions,
            List<GateLeaf> leaves,
            GateSelectionMode selectionMode,
            GateOpeningDirection openingDirection,
            boolean borderTextureEnabled,
            GateBlockPosition leftHingePosition,
            GateBlockPosition rightHingePosition
    ) {
        clear();
        ClientGateCreationState.active = controllerPosition != null;
        ClientGateCreationState.dimensionId = dimensionId;
        ClientGateCreationState.controllerPosition = controllerPosition;
        ClientGateCreationState.activeLeaf = activeLeaf == null
                ? GateLeaf.LEFT
                : activeLeaf;
        applyConfiguration(
                activeLeaf,
                selectionMode,
                openingDirection,
                borderTextureEnabled,
                leftHingePosition,
                rightHingePosition
        );
        int count = Math.min(positions.size(), leaves.size());
        for (int i = 0; i < count; ++i) {
            if (positions.get(i) != null && leaves.get(i) != null) {
                SELECTIONS.put(positions.get(i), leaves.get(i));
            }
        }
    }

    public static void setActiveLeaf(GateLeaf leaf) {
        if (active && leaf != null) {
            activeLeaf = leaf;
        }
    }

    public static void applyConfiguration(
            GateLeaf activeLeaf,
            GateSelectionMode selectionMode,
            GateOpeningDirection openingDirection,
            boolean borderTextureEnabled,
            GateBlockPosition leftHingePosition,
            GateBlockPosition rightHingePosition
    ) {
        if (!active) {
            return;
        }
        if (activeLeaf != null) {
            ClientGateCreationState.activeLeaf = activeLeaf;
        }
        ClientGateCreationState.selectionMode = selectionMode == null
                ? GateSelectionMode.NONE
                : selectionMode;
        ClientGateCreationState.openingDirection =
                openingDirection == null
                ? GateOpeningDirection.FORWARD
                : openingDirection;
        ClientGateCreationState.borderTextureEnabled =
                borderTextureEnabled;
        ClientGateCreationState.leftHingePosition = leftHingePosition;
        ClientGateCreationState.rightHingePosition = rightHingePosition;
    }

    public static void updateSelection(
            GateBlockPosition position,
            GateLeaf leaf
    ) {
        if (!active || position == null) {
            return;
        }
        if (leaf == null) {
            SELECTIONS.remove(position);
        } else {
            SELECTIONS.put(position, leaf);
        }
    }

    public static void clear() {
        active = false;
        controllerPosition = null;
        activeLeaf = GateLeaf.LEFT;
        selectionMode = GateSelectionMode.NONE;
        openingDirection = GateOpeningDirection.FORWARD;
        borderTextureEnabled = true;
        leftHingePosition = null;
        rightHingePosition = null;
        SELECTIONS.clear();
    }

    public static boolean isActive() {
        return active;
    }

    public static int getDimensionId() {
        return dimensionId;
    }

    public static GateBlockPosition getControllerPosition() {
        return controllerPosition;
    }

    public static GateLeaf getActiveLeaf() {
        return activeLeaf;
    }

    public static GateSelectionMode getSelectionMode() {
        return selectionMode;
    }

    public static boolean isWorldSelectionActive() {
        return active && selectionMode != GateSelectionMode.NONE;
    }

    public static GateOpeningDirection getOpeningDirection() {
        return openingDirection;
    }

    public static boolean isBorderTextureEnabled() {
        return borderTextureEnabled;
    }

    public static GateBlockPosition getLeftHingePosition() {
        return leftHingePosition;
    }

    public static GateBlockPosition getRightHingePosition() {
        return rightHingePosition;
    }

    public static Map<GateBlockPosition, GateLeaf> getSelections() {
        return Collections.unmodifiableMap(SELECTIONS);
    }

    public static int getSelectionCount(GateLeaf leaf) {
        int count = 0;
        for (GateLeaf selectedLeaf : SELECTIONS.values()) {
            if (selectedLeaf == leaf) {
                ++count;
            }
        }
        return count;
    }
}
