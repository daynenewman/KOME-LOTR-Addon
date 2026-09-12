package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;

/** Token-bound Phase-2 draft intents; none represent a durable commit. */
public enum GateEditDraftAction {
    SELECT_LEFT(GateLeaf.LEFT), SELECT_RIGHT(GateLeaf.RIGHT), SELECT_SPLIT_CENTER(GateLeaf.SPLIT_CENTER),
    SET_LEFT_HINGE(null), SET_RIGHT_HINGE(null),
    SET_DIRECTION_FORWARD(null), SET_DIRECTION_BACKWARD(null),
    SET_BORDER_TEXTURE_ENABLED(null), SET_BORDER_TEXTURE_DISABLED(null);
    private final GateLeaf leaf;
    GateEditDraftAction(GateLeaf leaf){this.leaf=leaf;}
    public GateLeaf getLeaf(){return leaf;}
    public boolean isSelect(){return this==SELECT_LEFT||this==SELECT_RIGHT||this==SELECT_SPLIT_CENTER;}
    public boolean isDirection(){return this==SET_DIRECTION_FORWARD||this==SET_DIRECTION_BACKWARD;}
    public GateOpeningDirection getDirection(){return this==SET_DIRECTION_FORWARD?GateOpeningDirection.FORWARD:this==SET_DIRECTION_BACKWARD?GateOpeningDirection.BACKWARD:null;}
    public boolean isBorderTexture(){return this==SET_BORDER_TEXTURE_ENABLED||this==SET_BORDER_TEXTURE_DISABLED;}
    public boolean getBorderTextureEnabled(){return this==SET_BORDER_TEXTURE_ENABLED;}
}
