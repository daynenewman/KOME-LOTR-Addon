package com.enovak.lotrmoremobs.siege.management;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.gate.GateState;
import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Detached, immutable display snapshot of one already-finalized Siege Gate.
 * It deliberately has no World, player, TileEntity, or persistence reference.
 */
public final class FinalizedGateSnapshot {

    public static final int MAX_FACTION_NAME_LENGTH = 64;

    private final UUID gateUuid;
    private final int dimensionId;
    private final int controllerX;
    private final int controllerY;
    private final int controllerZ;
    private final int baseStructureRevision;
    private final GateState gateState;
    private final int currentHealth;
    private final int maxHealth;
    private final boolean repairActive;
    private final boolean ramReserved;
    private final String gateName;
    private final String factionName;
    private final int requiredAlignment;
    private final GateOrientation orientation;
    private final GateOpeningDirection openingDirection;
    private final GateHinge leftHinge;
    private final GateHinge rightHinge;
    private final List<PartEntry> parts;
    private final int minRelativeX;
    private final int maxRelativeX;
    private final int minRelativeY;
    private final int maxRelativeY;
    private final int minRelativeZ;
    private final int maxRelativeZ;

    public FinalizedGateSnapshot(
            UUID gateUuid,
            int dimensionId,
            int controllerX,
            int controllerY,
            int controllerZ,
            int baseStructureRevision,
            GateState gateState,
            int currentHealth,
            int maxHealth,
            boolean repairActive,
            boolean ramReserved,
            String gateName,
            String factionName,
            int requiredAlignment,
            GateOrientation orientation,
            GateOpeningDirection openingDirection,
            GateHinge leftHinge,
            GateHinge rightHinge,
            Collection<PartEntry> parts
    ) {
        if (gateUuid == null || baseStructureRevision <= 0
                || gateState == null || parts == null
                || parts.isEmpty()
                || parts.size() > GateStructureValidator.MAX_GATE_PARTS) {
            throw new IllegalArgumentException(
                    "Invalid finalized Siege Gate inspection snapshot."
            );
        }

        List<PartEntry> copiedParts = new ArrayList<PartEntry>(parts.size());
        for (PartEntry part : parts) {
            if (part == null || part.getLeaf() == null) {
                throw new IllegalArgumentException(
                        "Invalid finalized Siege Gate part entry."
                );
            }
            copiedParts.add(new PartEntry(
                    part.getRelativeX(),
                    part.getRelativeY(),
                    part.getRelativeZ(),
                    part.getLeaf()
            ));
        }

        this.gateUuid = gateUuid;
        this.dimensionId = dimensionId;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
        this.baseStructureRevision = baseStructureRevision;
        this.gateState = gateState;
        this.currentHealth = Math.max(0, currentHealth);
        this.maxHealth = Math.max(0, maxHealth);
        this.repairActive = repairActive;
        this.ramReserved = ramReserved;
        this.gateName = bounded(
                gateName,
                TileEntitySiegeGate.MAX_GATE_NAME_LENGTH
        );
        this.factionName = bounded(factionName, MAX_FACTION_NAME_LENGTH);
        this.requiredAlignment = Math.max(0, requiredAlignment);
        this.orientation = orientation;
        this.openingDirection = openingDirection;
        this.leftHinge = copyHinge(leftHinge);
        this.rightHinge = copyHinge(rightHinge);
        this.parts = Collections.unmodifiableList(copiedParts);

        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (PartEntry part : copiedParts) {
            minimumX = Math.min(minimumX, part.relativeX);
            maximumX = Math.max(maximumX, part.relativeX);
            minimumY = Math.min(minimumY, part.relativeY);
            maximumY = Math.max(maximumY, part.relativeY);
            minimumZ = Math.min(minimumZ, part.relativeZ);
            maximumZ = Math.max(maximumZ, part.relativeZ);
        }
        minRelativeX = minimumX;
        maxRelativeX = maximumX;
        minRelativeY = minimumY;
        maxRelativeY = maximumY;
        minRelativeZ = minimumZ;
        maxRelativeZ = maximumZ;
    }

