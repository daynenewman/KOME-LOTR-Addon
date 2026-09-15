package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Locale;
import java.util.UUID;

/**
 * Persistent accounting link between one DEFENSIVE Build and one physical siege gate.
 * Live gate health, damage, repair, and animation state remain owned by the gate subsystem.
 */
public final class KOMEDefensiveGateRecord {
    public enum DimensionDetectionStatus {
        UNAVAILABLE,
        RELIABLE,
        AMBIGUOUS,
        IRREGULAR,
        INVALID,
        STALE;

        static DimensionDetectionStatus forKey(String value) {
            String key = clean(value).toUpperCase(Locale.ROOT);
            if (key.length() == 0) return UNAVAILABLE;
            try {
                return valueOf(key);
            } catch (IllegalArgumentException ignored) {
                return INVALID;
            }
        }
    }

    public enum EffectiveDimensionProvenance {
        UNAVAILABLE,
        AUTOMATIC,
        ADMIN_CONFIRMED
    }

    String id = "";
    long createdAtMillis;
    long updatedAtMillis;

    /** Nullable identity components preserve the difference between coordinate/dimension zero and missing data. */
    Integer gateDimension;
    UUID gateUuid;
    Integer controllerX;
    Integer controllerY;
    Integer controllerZ;
    int capturedStructureRevision;

    /** Captured inspection metadata. Orientation is adapter-owned and stored as a stable key. */
    String detectedOrientation = "";
    int detectedWidth;
    int detectedHeight;
    int detectedProjectedArea;
    DimensionDetectionStatus dimensionDetectionStatus = DimensionDetectionStatus.UNAVAILABLE;

    /** Optional effective dimensions confirmed by an administrator for one structure revision. */
    Integer adminConfirmedWidth;
    Integer adminConfirmedHeight;
    int adminConfirmationStructureRevision;
    UUID dimensionsConfirmedByUuid;
    String dimensionsConfirmedByName = "";
    long dimensionsConfirmedAtMillis;
    String dimensionConfirmationReason = "";

    /** Optional logical Max-HP override. This is not the physical gate's Creative editor value. */
    Integer adminMaxHpOverride;
    UUID maxHpOverrideByUuid;
    String maxHpOverrideByName = "";
    long maxHpOverrideAtMillis;
    String maxHpOverrideReason = "";

    public boolean hasPhysicalBinding() {
        return gateUuid != null && gateDimension != null
            && controllerX != null && controllerY != null && controllerZ != null
            && capturedStructureRevision > 0;
    }

    public String getId() { return id; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public long getUpdatedAtMillis() { return updatedAtMillis; }
    public Integer getGateDimension() { return gateDimension; }
    public UUID getGateUuid() { return gateUuid; }
    public Integer getControllerX() { return controllerX; }
    public Integer getControllerY() { return controllerY; }
    public Integer getControllerZ() { return controllerZ; }
    public int getCapturedStructureRevision() { return capturedStructureRevision; }
    public String getDetectedOrientation() { return detectedOrientation; }
    public int getDetectedWidth() { return detectedWidth; }
    public int getDetectedHeight() { return detectedHeight; }
    public int getDetectedProjectedArea() { return detectedProjectedArea; }
    public DimensionDetectionStatus getDimensionDetectionStatus() { return dimensionDetectionStatus; }
    public Integer getAdminConfirmedWidth() { return adminConfirmedWidth; }
    public Integer getAdminConfirmedHeight() { return adminConfirmedHeight; }
    public int getAdminConfirmationStructureRevision() { return adminConfirmationStructureRevision; }
    public UUID getDimensionsConfirmedByUuid() { return dimensionsConfirmedByUuid; }
    public String getDimensionsConfirmedByName() { return dimensionsConfirmedByName; }
    public long getDimensionsConfirmedAtMillis() { return dimensionsConfirmedAtMillis; }
    public String getDimensionConfirmationReason() { return dimensionConfirmationReason; }
    public Integer getAdminMaxHpOverride() { return adminMaxHpOverride; }
    public UUID getMaxHpOverrideByUuid() { return maxHpOverrideByUuid; }
    public String getMaxHpOverrideByName() { return maxHpOverrideByName; }
    public long getMaxHpOverrideAtMillis() { return maxHpOverrideAtMillis; }
    public String getMaxHpOverrideReason() { return maxHpOverrideReason; }

    public boolean hasReliableDetectedDimensions() {
        return dimensionDetectionStatus == DimensionDetectionStatus.RELIABLE
            && hasRectangularDetectedGeometry();
    }

    public boolean hasCurrentAdminConfirmedDimensions() {
        return adminConfirmedWidth != null && adminConfirmedWidth.intValue() > 0
            && adminConfirmedHeight != null && adminConfirmedHeight.intValue() > 0
            && capturedStructureRevision > 0
            && adminConfirmationStructureRevision == capturedStructureRevision;
    }

    public EffectiveDimensionProvenance effectiveDimensionProvenance() {
        if (hasCurrentAdminConfirmedDimensions()) return EffectiveDimensionProvenance.ADMIN_CONFIRMED;
        if (hasReliableDetectedDimensions()) return EffectiveDimensionProvenance.AUTOMATIC;
        return EffectiveDimensionProvenance.UNAVAILABLE;
    }

    public int effectiveWidth() {
        if (hasCurrentAdminConfirmedDimensions()) return adminConfirmedWidth.intValue();
        return hasReliableDetectedDimensions() ? detectedWidth : 0;
    }

    public int effectiveHeight() {
        if (hasCurrentAdminConfirmedDimensions()) return adminConfirmedHeight.intValue();
        return hasReliableDetectedDimensions() ? detectedHeight : 0;
    }

    public boolean hasAdminMaxHpOverride() {
        return adminMaxHpOverride != null && adminMaxHpOverride.intValue() > 0;
    }

    void setAdminConfirmedDimensions(int width, int height, int structureRevision,
            UUID actorUuid, String actorName, long timestamp, String reason) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Confirmed gate dimensions must be positive.");
        if (structureRevision <= 0) throw new IllegalArgumentException("A positive gate structure revision is required.");
        adminConfirmedWidth = Integer.valueOf(width);
        adminConfirmedHeight = Integer.valueOf(height);
        adminConfirmationStructureRevision = structureRevision;
        dimensionsConfirmedByUuid = actorUuid;
        dimensionsConfirmedByName = clean(actorName);
        dimensionsConfirmedAtMillis = Math.max(0L, timestamp);
        dimensionConfirmationReason = cleanAuditText(reason);
        updatedAtMillis = Math.max(updatedAtMillis, timestamp);
    }

