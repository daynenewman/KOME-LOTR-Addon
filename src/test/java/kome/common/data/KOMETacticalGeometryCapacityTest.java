package kome.common.data;

import java.util.*;
import kome.client.tactical.KOMETacticalAreaEditor;
import kome.common.network.KOMEPacketTacticalEditRequest;
import kome.common.siege.*;
import kome.common.siege.geometry.*;
import kome.common.tactical.*;
import kome.common.tactical.edit.*;
import io.netty.buffer.*;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMETacticalEditorFixtures.*;

public class KOMETacticalGeometryCapacityTest {
    @Rule public final KOMETileTestResources tiles = new KOMETileTestResources();
    private static KOMEPolygonPrism polygon(int vertices) {
        List<KOMEXZPoint> points=new ArrayList<>();
        for(int i=0;i<vertices-2;i++) points.add(new KOMEXZPoint(i,0));
        points.add(new KOMEXZPoint(vertices-3,10)); points.add(new KOMEXZPoint(0,10));
        return new KOMEPolygonPrism(new KOMEPolygon(points),0,10);
    }
    @Test public void thousandVertexAreaPassesServerPreflightSaveTransportAndExactReopen() {
        CountingWorld data=world(); Actor actor=new Actor(); KOMETacticalEditSessionManager manager=new KOMETacticalEditSessionManager();
        KOMETacticalEditSnapshot s=manager.handle(actor,data,KOMETacticalEditRequest.create(areaScope("BIG"))).getSnapshot();
        KOMEForceDeploymentArea large=new KOMEForceDeploymentArea("BIG","T100",dimension(),"Large field",polygon(1024),0);
        KOMETacticalEditDraft draft=new KOMETacticalEditDraft(large);
        assertTrue(new KOMEForceDeploymentAreaValidator().validate(large).isValid());
        KOMETacticalEditRequest request=KOMETacticalEditRequest.update(s,draft);
        ByteBuf bytes=Unpooled.buffer();
        try {
            new KOMEPacketTacticalEditRequest(request).toBytes(bytes);
            assertTrue(bytes.readableBytes()<32767);
            KOMEPacketTacticalEditRequest decoded=new KOMEPacketTacticalEditRequest(); decoded.fromBytes(bytes); assertTrue(decoded.isValid());
            s=manager.handle(actor,data,decoded.getRequest()).getSnapshot();
        } finally { bytes.release(); }
        KOMETacticalEditPreflight p=manager.handle(actor,data,KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.PREFLIGHT,s)).getSnapshot().getPreflight();
        assertTrue(p.canSave()); assertTrue(p.isStructurallyValid());
        assertEquals(KOMETacticalEditSessionManager.Status.SAVED,manager.handle(actor,data,KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.SAVE,s)).getStatus());
        KOMEWorldData restored=new KOMEWorldData("large"); restored.readFromNBT(KOMETacticalMembershipFixtures.save(data));
        KOMEPolygon saved=restored.getTacticalConfigurationSnapshot().findForceDeploymentArea("BIG").getPrism().getPolygon();
        assertEquals(large.getPrism().getPolygon().getVertices(),saved.getVertices()); assertEquals(1024,saved.getVertices().size());
    }
    @Test public void hundredsOfIndependentPolygonsPersistDeterministicallyAndLargeZoneDraftIsSaveable() {
        CountingWorld data=world(); KOMETacticalConfiguration config=data.getTacticalConfigurationSnapshot(); List<KOMENormalSegment> zones=new ArrayList<>();
        for(int i=0;i<140;i++) zones.add(new KOMENormalSegment("N"+i,"",KOMESiegeReadinessFixtures.prism(i*4,0,i*4+2,2)));
        KOMESiegeComplex complex=new KOMESiegeComplex("BIG","T100",dimension(),0,zones,Collections.emptyList(),Collections.emptyList(),null,Collections.emptyList());
        config.addComplex(complex);
        for(int i=239;i>=0;i--) config.addForceDeploymentArea(new KOMEForceDeploymentArea("F"+i,"T100",dimension(),"Field "+i,polygon(4),i));
        KOMETacticalMembershipFixtures.installConfiguration(data,data,config);
        KOMEWorldData restored=new KOMEWorldData("many"); restored.readFromNBT(KOMETacticalMembershipFixtures.save(data));
        KOMETacticalConfiguration copy=restored.getTacticalConfigurationSnapshot();
        assertEquals(KOMETacticalConfigurationCodec.encode(config),KOMETacticalConfigurationCodec.encode(copy));
        assertEquals(140,copy.findComplex("BIG").getNormalSegments().size()); assertEquals(243,copy.getForceDeploymentAreasById().size());
        assertEquals(KOMETacticalConfigurationCodec.encode(copy),KOMETacticalConfigurationCodec.encode(KOMETacticalConfigurationCodec.decode(KOMETacticalConfigurationCodec.encode(copy))));
        KOMETacticalEditDraft large=new KOMETacticalEditDraft(complex);
        assertEquals(140,KOMETacticalEditWire.decodeDraft(KOMETacticalEditWire.encodeDraft(large)).getComplex().getNormalSegments().size());
        KOMESiegeComplex edited=new KOMESiegeComplex("BIG","T100",dimension(),0,zones,Collections.emptyList(),Collections.emptyList(),"UNRESOLVED",Collections.emptyList());
        KOMETacticalEditPreflight p=KOMETacticalEditService.preflight(data,complexScope("BIG"),new KOMETacticalEditDraft(edited),revision(data),0);
        assertTrue(p.canSave()); assertTrue(p.isStructurallyValid()); assertFalse(p.isReady());
    }
    @Test public void clientCanContinuePreciseVertexAuthoringBeyondTheOld128Cap() {
        List<KOMETacticalEditRequest> requests=new ArrayList<>(); KOMETacticalAreaEditor editor=new KOMETacticalAreaEditor(requests::add);
        editor.acceptCatalog(new KOMETacticalAreaCatalog("T100",dimension(),10,0,1,Collections.emptyList())); editor.open("BIG",false);
        KOMEForceDeploymentArea large=new KOMEForceDeploymentArea("BIG","T100",dimension(),"Large",polygon(1024),0);
        KOMETacticalEditSnapshot snapshot=new KOMETacticalEditSnapshot(UUID.randomUUID(),UUID.randomUUID(),areaScope("BIG"),1,1,10,0,0,10,false,new KOMETacticalEditDraft(large),null);
        editor.accept(snapshot,KOMETacticalEditSessionManager.Status.OPENED);
        editor.addVertex(-1,5); assertEquals(1025,editor.getConfirmedVertexCount());
        assertEquals(new KOMEXZPoint(-1,5),editor.getConfirmedVertices().get(1024));
        assertEquals(editor.getDraft().getPrism().getPolygon().getVertices(),KOMETacticalEditWire.decodeDraft(KOMETacticalEditWire.encodeDraft(new KOMETacticalEditDraft(editor.getDraft()))).getArea().getPrism().getPolygon().getVertices());
    }
    @Test public void oversizedAuthoringIsRejectedWithTechnicalDiagnosticAndNoTruncationOrPublication() {
        CountingWorld data=world(); long revision=revision(data); KOMEForceDeploymentArea huge=new KOMEForceDeploymentArea("HUGE","T100",dimension(),"",polygon(5000),0);
        IllegalArgumentException failure=assertThrows(IllegalArgumentException.class,()->KOMETacticalEditWire.encodeDraft(new KOMETacticalEditDraft(huge)));
        assertTrue(failure.getMessage().contains("technical")); assertEquals(5000,huge.getPrism().getPolygon().getVertices().size());
        assertFalse(KOMETacticalEditService.preflightCreation(data,areaScope("HUGE"),new KOMETacticalEditDraft(huge),revision).canSave());
        assertNull(data.getTacticalConfigurationSnapshot().findForceDeploymentArea("HUGE")); assertEquals(revision,revision(data)); assertEquals(0,data.dirtyCalls);
    }
}
