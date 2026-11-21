package kome.client;

import cpw.mods.fml.common.FMLLog;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.function.Consumer;
import lotr.client.gui.LOTRGuiMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** One opt-in, bounded client trace for the native 25%-to-50% zoom animation. */
final class KOMEMapMarkerTrace {
    static final boolean ENABLED=Boolean.getBoolean("kome.mapMarkerTrace");
    enum Kind { COMPANY_SHIELD, NORMAL_BANNER, DEFENSIVE_CASTLE }
    enum Phase { NONE, ANIMATED, SETTLED }
    private static final Recorder LIVE=ENABLED?new Recorder(line->FMLLog.info("%s",line)):null;

    private KOMEMapMarkerTrace() { }
    static void beginFrame(float partialTicks){if(ENABLED)LIVE.partialTicks=partialTicks;}
    static void observe(LOTRGuiMap map){if(ENABLED)try{LIVE.observe(map);}catch(RuntimeException failure){LIVE.failure(failure);}}
    static void beginMarker(Kind kind,String id){if(ENABLED)LIVE.beginMarker(kind,id);}
    static void endMarker(){if(ENABLED)LIVE.active=null;}
    static void submitted(ResourceLocation texture,double x,double y,double width,double height,float alpha,
            double u0,double v0,double u1,double v1) {
        if(ENABLED)LIVE.submitted(texture,x,y,width,height,alpha,u0,v0,u1,v1);
    }
    static void endFrame(){if(ENABLED)try{LIVE.endFrame();}catch(RuntimeException failure){LIVE.failure(failure);}}
    static void close(){if(ENABLED)try{LIVE.close();}catch(RuntimeException failure){LIVE.failure(failure);}}

    /** This state machine is independent of GL and can be exercised with deterministic observations. */
    static final class Capture {
        static final double TARGET=.375, WINDOW=.025;
        static final int MAX_UPDATES_PER_KIND=16;
        final Observation[][] chosen=new Observation[2][Kind.values().length];
        final String[] selectedIds=new String[Kind.values().length];
        final int[] animatedUpdates=new int[Kind.values().length];
        boolean armed,finished;
        Phase phase=Phase.NONE;

        void observe(double zoom,int ticks,int previousPower,int currentPower) {
            phase=Phase.NONE;
            if(finished)return;
            if(previousPower==-2&&currentPower==-1&&ticks>0&&Math.abs(zoom-TARGET)<=WINDOW) {
                armed=true;phase=Phase.ANIMATED;
            } else if(armed&&currentPower==-1&&ticks==0&&Math.abs(zoom-.5)<=.001)phase=Phase.SETTLED;
        }
        boolean wants(Kind kind,String id,double zoom) {
            if(phase==Phase.NONE||id==null)return false;
            int k=kind.ordinal();
            if(selectedIds[k]!=null&&!selectedIds[k].equals(id))return false;
            if(phase==Phase.SETTLED)return chosen[1][k]==null;
            Observation previous=chosen[0][k];
            return animatedUpdates[k]<MAX_UPDATES_PER_KIND
                &&(previous==null||Math.abs(zoom-TARGET)<Math.abs(previous.zoom-TARGET));
        }
        void accept(Observation observation) {
            int k=observation.kind.ordinal();
            if(!wants(observation.kind,observation.id,observation.zoom))return;
            selectedIds[k]=observation.id;
            chosen[observation.phase==Phase.ANIMATED?0:1][k]=observation;
            if(observation.phase==Phase.ANIMATED)animatedUpdates[k]++;
        }
        boolean ready(){return armed&&phase==Phase.SETTLED&&!finished;}
        void finish(){finished=true;phase=Phase.NONE;}
    }

    static final class Observation {
        final Phase phase;
        final Kind kind;
        final String id;
        final double zoom;
        final String line;
        Observation(Phase phase,Kind kind,String id,double zoom,String line) {
            this.phase=phase;this.kind=kind;this.id=id;this.zoom=zoom;this.line=line;
        }
    }

