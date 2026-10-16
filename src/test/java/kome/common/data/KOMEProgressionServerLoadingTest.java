package kome.common.data;

import java.net.URLClassLoader;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises initialization with all client package loading forbidden. */
public class KOMEProgressionServerLoadingTest {
    @Test public void helperAndProtocolInitializeWithNoClientClasses()throws Exception {
        try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{KOMEProgressionTrackerSnapshot.class.getProtectionDomain().getCodeSource().getLocation()},getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(name.startsWith("kome.client.")||name.startsWith("lotr.client.")||name.startsWith("net.minecraft.client."))throw new ClassNotFoundException("Client class on server: "+name);
                if(name.startsWith("kome.")) {
                    synchronized(getClassLoadingLock(name)) {
                        Class<?> result=findLoadedClass(name);
                        if(result==null)try{result=findClass(name);}catch(ClassNotFoundException absent){result=super.loadClass(name,false);}
                        if(resolve)resolveClass(result);return result;
                    }
                }
                return super.loadClass(name,resolve);
            }
        }) {
            Class<?> snapshot=Class.forName("kome.common.data.KOMEProgressionTrackerSnapshot",true,loader);
            assertNotNull(snapshot.getField("EMPTY").get(null));
            Class<?> eligibility=Class.forName("kome.common.data.KOMEStandingTrialEligibility",true,loader);
            assertEquals(250,eligibility.getMethod("requiredAlignment",String.class).invoke(null,"WOOD_ELF"));
            assertNotNull(Class.forName("kome.common.network.KOMEPacketProgressionTracker",true,loader).newInstance());
            Class.forName("kome.common.data.KOMESerfProvisioningAssignment",true,loader);
            Class.forName("kome.common.data.KOMESerfCourierAssignment",true,loader);
            Class.forName("kome.common.data.KOMECourierIssuance",true,loader);
            Class.forName("kome.common.data.KOMECourierCorrespondence",true,loader);
            Class.forName("kome.common.data.KOMEPartingGiftService",true,loader);
            Class.forName("kome.common.data.KOMELocalProvisionFoods",true,loader);
            Class.forName("kome.common.data.KOMECanonicalRankService",true,loader);
            Class.forName("kome.common.data.KOMEServicePromotionService",true,loader);
            Class.forName("kome.common.data.KOMEServiceRecognition",true,loader);
            Class.forName("kome.common.data.KOMERecommendationService",true,loader);
            Class.forName("kome.common.data.KOMESerfServiceObjectives",true,loader);
            Class<?> seats=Class.forName("kome.common.data.KOMEProgressionServiceLocation",true,loader);
            assertFalse(((java.util.Map)seats.getMethod("supportedHosts").invoke(null)).isEmpty());
            Class.forName("kome.core.KOMEProgressionSeatTransformer",true,loader);
            for(String type:new String[]{"KOMEKnightFieldService","KOMEKnightEspionage","KOMEKnightHelpService","KOMEKnightBattlefieldService"})Class.forName("kome.common.data."+type,true,loader);
            Class.forName("kome.core.KOMEKnightPickpocketTransformer",true,loader);
        }
    }
}
