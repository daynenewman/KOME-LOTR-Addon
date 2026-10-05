package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.nbt.NBTTagCompound;

/** Narrow entity-NBT delivery receipt for autonomous Emergency Defense defenders. */
public final class KOMEEmergencyDefenseEntityMarker {
    private static final String INTENT = "KOMEEmergencyDefenseIntent";
    private static final String CONFLICT = "KOMEEmergencyDefenseConflict";
    private static final String HOME_X = "KOMEEmergencyDefenseHomeX";
    private static final String HOME_Y = "KOMEEmergencyDefenseHomeY";
    private static final String HOME_Z = "KOMEEmergencyDefenseHomeZ";
    private static final String HOME_RADIUS = "KOMEEmergencyDefenseHomeRadius";

    private KOMEEmergencyDefenseEntityMarker() { }

    public static void mark(LOTREntityNPC npc, String intentId, String conflictId,
            int homeX, int homeY, int homeZ, int homeRadius) {
        if (npc == null || intentId == null || intentId.length() == 0
                || conflictId == null || conflictId.length() == 0 || homeRadius <= 0)
            throw new IllegalArgumentException("Complete Emergency Defense marker authority is required.");
        NBTTagCompound data = npc.getEntityData();
        data.setString(INTENT, intentId);
        data.setString(CONFLICT, conflictId);
        data.setInteger(HOME_X, homeX);
        data.setInteger(HOME_Y, homeY);
        data.setInteger(HOME_Z, homeZ);
        data.setInteger(HOME_RADIUS, homeRadius);
    }

    public static Marker read(LOTREntityNPC npc) {
        if (npc == null) return null;
        NBTTagCompound data = npc.getEntityData();
        String intent = data.getString(INTENT);
        String conflict = data.getString(CONFLICT);
        if (intent.length() == 0 || conflict.length() == 0) return null;
        boolean hasHome = data.hasKey(HOME_X, 3) && data.hasKey(HOME_Y, 3)
            && data.hasKey(HOME_Z, 3) && data.hasKey(HOME_RADIUS, 3)
            && data.getInteger(HOME_RADIUS) > 0;
        return new Marker(intent, conflict, hasHome,
            hasHome ? data.getInteger(HOME_X) : 0,
            hasHome ? data.getInteger(HOME_Y) : 0,
            hasHome ? data.getInteger(HOME_Z) : 0,
            hasHome ? data.getInteger(HOME_RADIUS) : 0);
    }

    public static final class Marker {
        public final String intentId;
        public final String conflictId;
        public final boolean hasHome;
        public final int homeX;
        public final int homeY;
        public final int homeZ;
        public final int homeRadius;

        private Marker(String intentId, String conflictId, boolean hasHome,
                int homeX, int homeY, int homeZ, int homeRadius) {
            this.intentId = intentId;
            this.conflictId = conflictId;
            this.hasHome = hasHome;
            this.homeX = homeX;
            this.homeY = homeY;
            this.homeZ = homeZ;
            this.homeRadius = homeRadius;
        }
    }
}
