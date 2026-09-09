package com.enovak.lotrmoremobs.siege.edit;

/** Current, informational admission state for a transient edit draft. */
public enum GateEditPreflightState {
    INVALID_DRAFT,
    NO_CHANGES,
    STALE_SESSION,
    NOT_READY,
    READY
}
