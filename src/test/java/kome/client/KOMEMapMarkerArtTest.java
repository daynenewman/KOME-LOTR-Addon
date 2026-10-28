package kome.client;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import javax.imageio.ImageIO;
import kome.common.data.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;
import org.junit.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

/** Native jar/resource-pack fixtures, no GL context or Minecraft process. */
public class KOMEMapMarkerArtTest {
    private static Object originalTextures,originalAvailable,originalValidated;
    @BeforeClass public static void headlessGpu()throws Exception {
        originalTextures=state("textures").get(null);originalAvailable=state("available").get(null);originalValidated=state("validated").get(null);
        state("textures").set(null,new KOMEMapMarkerTextures(new KOMEMapMarkerTextures.Gpu(){int next;public int upload(BufferedImage image,boolean blur,boolean clamp){return ++next;}public void delete(int id){}}));
    }
    @AfterClass public static void restoreGpu()throws Exception {
        KOMEMapMarkerArt.reload(null);state("textures").set(null,originalTextures);state("available").set(null,originalAvailable);state("validated").set(null,originalValidated);
    }
    private static java.lang.reflect.Field state(String name)throws Exception{java.lang.reflect.Field field=KOMEMapMarkerArt.class.getDeclaredField(name);field.setAccessible(true);return field;}
    @After public void restoreAssets(){KOMEMapMarkerArt.reload(baseManager());}

    @Test public void exactInventoryCovers22ShieldsAnd23BannersOf24BundledKeys() {
        String[] keys={"hobbit","bree","dunedain","bluemountains","highelves","woodelf","gundabad","angmar","dolguldur","dale","durinsfolk","lothlorien","dunland","isengard","fangorn","rohan","gondor","mordor","dorwinion","rhudel","harad","morwaith","taurethrim","halftroll"};
        int shields=0,banners=0;
        for(String key:keys){if(KOMEMapMarkerArt.shield(key)!=KOMEMapMarkerArt.NEUTRAL_SHIELD)shields++;if(KOMEMapMarkerArt.banner(key)!=KOMEMapMarkerArt.NEUTRAL_BANNER)banners++;}
        assertEquals(22,shields);assertEquals(23,banners);
    }

    @Test public void nativeResourcesUseOriginalPathsAndVerifiedFrontFaceUvs() {
        assertSprite(KOMEMapMarkerArt.shield("gondor"),"lotr:shield/alignment_gondor.png",.5,1,11,11);
        assertSprite(KOMEMapMarkerArt.NEUTRAL_SHIELD,"lotr:shield/achievement_bronze.png",.5,1,11,11);
        assertSprite(KOMEMapMarkerArt.banner("gondor"),"lotr:item/banner/banner_gondor.png",.25,.5,7,14);
    }

    @Test public void exceptionalNamesAndCanonicalAliasesResolveExplicitly() {
        String[][] cases={{"RANGER_NORTH","ranger","ranger"},{"NEAR_HARAD","near_harad","nearHarad"},{"durinsfolk","dwarf","durin"},{"woodelf","wood_elf","mirkwood"},{"lothlorien","galadhrim","lothlorien"},{"isengard","uruk_hai","isengard"},{"morwaith","moredain","moredain"},{"taurethrim","tauredain","tauredain"},{"rhudel","rhun","rhun"},{"bluemountains","blue_mountains","blueMountains"},{"highelves","high_elf","highElf"},{"dolguldur","dol_guldur","dolGuldur"},{"halftroll","half_troll","halfTroll"},{"umbar","umbar","umbar"}};
        for(String[] c:cases){assertEquals(c[0],new ResourceLocation("lotr:shield/alignment_"+c[1]+".png"),KOMEMapMarkerArt.shield(c[0]).texture);assertEquals(new ResourceLocation("lotr:item/banner/banner_"+c[2]+".png"),KOMEMapMarkerArt.banner(c[0]).texture);}
    }

    @Test public void unsupportedFactionsNeverBorrowAnotherFactionsEmblem() {
        for(String key:new String[]{null,"","unknown","fangorn","utumno","farharad"}){assertSame(KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.shield(key));assertSame(KOMEMapMarkerArt.NEUTRAL_BANNER,KOMEMapMarkerArt.banner(key));}
        assertSame(KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.shield("hobbit"));
        assertSame(KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.shield("nomad"));
        assertEquals(new ResourceLocation("lotr:item/banner/banner_hobbit.png"),KOMEMapMarkerArt.banner("hobbit").texture);
        assertEquals(new ResourceLocation("lotr:item/banner/banner_haradNomad.png"),KOMEMapMarkerArt.banner("nomad").texture);
    }

