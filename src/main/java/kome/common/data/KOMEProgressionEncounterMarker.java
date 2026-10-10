package kome.common.data;

import java.util.UUID;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Persistent NPC entity-data marker for temporary progression encounter ownership.
 * It survives ordinary NPC unload/reload and contains no gameplay authority by itself.
 */
public final class KOMEProgressionEncounterMarker {
    private static final String ROOT = "KOMEProgressionEncounter";
    private static final String KIND = "Kind";
    private static final String OWNER = "Owner";
    private static final String TOKEN = "Token";

    public static final String ESCORT = "escort";
    public static final String DEFENSE = "defense";

    private KOMEProgressionEncounterMarker() {
    }
    public static int rulerIncarnation(LOTREntityNPC npc){
        return npc.getEntityData().hasKey("KOMERulerIncarnation")?npc.getEntityData().getInteger("KOMERulerIncarnation"):-1;
    }
    public static String rulerSlot(LOTREntityNPC npc){return npc.getEntityData().getString("KOMERulerSlot");}
    public static void stampRuler(LOTREntityNPC npc,int incarnation){npc.getEntityData().setInteger("KOMERulerIncarnation",incarnation);npc.getEntityData().setString("KOMERulerSlot",npc.getUniqueID().toString());}

    public static void mark(LOTREntityNPC npc, String kind, UUID owner, String token) {
        if (npc == null) {
            return;
        }
        mark(npc.getEntityData(), kind, owner, token);
    }

    public static Marker read(LOTREntityNPC npc) {
        return npc == null ? null : read(npc.getEntityData());
    }

    public static void clear(LOTREntityNPC npc) {
        if (npc != null) {
            clear(npc.getEntityData());
        }
    }

    static void mark(NBTTagCompound entityData, String kind, UUID owner, String token) {
        if (entityData == null || kind == null || owner == null
                || token == null || token.length() == 0) {
            return;
        }

        NBTTagCompound marker = new NBTTagCompound();
        marker.setString(KIND, kind);
        marker.setString(OWNER, owner.toString());
        marker.setString(TOKEN, token);
        entityData.setTag(ROOT, marker);
    }

    static Marker read(NBTTagCompound entityData) {
        if (entityData == null || !entityData.hasKey(ROOT, 10)) {
            return null;
        }

        NBTTagCompound marker = entityData.getCompoundTag(ROOT);
        String kind = marker.getString(KIND);
        String ownerText = marker.getString(OWNER);
        String token = marker.getString(TOKEN);

        if (kind.length() == 0 || token.length() == 0) {
            return null;
        }

        try {
            return new Marker(kind, UUID.fromString(ownerText), token);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static void clear(NBTTagCompound entityData) {
        if (entityData != null) {
            entityData.removeTag(ROOT);
        }
    }

    public static final class Marker {
        public final String kind;
        public final UUID owner;
        public final String token;

        private Marker(String kind, UUID owner, String token) {
            this.kind = kind;
            this.owner = owner;
            this.token = token;
        }
    }
}
