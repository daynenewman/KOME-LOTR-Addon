package kome.common.tactical.edit;

import java.util.Locale;
import java.util.Objects;

/** Server-checked ownership context, independent of UI selection and physical gates. */
public final class KOMETacticalEditScope {
    public enum Type { TILE_FORCE_DEPLOYMENT_AREA, SIEGE_COMPLEX }
    public static final int MAX_ID_LENGTH = 128;
    private final Type type;
    private final String tileId, complexId, targetId;
    private final int dimensionId;

    public KOMETacticalEditScope(Type type, String tileId, String complexId, String targetId, int dimensionId) {
        if (type == null) throw new IllegalArgumentException("Editor scope is required.");
        this.type = type;
        this.tileId = canonicalId(tileId);
        if (!this.tileId.matches("[A-Z]+[0-9]+")) throw new IllegalArgumentException("Invalid conquest tile ID.");
        this.targetId = canonicalId(targetId);
        this.complexId = complexId == null ? null : canonicalId(complexId);
        if (type == Type.SIEGE_COMPLEX ? !this.targetId.equals(this.complexId) : this.complexId != null) {
            throw new IllegalArgumentException("Scope ownership does not match its target.");
        }
        this.dimensionId = dimensionId;
    }

    public static String canonicalId(String value) {
        validateId(value);
        String result = value.trim().toUpperCase(Locale.ROOT);
        validateId(result);
        return result;
    }

    /** Local authored IDs are checked without changing their case. */
    public static void validateId(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Missing or oversized ID.");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) throw new IllegalArgumentException("Control character in ID.");
        }
    }

    public Type getType() { return type; }
    public String getTileId() { return tileId; }
    public String getComplexId() { return complexId; }
    public String getTargetId() { return targetId; }
    public int getDimensionId() { return dimensionId; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof KOMETacticalEditScope)) return false;
        KOMETacticalEditScope s = (KOMETacticalEditScope) other;
        return type == s.type && dimensionId == s.dimensionId && tileId.equals(s.tileId)
            && Objects.equals(complexId, s.complexId) && targetId.equals(s.targetId);
    }
    @Override public int hashCode() { return Objects.hash(type, tileId, complexId, targetId, dimensionId); }
}
