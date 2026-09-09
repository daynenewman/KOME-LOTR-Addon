package com.enovak.lotrmoremobs.siege.edit;

import java.util.UUID;

/** One transient, player-bound EDIT_EXISTING session. */
public final class GateEditSession {
    private final UUID playerUuid, sessionToken, gateUuid; private final int dimensionId, controllerX, controllerY, controllerZ, baseRevision;
    private final GateEditOriginalSnapshot original; private final GateEditDraft draft; private long expiresAtTick; private long draftSequence, preflightGeneration;
    GateEditSession(UUID playerUuid, UUID token, GateEditOriginalSnapshot original, long expiresAtTick) {
        if (playerUuid == null || token == null || original == null) throw new IllegalArgumentException("Edit identity is required.");
        this.playerUuid=playerUuid; sessionToken=token; gateUuid=original.getGateUuid(); dimensionId=original.getDimensionId(); controllerX=original.getControllerX(); controllerY=original.getControllerY(); controllerZ=original.getControllerZ(); baseRevision=original.getBaseRevision(); this.original=original; draft=new GateEditDraft(original); this.expiresAtTick=expiresAtTick;
    }
    public UUID getPlayerUuid(){return playerUuid;} public UUID getSessionToken(){return sessionToken;} public UUID getGateUuid(){return gateUuid;}
    public int getDimensionId(){return dimensionId;} public int getControllerX(){return controllerX;} public int getControllerY(){return controllerY;} public int getControllerZ(){return controllerZ;} public int getBaseRevision(){return baseRevision;}
    public GateEditOriginalSnapshot getOriginal(){return original;} public GateEditDraft getDraft(){return draft;} boolean isExpired(long tick){return tick>=expiresAtTick;}
    void refreshExpiry(long expiresAtTick){this.expiresAtTick=expiresAtTick;}
    public long getDraftSequence(){return draftSequence;} void incrementDraftSequence(){if(draftSequence<Long.MAX_VALUE)++draftSequence;}
    long nextPreflightGeneration(){if(preflightGeneration<Long.MAX_VALUE)++preflightGeneration;return preflightGeneration;}
}
