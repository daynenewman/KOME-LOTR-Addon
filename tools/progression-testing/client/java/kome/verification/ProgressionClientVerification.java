package kome.verification;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import kome.client.*;
import kome.client.gui.KOMEGuiProgression;
import kome.common.data.KOMEVisualMarker;
import lotr.client.gui.LOTRGuiMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import com.lotrcharactercreation.client.gui.*;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.faction.StartingFaction;

/** Rendered native screens and actual mouse event path. Markers are explicit UI fixtures, not server issuance evidence. */
@Mod(modid="progressionclientprobe",name="Disposable progression client verification",version="1",dependencies="required-after:kome")
public final class ProgressionClientVerification {
    private int stage,ticks;
    private final StringBuilder evidence=new StringBuilder("Controlled UI fixture; does not establish multiplayer or server objective issuance.\n");
    private KOMEVisualMarker destination;
    private KOMEVisualMarker gift;
    private static Field field(String name)throws Exception{Field f=LOTRGuiMap.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);evidence.append("OK ").append(message).append('\n');}
    private void capture(Minecraft mc,String name){ScreenShotHelper.saveScreenshot(mc.mcDataDir,name+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());evidence.append("SCREENSHOT ").append(name).append('\n');}
    private void click(GuiScreen gui,int x,int y)throws Exception{Method m=GuiScreen.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);m.setAccessible(true);m.invoke(gui,x,y,0);}
    @Mod.EventHandler public void init(FMLInitializationEvent event){FMLCommonHandler.instance().bus().register(this);}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();
        try{
            if(stage==0){if(++ticks<40)return;stage=1;ticks=0;mc.launchIntegratedServer("progression-disposable","Progression disposable",new WorldSettings(17,WorldSettings.GameType.CREATIVE,false,false,WorldType.FLAT));return;}
            if(mc.theWorld==null||mc.thePlayer==null)return;
            ClientCreationContinuation.INSTANCE.complete();
            if(stage==1){if(++ticks<30)return;mc.gameSettings.limitFramerate=30;mc.gameSettings.renderDistanceChunks=2;mc.gameSettings.guiScale=2;
                LOTRGuiMap map=new LOTRGuiMap();mc.displayGuiScreen(map);field("posX").setFloat(map,900);field("prevPosX").setFloat(map,900);field("posY").setFloat(map,800);field("prevPosY").setFloat(map,800);field("zoomPower").setInt(null,0);field("prevZoomPower").setInt(map,0);field("zoomTicks").setInt(map,0);map.setupZoomVariables(1);stage=2;ticks=0;return;}
            if(stage==2){if(++ticks<15)return;destination=new KOMEVisualMarker(KOMEVisualMarker.Role.ESCORT,"","Destination","",mc.thePlayer.dimension,12345,65,-6789);KOMEVisualMarkerClientState.update(Collections.singletonList(destination));mc.displayGuiScreen(KOMEGuiProgression.dutyView());stage=3;ticks=0;return;}
            if(stage==3){KOMEVisualMarkerClientState.update(Collections.singletonList(destination));if(++ticks<12)return;
                check(mc.currentScreen instanceof KOMEGuiProgression,"progression book opened");KOMEGuiProgression gui=(KOMEGuiProgression)mc.currentScreen;Field f=KOMEGuiProgression.class.getDeclaredField("buttonShowMe");f.setAccessible(true);GuiButton button=(GuiButton)f.get(gui);check(button.visible&&button.enabled,"Show Me available for objective");capture(mc,"book-before-show-me");click(gui,button.xPosition+4,button.yPosition+4);stage=4;ticks=0;return;}
            if(stage==4){KOMEVisualMarkerClientState.update(Collections.singletonList(destination));if(++ticks<12)return;
                check(mc.currentScreen instanceof LOTRGuiMap,"actual button mouse event opened native map");LOTRGuiMap map=(LOTRGuiMap)mc.currentScreen;
                check(field("zoomPower").getInt(null)==4&&field("zoomScale").getFloat(map)==16F,"rendered map at native maximum zoom");
                float[] point=coords(map);int cx=field("mapXMin").getInt(null)+field("mapWidth").getInt(null)/2,cy=field("mapYMin").getInt(null)+field("mapHeight").getInt(null)/2;
                evidence.append("CAMERA class=").append(map.getClass().getName()).append(" x=").append(field("posX").getFloat(map)).append(" y=").append(field("posY").getFloat(map)).append(" prevX=").append(field("prevPosX").getFloat(map)).append(" moveX=").append(field("posXMove").getFloat(map)).append(" moveY=").append(field("posYMove").getFloat(map)).append(" targetPixel=").append(Arrays.toString(point)).append(" center=").append(cx).append(',').append(cy).append('\n');capture(mc,"show-me-camera-check");
                check(Math.abs(point[0]-cx)<0.01&&Math.abs(point[1]-cy)<0.01,"rendered objective projects to exact viewport center after first draws");check(KOMEProgressionMapFocus.isFocused(destination),"yellow highlight active");capture(mc,"show-me-centered");stage=5;ticks=0;return;}
            if(stage==5){KOMEVisualMarkerClientState.update(Collections.singletonList(destination));if(++ticks<225)return;check(!KOMEProgressionMapFocus.isFocused(destination),"highlight expired after ten seconds");capture(mc,"show-me-highlight-expired");mc.displayGuiScreen(new LOTRGuiMap());stage=6;ticks=0;return;}
            if(stage==6){if(++ticks<12)return;LOTRGuiMap map=(LOTRGuiMap)mc.currentScreen;check(field("zoomPower").getInt(null)==0,"prior native zoom restored on close");check(Math.abs(field("posX").getFloat(map)-900)<0.01&&Math.abs(field("posY").getFloat(map)-800)<0.01,"prior manual conquest viewport restored");capture(mc,"manual-map-restored");
                destination=new KOMEVisualMarker(KOMEVisualMarker.Role.KNIGHT_LIEGE,"00000000-0000-0000-0000-000000000123","Liege Aldor","",mc.thePlayer.dimension,7000,65,9000,false,true);KOMEVisualMarkerClientState.update(Collections.singletonList(destination));mc.displayGuiScreen(KOMEGuiProgression.dutyView());stage=11;ticks=0;return;
            }
            if(stage==11){KOMEVisualMarkerClientState.update(Collections.singletonList(destination));if(++ticks<12)return;Field f=KOMEGuiProgression.class.getDeclaredField("buttonShowMe");f.setAccessible(true);GuiButton button=(GuiButton)f.get(mc.currentScreen);check(button.visible&&button.enabled,"Show Me available for relationship-only return target");click(mc.currentScreen,button.xPosition+4,button.yPosition+4);stage=12;ticks=0;return;}
            if(stage==12){KOMEVisualMarkerClientState.update(Collections.singletonList(destination));if(++ticks<12)return;check(mc.currentScreen instanceof LOTRGuiMap,"return target opens native map");LOTRGuiMap map=(LOTRGuiMap)mc.currentScreen;float[] p=coords(map);int cx=field("mapXMin").getInt(null)+field("mapWidth").getInt(null)/2,cy=field("mapYMin").getInt(null)+field("mapHeight").getInt(null)/2;
                check(Math.abs(p[0]-cx)<0.01&&Math.abs(p[1]-cy)<0.01&&field("zoomPower").getInt(null)==4,"relationship return target centered at maximum zoom");capture(mc,"return-to-liege-centered");mc.displayGuiScreen(new GuiAppearanceSelection(PlayerRace.ELF,PlayerSex.MALE,StartingFaction.LOTHLORIEN,null));stage=7;ticks=0;return;
            }
            if(stage==7){if(++ticks<12)return;check(mc.currentScreen instanceof GuiAppearanceSelection,"native Elven appearance preview opened");capture(mc,"avatar-before-swing");click(mc.currentScreen,mc.currentScreen.width/2,mc.currentScreen.height/2);stage=8;ticks=0;return;}
            if(stage==8){if(++ticks<2)return;capture(mc,"avatar-swing");check(!mc.thePlayer.isSwingInProgress,"preview click did not swing the live player");
                Method selected=GuiAppearanceSelection.class.getDeclaredMethod("getSelectedPreset");selected.setAccessible(true);com.lotrcharactercreation.appearance.AppearancePreset preset=(com.lotrcharactercreation.appearance.AppearancePreset)selected.invoke(mc.currentScreen);check(preset!=null,"native Elven preview preset available");
                com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache.getInstance().update(mc.thePlayer.getUniqueID(),mc.thePlayer.getEntityId(),PlayerRace.ELF,PlayerSex.MALE,preset.getId(),true);
                mc.gameSettings.thirdPersonView=1;mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=15;
                lotr.common.entity.npc.LOTREntityDunlendingWarlord npc=new lotr.common.entity.npc.LOTREntityDunlendingWarlord(mc.theWorld);npc.setPositionAndRotation(mc.thePlayer.posX+1.6,mc.thePlayer.posY,mc.thePlayer.posZ+0.7,180,0);
                kome.common.data.KOMEProgressionNativeAuthority.applyName(npc,new kome.common.data.KOMEProgressionNpcRankRecord(npc.getUniqueID(),"dunland",kome.common.data.KOMEProgressionNpcRank.PRINCE,"Gair"));mc.theWorld.spawnEntityInWorld(npc);
                gift=new KOMEVisualMarker(KOMEVisualMarker.Role.MASTER_GIFT,npc.getUniqueID().toString(),"Parting Gift","",mc.thePlayer.dimension,npc.posX,npc.posY,npc.posZ);KOMEVisualMarkerClientState.update(Collections.singletonList(gift));mc.displayGuiScreen(null);stage=9;ticks=0;return;
            }
            if(stage==9){mc.inGameHasFocus=false;mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=15;KOMEVisualMarkerClientState.update(Collections.singletonList(gift));if(++ticks==18)capture(mc,"elven-feet-and-generic-pouch");if(ticks<27)return;capture(mc,"generic-pouch-front");
                Field fx=lotr.client.fx.LOTREffectRenderer.class.getDeclaredField("particleLayers");fx.setAccessible(true);int count=0;for(Object layer:(Object[])fx.get(lotr.client.LOTRClientProxy.customEffectRenderer))for(Object particle:(java.util.List)layer)if(particle.getClass().getName().contains("KOMEElvenFootGlow$FootParticle"))count++;
                check(count>0&&count<=3,"bounded native Elven foot particles active in world rendering");evidence.append("PARTICLES ").append(count).append('\n');
                evidence.append("PASS controlled native GUI fixture; screenshots require visual review\n");finish(mc);stage=10;}
        }catch(Throwable failure){evidence.append("FAIL ").append(failure).append('\n');failure.printStackTrace();try{finish(mc);}catch(Exception ignored){}stage=10;}
    }
    private float[] coords(LOTRGuiMap map)throws Exception{Method m=LOTRGuiMap.class.getDeclaredMethod("transformCoords",float.class,float.class);m.setAccessible(true);return (float[])m.invoke(map,(float)destination.x,(float)destination.z);}
    private void finish(Minecraft mc)throws Exception{Files.write(mc.mcDataDir.toPath().resolve("progression-client.txt"),evidence.toString().getBytes(StandardCharsets.UTF_8));mc.shutdown();}
}