    void clearAdminConfirmedDimensions(long timestamp) {
        adminConfirmedWidth = null;
        adminConfirmedHeight = null;
        adminConfirmationStructureRevision = 0;
        dimensionsConfirmedByUuid = null;
        dimensionsConfirmedByName = "";
        dimensionsConfirmedAtMillis = 0L;
        dimensionConfirmationReason = "";
        updatedAtMillis = Math.max(updatedAtMillis, timestamp);
    }

    void setAdminMaxHpOverride(int maxHp, UUID actorUuid, String actorName,
            long timestamp, String reason) {
        if (maxHp <= 0) throw new IllegalArgumentException("The administrator Max-HP override must be positive.");
        adminMaxHpOverride = Integer.valueOf(maxHp);
        maxHpOverrideByUuid = actorUuid;
        maxHpOverrideByName = clean(actorName);
        maxHpOverrideAtMillis = Math.max(0L, timestamp);
        maxHpOverrideReason = cleanAuditText(reason);
        updatedAtMillis = Math.max(updatedAtMillis, timestamp);
    }

    void clearAdminMaxHpOverride(long timestamp) {
        adminMaxHpOverride = null;
        maxHpOverrideByUuid = null;
        maxHpOverrideByName = "";
        maxHpOverrideAtMillis = 0L;
        maxHpOverrideReason = "";
        updatedAtMillis = Math.max(updatedAtMillis, timestamp);
    }

    public NBTTagCompound writeToNBT() {
        if (clean(id).length() == 0) throw new IllegalStateException("Defensive gate record ID is required.");
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", clean(id));
        nbt.setLong("CreatedAtMillis", Math.max(0L, createdAtMillis));
        nbt.setLong("UpdatedAtMillis", Math.max(0L, updatedAtMillis));

        if (gateDimension != null) nbt.setInteger("GateDimension", gateDimension.intValue());
        if (gateUuid != null) nbt.setString("GateUuid", gateUuid.toString());
        if (controllerX != null) nbt.setInteger("ControllerX", controllerX.intValue());
        if (controllerY != null) nbt.setInteger("ControllerY", controllerY.intValue());
        if (controllerZ != null) nbt.setInteger("ControllerZ", controllerZ.intValue());
        nbt.setInteger("CapturedStructureRevision", Math.max(0, capturedStructureRevision));

        nbt.setString("DetectedOrientation", normalizeOrientation(detectedOrientation));
        nbt.setInteger("DetectedWidth", Math.max(0, detectedWidth));
        nbt.setInteger("DetectedHeight", Math.max(0, detectedHeight));
        nbt.setInteger("DetectedProjectedArea", Math.max(0, detectedProjectedArea));
        nbt.setString("DimensionDetectionStatus", normalizedDetectionStatus().name());

        if (adminConfirmedWidth != null && adminConfirmedWidth.intValue() > 0
                && adminConfirmedHeight != null && adminConfirmedHeight.intValue() > 0) {
            nbt.setInteger("AdminConfirmedWidth", adminConfirmedWidth.intValue());
            nbt.setInteger("AdminConfirmedHeight", adminConfirmedHeight.intValue());
            nbt.setInteger("AdminConfirmationStructureRevision", Math.max(0, adminConfirmationStructureRevision));
            nbt.setString("DimensionsConfirmedByUuid", dimensionsConfirmedByUuid == null ? "" : dimensionsConfirmedByUuid.toString());
            nbt.setString("DimensionsConfirmedByName", clean(dimensionsConfirmedByName));
            nbt.setLong("DimensionsConfirmedAtMillis", Math.max(0L, dimensionsConfirmedAtMillis));
            nbt.setString("DimensionConfirmationReason", cleanAuditText(dimensionConfirmationReason));
        }

        if (hasAdminMaxHpOverride()) {
            nbt.setInteger("AdminMaxHpOverride", adminMaxHpOverride.intValue());
            nbt.setString("MaxHpOverrideByUuid", maxHpOverrideByUuid == null ? "" : maxHpOverrideByUuid.toString());
            nbt.setString("MaxHpOverrideByName", clean(maxHpOverrideByName));
            nbt.setLong("MaxHpOverrideAtMillis", Math.max(0L, maxHpOverrideAtMillis));
            nbt.setString("MaxHpOverrideReason", cleanAuditText(maxHpOverrideReason));
        }
        return nbt;
    }

