package com.lotrcharactercreation.client.trait;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.client.audio.ISound;

/** Tracks only KOME's confirmed-hit sound instances, including overlapping custom recordings. */
final class UrukEmpoweredHitAudio {

    private final List<ISound> active = new ArrayList<>();

    void track(ISound sound) {
        active.add(sound);
    }

    boolean hasActive() {
        return !active.isEmpty();
    }

    void forgetFinished(Predicate<ISound> isPlaying) {
        for (Iterator<ISound> sounds = active.iterator(); sounds.hasNext();) {
            if (!isPlaying.test(sounds.next())) {
                sounds.remove();
            }
        }
    }

    void stopAll(Consumer<ISound> stop) {
        try {
            for (ISound sound : active) {
                try {
                    stop.accept(sound);
                } catch (RuntimeException ignored) {
                    // Continue stopping the remaining confirmed-hit sounds during teardown.
                }
            }
        } finally {
            active.clear();
        }
    }
}
