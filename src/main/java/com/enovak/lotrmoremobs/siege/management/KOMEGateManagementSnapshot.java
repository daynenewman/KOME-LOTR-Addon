package com.enovak.lotrmoremobs.siege.management;

import kome.common.config.KOMEConfigRegistry;
import kome.common.data.KOMEBuildService;
import kome.common.data.KOMEDefensiveGateHealthCalculator;
import kome.common.data.KOMEDefensiveGateLinkService;
import kome.common.data.KOMEDefensiveGateRecord;
import kome.common.data.KOMEPhysicalGateInspection;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMEBuildTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable, concise server-computed KOME presentation sent with Gate Management. */
public final class KOMEGateManagementSnapshot {
    /** Unsigned-short wire bound; large enough to avoid geographic or ordinary data filtering. */
    public static final int MAX_OPTIONS = 65535;

    private final boolean linked;
    private final boolean relinkRequired;
    private final boolean needsDimensionConfirmation;
    private final String buildId;
    private final String buildName;
    private final String recordId;
    private final String gateSizeLabel;
    private final String projectedMaxHpLabel;
    private final int suggestedWidth;
    private final int suggestedHeight;
    private final int serverDefaultMaxHp;
    private final List<BuildOption> eligibleBuilds;
    private final List<RelinkOption> relinkOptions;

    public KOMEGateManagementSnapshot(boolean linked, boolean relinkRequired,
            boolean needsDimensionConfirmation, String buildId, String buildName,
            String recordId, String gateSizeLabel, String projectedMaxHpLabel,
            int suggestedWidth, int suggestedHeight, int serverDefaultMaxHp,
            List<BuildOption> eligibleBuilds, List<RelinkOption> relinkOptions) {
        this.linked = linked;
        this.relinkRequired = relinkRequired;
        this.needsDimensionConfirmation = needsDimensionConfirmation;
        this.buildId = safe(buildId);
        this.buildName = safe(buildName);
        this.recordId = safe(recordId);
        this.gateSizeLabel = safe(gateSizeLabel);
        this.projectedMaxHpLabel = safe(projectedMaxHpLabel);
        this.suggestedWidth = Math.max(0, suggestedWidth);
        this.suggestedHeight = Math.max(0, suggestedHeight);
        this.serverDefaultMaxHp = Math.max(1, serverDefaultMaxHp);
        this.eligibleBuilds = immutableLimited(eligibleBuilds);
        this.relinkOptions = immutableLimitedRelinks(relinkOptions);
    }

