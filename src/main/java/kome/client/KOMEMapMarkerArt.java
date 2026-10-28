package kome.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEUnitMapMarker;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.data.TextureMetadataSection;
import net.minecraft.util.ResourceLocation;
import lotr.common.LOTRShields;
import lotr.common.item.LOTRItemBanner.BannerType;

/** Client presentation assets; native resource identities are never copied or recolored. */
final class KOMEMapMarkerArt {
    private enum Layout { SHIELD, BANNER, SQUARE, PORTRAIT, CUSTOM }
    static final class Sprite {
        final ResourceLocation texture;
        final double u0=0,v0=0,u1,v1;
        final int width,height;
        private final Layout layout;
        private final Sprite fallback;
        /** Alpha footprint of the sampled shield front (or neutral square), refreshed with the pack. */
        volatile VisibleRect visible=VisibleRect.FULL;
        Sprite(ResourceLocation texture,double u1,double v1,int width,int height,Layout layout,Sprite fallback) {
            this.texture=texture;this.u1=u1;this.v1=v1;this.width=width;this.height=height;this.layout=layout;this.fallback=fallback;
        }
        KOMEMapIconPresentation.Bounds bounds(KOMEMapIconPresentation presentation,int anchorX,int anchorY) {
            boolean banner=layout==Layout.BANNER||layout==Layout.PORTRAIT;
            double shieldBump=layout==Layout.SHIELD||this==CUSTOM_FALLBACK_SHIELD?presentation.shieldBump:0;
            int fullWidth=banner?10:this==CASTLE?10:18,fullHeight=banner?20:this==CASTLE?10:18;
            int w=Math.max(1,(int)Math.round(width*presentation.spriteScale+(fullWidth-width)*presentation.spriteEnlargement+shieldBump));
            int h=Math.max(1,(int)Math.round(height*presentation.spriteScale+(fullHeight-height)*presentation.spriteEnlargement+shieldBump));
            return new KOMEMapIconPresentation.Bounds(anchorX-w/2,anchorY-h/2,w,h,presentation.alpha);
        }
    }
    static final class VisibleRect {
        static final VisibleRect FULL=new VisibleRect(0F,1F,1F);
        final float left,top,right,bottom;
        VisibleRect(float left,float right,float bottom){this(left,0F,right,bottom);}
        VisibleRect(float left,float top,float right,float bottom){
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
    }
    // Logical GUI sizes, independent of source resolution. Keep easy to tune after live acceptance.
    private static final Sprite CUSTOM_FALLBACK_SHIELD=custom("neutral_shield",11,11,Layout.SQUARE);
    static final Sprite NEUTRAL_SHIELD=new Sprite(LOTRShields.ACHIEVEMENT_BRONZE.getTexture(),.5,1,
        11,11,Layout.SHIELD,CUSTOM_FALLBACK_SHIELD);
    static final Sprite NEUTRAL_BANNER=custom("neutral_banner",7,14,Layout.PORTRAIT);
    static final Sprite CASTLE=custom("defensive_build",7,7,Layout.CUSTOM);
    private static final Map<String,Sprite> SHIELDS=new HashMap<>(),BANNERS=new HashMap<>();
    private static final Set<String> KNOWN_KEYS=new HashSet<>();
    private static final Set<Sprite> ALL=new LinkedHashSet<>();
    private static final Set<ResourceLocation> LOCATIONS=new HashSet<>();
    private static KOMEMapMarkerTextures textures=new KOMEMapMarkerTextures();
    private static Map<Sprite,Sprite> available=Collections.emptyMap();
    private static boolean validated;
    static {
        add("bree",LOTRShields.ALIGNMENT_BREE,BannerType.BREE);
        add("dunedain",LOTRShields.ALIGNMENT_RANGER,BannerType.RANGER_NORTH);
        add("bluemountains",LOTRShields.ALIGNMENT_BLUE_MOUNTAINS,BannerType.BLUE_MOUNTAINS);
        add("highelves",LOTRShields.ALIGNMENT_HIGH_ELF,BannerType.HIGH_ELF);
        add("gundabad",LOTRShields.ALIGNMENT_GUNDABAD,BannerType.GUNDABAD);
        add("angmar",LOTRShields.ALIGNMENT_ANGMAR,BannerType.ANGMAR);
        add("woodelf",LOTRShields.ALIGNMENT_WOOD_ELF,BannerType.WOOD_ELF);
        add("dolguldur",LOTRShields.ALIGNMENT_DOL_GULDUR,BannerType.DOL_GULDUR);
        add("dale",LOTRShields.ALIGNMENT_DALE,BannerType.DALE);
        add("durinsfolk",LOTRShields.ALIGNMENT_DWARF,BannerType.DWARF);
        add("lothlorien",LOTRShields.ALIGNMENT_GALADHRIM,BannerType.GALADHRIM);
        add("dunland",LOTRShields.ALIGNMENT_DUNLAND,BannerType.DUNLAND);
        add("isengard",LOTRShields.ALIGNMENT_URUK_HAI,BannerType.ISENGARD);
        add("rohan",LOTRShields.ALIGNMENT_ROHAN,BannerType.ROHAN);
        add("gondor",LOTRShields.ALIGNMENT_GONDOR,BannerType.GONDOR);
        add("mordor",LOTRShields.ALIGNMENT_MORDOR,BannerType.MORDOR);
        add("dorwinion",LOTRShields.ALIGNMENT_DORWINION,BannerType.DORWINION);
        add("rhudel",LOTRShields.ALIGNMENT_RHUN,BannerType.RHUN);
        add("harad",LOTRShields.ALIGNMENT_NEAR_HARAD,BannerType.NEAR_HARAD);
        add("morwaith",LOTRShields.ALIGNMENT_MOREDAIN,BannerType.MOREDAIN);
        add("taurethrim",LOTRShields.ALIGNMENT_TAUREDAIN,BannerType.TAUREDAIN);
        add("halftroll",LOTRShields.ALIGNMENT_HALF_TROLL,BannerType.HALF_TROLL);
        add("hobbit",null,BannerType.HOBBIT);
        add("umbar",LOTRShields.ALIGNMENT_UMBAR,BannerType.UMBAR);
        add("nomad",null,BannerType.HARAD_NOMAD);
        KNOWN_KEYS.addAll(Arrays.asList("fangorn","utumno","farharad"));
        ALL.addAll(Arrays.asList(CUSTOM_FALLBACK_SHIELD,NEUTRAL_SHIELD,NEUTRAL_BANNER,CASTLE));
        for(Sprite sprite:ALL)LOCATIONS.add(sprite.texture);
    }
    private static Sprite custom(String name,int w,int h,Layout layout) {
        return new Sprite(new ResourceLocation("kome:textures/gui/"+name+".png"),1,1,w,h,layout,null);
    }
    private static void add(String key,LOTRShields shield,BannerType banner) {
        KNOWN_KEYS.add(key);
        if(shield!=null){Sprite sprite=new Sprite(shield.getTexture(),.5,1,NEUTRAL_SHIELD.width,NEUTRAL_SHIELD.height,Layout.SHIELD,NEUTRAL_SHIELD);SHIELDS.put(key,sprite);ALL.add(sprite);}
        // Exact native getBannerTexture formula, without initializing its GL-reading Frustrum.
        if(banner!=null){Sprite sprite=new Sprite(new ResourceLocation("lotr:item/banner/banner_"+banner.bannerName+".png"),.25,.5,NEUTRAL_BANNER.width,NEUTRAL_BANNER.height,Layout.BANNER,NEUTRAL_BANNER);BANNERS.put(key,sprite);ALL.add(sprite);}
    }
    private static String key(String name) {
        String key=KOMEAlliance.normalizeFactionKey(name==null?"":name.toLowerCase(Locale.ROOT));
        switch(key){case "rhun":return "rhudel";case "moredain":return "morwaith";case "tauredain":return "taurethrim";
            case "galadhrim":return "lothlorien";case "dwarf":return "durinsfolk";case "urukhai":return "isengard";case "haradnomad":return "nomad";default:return key;}
    }
    static Sprite shield(String name){return SHIELDS.getOrDefault(key(name),NEUTRAL_SHIELD);}
    static Sprite banner(String name){return BANNERS.getOrDefault(key(name),NEUTRAL_BANNER);}
    static Sprite company(KOMEUnitMapMarker marker,Map<String,KOMEArmyCompany> companies) {
        KOMEArmyCompany company=marker==null||marker.entityId==null||companies==null?null:companies.get(marker.entityId);
        if(company==null)return NEUTRAL_SHIELD;
        String nativeKey=key(company.nativeFaction);
        // A known faction without a shield stays neutral; never replace it with a different allegiance.
        return KNOWN_KEYS.contains(nativeKey)?shield(nativeKey):shield(company.faction);
    }
    static Sprite build(KOMEPlayerBuild build) {
        if(build==null)return NEUTRAL_BANNER;
        return build.isDefensive()?CASTLE:build.isNormal()?banner(build.populationFaction):NEUTRAL_BANNER;
    }
    static Sprite displayed(Sprite sprite){return sprite==null?null:validated?available.get(sprite):sprite.fallback==null?sprite:sprite.fallback;}
    static boolean owns(ResourceLocation location){return LOCATIONS.contains(location);}
    static boolean bind(ResourceLocation location){return textures.bind(location);}