    /** Column-major OpenGL matrices, applied to the four exact GUI quad vertices at z=0. */
    static double[] framebufferBounds(double x,double y,double width,double height,
            float[] model,float[] projection,int[] viewport) {
        if(model.length!=16||projection.length!=16||viewport.length<4)throw new IllegalArgumentException("GL snapshot");
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(double px:new double[]{x,x+width})for(double py:new double[]{y,y+height}) {
            double[] mv=transform(model,px,py,0,1);
            double[] clip=transform(projection,mv[0],mv[1],mv[2],mv[3]);
            if(clip[3]==0)throw new IllegalArgumentException("Zero clip W");
            double sx=viewport[0]+(clip[0]/clip[3]+1)*viewport[2]/2D;
            double sy=viewport[1]+(clip[1]/clip[3]+1)*viewport[3]/2D;
            minX=Math.min(minX,sx);maxX=Math.max(maxX,sx);
            minY=Math.min(minY,sy);maxY=Math.max(maxY,sy);
        }
        return new double[]{minX,minY,maxX,maxY};
    }
    private static double[] transform(float[] m,double x,double y,double z,double w) {
        return new double[]{m[0]*x+m[4]*y+m[8]*z+m[12]*w,
            m[1]*x+m[5]*y+m[9]*z+m[13]*w,
            m[2]*x+m[6]*y+m[10]*z+m[14]*w,
            m[3]*x+m[7]*y+m[11]*z+m[15]*w};
    }

    private static final class Recorder {
        final Capture capture=new Capture();
        final Consumer<String> output;
        final String build=buildIdentity();
        final FloatBuffer modelBuffer=BufferUtils.createFloatBuffer(16),projectionBuffer=BufferUtils.createFloatBuffer(16);
        final FloatBuffer colorBuffer=BufferUtils.createFloatBuffer(16);
        // LWJGL 2 validates 16 remaining integers even for the four-value viewport.
        final IntBuffer viewportBuffer=BufferUtils.createIntBuffer(16);
        float partialTicks;
        double zoom;
        int ticks,previousPower,currentPower;
        Active active;
        boolean reported,queryFailed;

