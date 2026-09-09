package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;

/** Bounded, wire-safe Phase-3 issue codes. Structural values mirror the canonical validator. */
public enum GateEditPreflightIssueCode {
    NONE(Category.STRUCTURAL),
    EMPTY_STRUCTURE(Category.STRUCTURAL), TOO_MANY_PARTS(Category.STRUCTURAL),
    NULL_PART(Category.STRUCTURAL), INVALID_LEAF(Category.STRUCTURAL),
    INVALID_COORDINATE(Category.STRUCTURAL), DUPLICATE_COORDINATE(Category.STRUCTURAL),
    EMPTY_LEAF(Category.STRUCTURAL), ENVELOPE_EXCEEDED(Category.STRUCTURAL),
    LEFT_LEAF_DISCONNECTED(Category.STRUCTURAL), RIGHT_LEAF_DISCONNECTED(Category.STRUCTURAL),
    CONTROLLER_NOT_ADJACENT(Category.STRUCTURAL), INVALID_HINGE(Category.STRUCTURAL),
    INVALID_ORIENTATION(Category.STRUCTURAL), INVALID_OPENING_DIRECTION(Category.STRUCTURAL),
    INVALID_SPLIT_CENTER(Category.STRUCTURAL),
    CONTROLLER_MISSING(Category.IDENTITY), CONTROLLER_MISMATCH(Category.IDENTITY),
    UUID_MISMATCH(Category.IDENTITY), STALE_REVISION(Category.IDENTITY), NO_PERMISSION(Category.IDENTITY),
    SOURCE_CHANGED(Category.WORLD_CONFLICT), REMOVE_TARGET_CHANGED(Category.WORLD_CONFLICT),
    FOREIGN_OWNER(Category.WORLD_CONFLICT), OWNERSHIP_MISMATCH(Category.WORLD_CONFLICT),
    ORIGINAL_MISMATCH(Category.WORLD_CONFLICT),
    GATE_NOT_CLOSED(Category.TRANSIENT_GAMEPLAY), REPAIR_ACTIVE(Category.TRANSIENT_GAMEPLAY),
    RAM_RESERVED(Category.TRANSIENT_GAMEPLAY), CHUNK_UNLOADED(Category.CHUNK_AVAILABILITY),
    NO_CHANGES(Category.NO_CHANGE), MUTATION_IN_PROGRESS(Category.MUTATION_QUARANTINE),
    QUARANTINED(Category.MUTATION_QUARANTINE),
    BANNER_ATTACHMENT_CONFLICT(Category.WORLD_CONFLICT);

    public enum Category { STRUCTURAL, IDENTITY, WORLD_CONFLICT, TRANSIENT_GAMEPLAY, CHUNK_AVAILABILITY, NO_CHANGE, MUTATION_QUARANTINE }
    private final Category category;
    GateEditPreflightIssueCode(Category category) { this.category = category; }
    public Category getCategory() { return category; }
    public static GateEditPreflightIssueCode fromFailure(GateStructureValidator.Failure failure) {
        if (failure == null) return NONE;
        try { return valueOf(failure.name()); } catch (IllegalArgumentException ignored) { return NONE; }
    }
}
