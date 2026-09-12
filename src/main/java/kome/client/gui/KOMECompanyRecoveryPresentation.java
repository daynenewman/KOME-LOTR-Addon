package kome.client.gui;

import kome.common.network.KOMECompanyGuiEntry;

final class KOMECompanyRecoveryPresentation {
    private KOMECompanyRecoveryPresentation() {
    }

    static String haltedRecoverySummary(KOMECompanyGuiEntry company) {
        if (company == null || company.accessLossReason.length() == 0) {
            return "";
        }
        String summary = "Access: " + company.accessLossReason + " | Current " + company.currentTile
            + " | Next " + company.nextTile;
        if (company.intendedDestinationTile.length() > 0) {
            summary += " | Destination " + company.intendedDestinationTile;
        }
        return summary;
    }
}
