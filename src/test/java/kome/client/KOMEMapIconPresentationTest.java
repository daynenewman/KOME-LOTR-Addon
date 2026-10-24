package kome.client;

import java.io.InputStream;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEMapIconPresentationTest {
    @Test public void geographyAssetsHaveStableHigherResolutionEditableCanvases()throws Exception {
        for(String name:new String[]{"bridge","river"})try(InputStream in=getClass().getResourceAsStream("/assets/kome/textures/gui/"+name+".png")) {
            assertNotNull(in);java.awt.image.BufferedImage art=javax.imageio.ImageIO.read(in);
            assertEquals(name,64,art.getWidth());assertEquals(name,32,art.getHeight());assertTrue(art.getColorModel().hasAlpha());
        }
    }
    @Test public void bridgeResourceUsesTheWholeEditableCanvasWithoutDynamicCropping()throws Exception {
        java.lang.reflect.Method getter=KOMEConquestMapOverlay.class.getDeclaredMethod("getBridgeMarkerTextureLocation");getter.setAccessible(true);
        assertEquals(new net.minecraft.util.ResourceLocation("kome:textures/gui/bridge.png"),getter.invoke(null));
    }
    @Test public void automaticBridgePassDrawsOnlyTheWoodenSprite()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}
        boolean found=false,sprite=false;
        for(MethodNode m:node.methods)if(m.name.equals("drawAutomaticBridgeDebugMarkers")||m.name.equals("drawAutomaticBridgeMarkers")) {
            found=true;
            for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode) {
                String name=((MethodInsnNode)step).name;
                assertFalse("Automatic bridges must not draw the obsolete badge",name.equals("drawMarkerShapes")||name.equals("bridgeHalo")||name.equals("drawDiamond"));
                if(name.equals("drawGeographyTexture"))sprite=true;
            }
        }
        assertTrue(found);assertTrue(sprite);
    }
    @Test public void geographyDimensionsFollowExactZoomWithoutRoundingOrMinimumSize() {
        for(double zoom:new double[]{.0625,.125,.25,.5,.73,1,2,16})for(int[] size:new int[][]{{6,3},{4,2}}) {
            KOMEMapIconPresentation.MapBounds b=KOMEMapIconPresentation.geography(zoom,100,200,size[0],size[1]);
            assertEquals(size[0]*zoom,b.width,1E-12);assertEquals(size[1]*zoom,b.height,1E-12);
            assertEquals(100,b.x+(size[0]/2)*zoom,1E-12);assertEquals(200,b.y+(size[1]/2)*zoom,1E-12);
            assertEquals(KOMEMapIconPresentation.at(zoom).alpha,b.alpha);
        }
    }
    @Test public void geographyArtPreservesTheEditableRgbaCanvasContract()throws Exception {
        for(String name:new String[]{"bridge","river"})try(InputStream in=getClass().getResourceAsStream("/assets/kome/textures/gui/"+name+".png")) {
            assertNotNull(name,in);java.awt.image.BufferedImage art=javax.imageio.ImageIO.read(in);
            assertEquals(64,art.getWidth());assertEquals(32,art.getHeight());
            int opaque=0,transparent=0;
            for(int y=0;y<art.getHeight();y++)for(int x=0;x<art.getWidth();x++) {
                int a=art.getRGB(x,y)>>>24;
                if(a==0)transparent++;else opaque++;
            }
            assertTrue(opaque>0);assertTrue(transparent>0);
        }
    }
    @Test public void geographyPickingAndClippingUseFractionalDrawBoundsAndRejectInvalidZoom() {
        KOMEMapViewport view=new KOMEMapViewport(0,0,800,600,0,0,1);
        for(double zoom:new double[]{.125,.25,.5,.73,1,2,16})for(int[] size:new int[][]{{6,3},{4,2}}) {
            KOMEMapIconPresentation.MapBounds b=KOMEMapIconPresentation.geography(zoom,400,300,size[0],size[1]);
            assertTrue(b.visible(view));assertTrue(b.contains(400,300));assertTrue(b.contains(b.x,b.y));
            assertFalse(b.contains(b.x-.001,b.y));assertFalse(b.contains(b.x+b.width,b.y));assertFalse(b.contains(b.x,b.y+b.height));
            assertFalse(KOMEMapIconPresentation.geography(zoom,0,300,size[0],size[1]).visible(view));
            assertFalse(KOMEMapIconPresentation.geography(zoom,800,300,size[0],size[1]).visible(view));
        }
        for(double zoom:new double[]{0,-1,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY}) {
            KOMEMapIconPresentation.MapBounds b=KOMEMapIconPresentation.geography(zoom,400,300,8,5);
            assertEquals(0,b.width,0);assertEquals(0,b.height,0);assertEquals(0,b.alpha);
            assertFalse(b.contains(400,300));assertFalse(b.visible(view));
        }
    }
    @Test public void bridgePixelArtUsesResourceManagerWithoutCroppingOrAlphaRewriting()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}
        for(MethodNode m:node.methods)if(m.name.equals("getBridgeMarkerTextureLocation")) {
            boolean art=false;
            for(AbstractInsnNode step:m.instructions.toArray()) {
                assertFalse("No cropping, alpha rewriting or dynamic texture upload",step instanceof MethodInsnNode);
                if(step instanceof FieldInsnNode&&((FieldInsnNode)step).name.equals("BRIDGE_MARKER_ART"))art=true;
            }
            assertTrue(art);return;
        }
        fail("Missing bridge art getter");
    }
    @Test public void geographyRenderersUseExactPolicyWhileStrategicMarkersKeepTheirPolicy()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}
        for(String name:new String[]{"drawRouteEdgeMarkers","drawAutomaticBridgeMarkers","drawAutomaticRiverBlockMarkers","drawRouteSpecialEdgeMarkers"}) {
            boolean geography=false;
            for(MethodNode m:node.methods)if(m.name.equals(name))for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode) {
                MethodInsnNode c=(MethodInsnNode)step;
                assertFalse(name+" must not use the strategic minimum size",c.name.equals("drawStrategicTexture"));
                if(c.name.equals("drawGeographyTexture")||c.name.equals("geography"))geography=true;
                if(name.equals("drawAutomaticRiverBlockMarkers"))assertFalse("No old dominant diamond/R art",c.name.equals("drawMarkerShapes")||c.name.equals("drawString"));
            }
            assertTrue(name,geography);
        }
        for(String name:new String[]{"drawTroopMarkers","renderCompanyMarkers","renderBuildMarkers","buildMarkerAt","drawStrategicTexture"}) {
            boolean strategic=false;
            for(MethodNode m:node.methods)if(m.name.equals(name))for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode) {
                MethodInsnNode c=(MethodInsnNode)step;
                assertFalse(c.name.equals("geography")||c.name.equals("drawGeographyTexture"));
                if(c.name.equals("markerPresentation"))strategic=true;
            }
            assertTrue(name,strategic);
        }
        boolean fractional=false;
        for(MethodNode m:node.methods) {
            if(m.name.equals("drawTexturedMarker")&&m.desc.contains(";DDDDF"))fractional=true;
        }
        assertTrue("Fractional dimensions reach the vertex renderer",fractional);
    }
    @Test public void allBridgePassesUseTheSameReplacementArtWithoutDebugBadgeInfrastructure()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}
        for(FieldNode f:node.fields)assertFalse(f.name.equals("SHOW_AUTOMATIC_BRIDGE_DEBUG"));
        for(MethodNode m:node.methods)assertFalse(m.name.equals("drawAutomaticBridgeDebugMarkers"));
        for(String name:new String[]{"drawAutomaticBridgeMarkers","drawRouteEdgeMarkers","drawRouteSpecialEdgeMarkers"}) {
            int artCalls=0,textureCalls=0;
            for(MethodNode m:node.methods)if(m.name.equals(name))for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode) {
                String call=((MethodInsnNode)step).name;
                if(call.equals("getBridgeMarkerTextureLocation"))artCalls++;
                if(call.equals("drawGeographyTexture"))textureCalls++;
            }
            assertEquals(name,1,artCalls);assertEquals(name,1,textureCalls);
        }
        for(java.lang.reflect.Method method:KOMEMapIconPresentation.class.getDeclaredMethods())assertFalse(method.getName().equals("bridgeHalo"));
    }
    @Test public void automaticBridgeSpriteUsesOneSharedScopedTextureSubmissionWithoutHaloMatrix()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}
        int calls=0;
        for(MethodNode m:node.methods)if(m.name.equals("drawGeographyTexture")&&m.desc.contains("$MapBounds"))for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)step;
            assertFalse(call.name.equals("drawMarkerShapes")||call.owner.equals("net/minecraft/client/gui/Gui"));
            if(call.name.equals("drawTexturedMarker"))calls++;
        }
        assertEquals("One texture submission, with the existing tested GL cleanup scope",1,calls);
        for(MethodNode m:node.methods)if(m.name.equals("drawAutomaticBridgeMarkers"))for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode) {
            String call=((MethodInsnNode)step).name;assertFalse("Obsolete halo matrix is gone",call.equals("glPushMatrix")||call.equals("glScaled"));
        }
    }
    @Test public void zoomPolicyClampsSizeFadesAtFarZoomAndOnlyShowsNormalZoomLabels() {
        int lastAlpha=0;double lastScale=0;
        for(double zoom:new double[]{.0625,.125,.25,.5,1,2,16}) {
            KOMEMapIconPresentation p=KOMEMapIconPresentation.at(zoom);
            assertTrue(p.scale>=.65&&p.scale<=1);assertTrue(p.scale>=lastScale);assertTrue(p.alpha>=lastAlpha);
            assertEquals(zoom>=1,p.labels);lastAlpha=p.alpha;lastScale=p.scale;
        }
        assertEquals(64,KOMEMapIconPresentation.at(.125).alpha);assertEquals(128,KOMEMapIconPresentation.at(.25).alpha);
        assertEquals(191,KOMEMapIconPresentation.at(.5).alpha);assertEquals(255,KOMEMapIconPresentation.at(16).alpha);
        KOMEMapIconPresentation.Bounds far=KOMEMapIconPresentation.at(.125).bounds(100,100,11,18);
        assertEquals(7,far.width);assertEquals(12,far.height);
        assertEquals(5,KOMEMapIconPresentation.at(.125).bounds(100,100,7,7).width);
    }
    @Test public void boundsPreserveProjectedAnchorsAndHitOnlyTheActualVisibleFootprint() {
        for(double zoom:new double[]{.125,.2,.5,1,16})for(int[] size:new int[][]{{9,15},{11,18},{7,7},{8,5}}) {
            KOMEMapViewport view=new KOMEMapViewport(0,0,800,600,1200,1600,zoom);
            int x=(int)view.screenX(1200),y=(int)view.screenY(1600);
            KOMEMapIconPresentation.Bounds b=KOMEMapIconPresentation.at(zoom).bounds(x,y,size[0],size[1]);
            assertEquals(x,b.x+b.width/2);assertEquals(y,b.y+b.height/2);assertTrue(b.visible(view));
            assertTrue(b.contains(b.x,b.y));assertTrue(b.contains(b.x+b.width-1,b.y+b.height-1));
            assertFalse(b.contains(b.x-1,b.y));assertFalse(b.contains(b.x+b.width,b.y));assertFalse(b.contains(b.x,b.y+b.height));
            assertFalse(KOMEMapIconPresentation.at(zoom).bounds(0,0,size[0],size[1]).visible(view));
        }
    }
    @Test public void diamondPickingFadeAndInvalidZoomMatchVisibleArtwork() {
        KOMEMapIconPresentation p=KOMEMapIconPresentation.at(.125);KOMEMapIconPresentation.Bounds b=p.diamond(100,100,7);
        assertTrue(b.containsDiamond(100,100));assertTrue(b.containsDiamond(b.x,100));assertFalse(b.containsDiamond(b.x,b.y));
        assertEquals(64,p.color(0xFFFFFFFF)>>>24);assertEquals(55,p.color(0xDD001018)>>>24);assertEquals(0x001018,p.color(0xDD001018)&0xFFFFFF);
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            KOMEMapIconPresentation.Bounds hidden=KOMEMapIconPresentation.at(invalid).bounds(100,100,7,7);
            assertFalse(hidden.contains(100,100));assertFalse(hidden.visible(new KOMEMapViewport(0,0,800,600,0,0,1)));
        }
    }
    @Test public void allStrategicRendererPathsUseSharedPolicyAndExistingAnchors()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}
        for(String name:new String[]{"drawTroopMarkers","drawLiveUnitMarkers","renderCompanyMarkers","drawBuildMarkers","renderBuildMarkers","visibleBuildBounds","buildMarkerAt","drawRouteEdgeMarkers","drawAutomaticBridgeMarkers","drawAutomaticRiverBlockMarkers","drawRouteSpecialEdgeMarkers"}) {
            boolean policy=false,anchor=false;
            for(MethodNode m:node.methods)if(m.name.equals(name))for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode) {
                MethodInsnNode c=(MethodInsnNode)step;if(c.owner.equals("kome/client/KOMEMapIconPresentation")||c.name.equals("markerPresentation")||c.name.equals("drawStrategicTexture")||c.name.equals("drawGeographyTexture")||c.name.equals("buildMarkerBounds")||c.name.equals("visibleBuildBounds")||c.name.equals("renderBuildMarkers")||c.name.equals("renderCompanyMarkers")||c.name.equals("riverMarkerBounds"))policy=true;
                if(c.name.equals("worldScreenX")||c.name.equals("companyMarkerScreenPosition")||c.name.equals("tileAnchorScreenPosition")||c.name.equals("buildMarkerBounds")||c.name.equals("visibleBuildBounds")||c.name.equals("renderBuildMarkers")||c.name.equals("renderCompanyMarkers")||name.equals("drawRouteSpecialEdgeMarkers"))anchor=true;
            }
            assertTrue(name+" must use shared presentation",policy);assertTrue(name+" must preserve location projection",anchor);
        }
    }
    @Test public void bridgeBatchSavesColorBlendDepthAndEnableStateAndRestoresOnExceptionalExit()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}
        for(MethodNode m:node.methods)if(m.name.equals("drawMarkerShapes")) {
            boolean mask=false,blend=false,cleanup=false;
            int expected=org.lwjgl.opengl.GL11.GL_ENABLE_BIT|org.lwjgl.opengl.GL11.GL_CURRENT_BIT|org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT|org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
            for(AbstractInsnNode step:m.instructions.toArray()) {
                if(step instanceof LdcInsnNode&&Integer.valueOf(expected).equals(((LdcInsnNode)step).cst))mask=true;
                if(step instanceof IntInsnNode&&((IntInsnNode)step).operand==expected)mask=true;
                if(step instanceof MethodInsnNode&&((MethodInsnNode)step).name.equals("glBlendFunc"))blend=true;
            }
            for(TryCatchBlockNode block:m.tryCatchBlocks)if(block.type==null) {
                for(AbstractInsnNode step=block.handler;step!=null;step=step.getNext())
                    if(step instanceof MethodInsnNode&&((MethodInsnNode)step).name.equals("glPopAttrib"))cleanup=true;
            }
            assertTrue(mask);assertTrue(blend);assertTrue(cleanup);return;
        }
        fail("Missing scoped marker batch");
    }
    @Test public void buildFramesAndRiverRingsHaveSingleFadeContributionPerPixel() {
        KOMEMapIconPresentation p=KOMEMapIconPresentation.at(.125);int[][] hits=new int[30][30];
        KOMEMapIconPresentation.RectSink sink=(x,y,r,d,c)->{for(int yy=y;yy<d;yy++)for(int xx=x;xx<r;xx++){assertEquals(64,c>>>24);assertEquals(1,++hits[yy][xx]);}};
        KOMEMapIconPresentation.Bounds build=p.bounds(10,10,7,7);p.frame(build,0,0xFFFFFFFF,sink);p.frame(build,1,0xFFFFFFFF,sink);
        sink.rect(build.x+2,build.y+2,build.x+build.width-2,build.y+build.height-2,p.color(0xFFFFFFFF));
        p.diamondRing(22,22,5,4,0xFFFFFFFF,sink);p.diamondRing(22,22,4,2,0xFFFFFFFF,sink);p.diamondRing(22,22,2,-1,0xFFFFFFFF,sink);
        for(int y=build.y;y<build.y+build.height;y++)for(int x=build.x;x<build.x+build.width;x++)assertEquals(1,hits[y][x]);
    }
    @Test public void fadedTextureSubmissionRestoresGlStateEvenOnFailure()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}
        boolean found=false;
        int scopes=0;
        for(MethodNode method:node.methods)if((method.name.equals("drawTexturedMarker")&&method.desc.endsWith("FDDDD)V"))||method.name.equals("drawMarkerShapes")) {
            scopes++;
            found=true;assertFalse(method.tryCatchBlocks.isEmpty());boolean restores=false,noDepthWrites=false;
            for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode) {
                String name=((MethodInsnNode)step).name;if(name.equals("glPopAttrib"))restores=true;if(name.equals("glDepthMask"))noDepthWrites=true;
            }
            assertTrue(restores);assertTrue(noDepthWrites);
        }
        assertTrue(found);assertEquals(2,scopes);
    }
}