    public static FinalizedGateSnapshot fromController(
            TileEntitySiegeGate controller,
            UUID gateUuid
    ) {
        if (controller == null || controller.getWorldObj() == null
                || controller.getWorldObj().isRemote || gateUuid == null) {
            throw new IllegalArgumentException(
                    "Finalized controller is unavailable."
            );
        }
        List<PartEntry> entries = new ArrayList<PartEntry>();
        for (GatePartData part : controller.getGateParts()) {
            entries.add(new PartEntry(
                    part.getRelativeX(),
                    part.getRelativeY(),
                    part.getRelativeZ(),
                    part.getLeaf()
            ));
        }
        return new FinalizedGateSnapshot(
                gateUuid,
                controller.getWorldObj().provider.dimensionId,
                controller.xCoord,
                controller.yCoord,
                controller.zCoord,
                controller.getStructureRevision(),
                controller.getGateState(),
                controller.getCurrentHealth(),
                controller.getMaxHealth(),
                controller.isRepairActive(),
                controller.getReservedRamUuid() != null,
                controller.getGateName(),
                controller.getGateFaction() == null
                        ? ""
                        : controller.getGateFaction().codeName(),
                controller.getRequiredAlignment(),
                controller.getGateOrientation(),
                controller.getOpeningDirection(),
                controller.getLeftHinge(),
                controller.getRightHinge(),
                entries
        );
    }

    public UUID getGateUuid() {
        return gateUuid;
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

    public int getBaseStructureRevision() {
        return baseStructureRevision;
    }

    public GateState getGateState() {
        return gateState;
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public boolean isRepairActive() {
        return repairActive;
    }

    public boolean isRamReserved() {
        return ramReserved;
    }

    public String getGateName() {
        return gateName;
    }

    public String getFactionName() {
        return factionName;
    }

    public int getRequiredAlignment() {
        return requiredAlignment;
    }

    public GateOrientation getOrientation() {
        return orientation;
    }

    public GateOpeningDirection getOpeningDirection() {
        return openingDirection;
    }

    public GateHinge getLeftHinge() {
        return copyHinge(leftHinge);
    }

    public GateHinge getRightHinge() {
        return copyHinge(rightHinge);
    }

    public List<PartEntry> getParts() {
        return parts;
    }

    public int getMinRelativeX() {
        return minRelativeX;
    }

    public int getMaxRelativeX() {
        return maxRelativeX;
    }

    public int getMinRelativeY() {
        return minRelativeY;
    }

    public int getMaxRelativeY() {
        return maxRelativeY;
    }

    public int getMinRelativeZ() {
        return minRelativeZ;
    }

    public int getMaxRelativeZ() {
        return maxRelativeZ;
    }

    public int getWidth() {
        return maxRelativeX - minRelativeX + 1;
    }

    public int getHeight() {
        return maxRelativeY - minRelativeY + 1;
    }

    public int getThickness() {
        return maxRelativeZ - minRelativeZ + 1;
    }

    private static GateHinge copyHinge(GateHinge hinge) {
        return hinge == null
                ? null
                : new GateHinge(
                        hinge.getRelativeX(),
                        hinge.getRelativeZ(),
                        hinge.getSide()
                );
    }

    private static String bounded(String value, int maximumLength) {
        String safeValue = value == null ? "" : value;
        return safeValue.length() <= maximumLength
                ? safeValue
                : safeValue.substring(0, maximumLength);
    }

    public static final class PartEntry {

        private final int relativeX;
        private final int relativeY;
        private final int relativeZ;
        private final GateLeaf leaf;

        public PartEntry(
                int relativeX,
                int relativeY,
                int relativeZ,
                GateLeaf leaf
        ) {
            if (leaf == null) {
                throw new IllegalArgumentException("Gate part role is required.");
            }
            this.relativeX = relativeX;
            this.relativeY = relativeY;
            this.relativeZ = relativeZ;
            this.leaf = leaf;
        }

        public int getRelativeX() {
            return relativeX;
        }

        public int getRelativeY() {
            return relativeY;
        }

        public int getRelativeZ() {
            return relativeZ;
        }

        public GateLeaf getLeaf() {
            return leaf;
        }
    }
}
