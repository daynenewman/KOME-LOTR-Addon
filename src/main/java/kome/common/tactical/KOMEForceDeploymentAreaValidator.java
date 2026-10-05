package kome.common.tactical;

import java.util.ArrayList;
import java.util.List;
import kome.common.siege.geometry.KOMEPolygonValidator;
import kome.common.siege.validation.KOMEValidationCode;
import kome.common.siege.validation.KOMEValidationIssue;
import kome.common.siege.validation.KOMEValidationResult;
import kome.common.siege.validation.KOMEValidationSeverity;

/** Pure authoring validation; no world lookup, fortress-overlap policy or tactical readiness. */
public final class KOMEForceDeploymentAreaValidator {
    public KOMEValidationResult validate(KOMEForceDeploymentArea area) {
        if (area == null) throw new IllegalArgumentException("A Force Deployment Area is required.");
        // Identity, explicit dimension metadata and non-null geometry are constructor invariants.
        List<KOMEValidationIssue> issues = new ArrayList<KOMEValidationIssue>();
        if (!area.getTileId().matches("[A-Z]+[0-9]+")) {
            add(issues, KOMEValidationCode.INVALID_FORCE_DEPLOYMENT_AREA_TILE_ID,
                "Force Deployment Area requires a canonical conquest tile ID.", area.getAreaId());
        }
        if (area.getRevision() < 0L) {
            add(issues, KOMEValidationCode.INVALID_FORCE_DEPLOYMENT_AREA_REVISION,
                "Revision cannot be negative.", area.getAreaId());
        }
        issues.addAll(KOMEPolygonValidator.validate(area.getPrism().getPolygon(), area.getAreaId()).getIssues());
        if (!area.getPrism().hasValidYRange()) {
            add(issues, KOMEValidationCode.PRISM_MALFORMED_Y,
                "Prism minimum Y must be below maximum-exclusive Y.", area.getAreaId());
        }
        return new KOMEValidationResult(issues);
    }

    private static void add(List<KOMEValidationIssue> issues, KOMEValidationCode code,
            String message, String areaId) {
        issues.add(new KOMEValidationIssue(KOMEValidationSeverity.ERROR, code, message, areaId));
    }
}