    void readFromNBT(NBTTagCompound nbt) {
        id = clean(nbt.getString("Id"));
        createdAtMillis = Math.max(0L, nbt.getLong("CreatedAtMillis"));
        updatedAtMillis = Math.max(0L, nbt.getLong("UpdatedAtMillis"));

        gateDimension = nbt.hasKey("GateDimension")
            ? Integer.valueOf(nbt.getInteger("GateDimension")) : null;
        gateUuid = parseUuid(nbt.getString("GateUuid"));
        controllerX = nbt.hasKey("ControllerX") ? Integer.valueOf(nbt.getInteger("ControllerX")) : null;
        controllerY = nbt.hasKey("ControllerY") ? Integer.valueOf(nbt.getInteger("ControllerY")) : null;
        controllerZ = nbt.hasKey("ControllerZ") ? Integer.valueOf(nbt.getInteger("ControllerZ")) : null;
        capturedStructureRevision = Math.max(0, nbt.getInteger("CapturedStructureRevision"));

        detectedOrientation = normalizeOrientation(nbt.getString("DetectedOrientation"));
        detectedWidth = Math.max(0, nbt.getInteger("DetectedWidth"));
        detectedHeight = Math.max(0, nbt.getInteger("DetectedHeight"));
        detectedProjectedArea = Math.max(0, nbt.getInteger("DetectedProjectedArea"));
        dimensionDetectionStatus = DimensionDetectionStatus.forKey(nbt.getString("DimensionDetectionStatus"));
        if (dimensionDetectionStatus == DimensionDetectionStatus.RELIABLE
                && !hasRectangularDetectedGeometry()) {
            dimensionDetectionStatus = DimensionDetectionStatus.INVALID;
        }

        if (nbt.hasKey("AdminConfirmedWidth") && nbt.getInteger("AdminConfirmedWidth") > 0
                && nbt.hasKey("AdminConfirmedHeight") && nbt.getInteger("AdminConfirmedHeight") > 0) {
            adminConfirmedWidth = Integer.valueOf(nbt.getInteger("AdminConfirmedWidth"));
            adminConfirmedHeight = Integer.valueOf(nbt.getInteger("AdminConfirmedHeight"));
            adminConfirmationStructureRevision = Math.max(0, nbt.getInteger("AdminConfirmationStructureRevision"));
            dimensionsConfirmedByUuid = parseUuid(nbt.getString("DimensionsConfirmedByUuid"));
            dimensionsConfirmedByName = clean(nbt.getString("DimensionsConfirmedByName"));
            dimensionsConfirmedAtMillis = Math.max(0L, nbt.getLong("DimensionsConfirmedAtMillis"));
            dimensionConfirmationReason = cleanAuditText(nbt.getString("DimensionConfirmationReason"));
        } else {
            clearAdminConfirmedDimensions(0L);
        }

        if (nbt.hasKey("AdminMaxHpOverride") && nbt.getInteger("AdminMaxHpOverride") > 0) {
            adminMaxHpOverride = Integer.valueOf(nbt.getInteger("AdminMaxHpOverride"));
            maxHpOverrideByUuid = parseUuid(nbt.getString("MaxHpOverrideByUuid"));
            maxHpOverrideByName = clean(nbt.getString("MaxHpOverrideByName"));
            maxHpOverrideAtMillis = Math.max(0L, nbt.getLong("MaxHpOverrideAtMillis"));
            maxHpOverrideReason = cleanAuditText(nbt.getString("MaxHpOverrideReason"));
        } else {
            clearAdminMaxHpOverride(0L);
        }
    }

    private DimensionDetectionStatus normalizedDetectionStatus() {
        return dimensionDetectionStatus == null ? DimensionDetectionStatus.INVALID : dimensionDetectionStatus;
    }

    private boolean hasRectangularDetectedGeometry() {
        return detectedWidth > 0 && detectedHeight > 0 && detectedProjectedArea > 0
            && (long) detectedWidth * (long) detectedHeight == detectedProjectedArea;
    }

    private static String normalizeOrientation(String value) {
        String source = clean(value).toUpperCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < source.length() && result.length() < 40; i++) {
            char c = source.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') result.append(c);
        }
        return result.toString();
    }

    private static UUID parseUuid(String value) {
        try {
            String key = clean(value);
            return key.length() == 0 ? null : UUID.fromString(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String cleanAuditText(String value) {
        return clean(value).replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
