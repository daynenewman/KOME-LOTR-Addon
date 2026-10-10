package kome.client;
import java.util.*;
import java.lang.reflect.Field;
import kome.common.data.*;
import lotr.client.gui.LOTRGuiMap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;

public class KOMEHierarchyPresentationTest {
    private java.util.function.Supplier<LOTRGuiMap> previous;
    @org.junit.Before public void setup(){previous=KOMEProgressionMapFocus.mapFactory;KOMEProgressionMapFocus.mapFactory=()->{try{return kome.common.KOMEAccessFixture.allocate(LOTRGuiMap.class);}catch(Exception e){throw new RuntimeException(e);}};}
    @After public void clear(){KOMEVisualMarkerClientState.clear();KOMEProgressionMapFocus.mapFactory=previous;}
    private KOMEVisualMarker target(){return new KOMEVisualMarker(KOMEVisualMarker.Role.COMMISSION,"","Saved objective","",100,32100,65,-17400);}
    @Test public void showMeOpensNativeMapAndCentersSavedCameraThenExpiresOnlyEmphasis()throws Exception{
        KOMEVisualMarker target=target();KOMEVisualMarkerClientState.update(Arrays.asList(target));List<LOTRGuiMap> screens=new ArrayList<LOTRGuiMap>();
        LOTRGuiMap map=KOMEProgressionMapFocus.open(target,screens::add,0);
        assertSame(map,screens.get(0));assertEquals(1,screens.size());
        for(String name:new String[]{"posX","prevPosX"})assertEquals(KOMEProgressionMapFocus.mapX(target.x),field(map,name),0F);
        for(String name:new String[]{"posY","prevPosY"})assertEquals(KOMEProgressionMapFocus.mapY(target.z),field(map,name),0F);
        assertTrue(KOMEProgressionMapFocus.isFocused(target,9_999_999_999L));assertFalse(KOMEProgressionMapFocus.isFocused(target,10_000_000_000L));
        assertEquals(Arrays.asList(target),KOMEVisualMarkerClientState.markers());
    }
    @Test public void repeatedShowMeRestartsTimerWithoutAddingMarkerOrWaypoint()throws Exception{
        KOMEVisualMarker target=target();KOMEVisualMarkerClientState.update(Arrays.asList(target));
        KOMEProgressionMapFocus.open(target,map->{},0);KOMEProgressionMapFocus.open(target,map->{},8_000_000_000L);
        assertTrue(KOMEProgressionMapFocus.isFocused(target,17_999_999_999L));assertFalse(KOMEProgressionMapFocus.isFocused(target,18_000_000_000L));
        assertEquals(1,KOMEVisualMarkerClientState.markers().size());
    }
    @Test public void persistentRelationshipAndCrownWinOverActionIconsEvenOnCooldown(){
        KOMEVisualMarker master=new KOMEVisualMarker(KOMEVisualMarker.Role.SERFDOM_MASTER,"npc","Master","",100,0,0,0,false);
        KOMEVisualMarker courier=new KOMEVisualMarker(KOMEVisualMarker.Role.COURIER,"npc","Courier","",100,0,0,0);
        KOMEVisualMarker crown=new KOMEVisualMarker(KOMEVisualMarker.Role.RULER,"npc","King","",100,0,0,0,false);
        KOMEVisualMarkerClientState.update(Arrays.asList(courier,master));assertSame(master,KOMEVisualRenderBridge.overheadFor("npc",100));
        KOMEVisualMarkerClientState.update(Arrays.asList(master,courier,crown));assertSame(crown,KOMEVisualRenderBridge.overheadFor("npc",100));
        assertSame(KOMEProgressionVisualItems.RULER,KOMEVisualRenderBridge.icon(crown.role).getItem());
    }
    @Test public void boundBookSuppressesOrdinaryUseOnlyAtSavedRecipient(){
        ItemStack book=new ItemStack(Items.written_book);NBTTagCompound tag=new NBTTagCompound();tag.setTag("KOMECourier",new NBTTagCompound());book.setTagCompound(tag);
        KOMEVisualMarker recipient=new KOMEVisualMarker(KOMEVisualMarker.Role.COURIER,"recipient","Recipient","",100,1,2,3);
        KOMEVisualMarkerClientState.update(Arrays.asList(recipient));
        assertTrue(KOMECourierInteractionPriority.deliveryTarget(book,"recipient",100));
        assertFalse(KOMECourierInteractionPriority.deliveryTarget(book,"wrong",100));assertFalse(KOMECourierInteractionPriority.deliveryTarget(book,"recipient",0));
        assertFalse(KOMECourierInteractionPriority.deliveryTarget(new ItemStack(Items.written_book),"recipient",100));
    }
    @Test public void redSilhouetteIsPlayerSpecificLoadedBoundEnemyAtThirtyTwoToNinetySixBlocks(){
        KOMEVisualMarkerClientState.update(Arrays.asList(new KOMEVisualMarker(KOMEVisualMarker.Role.ENCOUNTER_ENEMY,"enemy","Enemy","",100,0,0,0)));
        assertFalse(KOMEProgressionEnemyOutline.relevant("enemy",100,31*31));
        assertTrue(KOMEProgressionEnemyOutline.relevant("enemy",100,32*32));assertTrue(KOMEProgressionEnemyOutline.relevant("enemy",100,96*96));
        assertFalse(KOMEProgressionEnemyOutline.relevant("enemy",100,97*97));assertFalse(KOMEProgressionEnemyOutline.relevant("other",100,64*64));assertFalse(KOMEProgressionEnemyOutline.relevant("enemy",0,64*64));
        KOMEVisualMarkerClientState.clear();assertFalse(KOMEProgressionEnemyOutline.relevant("enemy",100,64*64));
    }
    private float field(LOTRGuiMap map,String name)throws Exception{Field f=LOTRGuiMap.class.getDeclaredField(name);f.setAccessible(true);return f.getFloat(map);}
    @Test public void nativeIndicatorSuppressionReturnsWithPlayerSpecificRelevance(){
        KOMEVisualMarker participant=new KOMEVisualMarker(KOMEVisualMarker.Role.PARTICIPANT,"guard","","",100,0,0,0,false);
        KOMEVisualMarkerClientState.update(Arrays.asList(participant));assertTrue(KOMEVisualMarkerClientState.relevant("guard",100));
        assertNull(KOMEVisualRenderBridge.overheadFor("guard",100));assertFalse(KOMEVisualMarkerClientState.relevant("other",100));
        KOMEVisualMarkerClientState.update(Collections.emptyList());assertFalse(KOMEVisualMarkerClientState.relevant("guard",100));
    }
    @Test public void packetIncludesAllTwentyThreeCrownsAndObjectiveParticipants(){
        List<KOMEVisualMarker> markers=new ArrayList<KOMEVisualMarker>();
        for(int i=0;i<23;i++)markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.RULER,UUID.randomUUID().toString(),"King","",100,i,65,i,false));
        for(int i=0;i<16;i++)markers.add(new KOMEVisualMarker(KOMEVisualMarker.Role.PARTICIPANT,UUID.randomUUID().toString(),"","",100,0,0,0,false));
        io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer();
        new kome.common.network.KOMEPacketVisualMarkers(markers).toBytes(buffer);
        kome.common.network.KOMEPacketVisualMarkers decoded=new kome.common.network.KOMEPacketVisualMarkers();decoded.fromBytes(buffer);
        assertEquals(39,decoded.markers.size());assertEquals(0,buffer.readableBytes());
    }
    @Test(expected=IllegalArgumentException.class) public void oversizedMarkerPacketStillFailsClosed(){
        io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer();buffer.writeByte(65);
        new kome.common.network.KOMEPacketVisualMarkers().fromBytes(buffer);
    }
}
