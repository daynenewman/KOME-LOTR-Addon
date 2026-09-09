package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.siege.gate.GateLeaf;

/** Server-owned membership entry; added-source provenance never leaves server. */
public final class GateEditDraftPart {
    private final int relativeX, relativeY, relativeZ;
    private GateLeaf leaf;
    private final boolean originatesFromOriginal;
    private final GateEditAddedSource addedSource;
    GateEditDraftPart(GateEditOriginalPart original) {
        relativeX = original.getRelativeX(); relativeY = original.getRelativeY(); relativeZ = original.getRelativeZ();
        leaf = original.getLeaf(); originatesFromOriginal = true; addedSource = null;
    }
    GateEditDraftPart(int x, int y, int z, GateLeaf leaf, GateEditAddedSource source) {
        if (leaf == null || source == null) throw new IllegalArgumentException("Added draft part requires source and role.");
        relativeX=x; relativeY=y; relativeZ=z; this.leaf=leaf; originatesFromOriginal=false; addedSource=source;
    }
    public int getRelativeX() { return relativeX; } public int getRelativeY() { return relativeY; } public int getRelativeZ() { return relativeZ; }
    public GateLeaf getLeaf() { return leaf; } public boolean originatesFromOriginal() { return originatesFromOriginal; }
    GateEditAddedSource getAddedSource() { return addedSource; }
    void setLeaf(GateLeaf value) { if(value==null)throw new IllegalArgumentException("Gate leaf cannot be null."); leaf=value; }
}