    @Test public void companyJoinIsExactAndLateSnapshotsRecoverWithoutCachedFactionGuesses() {
        Map<String,KOMEArmyCompany> companies=new HashMap<>();KOMEUnitMapMarker marker=new KOMEUnitMapMarker();marker.entityId="company-A";marker.companyName="Mordor";marker.currentTile="T001";
        assertSame(KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.company(marker,companies));
        KOMEArmyCompany company=new KOMEArmyCompany();company.nativeFaction="rohan";company.faction="mordor";companies.put("company-A",company);
        assertEquals(new ResourceLocation("lotr:shield/alignment_rohan.png"),KOMEMapMarkerArt.company(marker,companies).texture);
        company.nativeFaction="unknown";assertSame(KOMEMapMarkerArt.shield("mordor"),KOMEMapMarkerArt.company(marker,companies));
        company.nativeFaction="hobbit";assertSame(KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.company(marker,companies));
        marker.entityId="company-a";assertSame(KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.company(marker,companies));
    }

    @Test public void buildTypeAndPopulationOwnerSelectArtWithoutTileAuthority() {
        KOMEPlayerBuild b=new KOMEPlayerBuild();b.type=KOMEBuildType.NORMAL;b.populationFaction="gondor";b.originalBuilderFaction="mordor";b.tileId="T001";
        assertSame(KOMEMapMarkerArt.banner("gondor"),KOMEMapMarkerArt.build(b));
        b.populationFaction="rohan";assertSame(KOMEMapMarkerArt.banner("rohan"),KOMEMapMarkerArt.build(b));
        b.type=KOMEBuildType.DEFENSIVE;assertSame(KOMEMapMarkerArt.CASTLE,KOMEMapMarkerArt.build(b));
        b.type=null;assertSame(KOMEMapMarkerArt.NEUTRAL_BANNER,KOMEMapMarkerArt.build(b));
    }

