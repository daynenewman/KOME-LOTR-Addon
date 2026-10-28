package kome.client;

import java.util.IdentityHashMap;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEUnitMapMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import org.lwjgl.opengl.GL11;

/** Optional, presentation-only icons from the sanitized g20 marker projection. */
final class KOMECompanyDescriptorRenderer {
    private static final IdentityHashMap<KOMEUnitMapMarker,Resolved> CACHE=new IdentityHashMap<KOMEUnitMapMarker,Resolved>();
    private static long session=Long.MIN_VALUE;
    private static int revision=Integer.MIN_VALUE;
    private static final int MAX_CACHE=1024;
    private static ItemStack bootsFallback,saddleFallback;

    private KOMECompanyDescriptorRenderer() { }

    static boolean enabled(KOMEMapPreferences.CompanyDisplay mode,boolean spotted,KOMEUnitMapMarker marker) {
        return mode==KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS && !spotted && marker!=null;
    }
    static boolean hasWeapon(String id){return id!=null&&!id.isEmpty()&&KOMEUnitMapMarker.validItemId(id);}
    static boolean hasMovementIcon(KOMEUnitMapMarker marker){return marker!=null
        &&(marker.movementEntitlement==KOMEUnitMapMarker.ENTITLEMENT_FOOT_OR_MIXED
            ||marker.movementEntitlement==KOMEUnitMapMarker.ENTITLEMENT_MOUNTED)
        &&(marker.movementCredit==KOMEUnitMapMarker.CREDIT_AVAILABLE
            ||marker.movementCredit==KOMEUnitMapMarker.CREDIT_EXHAUSTED);}
    static float movementBrightness(KOMEUnitMapMarker marker){return marker.movementCredit==KOMEUnitMapMarker.CREDIT_EXHAUSTED?.25F:1F;}
    static float movementAlpha(KOMEUnitMapMarker marker,KOMEMapIconPresentation.Bounds shield){
        return shield.alpha/255F;
    }
    static float itemColorChannel(int color,int shift,float brightness){
        return ((color>>shift)&255)/255F*brightness;
    }
    static ItemStack movementIcon(KOMEUnitMapMarker marker,ItemStack saved){
        if(saved!=null)return saved;
        if(marker.movementEntitlement==KOMEUnitMapMarker.ENTITLEMENT_MOUNTED){
            if(saddleFallback==null&&Items.saddle!=null)saddleFallback=new ItemStack(Items.saddle);
            return saddleFallback;
        }
        if(bootsFallback==null&&Items.leather_boots!=null)bootsFallback=new ItemStack(Items.leather_boots);
        return bootsFallback;
    }
    static int weaponSize(KOMEMapIconPresentation.Bounds shield,KOMEMapMarkerArt.Sprite art){
        KOMEMapMarkerArt.VisibleRect face=art.visible;
        return Math.max(3,Math.round(shield.width*(face.right-face.left)*.80F));
    }
    static int movementSize(KOMEMapIconPresentation.Bounds shield){return Math.max(1,Math.round(shield.width*.55F));}
    static int meleeX(KOMEMapIconPresentation.Bounds shield,KOMEMapMarkerArt.Sprite art,int size){
        return centeredWeaponX(shield,art,size);
    }
    static int rangedX(KOMEMapIconPresentation.Bounds shield,KOMEMapMarkerArt.Sprite art,int size){
        return centeredWeaponX(shield,art,size);
    }
    private static int centeredWeaponX(KOMEMapIconPresentation.Bounds shield,KOMEMapMarkerArt.Sprite art,int size){
        return Math.round(shield.x+shield.width*(art.visible.left+art.visible.right)/2F-size/2F);
    }
    static int weaponY(KOMEMapIconPresentation.Bounds shield,KOMEMapMarkerArt.Sprite art,int size){
        return Math.round(shield.y+shield.height*(art.visible.top+art.visible.bottom)/2F-size/2F);
    }
    static int mixedMeleeOffset(int size){return size>=5?1:0;}
    static int movementX(KOMEMapIconPresentation.Bounds shield,KOMEMapMarkerArt.Sprite art,int size){
        KOMEMapMarkerArt.VisibleRect face=art.visible;
        return Math.round(shield.x+shield.width*(face.left+face.right)/2F-size/2F);
    }
    static int movementY(KOMEMapIconPresentation.Bounds shield,KOMEMapMarkerArt.Sprite art){
        return Math.round(shield.y+shield.height*art.visible.bottom)-1;
    }
    static boolean intersectsViewport(int x,int y,int size,KOMEMapViewport view){
        return view!=null&&size>0&&x<view.right&&x+size>view.left&&y<view.bottom&&y+size>view.top;
    }

    static void clear(){CACHE.clear();session=Long.MIN_VALUE;revision=Integer.MIN_VALUE;}

