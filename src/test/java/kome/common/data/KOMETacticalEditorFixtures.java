package kome.common.data;

import java.util.UUID;
import kome.common.siege.KOMESiegeReadinessFixtures;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.edit.*;

/** Root-installed editor fixtures, without exposing mutable world tactical state. */
public final class KOMETacticalEditorFixtures {
    private KOMETacticalEditorFixtures() { }
    public static CountingWorld world() { CountingWorld data = new CountingWorld(); install(data); return data; }
    public static void install(KOMEWorldData data) {
        data.initializeIntegratedWorld();
        KOMETacticalMembershipFixtures.addBuild(data, "B1");
        KOMETacticalMembershipFixtures.addBuild(data, "B2");
        KOMETacticalConfiguration configuration = new KOMETacticalConfiguration();
        configuration.addComplex(KOMESiegeReadinessFixtures.minimal("A", "T100", dimension(), null));
        configuration.addComplex(KOMESiegeReadinessFixtures.minimal("B", "T100", dimension(), null));
        configuration.addComplex(KOMESiegeReadinessFixtures.empty("EMPTY", "T100", dimension()));
        configuration.addForceDeploymentArea(area("FIELD", "Field", 7));
        configuration.addForceDeploymentArea(area("OTHER", "Other field", 3));
        configuration.addForceDeploymentArea(new KOMEForceDeploymentArea("WRONG_TILE", "T101", dimension(),
            "Wrong tile", KOMESiegeReadinessFixtures.prism(30, 30, 40, 40), 1));
        KOMETacticalMembershipFixtures.installConfiguration(data, data, configuration);
        data.setDirty(false);
        if (data instanceof CountingWorld) ((CountingWorld) data).dirtyCalls = 0;
    }
    public static int dimension() { return KOMETileTestResources.dimension(); }
    public static KOMEForceDeploymentArea area(String id, String label, long revision) {
        return new KOMEForceDeploymentArea(id, "T100", dimension(), label, KOMESiegeReadinessFixtures.prism(30, 30, 40, 40), revision);
    }
    public static KOMETacticalEditScope areaScope(String id) { return new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, "T100", null, id, dimension()); }
    public static KOMETacticalEditScope complexScope(String id) { return new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX, "T100", id, id, dimension()); }
    public static long revision(KOMEWorldData data) { return data.getTacticalConfigurationSnapshot().getRevision(); }
    public static final class CountingWorld extends KOMEWorldData {
        public int dirtyCalls;
        public boolean failDirty;
        public CountingWorld() { super("editorTest"); }
        @Override public void markDirty() { super.markDirty(); ++dirtyCalls; if (failDirty) throw new IllegalStateException("Simulated publication failure"); }
    }
    public static final class Actor implements KOMETacticalEditSessionManager.Actor {
        public UUID id = UUID.randomUUID();
        public int dimension = dimension();
        public boolean authorized = true, connected = true;
        public UUID getPlayerId() { return id; }
        public int getDimensionId() { return dimension; }
        public boolean isAuthorized() { return authorized; }
        public boolean isConnected() { return connected; }
    }
}
