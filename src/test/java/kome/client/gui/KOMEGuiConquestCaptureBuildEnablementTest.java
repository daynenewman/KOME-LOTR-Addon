package kome.client.gui;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KOMEGuiConquestCaptureBuildEnablementTest {
    @Test public void nonEmptySelectableOwnersEnableCreateBuild() {
        assertTrue(KOMEBuildCreatePresentation.canCreateBuild(Collections.singletonList("gondor")));
    }

    @Test public void emptySelectableOwnersDisableCreateBuild() {
        assertFalse(KOMEBuildCreatePresentation.canCreateBuild(Collections.emptyList()));
    }
}