    public static KOMEGateManagementSnapshot create(KOMEWorldData data,
            KOMEPhysicalGateInspection.Result inspection, int serverDefaultMaxHp,
            List<BrokenRecord> brokenRecords, boolean includeAdminOptions) {
        KOMEDefensiveGateLinkService.ActiveLink link = inspection == null ? null
            : KOMEDefensiveGateLinkService.findActiveLinkByPhysicalGateUuid(data,
                inspection.getGateUuid());
        List<BuildOption> builds = new ArrayList<BuildOption>();
        if (includeAdminOptions && inspection != null && inspection.isLinkable()) {
            for (KOMEPlayerBuild build : KOMEBuildService.activeDefensiveBuilds(data)) {
                builds.add(new BuildOption(build.id, buildLabel(build, inspection)));
            }
        }
        List<RelinkOption> relinks = new ArrayList<RelinkOption>();
        List<RelinkOption> controllerRelinks = new ArrayList<RelinkOption>();
        boolean replacementAtBrokenController = false;
        if (includeAdminOptions && brokenRecords != null) {
            for (BrokenRecord broken : brokenRecords) {
                if (broken != null && broken.parent != null && broken.record != null
                        && broken.parent.active && broken.parent.isDefensive()) {
                    RelinkOption option = new RelinkOption(broken.parent.id, broken.record.getId(),
                        KOMEPlayerBuild.sanitizeName(broken.parent.displayName) + " ("
                            + broken.parent.id + "/" + broken.record.getId() + ") — Relink");
                    relinks.add(option);
                    boolean sameController = inspection != null
                        && inspection.getGateUuid() != null
                        && broken.record.hasPhysicalBinding()
                        && broken.record.getGateDimension().intValue() == inspection.getDimension()
                        && broken.record.getControllerX().intValue() == inspection.getControllerX()
                        && broken.record.getControllerY().intValue() == inspection.getControllerY()
                        && broken.record.getControllerZ().intValue() == inspection.getControllerZ()
                        && !inspection.getGateUuid().equals(broken.record.getGateUuid());
                    if (sameController) controllerRelinks.add(option);
                    replacementAtBrokenController |= sameController;
                }
            }
        }
        if (replacementAtBrokenController) {
            builds.clear();
            relinks.clear();
            relinks.addAll(controllerRelinks);
        }

        if (link == null) {
            String size = inspection != null && inspection.isLinkable()
                && inspection.getStatus() == KOMEDefensiveGateRecord.DimensionDetectionStatus.RELIABLE
                ? inspection.getDetectedWidth() + "×" + inspection.getDetectedHeight()
                : "Needs confirmation";
            return new KOMEGateManagementSnapshot(false, replacementAtBrokenController, false, "", "", "", size,
                formatHp(serverDefaultMaxHp) + " (Server Default)",
                inspection == null ? 0 : inspection.getDetectedWidth(),
                inspection == null ? 0 : inspection.getDetectedHeight(), serverDefaultMaxHp,
                builds, relinks);
        }

        KOMEPlayerBuild parent = link.getParent();
        KOMEDefensiveGateRecord record = link.getRecord();
        boolean sameController = KOMEDefensiveGateLinkService.isSamePhysicalController(record, inspection);
        boolean broken = !sameController;
        boolean capturedInspectionCurrent = sameController && matchesCapturedInspection(record, inspection);
        boolean needsConfirmation = !broken && (capturedInspectionCurrent
            ? record.effectiveDimensionProvenance()
                == KOMEDefensiveGateRecord.EffectiveDimensionProvenance.UNAVAILABLE
            : inspection.getStatus() != KOMEDefensiveGateRecord.DimensionDetectionStatus.RELIABLE);
        String size = broken ? "Relink required" : needsConfirmation ? "Needs confirmation"
            : capturedInspectionCurrent
                ? record.effectiveWidth() + "×" + record.effectiveHeight()
                : inspection.getDetectedWidth() + "×" + inspection.getDetectedHeight();
        String projected;
        if (broken) {
            projected = "Unavailable — relink required";
        } else if (record.hasAdminMaxHpOverride()) {
            projected = formatHp(record.getAdminMaxHpOverride().intValue()) + " (Admin Override)";
        } else if (capturedInspectionCurrent) {
            KOMEDefensiveGateHealthCalculator.Result health =
                KOMEDefensiveGateHealthCalculator.calculate(parent, record.getId(),
                    KOMEConfigRegistry.siege().getGateHpPerApprovedHour(),
                    KOMEConfigRegistry.siege().getGateSizeParameters());
            projected = health.isEffectiveMaxHpAvailable() ? formatHp(health.getEffectiveMaxHp())
                : needsConfirmation ? "Unavailable — confirm dimensions" : "Unavailable";
        } else if (!needsConfirmation
                && KOMEConfigRegistry.siege().getGateHpPerApprovedHour().isPresent()) {
            try {
                projected = formatHp(KOMEDefensiveGateHealthCalculator.calculateAutomaticMaxHp(
                    parent.approvedDefensiveCentiHours(),
                    KOMEConfigRegistry.siege().getGateHpPerApprovedHour().getAsDouble(),
                    inspection.getDetectedWidth(), inspection.getDetectedHeight(),
                    KOMEConfigRegistry.siege().getGateSizeParameters()));
            } catch (IllegalArgumentException ignored) {
                projected = "Unavailable";
            }
        } else {
            projected = needsConfirmation ? "Unavailable — confirm dimensions" : "Unavailable";
        }
        return new KOMEGateManagementSnapshot(true, broken, needsConfirmation,
            parent.id, parent.displayName, record.getId(), size, projected,
            record.getDetectedWidth(), record.getDetectedHeight(), serverDefaultMaxHp,
            builds, relinks);
    }

    private static boolean matchesCapturedInspection(KOMEDefensiveGateRecord record,
            KOMEPhysicalGateInspection.Result inspection) {
        return record.getCapturedStructureRevision() == inspection.getStructureRevision()
            && record.getDetectedOrientation().equals(inspection.getOrientation())
            && record.getDetectedWidth() == inspection.getDetectedWidth()
            && record.getDetectedHeight() == inspection.getDetectedHeight()
            && record.getDetectedProjectedArea() == inspection.getProjectedArea()
            && record.getDimensionDetectionStatus() == inspection.getStatus();
    }