    static void draw(KOMEUnitMapMarker marker,KOMEMapMarkerArt.Sprite art,KOMEMapIconPresentation.Bounds shield,KOMEMapViewport view,
            KOMEMapPreferences.CompanyDisplay mode,boolean spotted){
        if(!enabled(mode,spotted,marker)||art==null||shield.alpha<=0)return;
        KOMEClientData data=KOMEClientData.INSTANCE;
        if(session!=data.clientSessionEpoch||revision!=data.conquestRevision){
            CACHE.clear();session=data.clientSessionEpoch;revision=data.conquestRevision;
        }
        Resolved icons=CACHE.get(marker);
        if(icons==null||!icons.matches(marker)){
            if(CACHE.size()>=MAX_CACHE)CACHE.clear();
            icons=new Resolved(marker);CACHE.put(marker,icons);
        }
        int weaponSize=weaponSize(shield,art),weaponY=weaponY(shield,art,weaponSize);
        int movementSize=movementSize(shield);
        float alpha=shield.alpha/255F;
        if(hasMovementIcon(marker))drawItem(movementIcon(marker,icons.movement),
            movementX(shield,art,movementSize),movementY(shield,art),movementSize,movementAlpha(marker,shield),movementBrightness(marker),view);
        if(icons.ranged!=null)
            drawItem(icons.ranged,rangedX(shield,art,weaponSize),weaponY,weaponSize,alpha,1F,view);
        if(icons.melee!=null)
            drawItem(icons.melee,meleeX(shield,art,weaponSize)
                +(icons.ranged==null?0:mixedMeleeOffset(weaponSize)),weaponY,weaponSize,alpha,1F,view);
    }

    static ItemStack resolve(String id,int metadata){
        if(!hasWeapon(id)||metadata<0||metadata>Short.MAX_VALUE)return null;
        Object found=Item.itemRegistry.getObject(id);
        return found instanceof Item?new ItemStack((Item)found,1,metadata):null;
    }

    private static void drawItem(ItemStack stack,int x,int y,int size,float alpha,float brightness,KOMEMapViewport view){
        if(stack==null||alpha<=0||!intersectsViewport(x,y,size,view))return;
        double left=Math.max(x,view.left),top=Math.max(y,view.top);
        double right=Math.min(x+size,view.right),bottom=Math.min(y+size,view.bottom);
        if(left>=right||top>=bottom)return;
        Item item=stack.getItem();
        if(item==null)return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT
            |GL11.GL_TEXTURE_BIT|GL11.GL_DEPTH_BUFFER_BIT|GL11.GL_LIGHTING_BIT);
        try{
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            // Item icons live in Minecraft's reloadable item atlas. Never decode or copy pack pixels.
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationItemsTexture);
            if(brightness<1F)GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            int passes=Math.min(2,item.getRenderPasses(stack.getItemDamage()));
            for(int pass=0;pass<passes;pass++){
                IIcon icon=item.getIcon(stack,pass);
                if(icon==null)continue;
                int color=item.getColorFromItemStack(stack,pass);
                GL11.glColor4f(itemColorChannel(color,16,brightness),itemColorChannel(color,8,brightness),
                    itemColorChannel(color,0,brightness),alpha);
                double u0=icon.getMinU(),v0=icon.getMinV();
                double du=(icon.getMaxU()-u0)/size,dv=(icon.getMaxV()-v0)/size;
                Tessellator tess=Tessellator.instance;tess.startDrawingQuads();
                tess.addVertexWithUV(left,bottom,0,u0+(left-x)*du,v0+(bottom-y)*dv);
                tess.addVertexWithUV(right,bottom,0,u0+(right-x)*du,v0+(bottom-y)*dv);
                tess.addVertexWithUV(right,top,0,u0+(right-x)*du,v0+(top-y)*dv);
                tess.addVertexWithUV(left,top,0,u0+(left-x)*du,v0+(top-y)*dv);
                tess.draw();
            }
        }catch(RuntimeException invalidItemArt){
            // A removed or malformed client item icon cannot compromise the rest of the map pass.
        }finally{GL11.glPopAttrib();}
    }

    private static final class Resolved {
        final String meleeId,rangedId,movementId;final int meleeMeta,rangedMeta,movementMeta;
        final ItemStack melee,ranged,movement;
        Resolved(KOMEUnitMapMarker marker){
            meleeId=marker.meleeItemId;rangedId=marker.rangedItemId;movementId=marker.movementItemId;
            meleeMeta=marker.meleeItemMetadata;rangedMeta=marker.rangedItemMetadata;
            movementMeta=marker.movementItemMetadata;
            melee=resolve(meleeId,meleeMeta);ranged=resolve(rangedId,rangedMeta);
            movement=resolve(movementId,movementMeta);
        }
        boolean matches(KOMEUnitMapMarker marker){
            return meleeMeta==marker.meleeItemMetadata&&rangedMeta==marker.rangedItemMetadata
                &&movementMeta==marker.movementItemMetadata
                &&java.util.Objects.equals(meleeId,marker.meleeItemId)
                &&java.util.Objects.equals(rangedId,marker.rangedItemId)
                &&java.util.Objects.equals(movementId,marker.movementItemId);
        }
    }
}