    @Test public void everyMappedNativeAssetExistsAndPassesItsAuditedLayout() {
        KOMEMapMarkerArt.reload(baseManager());
        assertSame(KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.NEUTRAL_SHIELD));
        for(String key:new String[]{"gondor","rohan","mordor","bree","angmar","dunland","dale","dorwinion","gundabad","dunedain","harad","durinsfolk","woodelf","lothlorien","isengard","morwaith","taurethrim","rhudel","bluemountains","highelves","dolguldur","halftroll","umbar"}) {
            assertSame(key,KOMEMapMarkerArt.shield(key),KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.shield(key)));
            assertSame(key,KOMEMapMarkerArt.banner(key),KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.banner(key)));
        }
        for(String key:new String[]{"hobbit","nomad"})assertSame(key,KOMEMapMarkerArt.banner(key),KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.banner(key)));
    }
    @Test public void bronzeAdventurerShieldFollowsResourcePackReloadAndCustomLastResort()throws Exception{
        KOMEMapMarkerArt.Sprite bronze=KOMEMapMarkerArt.NEUTRAL_SHIELD;
        Map<ResourceLocation,byte[]> replacement=new HashMap<>();
        replacement.put(bronze.texture,hdShield());
        KOMEMapMarkerArt.reload(manager(pack("base",null),pack("hdBronze",replacement)));
        assertSame(bronze,KOMEMapMarkerArt.displayed(bronze));
        assertEquals("lotr",KOMEMapMarkerArt.displayed(bronze).texture.getResourceDomain());
        replacement.put(bronze.texture,new byte[]{1,2,3});
        KOMEMapMarkerArt.reload(manager(pack("base",null),pack("brokenBronze",replacement)));
        assertEquals(new ResourceLocation("kome:textures/gui/neutral_shield.png"),
            KOMEMapMarkerArt.displayed(bronze).texture);
        replacement.put(bronze.texture,hdShield());
        KOMEMapMarkerArt.reload(manager(pack("base",null),pack("repairedBronze",replacement)));
        assertSame(bronze,KOMEMapMarkerArt.displayed(bronze));
    }

    @Test public void nativePackPriorityReloadAndMissingAssetRecoveryUseNormalResourceManager()throws Exception {
        KOMEMapMarkerArt.Sprite desired=KOMEMapMarkerArt.banner("gondor");
        Map<ResourceLocation,byte[]> low=new HashMap<>(),high=new HashMap<>();low.put(desired.texture,png(256,256,64,128));high.put(desired.texture,png(128,64,128,64));
        SimpleReloadableResourceManager manager=manager(pack("base",null),pack("low",low),pack("high",high));KOMEMapMarkerArt.reload(manager);
        assertSame(KOMEMapMarkerArt.NEUTRAL_BANNER,KOMEMapMarkerArt.displayed(desired));
        manager=manager(pack("base",null),pack("low",low));KOMEMapMarkerArt.reload(manager);
        assertSame(desired,KOMEMapMarkerArt.displayed(desired));
        low.put(desired.texture,new byte[]{1,2,3});manager=manager(pack("base",null),pack("low",low));KOMEMapMarkerArt.reload(manager);
        assertSame(KOMEMapMarkerArt.NEUTRAL_BANNER,KOMEMapMarkerArt.displayed(desired));
        KOMEMapMarkerArt.reload(emptyManager());assertNull(KOMEMapMarkerArt.displayed(desired));
        KOMEMapMarkerArt.reload(baseManager());assertSame(desired,KOMEMapMarkerArt.displayed(desired));
    }

    @Test public void incompatibleBannerLayoutFallsBackEvenWhenCanvasDimensionsMatch()throws Exception {
        KOMEMapMarkerArt.Sprite desired=KOMEMapMarkerArt.banner("rohan");Map<ResourceLocation,byte[]> override=new HashMap<>();override.put(desired.texture,png(128,128,128,128));
        SimpleReloadableResourceManager manager=manager(pack("base",null),pack("repacked",override));KOMEMapMarkerArt.reload(manager);
        assertSame(KOMEMapMarkerArt.NEUTRAL_BANNER,KOMEMapMarkerArt.displayed(desired));
    }

    @Test public void compatibleHdShieldsRemainNamespacedAndNoPerFrameResourcesAreOpened()throws Exception {
        KOMEMapMarkerArt.Sprite shield=KOMEMapMarkerArt.shield("gondor");Map<ResourceLocation,byte[]> overrides=new HashMap<>();overrides.put(shield.texture,hdShield());
        final int[] reads={0};IResourceManager manager=counting(manager(pack("base",null),pack("hd",overrides)),reads);
        KOMEMapMarkerArt.reload(manager);int initial=reads[0];assertTrue(initial>0);
        for(int i=0;i<1000;i++)assertSame(shield,KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.shield("gondor")));
        assertEquals(initial,reads[0]);assertEquals("lotr",shield.texture.getResourceDomain());
    }

    @Test public void paintedFrontFaceBoundsFollowNativeArtAndPackReloads()throws Exception {
        KOMEMapMarkerArt.Sprite gondor=KOMEMapMarkerArt.shield("gondor");
        KOMEMapMarkerArt.reload(baseManager());
        assertEquals(7F/32F,gondor.visible.left,.001F);
        assertEquals(25F/32F,gondor.visible.right,.001F);
        assertEquals(31F/32F,gondor.visible.bottom,.001F);
        Map<ResourceLocation,byte[]> replacement=new HashMap<>();
        replacement.put(gondor.texture,shieldWithFrontFace(8,23,4,27));
        KOMEMapMarkerArt.reload(manager(pack("base",null),pack("replacement",replacement)));
        assertEquals(8F/32F,gondor.visible.left,.001F);
        assertEquals(24F/32F,gondor.visible.right,.001F);
        assertEquals(28F/32F,gondor.visible.bottom,.001F);
        KOMEMapMarkerArt.reload(baseManager());
        assertEquals(25F/32F,gondor.visible.right,.001F);
    }

    @Test public void incompatibleShieldCanvasAndMissingNativeAssetUseNeutralArt()throws Exception {
        KOMEMapMarkerArt.Sprite shield=KOMEMapMarkerArt.shield("gondor");Map<ResourceLocation,byte[]> overrides=new HashMap<>();
        overrides.put(shield.texture,png(64,32,64,32));KOMEMapMarkerArt.reload(manager(pack("base",null),pack("singleCanvas",overrides)));
        assertSame(KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.displayed(shield));
        Map<ResourceLocation,byte[]> onlyFallback=new HashMap<>();
        try(InputStream in=getClass().getResourceAsStream("/assets/kome/textures/gui/neutral_shield.png")){onlyFallback.put(new ResourceLocation("kome:textures/gui/neutral_shield.png"),read(in));}
        KOMEMapMarkerArt.reload(manager(pack("onlyFallback",onlyFallback)));
        assertEquals(new ResourceLocation("kome:textures/gui/neutral_shield.png"),KOMEMapMarkerArt.displayed(shield).texture);
    }

    @Test public void customCastleAndFallbackArtAlsoHonorResourcePacksAndReload()throws Exception {
        Map<ResourceLocation,byte[]> override=new HashMap<>();override.put(KOMEMapMarkerArt.CASTLE.texture,png(128,128,128,128));
        override.put(KOMEMapMarkerArt.NEUTRAL_BANNER.texture,png(64,64,64,64));
        KOMEMapMarkerArt.reload(manager(pack("base",null),pack("custom",override)));
        assertSame(KOMEMapMarkerArt.CASTLE,KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.CASTLE));
        assertNull(KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.NEUTRAL_BANNER));
        KOMEMapMarkerArt.reload(baseManager());assertSame(KOMEMapMarkerArt.NEUTRAL_BANNER,KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.NEUTRAL_BANNER));
    }

    @Test public void customCastleAndNeutralPngsAreEditableTransparentAssets()throws Exception {
        for(String name:new String[]{"defensive_build","neutral_shield","neutral_banner"})try(InputStream in=getClass().getResourceAsStream("/assets/kome/textures/gui/"+name+".png")) {
            assertNotNull(name,in);BufferedImage image=ImageIO.read(in);
            if(name.equals("defensive_build")){assertTrue(image.getWidth()>0);assertTrue(image.getHeight()>0);assertTrue((long)image.getWidth()*image.getHeight()<=1_048_576);}
            else {assertEquals(64,image.getWidth());assertEquals(name.equals("neutral_banner")?128:64,image.getHeight());}
            assertTrue(image.getColorModel().hasAlpha());
            int clear=0,ink=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)==0)clear++;else ink++;
            assertTrue(ink>0);assertTrue(clear>0);
        }
    }

    @Test public void spriteBoundsPreserveZoomFadeAndAnchorForEveryArtCategory() {
        for(double zoom:new double[]{.125,.25,.5,.73,1,2,16})for(KOMEMapMarkerArt.Sprite sprite:new KOMEMapMarkerArt.Sprite[]{KOMEMapMarkerArt.shield("gondor"),KOMEMapMarkerArt.banner("gondor"),KOMEMapMarkerArt.CASTLE,KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.NEUTRAL_BANNER}) {
            KOMEMapIconPresentation policy=KOMEMapIconPresentation.at(zoom);KOMEMapIconPresentation.Bounds b=sprite.bounds(policy,100,200);
            if(zoom<=.5){assertEquals(Math.round(sprite.width*policy.scale),b.width);assertEquals(Math.round(sprite.height*policy.scale),b.height);}
            assertEquals(100,b.x+b.width/2);assertEquals(200,b.y+b.height/2);assertEquals(policy.alpha,b.alpha);
            assertTrue(b.contains(b.x,b.y));assertFalse(b.contains(b.x+b.width,b.y));assertFalse(b.contains(b.x,b.y+b.height));
        }
    }

    @Test public void bannerPathFormulaMatchesNativeApiWithoutInitializingItsGlRenderer()throws Exception {
        ClassNode nativeNode=new ClassNode();try(InputStream in=getClass().getResourceAsStream("/lotr/client/render/entity/LOTRRenderBanner.class")){assertNotNull(in);new ClassReader(in).accept(nativeNode,0);}
        boolean prefix=false,name=false,suffix=false;
        for(MethodNode m:nativeNode.methods)if(m.name.equals("getBannerTexture")&&m.desc.contains("BannerType"))for(AbstractInsnNode step:m.instructions.toArray()) {
            if(step instanceof LdcInsnNode){Object c=((LdcInsnNode)step).cst;if("lotr:item/banner/banner_".equals(c))prefix=true;if(".png".equals(c))suffix=true;}
            if(step instanceof FieldInsnNode&&((FieldInsnNode)step).name.equals("bannerName"))name=true;
        }
        assertTrue(prefix&&name&&suffix);
        ClassNode helper=new ClassNode();try(InputStream in=KOMEMapMarkerArt.class.getResourceAsStream("KOMEMapMarkerArt.class")){new ClassReader(in).accept(helper,0);}
        for(MethodNode m:helper.methods)for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode)assertFalse(((MethodInsnNode)step).owner.equals("lotr/client/render/entity/LOTRRenderBanner"));
    }

    @Test public void productionWiresNativeSpritesReloadAndUvScopeWithoutChangingAggregates()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}
        Set<String> calls=new HashSet<>();boolean uvScope=false;
        for(MethodNode m:node.methods)for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)step;if(c.owner.equals("kome/client/KOMEMapMarkerArt"))calls.add(m.name+":"+c.name);
            if(m.name.equals("drawTroopMarkers"))assertFalse(c.owner.equals("kome/client/KOMEMapMarkerArt"));
            if(m.name.equals("drawTexturedMarker")&&m.desc.endsWith("FDDDD)V")&&c.name.equals("glPopAttrib")){uvScope=true;assertFalse(m.tryCatchBlocks.isEmpty());}}
        assertTrue(calls.contains("visibleCompanyGroups:company"));assertTrue(calls.contains("drawBuildMarker:build"));assertTrue(calls.contains("onResourceManagerReload:reload"));assertTrue(uvScope);
    }

    private static void assertSprite(KOMEMapMarkerArt.Sprite s,String path,double u,double v,int w,int h){assertEquals(new ResourceLocation(path),s.texture);assertEquals(0,s.u0,0);assertEquals(0,s.v0,0);assertEquals(u,s.u1,0);assertEquals(v,s.v1,0);assertEquals(w,s.width);assertEquals(h,s.height);}
    private static byte[] png(int w,int h,int inkW,int inkH)throws Exception{BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<inkH;y++)for(int x=0;x<inkW;x++)image.setRGB(x,y,0xFFD0BFA0);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
    private static byte[] hdShield()throws Exception{try(InputStream in=KOMEMapMarkerArtTest.class.getResourceAsStream("/assets/lotr/shield/alignment_gondor.png")){BufferedImage original=ImageIO.read(in),hd=new BufferedImage(128,64,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<128;x++)hd.setRGB(x,y,original.getRGB(x/2,y/2));ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(hd,"png",out);return out.toByteArray();}}
    private static byte[] shieldWithFrontFace(int left,int right,int top,int bottom)throws Exception{BufferedImage art=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){art.setRGB(x,y,0xFFBBAA88);art.setRGB(x+32,y,0xFFBBAA88);}ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(art,"png",out);return out.toByteArray();}
    private static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
    private static IResourcePack pack(String name,Map<ResourceLocation,byte[]> images){return (IResourcePack)Proxy.newProxyInstance(IResourcePack.class.getClassLoader(),new Class[]{IResourcePack.class},(p,m,args)->{
        if(m.getName().equals("getPackName")||m.getName().equals("toString"))return name;
        if(m.getName().equals("getResourceDomains"))return new HashSet<>(Arrays.asList("lotr","kome"));
        if(m.getName().equals("resourceExists")||m.getName().equals("getInputStream")){ResourceLocation r=(ResourceLocation)args[0];InputStream in=images==null?KOMEMapMarkerArtTest.class.getResourceAsStream("/assets/"+r.getResourceDomain()+"/"+r.getResourcePath()):images.containsKey(r)?new ByteArrayInputStream(images.get(r)):null;
            if(m.getName().equals("resourceExists")){if(in!=null)in.close();return in!=null;}if(in==null)throw new FileNotFoundException(r.toString());return in;}
        return null;});}
    // Exercise native pack lookup/override order, without Forge's startup progress UI and Loader.
    // Production listener registration/wiring is checked separately; no Minecraft is launched.
    private static SimpleReloadableResourceManager manager(IResourcePack... packs){SimpleReloadableResourceManager manager=new SimpleReloadableResourceManager(new IMetadataSerializer());for(IResourcePack pack:packs)manager.reloadResourcePack(pack);return manager;}
    private static IResourceManager baseManager(){return manager(pack("base",null));}
    private static IResourceManager emptyManager(){return manager();}
    private static IResourceManager counting(IResourceManager delegate,int[] reads){return (IResourceManager)Proxy.newProxyInstance(IResourceManager.class.getClassLoader(),new Class[]{IResourceManager.class},(p,m,args)->{if(m.getName().equals("getResource"))reads[0]++;try{return m.invoke(delegate,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});}
}
