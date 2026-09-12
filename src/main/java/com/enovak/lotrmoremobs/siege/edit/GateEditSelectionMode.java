package com.enovak.lotrmoremobs.siege.edit;

/** Client world-selection UX only; server validates the corresponding action. */
public enum GateEditSelectionMode {
    NONE(null), SELECT_LEFT(GateEditDraftAction.SELECT_LEFT), SELECT_RIGHT(GateEditDraftAction.SELECT_RIGHT),
    SELECT_SPLIT_CENTER(GateEditDraftAction.SELECT_SPLIT_CENTER),
    SET_LEFT_HINGE(GateEditDraftAction.SET_LEFT_HINGE), SET_RIGHT_HINGE(GateEditDraftAction.SET_RIGHT_HINGE);
    private final GateEditDraftAction action;
    GateEditSelectionMode(GateEditDraftAction action){this.action=action;}
    public GateEditDraftAction getAction(){return action;}
}