    private static String buildLabel(KOMEPlayerBuild build,
            KOMEPhysicalGateInspection.Result inspection) {
        String projected = "Needs confirmation";
        if (inspection.getStatus() == KOMEDefensiveGateRecord.DimensionDetectionStatus.RELIABLE) {
            if (KOMEConfigRegistry.siege().getGateHpPerApprovedHour().isPresent()) {
                try {
                    BigDecimal hp = KOMEDefensiveGateHealthCalculator.calculateAutomaticMaxHp(
                        build.approvedDefensiveCentiHours(),
                        KOMEConfigRegistry.siege().getGateHpPerApprovedHour().getAsDouble(),
                        inspection.getDetectedWidth(), inspection.getDetectedHeight(),
                        KOMEConfigRegistry.siege().getGateSizeParameters());
                    projected = formatHp(hp) + " HP";
                } catch (IllegalArgumentException ignored) {
                    projected = "Unavailable";
                }
            } else {
                projected = "Unavailable";
            }
        }
        return KOMEPlayerBuild.sanitizeName(build.displayName) + " (" + build.id + ") — "
            + formatHours(build.approvedDefensiveCentiHours()) + "h — " + projected;
    }

    private static String formatHours(long centiHours) {
        String canonical = KOMEBuildTime.formatHours(centiHours);
        if (canonical.endsWith(".00")) return canonical.substring(0, canonical.length() - 3);
        return canonical.endsWith("0") ? canonical.substring(0, canonical.length() - 1)
            : canonical;
    }

    private static String formatHp(int hp) { return formatHp(BigDecimal.valueOf(hp)); }

    private static String formatHp(BigDecimal hp) {
        if (hp == null) return "Unavailable";
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        return format.format(hp.setScale(0, RoundingMode.HALF_UP));
    }

    private static List<BuildOption> immutableLimited(List<BuildOption> source) {
        List<BuildOption> result = new ArrayList<BuildOption>();
        if (source != null) for (BuildOption option : source) {
            if (option != null && result.size() < MAX_OPTIONS) result.add(option);
        }
        return Collections.unmodifiableList(result);
    }

    private static List<RelinkOption> immutableLimitedRelinks(List<RelinkOption> source) {
        List<RelinkOption> result = new ArrayList<RelinkOption>();
        if (source != null) for (RelinkOption option : source) {
            if (option != null && result.size() < MAX_OPTIONS) result.add(option);
        }
        return Collections.unmodifiableList(result);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public boolean isLinked() { return linked; }
    public boolean isRelinkRequired() { return relinkRequired; }
    public boolean needsDimensionConfirmation() { return needsDimensionConfirmation; }
    public String getBuildId() { return buildId; }
    public String getBuildName() { return buildName; }
    public String getRecordId() { return recordId; }
    public String getGateSizeLabel() { return gateSizeLabel; }
    public String getProjectedMaxHpLabel() { return projectedMaxHpLabel; }
    public int getSuggestedWidth() { return suggestedWidth; }
    public int getSuggestedHeight() { return suggestedHeight; }
    public int getServerDefaultMaxHp() { return serverDefaultMaxHp; }
    public List<BuildOption> getEligibleBuilds() { return eligibleBuilds; }
    public List<RelinkOption> getRelinkOptions() { return relinkOptions; }

    public static final class BuildOption {
        private final String buildId;
        private final String label;
        public BuildOption(String buildId, String label) {
            this.buildId = safe(buildId); this.label = safe(label);
        }
        public String getBuildId() { return buildId; }
        public String getLabel() { return label; }
    }

    public static final class RelinkOption {
        private final String buildId;
        private final String recordId;
        private final String label;
        public RelinkOption(String buildId, String recordId, String label) {
            this.buildId = safe(buildId); this.recordId = safe(recordId); this.label = safe(label);
        }
        public String getBuildId() { return buildId; }
        public String getRecordId() { return recordId; }
        public String getLabel() { return label; }
    }

    public static final class BrokenRecord {
        private final KOMEPlayerBuild parent;
        private final KOMEDefensiveGateRecord record;
        public BrokenRecord(KOMEPlayerBuild parent, KOMEDefensiveGateRecord record) {
            this.parent = parent; this.record = record;
        }
    }
}
