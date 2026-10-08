package kome.common.data;

/** Minimal server-authored presentation record for the native LOTR visual layer. */
public final class KOMEVisualMarker {
    public enum Role {
        SERFDOM_MASTER("serfdom_master", "Master"),
        MASTER_GIFT("master_gift", "Parting Gift"),
        KNIGHT_LIEGE("knight_liege", "Liege"),
        LORD_LIEGE("lord_liege", "Liege"),
        RULER("ruler", "Ruler"),
        COURIER("courier", "Courier Destination"),
        ENCOUNTER_ENEMY("encounter_enemy", "Objective enemy"),
        PARTICIPANT("participant", "Progression participant"),
        COMMISSION("commission", "Commission"),
        ESCORT("escort", "Escort Destination"),
        DEFENSE("defense", "Defend your people"),
        RECOVERY_SEARCH("recovery_search", "Recovery Search");

        public final String key, label;
        Role(String key, String label) { this.key = key; this.label = label; }
        public static Role forKey(String key) {
            if (key != null) for (Role role : values()) if (role.key.equals(key)) return role;
            return null;
        }
    }

    public final Role role;
    public final String entityUuid, title, subtitle;
    public final int dimension;
    public final boolean actionable;
    public final double x, y, z;

    public KOMEVisualMarker(Role role, String entityUuid, String title, String subtitle,
            int dimension, double x, double y, double z) {
        this(role,entityUuid,title,subtitle,dimension,x,y,z,true);
    }
    public KOMEVisualMarker(Role role,String entityUuid,String title,String subtitle,int dimension,
            double x,double y,double z,boolean actionable) {
        if (role == null) throw new IllegalArgumentException("Marker role is required.");
        this.role = role;
        this.entityUuid = entityUuid == null ? "" : entityUuid;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.actionable=actionable;
    }

    public boolean isRelationship() {
        return role == Role.MASTER_GIFT || role == Role.SERFDOM_MASTER || role == Role.KNIGHT_LIEGE
            || role == Role.LORD_LIEGE || role == Role.RULER;
    }
    public String signature() {
        return role.key + '|' + entityUuid + '|' + title + '|' + subtitle + '|'
            + dimension + '|' + x + '|' + y + '|' + z+'|'+actionable;
    }
}
