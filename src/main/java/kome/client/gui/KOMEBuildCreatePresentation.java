package kome.client.gui;

import java.util.List;

final class KOMEBuildCreatePresentation {
    private KOMEBuildCreatePresentation() {
    }

    static boolean canCreateBuild(List owners) {
        return owners != null && !owners.isEmpty();
    }
}
