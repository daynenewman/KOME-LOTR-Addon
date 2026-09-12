package com.lotrcharactercreation.client.appearance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import com.lotrcharactercreation.appearance.CustomSkinHashing;

public class ClientExternalTextureStateTest {

    @Test
    public void samePresetAndHashRetainsTheLoadedIdentity() {
        ClientExternalTextureState state = new ClientExternalTextureState();
        ClientCustomSkinIdentity identity = identity("custom_man_male_gondor_same", 1);

        assertNull(state.markLoaded(identity));

        assertEquals(identity, state.getLoadedIdentity(identity.getPresetId()));
        assertTrue(state.canAttempt(identity));
    }

    @Test
    public void changedHashReplacesTheOldIdentityForTheSamePreset() {
        ClientExternalTextureState state = new ClientExternalTextureState();
        ClientCustomSkinIdentity oldIdentity = identity("custom_man_male_gondor_changed", 1);
        ClientCustomSkinIdentity newIdentity = identity("custom_man_male_gondor_changed", 2);
        state.markLoaded(oldIdentity);

        assertSame(oldIdentity, state.markLoaded(newIdentity));

        assertEquals(newIdentity, state.getLoadedIdentity(newIdentity.getPresetId()));
        assertFalse(oldIdentity.equals(newIdentity));
    }

    @Test
    public void failureIsScopedToExactContentIdentityAndCanBeRetried() {
        ClientExternalTextureState state = new ClientExternalTextureState();
        ClientCustomSkinIdentity failed = identity("custom_man_male_gondor_retry", 1);
        ClientCustomSkinIdentity changed = identity("custom_man_male_gondor_retry", 2);

        assertTrue(state.markFailed(failed));
        assertFalse(state.markFailed(failed));
        assertFalse(state.canAttempt(failed));
        assertTrue(state.canAttempt(changed));

        state.contentAvailable(failed);
        assertTrue(state.canAttempt(failed));
    }

    @Test
    public void retainingNewServerIdentityDropsLoadedAndFailedOldContent() {
        ClientExternalTextureState state = new ClientExternalTextureState();
        ClientCustomSkinIdentity oldIdentity = identity("custom_man_male_gondor_server", 1);
        ClientCustomSkinIdentity newIdentity = identity("custom_man_male_gondor_server", 2);
        state.markLoaded(oldIdentity);
        state.markFailed(oldIdentity);

        assertEquals(
            Collections.singletonList(oldIdentity),
            state.retainOnly(Collections.singletonMap(newIdentity.getPresetId(), newIdentity)));

        assertNull(state.getLoadedIdentity(oldIdentity.getPresetId()));
        assertTrue(state.canAttempt(oldIdentity));
        assertTrue(state.canAttempt(newIdentity));
    }

    @Test
    public void disconnectClearRemovesLoadedAndFailureState() {
        ClientExternalTextureState state = new ClientExternalTextureState();
        ClientCustomSkinIdentity loaded = identity("custom_man_male_gondor_loaded", 1);
        ClientCustomSkinIdentity failed = identity("custom_man_male_gondor_failed", 2);
        state.markLoaded(loaded);
        state.markFailed(failed);

        assertEquals(Collections.singletonList(loaded), state.clear());

        assertNull(state.getLoadedIdentity(loaded.getPresetId()));
        assertTrue(state.canAttempt(failed));
    }

    @Test
    public void invalidatingOnePresetDoesNotDisturbAnother() {
        ClientExternalTextureState state = new ClientExternalTextureState();
        ClientCustomSkinIdentity removed = identity("custom_man_male_gondor_removed", 1);
        ClientCustomSkinIdentity retained = identity("custom_man_male_gondor_retained", 2);
        state.markLoaded(removed);
        state.markLoaded(retained);

        assertSame(removed, state.invalidatePreset(removed.getPresetId()));

        assertNull(state.getLoadedIdentity(removed.getPresetId()));
        assertSame(retained, state.getLoadedIdentity(retained.getPresetId()));
    }

    private static ClientCustomSkinIdentity identity(String presetId, int value) {
        return new ClientCustomSkinIdentity(presetId, CustomSkinHashing.sha256Hex(new byte[] { (byte) value }));
    }
}
