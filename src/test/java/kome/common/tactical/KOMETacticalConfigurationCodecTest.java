package kome.common.tactical;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import kome.common.siege.KOMEDefensiveGateRef;
import kome.common.siege.KOMENormalSegment;
import kome.common.siege.KOMESiegeAreaRef;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeComplexValidator;
import kome.common.siege.KOMESiegeConnection;
import kome.common.siege.KOMETransitionZone;
import kome.common.siege.KOMEWallZone;
import kome.common.siege.geometry.KOMEPolygon;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEXZPoint;
import kome.common.siege.validation.KOMEValidationCode;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMETacticalConfigurationCodecTest {
    @Test public void emptyConfigurationRoundTripsWithOnlyAuthoritativeSections() {
        KOMETacticalConfiguration empty = new KOMETacticalConfiguration();
        NBTTagCompound encoded = encode(empty);
        assertEquals(1, encoded.getInteger("SchemaVersion"));
        assertEquals(0L, encoded.getLong("Revision"));
        assertEquals(5, encoded.func_150296_c().size());
        KOMETacticalConfiguration decoded = decode(encoded);
        assertNotSame(empty, decoded);
        assertTrue(decoded.getComplexesById().isEmpty());
        assertTrue(decoded.getForceDeploymentAreasById().isEmpty());
        assertTrue(decoded.getBuildAssignmentsByBuildId().isEmpty());
        assertEquals(encoded, encode(decoded));
    }

    @Test public void revisionIsRestoredExactlyWithoutReplayingMutations() {
        for (long revision : new long[]{0L, 731L, Long.MAX_VALUE}) {
            NBTTagCompound encoded = document();
            encoded.setLong("Revision", revision);
            KOMETacticalConfiguration decoded = decode(encoded);
            assertEquals(revision, decoded.getRevision());
            assertEquals(7L, decoded.findComplex("WEST").getRevision());
            assertEquals(11L, decoded.findForceDeploymentArea("FIELD").getRevision());
            assertEquals(encoded, encode(decoded));
            if (revision != Long.MAX_VALUE) {
                decoded.unassignBuild("B1");
                assertEquals(revision + 1, decoded.getRevision());
            } else {
                assertThrows(IllegalStateException.class, () -> decoded.unassignBuild("B1"));
                assertEquals(revision, decoded.getRevision());
            }
        }
    }

    @Test public void sameTileComplexesRemainIndependentWithRepeatedLocalIds() {
        KOMETacticalConfiguration source = populated(false);
        KOMETacticalConfiguration decoded = roundTrip(source);
        assertEquals(2, decoded.listComplexesForTile("T277").size());
        assertEquals(Collections.singleton("inside"), decoded.findComplex("WEST").getAdjacentNormalSegmentIds("a"));
        assertEquals(Collections.singleton("OTHER"), decoded.findComplex("EAST").getAdjacentNormalSegmentIds("a"));
        assertNull(decoded.findComplex("WEST").findNormalSegment("OTHER"));
        assertNull(decoded.findComplex("EAST").findNormalSegment("inside"));
        assertNotSame(decoded.findComplex("WEST").findNormalSegment("a"), decoded.findComplex("EAST").findNormalSegment("a"));
        assertEquals(7L, decoded.findComplex("WEST").getRevision());
        assertEquals(19L, decoded.findComplex("EAST").getRevision());
        assertComplexEquals(source.findComplex("WEST"), decoded.findComplex("WEST"));
        assertComplexEquals(source.findComplex("EAST"), decoded.findComplex("EAST"));
    }

    @Test public void ordinaryTileRoundTripsMultipleAreasWithoutAnyComplex() {
        KOMETacticalConfiguration source = new KOMETacticalConfiguration();
        source.addForceDeploymentArea(area("WEST_FIELD", 0, 1));
        source.addForceDeploymentArea(area("EAST_FIELD", 0, 2));
        KOMETacticalConfiguration decoded = roundTrip(source);
        assertTrue(decoded.listComplexesForTile("T277").isEmpty());
        assertEquals(Arrays.asList("EAST_FIELD", "WEST_FIELD"), new ArrayList<String>(decoded.getForceDeploymentAreasById().keySet()));
        assertAreaEquals(source.findForceDeploymentArea("WEST_FIELD"), decoded.findForceDeploymentArea("WEST_FIELD"));
        assertAreaEquals(source.findForceDeploymentArea("EAST_FIELD"), decoded.findForceDeploymentArea("EAST_FIELD"));
    }

    @Test public void preferredTileAreaAndRepeatedDisplayLabelsRoundTrip() {
        KOMETacticalConfiguration source = populated(false);
        KOMETacticalConfiguration decoded = roundTrip(source);
        assertEquals("FIELD", decoded.findComplex("WEST").getPreferredForceDeploymentAreaId().get());
        assertEquals("FIELD", decoded.findComplex("EAST").getPreferredForceDeploymentAreaId().get());
        assertEquals("Shared staging label", decoded.findForceDeploymentArea("FIELD").getLabel());
        assertEquals(decoded.findForceDeploymentArea("FIELD").getLabel(), decoded.findForceDeploymentArea("SECOND_FIELD").getLabel());
        assertTrue(new KOMESiegeComplexValidator().validatePreferredForceDeploymentArea(decoded.findComplex("WEST"),
            decoded.findForceDeploymentArea("FIELD")).isValid());
    }

    @Test public void unresolvedPreferredAreaRemainsExplicitAndDiagnosable() {
        KOMETacticalConfiguration source = new KOMETacticalConfiguration();
        source.addComplex(emptyComplex("WEST", " missing ", 0));
        KOMETacticalConfiguration decoded = roundTrip(source);
        assertEquals("MISSING", decoded.findComplex("WEST").getPreferredForceDeploymentAreaId().get());
        assertNull(decoded.findForceDeploymentArea("MISSING"));
        assertTrue(new KOMESiegeComplexValidator().validatePreferredForceDeploymentArea(decoded.findComplex("WEST"), null)
            .hasCode(KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_UNKNOWN));
        assertEquals(encode(source), encode(decoded));
    }

    @Test public void absentPreferredAreaRemainsAbsent() {
        KOMETacticalConfiguration source = new KOMETacticalConfiguration();
        source.addComplex(emptyComplex("WEST", null, 0));
        assertFalse(firstComplex(encode(source)).hasKey("PreferredForceDeploymentAreaId"));
        assertFalse(roundTrip(source).findComplex("WEST").getPreferredForceDeploymentAreaId().isPresent());
    }

    @Test public void normalWallAndTransitionDefinitionsRoundTripWithoutLocalCaseChanges() {
        KOMETacticalConfiguration source = populated(false);
        KOMESiegeComplex before = source.findComplex("WEST");
        KOMESiegeComplex after = roundTrip(source).findComplex("WEST");
        assertEquals(before.getNormalSegments(), after.getNormalSegments());
        assertEquals(before.getWallZones(), after.getWallZones());
        assertEquals(before.getTransitionZones(), after.getTransitionZones());
        assertEquals(before.getConnections(), after.getConnections());
        assertNotNull(after.findNormalSegment("a"));
        assertNull(after.findNormalSegment("A"));
        assertNotNull(after.findWallZone("wallLower"));
        assertNotNull(after.findTransitionZone("gateLower"));
        assertEquals(Arrays.asList("a", "inside"), new ArrayList<String>(after.findWallZone("wallLower").getAccessibleFromNormalSegmentIds()));
    }

    @Test public void exactExtremeIntegerCoordinatesVertexOrderAndDerivedCachesArePreserved() {
        KOMEPolygon polygon = KOMEPolygon.of(new KOMEXZPoint(Integer.MAX_VALUE, Integer.MIN_VALUE),
            new KOMEXZPoint(Integer.MIN_VALUE, Integer.MIN_VALUE), new KOMEXZPoint(Integer.MIN_VALUE, Integer.MAX_VALUE),
            new KOMEXZPoint(Integer.MAX_VALUE, Integer.MAX_VALUE));
        KOMEPolygonPrism prism = new KOMEPolygonPrism(polygon, Integer.MIN_VALUE, Integer.MAX_VALUE);
        KOMETacticalConfiguration source = new KOMETacticalConfiguration();
        source.addForceDeploymentArea(new KOMEForceDeploymentArea("EXTREME", "T277", -1, "Exact", prism, 0));
        KOMEPolygonPrism after = roundTrip(source).findForceDeploymentArea("EXTREME").getPrism();
        assertEquals(prism, after);
        assertEquals(polygon.getVertices(), after.getPolygon().getVertices());
        assertEquals(polygon.getSignedAreaTwice(), after.getPolygon().getSignedAreaTwice());
        assertTrue(after.getPolygon().getSignedAreaTwice().bitLength() > 63);
        assertEquals(Integer.MIN_VALUE, after.getMinX());
        assertEquals(Integer.MAX_VALUE, after.getMaxZ());
        NBTTagCompound geometry = firstArea(encode(source)).getCompoundTag("Geometry");
        assertEquals(3, geometry.func_150296_c().size());
        NBTTagList vertices = geometry.getTagList("Vertices", 10);
        for (int i = 0; i < vertices.tagCount(); i++) {
            assertEquals(polygon.getVertices().get(i).getX(), vertices.getCompoundTagAt(i).getInteger("X"));
            assertEquals(polygon.getVertices().get(i).getZ(), vertices.getCompoundTagAt(i).getInteger("Z"));
        }
    }

    @Test public void signedDimensionIdsRemainExact() {
        for (int dimension : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
            KOMETacticalConfiguration source = new KOMETacticalConfiguration();
            source.addForceDeploymentArea(area("FIELD", dimension, 1));
            source.addComplex(emptyComplex("WEST", "FIELD", dimension));
            KOMETacticalConfiguration decoded = roundTrip(source);
            assertEquals(dimension, decoded.findComplex("WEST").getDimensionId());
            assertEquals(dimension, decoded.findForceDeploymentArea("FIELD").getDimensionId());
        }
    }

    @Test public void typedNormalEndpointsRoundTripAsLocalIds() {
        KOMESiegeConnection link = roundTrip(populated(false)).findComplex("WEST").getConnections().get(1);
        assertTrue(link.getEndpointA().isNormal());
        assertTrue(link.getEndpointB().isNormal());
        assertEquals(KOMESiegeAreaRef.normal("a"), link.getEndpointA());
        assertEquals(KOMESiegeAreaRef.normal("inside"), link.getEndpointB());
    }

    @Test public void exteriorIsConceptualLocalAndNeverADeploymentAreaReference() {
        NBTTagCompound encoded = document();
        NBTTagCompound exterior = firstConnection(encoded).getCompoundTag("EndpointA");
        assertEquals("EXTERIOR", exterior.getString("Type"));
        assertEquals(Collections.singleton("Type"), exterior.func_150296_c());
        KOMETacticalConfiguration decoded = decode(encoded);
        for (String id : Arrays.asList("EAST", "WEST")) {
            KOMESiegeComplex complex = decoded.findComplex(id);
            assertTrue(complex.getConnections().get(0).getEndpointA().isExterior());
            assertEquals(1, complex.getConnectionsFor(KOMESiegeAreaRef.exterior()).size());
            assertSame(complex.getConnections().get(0), complex.getConnectionsFor(KOMESiegeAreaRef.exterior()).get(0));
        }
        assertNull(decoded.findForceDeploymentArea("EXTERIOR"));
    }

    @Test public void gatelessConnectionRemainsGateless() {
        NBTTagCompound encoded = document();
        assertFalse(firstComplex(encoded).getTagList("Connections", 10).getCompoundTagAt(1).hasKey("GateRef"));
        KOMESiegeConnection decoded = decode(encoded).findComplex("WEST").getConnections().get(1);
        assertFalse(decoded.isGated());
        assertFalse(decoded.getGateRef().isPresent());
    }

    @Test public void gatedConnectionPreservesExactLogicalIdsWithoutAccountingOrPhysicalState() {
        NBTTagCompound encoded = document();
        NBTTagCompound gate = firstConnection(encoded).getCompoundTag("GateRef");
        assertEquals(2, gate.func_150296_c().size());
        assertEquals("b2", gate.getString("BuildId")); // First canonical complex row is EAST.
        assertEquals("gLower", gate.getString("DefensiveGateRecordId"));
        KOMESiegeConnection connection = decode(encoded).findComplex("EAST").getConnections().get(0);
        assertTrue(connection.isGated());
        assertEquals(new KOMEDefensiveGateRef("b2", "gLower"), connection.getGateRef().get());
    }

    @Test public void multipleAssignmentsAndUnassignedBuildsRoundTrip() {
        KOMETacticalConfiguration decoded = roundTrip(populated(false));
        assertEquals("WEST", decoded.findAssignedComplexId("B1").get());
        assertEquals("EAST", decoded.findAssignedComplexId("B2").get());
        assertEquals(Arrays.asList("B1", "B3"), decoded.listAssignedBuildIds("WEST"));
        assertFalse(decoded.findAssignedComplexId("UNASSIGNED").isPresent());
    }

    @Test public void encodedAssignmentIdentityUsesTheExistingRootLocaleLookupConvention() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            NBTTagCompound encoded = document();
            NBTTagCompound assignment = new NBTTagCompound();
            assignment.setString("BuildId", " i-build ");
            assignment.setString("ComplexId", " west ");
            encoded.setTag("DefensiveBuildAssignments", rows(assignment));
            KOMETacticalConfiguration decoded = decode(encoded);
            assertEquals(Collections.singletonMap("I-BUILD", "WEST"), decoded.getBuildAssignmentsByBuildId());
            assertEquals(KOMETacticalIds.buildLookup(" i-build "), decoded.listAssignedBuildIds("WEST").get(0));
        } finally { Locale.setDefault(previous); }
    }

    @Test public void authoritativeRowOrderingIsIndependentOfStoreInsertionOrder() throws Exception {
        NBTTagCompound first = encode(populated(false));
        NBTTagCompound second = encode(populated(true));
        assertEquals(first, second);
        assertArrayEquals(CompressedStreamTools.compress(first), CompressedStreamTools.compress(second));
        assertEquals(Arrays.asList("EAST", "WEST"), ids(first, "SiegeComplexes", "ComplexId"));
        assertEquals(Arrays.asList("FIELD", "SECOND_FIELD"), ids(first, "ForceDeploymentAreas", "AreaId"));
        assertEquals(Arrays.asList("B1", "B2", "B3"), ids(first, "DefensiveBuildAssignments", "BuildId"));
    }

    @Test public void nestedListOrderAndDuplicateLocalDefinitionsAreNotSilentlyAltered() {
        KOMEPolygonPrism first = prism(40);
        KOMEPolygonPrism second = prism(0);
        KOMETacticalConfiguration source = new KOMETacticalConfiguration();
        source.addComplex(new KOMESiegeComplex("WEST", "T277", 0, 0, Arrays.asList(
            new KOMENormalSegment("z", "first", first), new KOMENormalSegment("a", "", second),
            new KOMENormalSegment("z", "duplicate", second)), Collections.<KOMEWallZone>emptyList(),
            Collections.<KOMETransitionZone>emptyList(), null, Collections.<KOMESiegeConnection>emptyList()));
        KOMESiegeComplex decoded = roundTrip(source).findComplex("WEST");
        assertEquals(source.findComplex("WEST").getNormalSegments(), decoded.getNormalSegments());
        assertEquals(first, decoded.findNormalSegment("z").getPrism());
        assertFalse(new KOMESiegeComplexValidator().validate(decoded).isValid());
    }

    @Test public void missingWrongOrUnsupportedSchemaIsRejected() {
        assertRejected(tag -> tag.removeTag("SchemaVersion"), "SchemaVersion");
        assertRejected(tag -> tag.setLong("SchemaVersion", 1L), "SchemaVersion");
        for (int version : new int[]{-1, 0, 2, Integer.MAX_VALUE}) {
            assertRejected(tag -> tag.setInteger("SchemaVersion", version), "SchemaVersion");
        }
        assertThrows(IllegalArgumentException.class, () -> decode(null));
        assertThrows(IllegalArgumentException.class, () -> decode(new NBTTagCompound()));
    }

    @Test public void allMandatorySectionAndDefinitionFieldsRequireExactNbtTypes() {
        requiredFields(tag -> tag, "SchemaVersion", "Revision", "SiegeComplexes", "ForceDeploymentAreas", "DefensiveBuildAssignments");
        requiredFields(KOMETacticalConfigurationCodecTest::firstComplex, "ComplexId", "TileId", "DimensionId", "Revision",
            "NormalSegments", "WallZones", "TransitionZones", "Connections");
        requiredFields(KOMETacticalConfigurationCodecTest::firstArea, "AreaId", "TileId", "DimensionId", "Label", "Revision", "Geometry");
        requiredFields(tag -> tag.getTagList("DefensiveBuildAssignments", 10).getCompoundTagAt(0), "BuildId", "ComplexId");
    }

    @Test public void nestedGeometryZoneAndWallAccessFieldsRequireExactNbtTypes() {
        for (String group : Arrays.asList("NormalSegments", "WallZones", "TransitionZones")) {
            requiredFields(tag -> firstComplex(tag).getTagList(group, 10).getCompoundTagAt(0), "Id", "Label", "Geometry");
        }
        requiredFields(tag -> firstArea(tag).getCompoundTag("Geometry"), "Vertices", "MinYInclusive", "MaxYExclusive");
        requiredFields(tag -> firstArea(tag).getCompoundTag("Geometry").getTagList("Vertices", 10).getCompoundTagAt(0), "X", "Z");
        requiredFields(tag -> firstComplex(tag).getTagList("WallZones", 10).getCompoundTagAt(0), "AccessibleFromNormalSegmentIds");
        requiredFields(tag -> firstComplex(tag).getTagList("WallZones", 10).getCompoundTagAt(0)
            .getTagList("AccessibleFromNormalSegmentIds", 10).getCompoundTagAt(0), "NormalSegmentId");
    }

    @Test public void numericCoercionCannotHideWrongGeometryAndDimensionTagWidths() {
        assertRejected(tag -> firstArea(tag).setLong("DimensionId", -1L), "DimensionId");
        assertRejected(tag -> firstComplex(tag).setByte("DimensionId", (byte) -1), "DimensionId");
        assertRejected(tag -> firstArea(tag).getCompoundTag("Geometry").setLong("MinYInclusive", -10L), "MinYInclusive");
        assertRejected(tag -> firstArea(tag).getCompoundTag("Geometry").getTagList("Vertices", 10)
            .getCompoundTagAt(0).setDouble("X", -20.0), "Vertices[0].X");
    }

    @Test public void duplicateEncodedWallAccessSourcesCannotBeSilentlyCollapsedIntoASet() {
        assertRejected(tag -> {
            NBTTagList access = firstComplex(tag).getTagList("WallZones", 10).getCompoundTagAt(0)
                .getTagList("AccessibleFromNormalSegmentIds", 10);
            NBTTagCompound duplicate = copy(access.getCompoundTagAt(0));
            duplicate.setString("NormalSegmentId", " " + duplicate.getString("NormalSegmentId") + " ");
            access.appendTag(duplicate);
        }, "duplicate wall access source");
    }

    @Test public void wrongListElementTypesAndWrongTypedEmptyListsAreRejected() {
        for (String key : Arrays.asList("SiegeComplexes", "ForceDeploymentAreas", "DefensiveBuildAssignments")) {
            assertRejected(tag -> {
                NBTTagList wrong = new NBTTagList(); wrong.appendTag(new NBTTagString("wrong")); tag.setTag(key, wrong);
            }, key);
            assertRejected(tag -> {
                NBTTagList wrong = new NBTTagList(); wrong.appendTag(new NBTTagString("wrong")); wrong.removeTag(0); tag.setTag(key, wrong);
            }, key);
        }
        assertRejected(tag -> firstArea(tag).getCompoundTag("Geometry").setTag("Vertices", rows(new NBTTagCompound())), "Vertices[0].X");
    }

    @Test public void inconsistentDeclaredAndActualListTypesAreRejectedConservatively() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            NBTTagCompound source = document();
            NBTTagList list = source.getTagList("SiegeComplexes", 10);
            if (mode == 0) {
                Field field = listField(List.class); field.setAccessible(true);
                @SuppressWarnings("unchecked") List<NBTBase> actual = (List<NBTBase>) field.get(list);
                actual.set(0, new NBTTagString("wrong actual record"));
            } else {
                Field field = listField(byte.class); field.setAccessible(true); field.setByte(list, (byte) (mode == 1 ? 8 : 0));
            }
            assertThrows(IllegalArgumentException.class, () -> decode(source));
        }
    }

    @Test public void emptyCompoundTypedListsAreAccepted() throws Exception {
        NBTTagCompound source = encode(new KOMETacticalConfiguration());
        Field field = listField(byte.class); field.setAccessible(true);
        for (String key : Arrays.asList("SiegeComplexes", "ForceDeploymentAreas", "DefensiveBuildAssignments")) {
            field.setByte(source.getTag(key), (byte) 10);
        }
        assertTrue(decode(source).getComplexesById().isEmpty());
    }

    @Test public void duplicateCanonicalComplexIdsAreRejected() {
        assertRejected(tag -> {
            NBTTagCompound duplicate = copy(firstComplex(tag)); duplicate.setString("ComplexId", " east ");
            tag.getTagList("SiegeComplexes", 10).appendTag(duplicate);
        }, "duplicate canonical ComplexId EAST");
    }

    @Test public void duplicateCanonicalAreaIdsAreRejected() {
        assertRejected(tag -> {
            NBTTagCompound duplicate = copy(firstArea(tag)); duplicate.setString("AreaId", " field ");
            tag.getTagList("ForceDeploymentAreas", 10).appendTag(duplicate);
        }, "duplicate canonical AreaId FIELD");
    }

    @Test public void duplicateCanonicalBuildAssignmentsAreRejectedEvenForTheSameTarget() {
        assertRejected(tag -> {
            NBTTagCompound duplicate = copy(tag.getTagList("DefensiveBuildAssignments", 10).getCompoundTagAt(0));
            duplicate.setString("BuildId", " b1 "); tag.getTagList("DefensiveBuildAssignments", 10).appendTag(duplicate);
        }, "duplicate canonical BuildId B1");
    }

    @Test public void blankAuthoritativeIdsAndExplicitBlankPreferredReferenceAreRejected() {
        for (String blank : Arrays.asList("", " \t ")) {
            assertRejected(tag -> firstComplex(tag).setString("ComplexId", blank), "ComplexId");
            assertRejected(tag -> firstArea(tag).setString("AreaId", blank), "AreaId");
            assertRejected(tag -> tag.getTagList("DefensiveBuildAssignments", 10).getCompoundTagAt(0).setString("BuildId", blank), "BuildId");
            assertRejected(tag -> tag.getTagList("DefensiveBuildAssignments", 10).getCompoundTagAt(0).setString("ComplexId", blank), "ComplexId");
            assertRejected(tag -> firstComplex(tag).setString("PreferredForceDeploymentAreaId", blank), "PreferredForceDeploymentAreaId");
        }
    }

    @Test public void assignmentToAbsentComplexIsRejectedWithoutWorldResolution() {
        assertRejected(tag -> tag.getTagList("DefensiveBuildAssignments", 10).getCompoundTagAt(0)
            .setString("ComplexId", "absent"), "assignment targets absent complex ABSENT");
        NBTTagCompound tag = document();
        tag.getTagList("DefensiveBuildAssignments", 10).getCompoundTagAt(0).setString("BuildId", "not-a-world-build");
        assertEquals("WEST", decode(tag).findAssignedComplexId("not-a-world-build").get());
    }

    @Test public void unknownEndpointTypeAndDeploymentIdentityOnExteriorAreRejected() {
        for (String type : Arrays.asList("FIELD", "exterior", "", "DEPLOYMENT_AREA")) {
            assertRejected(tag -> firstConnection(tag).getCompoundTag("EndpointA").setString("Type", type), "unknown endpoint type");
        }
        for (String key : Arrays.asList("NormalSegmentId", "AreaId", "ForceDeploymentAreaId")) {
            assertRejected(tag -> firstConnection(tag).getCompoundTag("EndpointA").setString(key, "FIELD"), "unexpected endpoint field");
        }
    }

    @Test public void malformedConnectionsCannotBecomeGatelessOrLoseEndpointData() {
        requiredFields(KOMETacticalConfigurationCodecTest::firstConnection, "ConnectionId", "EndpointA", "EndpointB", "TransitionZoneId");
        requiredFields(tag -> firstConnection(tag).getCompoundTag("EndpointA"), "Type");
        requiredFields(tag -> firstConnection(tag).getCompoundTag("EndpointB"), "Type", "NormalSegmentId");
        requiredFields(tag -> firstConnection(tag).getCompoundTag("GateRef"), "BuildId", "DefensiveGateRecordId");
        assertRejected(tag -> firstConnection(tag).setString("GateRef", "broken"), "GateRef");
        assertRejected(tag -> firstComplex(tag).setInteger("PreferredForceDeploymentAreaId", 5), "PreferredForceDeploymentAreaId");
    }

    @Test public void allRevisionMetadataMustBeNonnegativeExactLongs() {
        for (Function<NBTTagCompound, NBTTagCompound> selector : Arrays.<Function<NBTTagCompound, NBTTagCompound>>asList(
                tag -> tag, KOMETacticalConfigurationCodecTest::firstComplex, KOMETacticalConfigurationCodecTest::firstArea)) {
            assertRejected(tag -> selector.apply(tag).setLong("Revision", -1L), "Revision");
            assertRejected(tag -> selector.apply(tag).setInteger("Revision", 0), "Revision");
        }
    }

    @Test public void invalidObjectRevisionMetadataIsAlsoRejectedOnEncode() {
        KOMETacticalConfiguration source = new KOMETacticalConfiguration();
        source.addComplex(new KOMESiegeComplex("WEST", "T277", 0, -1, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> encode(source));
        source.removeComplex("WEST");
        source.addForceDeploymentArea(area("FIELD", 0, -1));
        assertThrows(IllegalArgumentException.class, () -> encode(source));
    }

    @Test public void structurallyRepresentableIncompleteConnectionsAndEmptyGeometrySurvive() {
        KOMETacticalConfiguration source = new KOMETacticalConfiguration();
        KOMESiegeConnection incomplete = new KOMESiegeConnection("", KOMESiegeAreaRef.exterior(), KOMESiegeAreaRef.normal(""),
            "", new KOMEDefensiveGateRef("", ""));
        source.addComplex(new KOMESiegeComplex("WEST", "", 0, 0, Collections.<KOMENormalSegment>emptyList(),
            Collections.<KOMEWallZone>emptyList(), Collections.<KOMETransitionZone>emptyList(), "MISSING", Collections.singletonList(incomplete)));
        KOMESiegeComplex decoded = roundTrip(source).findComplex("WEST");
        assertTrue(decoded.getNormalSegments().isEmpty());
        assertEquals("MISSING", decoded.getPreferredForceDeploymentAreaId().get());
        assertEquals(Collections.singletonList(incomplete), decoded.getConnections());
        assertFalse(new KOMESiegeComplexValidator().validate(decoded).isValid());
    }

    @Test public void malformedAuthoredPolygonsAndYRangesSurviveAndRemainValidatorConcerns() {
        for (KOMEPolygon polygon : Arrays.asList(new KOMEPolygon(Collections.<KOMEXZPoint>emptyList()),
                KOMEPolygon.of(new KOMEXZPoint(0,0), new KOMEXZPoint(10,10), new KOMEXZPoint(0,10), new KOMEXZPoint(10,0)),
                KOMEPolygon.of(new KOMEXZPoint(0,0), new KOMEXZPoint(0,0), new KOMEXZPoint(1,0)))) {
            KOMEPolygonPrism bad = new KOMEPolygonPrism(polygon, 20, 10);
            KOMETacticalConfiguration source = new KOMETacticalConfiguration();
            source.addForceDeploymentArea(new KOMEForceDeploymentArea("FIELD", "T277", 0, "Malformed", bad, 0));
            source.addComplex(new KOMESiegeComplex("WEST", "T277", 0, 0,
                Collections.singletonList(new KOMENormalSegment("", "Unfinished", bad)), null, null, "FIELD", null));
            KOMETacticalConfiguration decoded = roundTrip(source);
            assertEquals(bad, decoded.findForceDeploymentArea("FIELD").getPrism());
            assertEquals(bad, decoded.findComplex("WEST").getNormalSegments().get(0).getPrism());
            assertTrue(new KOMEForceDeploymentAreaValidator().validate(decoded.findForceDeploymentArea("FIELD"))
                .hasCode(KOMEValidationCode.PRISM_MALFORMED_Y));
            assertFalse(new KOMESiegeComplexValidator().validate(decoded.findComplex("WEST")).isValid());
            assertEquals(encode(source), encode(decoded));
        }
    }

    @Test public void reconstructionEnforcesKnownPreferredAreaOwnershipWithoutNullingIt() {
        assertRejected(tag -> firstArea(tag).setString("TileId", "T278"), "must belong to");
        assertRejected(tag -> firstArea(tag).setInteger("DimensionId", 9), "dimension must match");
    }

    @Test public void lateDecodeFailureProducesNoResultAndDoesNotChangeInputOrOtherStores() {
        KOMETacticalConfiguration existing = populated(false);
        NBTTagCompound before = encode(existing);
        NBTTagCompound input = copy(before);
        NBTTagCompound bad = new NBTTagCompound(); bad.setString("BuildId", "B4"); bad.setString("ComplexId", "ABSENT");
        input.getTagList("DefensiveBuildAssignments", 10).appendTag(bad);
        NBTTagCompound inputBefore = copy(input);
        AtomicReference<KOMETacticalConfiguration> published = new AtomicReference<KOMETacticalConfiguration>();
        assertThrows(IllegalArgumentException.class, () -> published.set(decode(input)));
        assertNull(published.get());
        assertEquals(inputBefore, input);
        assertEquals(before, encode(existing));
        assertComplexEquals(existing.findComplex("WEST"), decode(before).findComplex("WEST"));
    }

    @Test public void decodedStoreAndEncodedNbtAreDetachedFromTheirInputs() {
        KOMETacticalConfiguration source = populated(false);
        NBTTagCompound encoded = encode(source);
        KOMETacticalConfiguration decoded = decode(encoded);
        NBTTagCompound expected = encode(decoded);
        firstArea(encoded).getCompoundTag("Geometry").getTagList("Vertices", 10).getCompoundTagAt(0).setInteger("X", 12345);
        firstComplex(encoded).setString("ComplexId", "CHANGED");
        source.unassignBuild("B1");
        source.replaceForceDeploymentArea(area("FIELD", -1, 12));
        assertEquals(expected, encode(decoded));
        decoded.unassignBuild("B2");
        assertTrue(source.findAssignedComplexId("B2").isPresent());
    }

    @Test public void binaryNbtRoundTripAndEncodeDecodeEncodeAreDeterministic() throws Exception {
        NBTTagCompound first = document();
        byte[] bytes = CompressedStreamTools.compress(first);
        NBTTagCompound binary = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes));
        NBTTagCompound second = encode(decode(binary));
        assertEquals(first, second);
        assertArrayEquals(bytes, CompressedStreamTools.compress(second));
    }

    @Test public void reconstructionFactoryRejectsInvariantViolationsWithoutAliasingAuthorities() {
        Map<String, String> assignments = new TreeMap<String, String>();
        assignments.put(" b1 ", " west ");
        List<KOMESiegeComplex> complexes = new ArrayList<KOMESiegeComplex>();
        complexes.add(emptyComplex("WEST", null, 0));
        KOMETacticalConfiguration result = KOMETacticalConfiguration.reconstruct(100, complexes,
            Collections.<KOMEForceDeploymentArea>emptyList(), assignments);
        assignments.clear(); complexes.clear();
        assertEquals(Collections.singletonMap("B1", "WEST"), result.getBuildAssignmentsByBuildId());
        assertNotNull(result.findComplex("WEST"));
        assignments.put("B1", "WEST"); assignments.put(" b1 ", "WEST");
        assertThrows(IllegalArgumentException.class, () -> KOMETacticalConfiguration.reconstruct(0,
            Collections.singletonList(emptyComplex("WEST", null, 0)), Collections.<KOMEForceDeploymentArea>emptyList(), assignments));
    }

    private static NBTTagCompound encode(KOMETacticalConfiguration source) { return KOMETacticalConfigurationCodec.encode(source); }
    private static KOMETacticalConfiguration decode(NBTTagCompound source) { return KOMETacticalConfigurationCodec.decode(source); }
    private static KOMETacticalConfiguration roundTrip(KOMETacticalConfiguration source) { return decode(encode(source)); }
    private static NBTTagCompound document() { return encode(populated(false)); }
    private static NBTTagCompound copy(NBTTagCompound source) { return (NBTTagCompound) source.copy(); }
    private static NBTTagCompound firstComplex(NBTTagCompound source) { return source.getTagList("SiegeComplexes", 10).getCompoundTagAt(0); }
    private static NBTTagCompound firstArea(NBTTagCompound source) { return source.getTagList("ForceDeploymentAreas", 10).getCompoundTagAt(0); }
    private static NBTTagCompound firstConnection(NBTTagCompound source) { return firstComplex(source).getTagList("Connections", 10).getCompoundTagAt(0); }

    private static KOMETacticalConfiguration populated(boolean reverse) {
        KOMETacticalConfiguration source = new KOMETacticalConfiguration();
        List<KOMESiegeComplex> complexes = new ArrayList<KOMESiegeComplex>(Arrays.asList(
            complex("WEST", "inside", "b1", 7), complex("EAST", "OTHER", "b2", 19)));
        List<KOMEForceDeploymentArea> areas = new ArrayList<KOMEForceDeploymentArea>(Arrays.asList(area("FIELD", -1, 11), area("SECOND_FIELD", -1, 13)));
        List<String> builds = new ArrayList<String>(Arrays.asList("B1", "B2", "B3"));
        if (reverse) { Collections.reverse(complexes); Collections.reverse(areas); Collections.reverse(builds); }
        for (KOMESiegeComplex complex : complexes) source.addComplex(complex);
        for (KOMEForceDeploymentArea area : areas) source.addForceDeploymentArea(area);
        for (String build : builds) source.assignBuild(build, "B2".equals(build) ? "EAST" : "WEST");
        return source;
    }

    private static KOMESiegeComplex emptyComplex(String id, String preferred, int dimension) {
        return new KOMESiegeComplex(id, "T277", dimension, 0, null, null, null, preferred, null);
    }

    private static KOMESiegeComplex complex(String id, String inner, String build, long revision) {
        return new KOMESiegeComplex(id, "T277", -1, revision,
            Arrays.asList(new KOMENormalSegment("a", "Outer", prism(0)), new KOMENormalSegment(inner, "Inner", prism(20))),
            Collections.singletonList(new KOMEWallZone("wallLower", "Wall", prism(40), Arrays.asList(inner, "a"))),
            Arrays.asList(new KOMETransitionZone("gateLower", "Gate", prism(60)), new KOMETransitionZone("linkLower", "Link", prism(80))),
            "FIELD", Arrays.asList(KOMESiegeConnection.gated("entryLower", KOMESiegeAreaRef.exterior(),
                KOMESiegeAreaRef.normal("a"), "gateLower", new KOMEDefensiveGateRef(build, "gLower")),
                KOMESiegeConnection.gateLess("linkLower", KOMESiegeAreaRef.normal("a"), KOMESiegeAreaRef.normal(inner), "linkLower")));
    }

    private static KOMEForceDeploymentArea area(String id, int dimension, long revision) {
        return new KOMEForceDeploymentArea(id, "T277", dimension, "Shared staging label", prism(-20), revision);
    }

    private static KOMEPolygonPrism prism(int offset) {
        return new KOMEPolygonPrism(KOMEPolygon.of(new KOMEXZPoint(offset, 0), new KOMEXZPoint(offset + 10, 0),
            new KOMEXZPoint(offset + 10, 10), new KOMEXZPoint(offset, 10)), -10, 100);
    }

    private static void assertComplexEquals(KOMESiegeComplex before, KOMESiegeComplex after) {
        assertEquals(before.getComplexId(), after.getComplexId()); assertEquals(before.getTileId(), after.getTileId());
        assertEquals(before.getDimensionId(), after.getDimensionId()); assertEquals(before.getRevision(), after.getRevision());
        assertEquals(before.getPreferredForceDeploymentAreaId(), after.getPreferredForceDeploymentAreaId());
        assertEquals(before.getNormalSegments(), after.getNormalSegments()); assertEquals(before.getWallZones(), after.getWallZones());
        assertEquals(before.getTransitionZones(), after.getTransitionZones()); assertEquals(before.getConnections(), after.getConnections());
    }

    private static void assertAreaEquals(KOMEForceDeploymentArea before, KOMEForceDeploymentArea after) {
        assertEquals(before.getAreaId(), after.getAreaId()); assertEquals(before.getTileId(), after.getTileId());
        assertEquals(before.getDimensionId(), after.getDimensionId()); assertEquals(before.getLabel(), after.getLabel());
        assertEquals(before.getRevision(), after.getRevision()); assertEquals(before.getPrism(), after.getPrism());
    }

    private static NBTTagList rows(NBTTagCompound... rows) {
        NBTTagList result = new NBTTagList(); for (NBTTagCompound row : rows) result.appendTag(row); return result;
    }

    private static List<String> ids(NBTTagCompound source, String group, String key) {
        List<String> ids = new ArrayList<String>(); NBTTagList rows = source.getTagList(group, 10);
        for (int i = 0; i < rows.tagCount(); i++) ids.add(rows.getCompoundTagAt(i).getString(key));
        return ids;
    }

    private static void requiredFields(Function<NBTTagCompound, NBTTagCompound> selector, String... keys) {
        for (String key : keys) {
            assertRejected(tag -> selector.apply(tag).removeTag(key), key);
            assertRejected(tag -> {
                NBTTagCompound row = selector.apply(tag);
                if (row.getTag(key).getId() == 8) row.setInteger(key, 0); else row.setString(key, "wrong type");
            }, key);
        }
    }

    private static void assertRejected(Consumer<NBTTagCompound> mutation, String messagePart) {
        NBTTagCompound source = document(); mutation.accept(source);
        NBTTagCompound before = copy(source);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> decode(source));
        assertTrue(failure.getMessage(), failure.getMessage().contains(messagePart));
        assertEquals(before, source);
    }

    private static Field listField(Class<?> type) {
        for (Field field : NBTTagList.class.getDeclaredFields()) if (field.getType() == type) return field;
        throw new AssertionError("Missing NBTTagList field of type " + type);
    }
}