    /** One bounded decode/upload pass per reload through the highest-priority resource manager.
     * Only owned GL IDs, descriptors and availability survive. No native decoded pixels. */
    static void reload(IResourceManager resources) {
        for(Sprite sprite:ALL)sprite.visible=VisibleRect.FULL;
        textures.reload(resources,ALL);
        if(resources==null){available=Collections.emptyMap();validated=false;return;}
        Map<Sprite,Sprite> ready=new IdentityHashMap<>();
        for(Sprite sprite:ALL){
            Sprite selected=sprite;
            while(selected!=null&&!textures.ready(selected))selected=selected.fallback;
            ready.put(sprite,selected);
        }
        available=Collections.unmodifiableMap(ready);validated=true;
    }
    interface Upload {void accept(BufferedImage image,boolean blur,boolean clamp);}
    static boolean read(IResourceManager resources,Sprite sprite,Upload upload) {
        try {
            IResource resource=resources.getResource(sprite.texture);
            try(InputStream in=resource.getInputStream();MemoryCacheImageInputStream image=new MemoryCacheImageInputStream(in)) {
                // SimpleResource closes its separate mcmeta stream when metadata is parsed.
                TextureMetadataSection metadata=resource.hasMetadata()?(TextureMetadataSection)resource.getMetadata("texture"):null;
                Iterator<ImageReader> readers=ImageIO.getImageReaders(image);if(!readers.hasNext())return false;
                ImageReader reader=readers.next();
                try {
                    reader.setInput(image,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
                    if(w<=0||h<=0||(long)w*h>1_048_576)return false;
                    if(sprite.layout==Layout.SHIELD&&(w!=2*h||w%64!=0))return false;
                    if(sprite.layout==Layout.BANNER&&(w!=h||w%128!=0))return false;
                    if(sprite.layout==Layout.SQUARE&&w!=h)return false;
                    if(sprite.layout==Layout.PORTRAIT&&h!=2*w)return false;
                    BufferedImage art=reader.read(0);
                    if(art==null)return false;
                    try {
                        boolean front=false,back=false,frontClear=false,backClear=false;
                        int regionWidth=sprite.layout==Layout.SHIELD?w/2:w;
                        int firstX=regionWidth,firstY=h,lastX=-1,lastY=-1;
                        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                            if((art.getRGB(x,y)>>>24)!=0) {
                                if(sprite.layout==Layout.BANNER&&(x>=w/4||y>=h/2))return false;
                                if(x<w/2)front=true;else back=true;
                                if(x<regionWidth){firstX=Math.min(firstX,x);firstY=Math.min(firstY,y);
                                    lastX=Math.max(lastX,x);lastY=Math.max(lastY,y);}
                            } else if(x<w/2)frontClear=true;else backClear=true;
                        }
                        boolean compatible=sprite.layout==Layout.SHIELD?front&&back&&frontClear&&backClear:front||back;
                        if(!compatible)return false;
                        upload.accept(art,metadata!=null&&metadata.getTextureBlur(),metadata!=null&&metadata.getTextureClamp());
                        if((sprite.layout==Layout.SHIELD||sprite.layout==Layout.SQUARE)&&lastX>=firstX)
                            sprite.visible=new VisibleRect(firstX/(float)regionWidth,firstY/(float)h,
                                (lastX+1F)/regionWidth,(lastY+1F)/h);
                        return true;
                    } finally {art.flush();}
                } finally {reader.dispose();}
            }
        } catch(java.io.IOException|RuntimeException missingOrIncompatible){return false;}
    }
}
