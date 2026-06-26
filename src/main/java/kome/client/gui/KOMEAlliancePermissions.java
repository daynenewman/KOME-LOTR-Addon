package kome.client.gui;

final class KOMEAlliancePermissions {
    static final String[] TYPES = new String[] {"Civil", "Military", "Trade"};
    static final String[][] UNLOCKS = new String[][] {
        {"Alliance begins", "Use faction waypoints", "Hire farmhands"},
        {"Alliance begins", "Hire 1 unit", "Attack through faction", "Command armies", "Spawn captain"},
        {"Alliance begins", "Build in faction land", "Produce merchant crop"}
    };

    private KOMEAlliancePermissions() {
    }
}
