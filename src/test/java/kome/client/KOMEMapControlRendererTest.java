package kome.client;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEMapControlRendererTest {
    @Test public void editablePictogramSheetExistsAsTransparentFixedGrid()throws Exception {
        File asset=new File("src/main/resources/assets/kome/textures/gui/map_button_icons.png");
        assertTrue("Owner-editable KOME pictogram sheet is missing",asset.isFile());
        BufferedImage sheet=ImageIO.read(asset);assertEquals(128,sheet.getWidth());assertEquals(32,sheet.getHeight());
        assertTrue(sheet.getColorModel().hasAlpha());
        boolean transparent=false;for(int pixel:sheet.getRGB(0,0,128,32,null,0,128))if((pixel>>>24)==0)transparent=true;
        assertTrue("Editable RGBA sheet must support transparent background",transparent);
        try(InputStream packaged=getClass().getResourceAsStream("/assets/kome/textures/gui/map_button_icons.png")) {
            assertNotNull("The sheet must be available on Minecraft's resource classpath",packaged);
            BufferedImage loaded=ImageIO.read(packaged);
            assertArrayEquals(sheet.getRGB(0,0,128,32,null,0,128),loaded.getRGB(0,0,128,32,null,0,128));
        }
    }
    @Test public void nativeAtlasFramesRemainBlankAndSpritesUseExactGridCells()throws Exception {
        assertEquals("lotr",KOMEMapControlRenderer.ATLAS.getResourceDomain());assertEquals("map/mapScreen.png",KOMEMapControlRenderer.ATLAS.getResourcePath());
        assertEquals("kome",KOMEMapControlRenderer.ICON_ATLAS.getResourceDomain());assertEquals("textures/gui/map_button_icons.png",KOMEMapControlRenderer.ICON_ATLAS.getResourcePath());
        BufferedImage sheet=ImageIO.read(new File("src/main/resources/assets/kome/textures/gui/map_button_icons.png"));
        try(ZipFile jar=new ZipFile("libs/LOTRMod v36.15.jar");InputStream in=jar.getInputStream(jar.getEntry("assets/lotr/map/mapScreen.png"))) {
            BufferedImage atlas=ImageIO.read(in);assertEquals(256,atlas.getWidth());
            for(boolean interactive:new boolean[]{true,false})for(int control=0;control<9;control++) {
                int[][] result=new int[10][10];
                KOMEMapControlRenderer.artwork(control,0,0,interactive,(x,y,w,h,u,v)->{for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++)result[y+yy][x+xx]=atlas.getRGB(u+xx,v+yy);});
                int background=atlas.getRGB(41,interactive?1:11);
                for(int y=0;y<10;y++)for(int x=0;x<10;x++)
                    assertEquals("Native blank frame at "+x+","+y,x==0||x==9||y==0||y==9?atlas.getRGB(40+x,(interactive?0:10)+y):background,result[y][x]);
            }
            BufferedImage preview=new BufferedImage(126,42,BufferedImage.TYPE_INT_ARGB);KOMETerritoryStyle style=new KOMETerritoryStyle();
            for(int state=0;state<3;state++)for(int control=0;control<9;control++) {
                int x=control*14,y=state*14;boolean interactive=state!=2;
                style.palette=state==1?KOMETerritoryStyle.Palette.CURRENT_KOME:KOMETerritoryStyle.Palette.CLASSIC_LOTR;
                style.mode=state==1?KOMETerritoryStyle.DisplayMode.BORDERS_ONLY:KOMETerritoryStyle.DisplayMode.FULL_COLOR;
                KOMEMapControlRenderer.artwork(control,x,y,interactive,(xx,yy,w,h,u,v)->{for(int row=0;row<h;row++)for(int col=0;col<w;col++)preview.setRGB(xx+col,yy+row,atlas.getRGB(u+col,v+row));});
                int selectedRow=KOMEMapControlRenderer.spriteRow(control,state==0,interactive,style);final int column=control;
                KOMEMapControlRenderer.pictogram(control,x,y,state==0,interactive,style,(xx,yy,w,h,u,v)->{
                    assertEquals(8,w);assertEquals(8,h);assertEquals(column*8,u);assertEquals(selectedRow*8,v);assertEquals(x+1,xx);assertEquals(y+1,yy);
                    assertTrue(u+w<=sheet.getWidth());assertTrue(v+h<=sheet.getHeight());
                    for(int row=0;row<h;row++)for(int col=0;col<w;col++)if((sheet.getRGB(u+col,v+row)>>>24)!=0)preview.setRGB(xx+col,yy+row,sheet.getRGB(u+col,v+row));
                });
            }
            java.nio.file.Path directory=java.nio.file.Paths.get("build/reports/map-controls");java.nio.file.Files.createDirectories(directory);
            BufferedImage enlarged=new BufferedImage(1008,336,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D graphics=enlarged.createGraphics();
            try{graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);graphics.drawImage(preview,0,0,1008,336,null);}finally{graphics.dispose();}
            ImageIO.write(enlarged,"png",directory.resolve("pictograms.png").toFile());
        }
    }
    @Test public void stateSelectionAndEditablePixelsCoverEveryButtonWithoutRuntimeTint()throws Exception {
        KOMETerritoryStyle style=new KOMETerritoryStyle();
        BufferedImage sheet=ImageIO.read(new File("src/main/resources/assets/kome/textures/gui/map_button_icons.png"));
        Set<String> symbols=new HashSet<>();
        for(int control=0;control<9;control++) {
            assertEquals(0,KOMEMapControlRenderer.spriteRow(control,true,true,style));
            assertEquals(2,KOMEMapControlRenderer.spriteRow(control,true,false,style));
            assertEquals(2,KOMEMapControlRenderer.spriteRow(control,false,false,style));
            int[] primary=sheet.getRGB(control*8,0,8,8,null,0,8),alternate=sheet.getRGB(control*8,8,8,8,null,0,8);
            assertTrue(symbols.add(Arrays.toString(primary)));if(control!=2)assertFalse(Arrays.equals(primary,alternate));
            // Owner artwork may use every pixel of an 8x8 cell; no particular corner must be empty.
            if(control>=3)assertEquals(1,KOMEMapControlRenderer.spriteRow(control,false,true,style));
        }
        style.palette=KOMETerritoryStyle.Palette.CURRENT_KOME;assertEquals(1,KOMEMapControlRenderer.spriteRow(0,true,true,style));
        style.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;assertEquals(1,KOMEMapControlRenderer.spriteRow(1,true,true,style));
        try(Reader reader=new FileReader("src/main/resources/assets/kome/textures/gui/map_button_icons.png.mcmeta")) {
            com.google.gson.JsonObject metadata=new com.google.gson.JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("texture");
            assertFalse(metadata.get("blur").getAsBoolean());assertTrue(metadata.get("clamp").getAsBoolean());
        }
    }
    @Test public void nativeTooltipAndStateScopesAreUsedAndRenderPathsNeverSavePreferences()throws Exception {
        ClassNode renderer=node(KOMEMapControlRenderer.class);boolean tooltip=false;
        for(MethodNode method:renderer.methods)if(method.name.equals("tooltip")) {
            boolean scope=false;for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode){String name=((MethodInsnNode)step).name;if(name.equals("drawHoveringText"))tooltip=true;if(name.equals("glPopAttrib"))scope=true;}assertTrue(scope);
        }
        assertTrue(tooltip);
        for(MethodNode method:renderer.methods)if(method.name.equals("draw")) {
            int submissions=0;for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)step;assertNotEquals("Pictogram pixels must not submit individually","drawRect",call.name);
                if(call.name.equals("draw")&&call.owner.equals("net/minecraft/client/renderer/Tessellator"))submissions++;
            }
            assertEquals("Only the rendering overload submits one native and one pictogram batch",
                method.desc.contains("KOMEMapPreferences$CompanyDisplay")?2:0,submissions);
        }
        for(Class<?> type:new Class<?>[]{KOMEMapControlRenderer.class,KOMEConquestMapOverlay.class})for(MethodNode method:node(type).methods)
            if(method.name.startsWith("draw")||method.name.equals("prepareTerritoryColors"))for(AbstractInsnNode step:method.instructions.toArray())
                if(step instanceof MethodInsnNode)assertFalse(((MethodInsnNode)step).name.equals("setMapPreferences"));
        boolean applies=false;for(MethodNode method:node(KOMEClientProxy.class).methods)if(method.name.equals("init"))for(AbstractInsnNode step:method.instructions.toArray())
            if(step instanceof MethodInsnNode&&((MethodInsnNode)step).name.equals("applyMapPreferences"))applies=true;
        assertTrue("Restored preferences are connected to client startup",applies);
        for(MethodNode method:node(KOMEConquestMapOverlay.class).methods)if(method.name.equals("drawToggleButtons")||method.name.equals("handleTerritoryControl")) {
            boolean covered=false;for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode&&((MethodInsnNode)step).name.equals("mapControlCovered"))covered=true;
            assertTrue("Tooltip and click picking share panel precedence",covered);
        }
    }
    @Test public void companyDescriptorModeUsesReservedFourthRowWithoutChangingLockedRow() {
        KOMETerritoryStyle style=new KOMETerritoryStyle();
        final int[] uv=new int[2];
        KOMEMapControlRenderer.pictogram(7,10,20,true,true,style,
            KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS,
            (x,y,w,h,u,v)->{uv[0]=u;uv[1]=v;assertEquals(11,x);assertEquals(21,y);});
        assertArrayEquals(new int[]{56,24},uv);
        assertEquals(2,KOMEMapControlRenderer.spriteRow(7,true,false,style,
            KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS));
        assertEquals(0,KOMEMapControlRenderer.spriteRow(7,true,true,style,
            KOMEMapPreferences.CompanyDisplay.SHIELDS_ONLY));
        assertEquals(1,KOMEMapControlRenderer.spriteRow(7,false,true,style,
            KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES));
    }
    @Test public void tooltipControlUsesPaintedTenthColumnAndNativeBounds()throws Exception {
        BufferedImage sheet=ImageIO.read(new File("src/main/resources/assets/kome/textures/gui/map_button_icons.png"));
        for(int row=0;row<4;row++){
            boolean painted=false;
            for(int y=row*8;y<row*8+8;y++)for(int x=72;x<80;x++)
                painted|=(sheet.getRGB(x,y)>>>24)!=0;
            assertTrue("Tooltip state row "+row+" must use owner-painted artwork",painted);
        }
        KOMEClientConfig.MapTooltipMode[] modes={KOMEClientConfig.MapTooltipMode.IMMEDIATE,
            KOMEClientConfig.MapTooltipMode.DELAYED,KOMEClientConfig.MapTooltipMode.OFF};
        for(int row=0;row<3;row++){
            final int expected=row*8;
            assertEquals(row,KOMEMapControlRenderer.tooltipSpriteRow(modes[row],true));
            KOMEMapControlRenderer.tooltipPictogram(10,20,modes[row],true,(x,y,w,h,u,v)->{
                assertEquals(11,x);assertEquals(21,y);assertEquals(8,w);assertEquals(8,h);
                assertEquals(72,u);assertEquals(expected,v);
            });
        }
        assertEquals(3,KOMEMapControlRenderer.tooltipSpriteRow(KOMEClientConfig.MapTooltipMode.OFF,false));
        KOMEMapControlRenderer.tooltipPictogram(10,20,KOMEClientConfig.MapTooltipMode.OFF,false,(x,y,w,h,u,v)->{
            assertEquals(72,u);assertEquals(24,v);
        });
        KOMEMapControlRenderer.artwork(9,10,20,true,(x,y,w,h,u,v)->{
            assertEquals(10,w);
            assertTrue(x>=10&&x+w<=20&&y>=20&&y+h<=30);
        });
    }
    @Test public void tooltipControlDrawsNoPlaceholderLetters()throws Exception {
        for(MethodNode method:node(KOMEConquestMapOverlay.class).methods)if(method.name.equals("drawToggleButtons"))
            for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode){
                MethodInsnNode call=(MethodInsnNode)step;
                assertFalse("Painted tooltip sprites must not receive code-drawn letters",call.name.equals("drawString"));
            }
    }
    private static ClassNode node(Class<?> type)throws Exception{ClassNode node=new ClassNode();try(InputStream in=type.getResourceAsStream(type.getSimpleName()+".class")){new ClassReader(in).accept(node,0);}return node;}
}
