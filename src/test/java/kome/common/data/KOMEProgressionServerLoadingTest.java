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
        }
    }
}
