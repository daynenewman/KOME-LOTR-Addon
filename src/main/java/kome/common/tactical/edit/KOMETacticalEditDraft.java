package kome.common.tactical.edit;

import kome.common.siege.KOMESiegeComplex;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMETacticalConfigurationCodec;
import kome.common.tactical.KOMETacticalIds;
import net.minecraft.nbt.NBTTagCompound;

/** Immutable detached definition, optionally carrying ONE explicit membership intent for Save. */
public final class KOMETacticalEditDraft {
    public enum MembershipAction { ASSIGN, UNASSIGN, REASSIGN }
    private final KOMESiegeComplex complex;
    private final KOMEForceDeploymentArea area;
    private final MembershipAction membershipAction;
    private final String buildId, expectedOldComplexId;

    public KOMETacticalEditDraft(KOMESiegeComplex complex) { this(complex, null, null, null, null); }
    public KOMETacticalEditDraft(KOMEForceDeploymentArea area) { this(null, area, null, null, null); }
    private KOMETacticalEditDraft(KOMESiegeComplex complex, KOMEForceDeploymentArea area,
            MembershipAction action, String buildId, String expectedOld) {
        if ((complex == null) == (area == null)) throw new IllegalArgumentException("One draft definition is required.");
        this.complex = complex; this.area = area; this.membershipAction = action;
        this.buildId = buildId; this.expectedOldComplexId = expectedOld;
    }

    /** Membership intent replaces definition editing for this commit; it is never applied during Update. */
    public KOMETacticalEditDraft withMembership(MembershipAction action, String build, String expectedOld) {
        if (complex == null || action == null) throw new IllegalArgumentException("Complex membership action required.");
        KOMETacticalEditScope.validateId(build);
        String id = KOMETacticalIds.buildLookup(build);
        KOMETacticalEditScope.validateId(id);
        String old = expectedOld == null ? null : KOMETacticalEditScope.canonicalId(expectedOld);
        if (action == MembershipAction.REASSIGN ? old == null : old != null) {
            throw new IllegalArgumentException("Unexpected old membership identity.");
        }
        return new KOMETacticalEditDraft(complex, null, action, id, old);
    }

    public KOMESiegeComplex getComplex() { return complex; }
    public KOMEForceDeploymentArea getArea() { return area; }
    public MembershipAction getMembershipAction() { return membershipAction; }
    public String getBuildId() { return buildId; }
    public String getExpectedOldComplexId() { return expectedOldComplexId; }
    public long getObjectRevision() { return complex != null ? complex.getRevision() : area.getRevision(); }
    public String getTargetId() { return complex != null ? complex.getComplexId() : area.getAreaId(); }
    public String getTileId() { return complex != null ? complex.getTileId() : area.getTileId(); }
    public int getDimensionId() { return complex != null ? complex.getDimensionId() : area.getDimensionId(); }
    public void requireScope(KOMETacticalEditScope scope) {
        if (scope == null || (complex != null) != (scope.getType() == KOMETacticalEditScope.Type.SIEGE_COMPLEX)
                || !getTargetId().equals(scope.getTargetId()) || !getTileId().equals(scope.getTileId())
                || getDimensionId() != scope.getDimensionId()) throw new IllegalArgumentException("Draft changed scope ownership.");
    }

    public KOMETacticalEditDraft atRevision(long revision) {
        if (complex != null) return new KOMETacticalEditDraft(new KOMESiegeComplex(complex.getComplexId(),
            complex.getTileId(), complex.getDimensionId(), revision, complex.getNormalSegments(), complex.getWallZones(),
            complex.getTransitionZones(), complex.getPreferredForceDeploymentAreaId().orElse(null), complex.getConnections()));
        return new KOMETacticalEditDraft(new KOMEForceDeploymentArea(area.getAreaId(), area.getTileId(),
            area.getDimensionId(), area.getLabel(), area.getPrism(), revision));
    }

    /** Reuses the tested tactical codec with a single-definition envelope, never the live store. */
    public NBTTagCompound encode() {
        KOMETacticalConfiguration single = new KOMETacticalConfiguration();
        if (complex != null) single.addComplex(complex); else single.addForceDeploymentArea(area);
        NBTTagCompound tag = KOMETacticalConfigurationCodec.encode(single);
        if (membershipAction != null) {
            tag.setString("MembershipAction", membershipAction.name()); tag.setString("BuildId", buildId);
            if (expectedOldComplexId != null) tag.setString("ExpectedOldComplexId", expectedOldComplexId);
        }
        return tag;
    }

    public static KOMETacticalEditDraft decode(NBTTagCompound tag) {
        KOMETacticalConfiguration single = KOMETacticalConfigurationCodec.decode(tag);
        if (!single.getBuildAssignmentsByBuildId().isEmpty()
                || single.getComplexesById().size() + single.getForceDeploymentAreasById().size() != 1) {
            throw new IllegalArgumentException("Draft must contain exactly one definition and no assignments.");
        }
        KOMETacticalEditDraft draft = single.getComplexesById().isEmpty()
            ? new KOMETacticalEditDraft(single.getForceDeploymentAreasById().values().iterator().next())
            : new KOMETacticalEditDraft(single.getComplexesById().values().iterator().next());
        if (tag.hasKey("MembershipAction")) {
            if (!tag.hasKey("MembershipAction", 8) || !tag.hasKey("BuildId", 8)
                    || (tag.hasKey("ExpectedOldComplexId") && !tag.hasKey("ExpectedOldComplexId", 8))) {
                throw new IllegalArgumentException("Invalid membership encoding.");
            }
            draft = draft.withMembership(MembershipAction.valueOf(tag.getString("MembershipAction")), tag.getString("BuildId"),
                tag.hasKey("ExpectedOldComplexId") ? tag.getString("ExpectedOldComplexId") : null);
        } else if (tag.hasKey("BuildId") || tag.hasKey("ExpectedOldComplexId")) {
            throw new IllegalArgumentException("Membership identity without action.");
        }
        return draft;
    }

    public boolean sameDefinition(KOMETacticalEditDraft other) {
        return other != null && atRevision(0L).encode().equals(other.atRevision(0L).encode());
    }
}
