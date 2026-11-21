package kome.client;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Marker-owned registrations keyed by original resource identities. */
final class KOMEMapMarkerTextures {
    interface Gpu {int upload(BufferedImage image,boolean blur,boolean clamp);void delete(int id);}
    private final Gpu gpu;
    private final Map<ResourceLocation,OwnedTexture> owned=new HashMap<>();
    private TextureManager manager;
    KOMEMapMarkerTextures(){this(new Driver());}
    KOMEMapMarkerTextures(Gpu gpu){this.gpu=gpu;}
    void reload(IResourceManager resources,Iterable<KOMEMapMarkerArt.Sprite> sprites) {
        close();if(resources==null)return;
        // Never register this manager as a global reload listener. The overlay supplies the
        // active resource manager after its reload; no shared native registrations are touched.
        manager=new TextureManager(resources);
        for(KOMEMapMarkerArt.Sprite sprite:sprites) {
            OwnedTexture texture=new OwnedTexture(sprite);owned.put(sprite.texture,texture);
            manager.loadTexture(sprite.texture,texture);
        }
    }
    boolean ready(KOMEMapMarkerArt.Sprite sprite){OwnedTexture texture=owned.get(sprite.texture);return texture!=null&&texture.ready;}
    boolean bind(ResourceLocation location) {
        OwnedTexture texture=owned.get(location);if(texture==null||!texture.ready)return false;
        manager.bindTexture(location);return true; // Original namespaced location, already loaded.
    }
    void close(){for(OwnedTexture texture:owned.values())texture.release();owned.clear();manager=null;}

    /** Never throws asset IO/decode failures into TextureManager.loadTexture: failed entries
     * remain our own retryable objects, never the engine's sticky shared missing texture. */
    private final class OwnedTexture implements ITextureObject {
        private final KOMEMapMarkerArt.Sprite sprite;
        private int id=-1;
        private boolean ready;
        OwnedTexture(KOMEMapMarkerArt.Sprite sprite){this.sprite=sprite;}
        @Override public void loadTexture(IResourceManager resources) {
            release();ready=KOMEMapMarkerArt.read(resources,sprite,(image,blur,clamp)->id=gpu.upload(image,blur,clamp));
            if(id<=0)ready=false;
            if(!ready)release(); // Also releases an upload followed by a stream-close failure.
        }
        @Override public int getGlTextureId(){return ready?id:0;}
        void release(){int previous=id;id=-1;ready=false;if(previous>0)gpu.delete(previous);}
    }
    private static final class Driver implements Gpu {
        @Override public int upload(BufferedImage image,boolean blur,boolean clamp) {
            int id=-1;boolean complete=false;
            GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
            try {
                id=TextureUtil.glGenTextures();TextureUtil.uploadTextureImageAllocate(id,image,blur,clamp);complete=true;return id;
            } finally {
                try{if(!complete&&id>0)TextureUtil.deleteTexture(id);}finally{GL11.glPopAttrib();}
            }
        }
        @Override public void delete(int id){TextureUtil.deleteTexture(id);}
    }
}
