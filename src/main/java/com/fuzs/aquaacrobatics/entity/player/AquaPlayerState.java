package com.fuzs.aquaacrobatics.entity.player;

import com.fuzs.aquaacrobatics.entity.EntitySize;

/**
 * Per-player transient Aqua state. The pose and forced-crawl flags remain in
 * their existing DataWatchers; this object owns only local derived state that
 * future Mixin or ASM adapters can expose through IPlayerResizeable.
 */
public class AquaPlayerState {

    public EntitySize size;
    public boolean eyesInWater;
    public boolean eyesInWaterPlayer;
    public float playerEyeHeight;
    public float previousEyeHeight;
    public float swimAnimation;
    public float lastSwimAnimation;
    public float timeUnderwater;
}
