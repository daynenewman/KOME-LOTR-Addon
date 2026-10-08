package kome.common.data;

import java.util.UUID;
import kome.common.KOMEAccessFixture;
import lotr.common.fac.LOTRFaction;
import net.minecraft.item.ItemStack;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionBanditPressureTest {
    @Test public void regionRateDoublesOnlyForActiveCarrierAndStopsAtHandover()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=s.active(KOMEKnightCommission.Type.STOLEN_GOODS);KOMEKnightCommissionGameplayTest.goods(a);
            assertEquals(.01,KOMEProgressionBanditPressure.chance(.01,s.f.world,640,640),0);
            s.f.player.inventory.mainInventory[0]=KOMEKnightCommissionService.property(a,s.f.player.id);
            assertEquals(.02,KOMEProgressionBanditPressure.chance(.01,s.f.world,640,640),0);
            assertEquals(0,KOMEProgressionBanditPressure.chance(0,s.f.world,640,640),0);
            s.f.data.setProgressionEnabled(false);assertEquals(.01,KOMEProgressionBanditPressure.chance(.01,s.f.world,640,640),0);s.f.data.setProgressionEnabled(true);
            a.stage=KOMEKnightCommission.Stage.REPORTED;assertEquals(.01,KOMEProgressionBanditPressure.chance(.01,s.f.world,640,640),0);
        }
    }
    @Test public void anotherLocalPlayerOrWrongPledgePreventsBoost()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=s.active(KOMEKnightCommission.Type.STOLEN_GOODS);KOMEKnightCommissionGameplayTest.goods(a);s.f.player.inventory.mainInventory[0]=KOMEKnightCommissionService.property(a,s.f.player.id);
            KOMEAccessFixture other=new KOMEAccessFixture();other.player.worldObj=s.f.world;other.player.posX=640;other.player.posZ=640;s.f.world.playerEntities.add(other.player);
            assertEquals(.01,KOMEProgressionBanditPressure.chance(.01,s.f.world,640,640),0);s.f.world.playerEntities.remove(other.player);
            s.f.pledge(LOTRFaction.GONDOR);assertEquals(.01,KOMEProgressionBanditPressure.chance(.01,s.f.world,640,640),0);
        }
    }
    @Test public void duplicatedOrStalePropertyDoesNotStackMultiplier()throws Exception {
        try(KOMEKnightCommissionGameplayTest.Session s=new KOMEKnightCommissionGameplayTest.Session()){
            KOMEKnightCommission a=s.active(KOMEKnightCommission.Type.STOLEN_GOODS);KOMEKnightCommissionGameplayTest.goods(a);
            ItemStack item=KOMEKnightCommissionService.property(a,s.f.player.id);s.f.player.inventory.mainInventory[0]=item;s.f.player.inventory.mainInventory[1]=item.copy();
            assertEquals(.02,KOMEProgressionBanditPressure.chance(.01,s.f.world,640,640),0);a.revision++;
            assertEquals(.01,KOMEProgressionBanditPressure.chance(.01,s.f.world,640,640),0);
        }
    }
}
