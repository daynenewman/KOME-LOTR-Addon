package com.lotrcharactercreation.client.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppearancePreviewOrientationTest {
    @Test public void centreHasNoRotationAndCursorAxesFollowInventoryConvention(){
        AppearancePreviewOrientation centre=new AppearancePreviewOrientation(0,0);assertEquals(0,centre.bodyYaw,0);assertEquals(0,centre.headYaw,0);assertEquals(0,centre.headPitch,0);
        AppearancePreviewOrientation rightDown=new AppearancePreviewOrientation(40,40),leftUp=new AppearancePreviewOrientation(-40,-40);
        assertTrue(rightDown.bodyYaw<0);assertTrue(rightDown.headYaw<0);assertTrue(rightDown.headPitch>0);
        assertEquals(-leftUp.bodyYaw,rightDown.bodyYaw,0);assertEquals(-leftUp.headPitch,rightDown.headPitch,0);
        assertEquals((float)(-Math.atan(1)*40),rightDown.bodyYaw+rightDown.headYaw,0.001F);
    }
    @Test public void pitchUsesRenderedHeadOriginAtEachPreviewScale(){
        for(float raceScale:new float[]{0.6F,0.8F,1F,1.2F}){
            float head=com.lotrcharactercreation.client.render.AppearancePreviewOrientation.headCentreY(200,46,raceScale);
            assertTrue(head<200);assertEquals(0,new AppearancePreviewOrientation(0,head-head).headPitch,0);
            assertTrue(new AppearancePreviewOrientation(0,head+20-head).headPitch>0);
            assertTrue(new AppearancePreviewOrientation(0,head-20-head).headPitch<0);
        }
    }
}