        Recorder(Consumer<String> output){this.output=output;}
        void observe(LOTRGuiMap map) {
            if(reported||queryFailed)return;
            zoom=KOMEConquestMapOverlay.mapNumber(map,"zoomScale");
            ticks=(int)KOMEConquestMapOverlay.mapNumber(map,"zoomTicks");
            previousPower=(int)KOMEConquestMapOverlay.mapNumber(map,"prevZoomPower");
            currentPower=KOMEConquestMapOverlay.mapInt("zoomPower");
            capture.observe(zoom,ticks,previousPower,currentPower);
        }
        void beginMarker(Kind kind,String id) {
            active=null;
            if(!reported&&!queryFailed&&capture.wants(kind,id,zoom))active=new Active(kind,id,capture.phase);
        }
        void submitted(ResourceLocation texture,double x,double y,double width,double height,float alpha,
                double u0,double v0,double u1,double v1) {
            Active marker=active;
            if(marker==null)return;
            try {
                float[] model=read(modelBuffer,GL11.GL_MODELVIEW_MATRIX);
                float[] projection=read(projectionBuffer,GL11.GL_PROJECTION_MATRIX);
                int[] viewport=readViewport();
                float[] color=read(colorBuffer,GL11.GL_CURRENT_COLOR);
                double[] framebuffer=framebufferBounds(x,y,width,height,model,projection,viewport);
                Minecraft mc=KOMEMinecraftClient.get();
                int displayWidth=mc.displayWidth,displayHeight=mc.displayHeight;
                ScaledResolution scaled=new ScaledResolution(mc,displayWidth,displayHeight);
                String line="[KOME-MAP-MARKER-TRACE] phase="+marker.phase+" kind="+marker.kind
                    +" id="+safe(marker.id)+" texture="+texture+" zoomScale="+zoom
                    +" zoomTicks="+ticks+" prevZoomPower="+previousPower+" zoomPower="+currentPower
                    +" partialTicks="+partialTicks+" guiBounds="+x+","+y+","+width+","+height
                    +" quad="+x+","+(y+height)+";"+(x+width)+","+(y+height)+";"+(x+width)+","+y+";"+x+","+y
                    +" uv="+u0+","+v0+","+u1+","+v1+" alpha="+alpha+" glColorAlpha="+color[3]
                    +" display="+displayWidth+"x"+displayHeight+" guiScale="+scaled.getScaleFactor()
                    +" scaledGui="+scaled.getScaledWidth()+"x"+scaled.getScaledHeight()
                    +" matrixMode="+GL11.glGetInteger(GL11.GL_MATRIX_MODE)
                    +" modelview="+Arrays.toString(model)+" projection="+Arrays.toString(projection)
                    +" viewport="+Arrays.toString(viewport)+" framebufferBounds="+Arrays.toString(framebuffer)
                    +" framebufferSize="+(framebuffer[2]-framebuffer[0])+"x"+(framebuffer[3]-framebuffer[1]);
                capture.accept(new Observation(marker.phase,marker.kind,marker.id,zoom,line));
            } catch(RuntimeException failure) {
                failure(failure);
            }
        }
        void endFrame() {
            if(!reported&&capture.ready())report("settled");
        }
        void failure(RuntimeException failure) {
            if(!queryFailed){queryFailed=true;output.accept("[KOME-MAP-MARKER-TRACE] observation unavailable: "+failure);}
            active=null;
        }
        void close() {
            active=null;
            if(!reported&&capture.armed)report("map-close-or-reload-before-settle");
        }
        private void report(String reason) {
            output.accept("[KOME-MAP-MARKER-TRACE] build="+build+" reason="+reason+" target=25%-to-50% one-transition maxRecords=6");
            for(int phase=0;phase<2;phase++)for(Kind kind:Kind.values()) {
                Observation selected=capture.chosen[phase][kind.ordinal()];
                if(selected!=null)output.accept(selected.line);
                else output.accept("[KOME-MAP-MARKER-TRACE] missing phase="+(phase==0?Phase.ANIMATED:Phase.SETTLED)+" kind="+kind);
            }
            output.accept("[KOME-MAP-MARKER-TRACE] end reason="+reason+" GL_QUERIES_ONLY_NO_FRAMEBUFFER_READBACK");
            capture.finish();reported=true;
        }
        private float[] read(FloatBuffer buffer,int parameter) {
            buffer.clear();GL11.glGetFloat(parameter,buffer);
            float[] values=new float[16];for(int i=0;i<16;i++)values[i]=buffer.get(i);
            return values;
        }
        private int[] readViewport() {
            viewportBuffer.clear();GL11.glGetInteger(GL11.GL_VIEWPORT,viewportBuffer);
            int[] values=new int[4];for(int i=0;i<4;i++)values[i]=viewportBuffer.get(i);
            return values;
        }
    }
    private static final class Active {
        final Kind kind;
        final String id;
        final Phase phase;
        Active(Kind kind,String id,Phase phase){this.kind=kind;this.id=id;this.phase=phase;}
    }
    private static String safe(String id) {return id.replace('\n',' ').replace('\r',' ').substring(0,Math.min(id.length(),128));}
    private static String buildIdentity() {
        try {
            Path path=Paths.get(KOMEMapMarkerTrace.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            if(Files.isRegularFile(path))try(InputStream input=Files.newInputStream(path)){update(digest,input);}
            else try(InputStream input=KOMEMapMarkerTrace.class.getResourceAsStream("KOMEMapMarkerTrace.class")){update(digest,input);}
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format("%02x",b&255));
            return path+" sha256="+hex;
        } catch(Exception failure){return "unavailable("+failure.getClass().getSimpleName()+")";}
    }
    private static void update(MessageDigest digest,InputStream input)throws java.io.IOException {
        if(input==null)throw new java.io.IOException("Missing class bytes");
        byte[] block=new byte[8192];int count;while((count=input.read(block))!=-1)digest.update(block,0,count);
    }
}
