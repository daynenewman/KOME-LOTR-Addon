package kome.common.data;

/** Minimal server-authored presentation record for the native LOTR visual layer. */
public final class KOMEVisualMarker {
    public enum Role {
        SERFDOM_MASTER("serfdom_master", "Serfdom Master"),
        KNIGHT_LIEGE("knight_liege", "Liege"),
        LORD_LIEGE("lord_liege", "Liege"),
        COURIER("courier", "Courier Destination");

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
    public final double x, y, z;

    public KOMEVisualMarker(Role role, String entityUuid, String title, String subtitle,
            int dimension, double x, double y, double z) {
        if (role == null) throw new IllegalArgumentException("Marker role is required.");
        this.role = role;
        this.entityUuid = entityUuid == null ? "" : entityUuid;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isRelationship() { return role != Role.COURIER; }
    public String signature() {
        return role.key + '|' + entityUuid + '|' + title + '|' + subtitle + '|'
            + dimension + '|' + x + '|' + y + '|' + z;
    }
}
