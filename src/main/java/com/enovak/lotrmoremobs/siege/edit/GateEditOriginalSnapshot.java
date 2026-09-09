package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.*;

/** Detached immutable revision-N original, including server-only source provenance. */
public final class GateEditOriginalSnapshot {
    private final UUID gateUuid; private final int dimensionId, controllerX, controllerY, controllerZ, baseRevision;
    private final GateOrientation orientation; private final GateOpeningDirection openingDirection;
    private final boolean borderTextureEnabled;
    private final GateHinge leftHinge, rightHinge; private final List<GateEditOriginalPart> parts; private final Map<GateEditCoordinate,GateEditOriginalPart> partsByCoordinate;

    private GateEditOriginalSnapshot(TileEntitySiegeGate gate) {
        gateUuid = gate.getExistingGateUuid(); dimensionId = gate.getWorldObj().provider.dimensionId;
        controllerX = gate.xCoord; controllerY = gate.yCoord; controllerZ = gate.zCoord;
        baseRevision = gate.getStructureRevision(); orientation = gate.getGateOrientation();
        openingDirection = gate.getOpeningDirection(); borderTextureEnabled = gate.isGateBorderTextureEnabled(); leftHinge = copy(gate.getLeftHinge()); rightHinge = copy(gate.getRightHinge());
        List<GateEditOriginalPart> copied = new ArrayList<GateEditOriginalPart>();
        Set<GateEditCoordinate> positions = new HashSet<GateEditCoordinate>();
        for (com.enovak.lotrmoremobs.siege.gate.GatePartData part : gate.getGateParts()) {
            GateEditCoordinate key = new GateEditCoordinate(part.getRelativeX(), part.getRelativeY(), part.getRelativeZ());
            if (!positions.add(key) || copied.size() >= GateStructureValidator.MAX_GATE_PARTS) throw new IllegalArgumentException("Invalid gate edit source.");
            copied.add(new GateEditOriginalPart(part));
        }
        if (gateUuid == null || baseRevision <= 0 || copied.isEmpty() || orientation == null || openingDirection == null) throw new IllegalArgumentException("Invalid gate edit identity.");
        parts = Collections.unmodifiableList(copied); Map<GateEditCoordinate,GateEditOriginalPart> indexed=new HashMap<GateEditCoordinate,GateEditOriginalPart>(); for(GateEditOriginalPart part:copied)indexed.put(new GateEditCoordinate(part.getRelativeX(),part.getRelativeY(),part.getRelativeZ()),part); partsByCoordinate=Collections.unmodifiableMap(indexed);
    }
    public static GateEditOriginalSnapshot fromController(TileEntitySiegeGate gate) { return new GateEditOriginalSnapshot(gate); }
    public UUID getGateUuid() { return gateUuid; } public int getDimensionId() { return dimensionId; }
    public int getControllerX() { return controllerX; } public int getControllerY() { return controllerY; } public int getControllerZ() { return controllerZ; }
    public int getBaseRevision() { return baseRevision; } public GateOrientation getOrientation() { return orientation; }
    public GateOpeningDirection getOpeningDirection() { return openingDirection; }
    public boolean isBorderTextureEnabled() { return borderTextureEnabled; }
    public GateHinge getLeftHinge() { return copy(leftHinge); } public GateHinge getRightHinge() { return copy(rightHinge); }
    public List<GateEditOriginalPart> getParts() { return parts; }
    GateEditOriginalPart findPart(GateEditCoordinate coordinate) {
        return coordinate == null ? null : partsByCoordinate.get(coordinate);
    }
    GateEditOriginalPart findPart(int x,int y,int z) { return partsByCoordinate.get(new GateEditCoordinate(x,y,z)); }
    private static GateHinge copy(GateHinge hinge) { return hinge == null ? null : new GateHinge(hinge.getRelativeX(), hinge.getRelativeZ(), hinge.getSide()); }
}
