package com.enovak.lotrmoremobs.siege.client.gui;

/** Disjoint identifiers for the paged KOME Link and Relink options. */
public final class KOMEGateActionButtonIds {
    public static final int LINK_BASE = 1000;
    /** Above the complete unsigned-short option-list range used on the wire. */
    public static final int RELINK_BASE = 100000;

    private KOMEGateActionButtonIds() {
    }

    public static boolean isLink(int id) {
        return id >= LINK_BASE && id < RELINK_BASE;
    }

    public static boolean isRelink(int id) {
        return id >= RELINK_BASE;
    }
}
