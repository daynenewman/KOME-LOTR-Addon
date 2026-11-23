package com.lotrcharactercreation.client.trait;

/** Connection-scoped deduplication and flash lifetime for confirmed heavy blows. */
public final class UrukHeavyBlowFeedback {

    private long lastActivationId;
    private int flashTicksRemaining;

    public boolean accept(long activationId, boolean active, boolean alive) {
        if (!active || !alive || activationId <= 0L || activationId <= lastActivationId) {
            return false;
        }
        lastActivationId = activationId;
        flashTicksRemaining = UrukRageHudModel.FLASH_TICKS;
        return true;
    }

    public void tick(boolean alive) {
        if (!alive) {
            clearTransient();
        } else if (flashTicksRemaining > 0) {
            --flashTicksRemaining;
        }
    }

    public void clearTransient() {
        flashTicksRemaining = 0;
    }

    public void resetConnection() {
        lastActivationId = 0L;
        flashTicksRemaining = 0;
    }

    public int getFlashTicksRemaining() {
        return flashTicksRemaining;
    }

    long getLastActivationId() {
        return lastActivationId;
    }
}
