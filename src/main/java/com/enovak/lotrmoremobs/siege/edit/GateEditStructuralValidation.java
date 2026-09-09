package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;

/** Immutable detached structural result; the canonical validator supplies its single failure. */
public final class GateEditStructuralValidation {
    private final boolean valid;
    private final GateEditPreflightIssueCode primaryIssue;
    private final GateStructureValidator.Failure canonicalFailure;
    private final int partCount, leftCount, rightCount, splitCenterCount, width, height, thickness;
    GateEditStructuralValidation(boolean valid, GateStructureValidator.Failure failure, int partCount, int leftCount, int rightCount, int splitCenterCount, int width, int height, int thickness) {
        this.valid = valid; canonicalFailure = failure == null ? GateStructureValidator.Failure.NONE : failure;
        primaryIssue = GateEditPreflightIssueCode.fromFailure(canonicalFailure);
        this.partCount=partCount; this.leftCount=leftCount; this.rightCount=rightCount; this.splitCenterCount=splitCenterCount;
        this.width=width; this.height=height; this.thickness=thickness;
    }
    public boolean isStructurallyValid(){return valid;} public GateEditPreflightIssueCode getPrimaryIssue(){return primaryIssue;}
    public GateStructureValidator.Failure getCanonicalFailure(){return canonicalFailure;}
    public int getPartCount(){return partCount;} public int getLeftCount(){return leftCount;} public int getRightCount(){return rightCount;}
    public int getSplitCenterCount(){return splitCenterCount;} public int getWidth(){return width;} public int getHeight(){return height;} public int getThickness(){return thickness;}
}
