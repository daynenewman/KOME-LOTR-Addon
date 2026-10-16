package kome.common.data;

import java.util.*;
import lotr.common.LOTRDimension;
import lotr.common.fac.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEKnightCommission.*;

public class KOMEKnightFieldGeographyTest {
    @Test public void raidAndEspionageRequireActualEnemyPresenceBeyondFriendlyLand()throws Exception{
        try(KOMELordshipTrialFixture f=new KOMELordshipTrialFixture();KOMEProgressionHardeningNativeFixture nativeData=new KOMEProgressionHardeningNativeFixture()){
            KOMEProgressionHardeningGeographyTest.territory(f);f.s.chunks=false;f.s.f.player.posZ=0;
            LOTRFactionRelations.overrideRelations(LOTRFaction.ROHAN,LOTRFaction.DUNLAND,LOTRFactionRelations.Relation.ENEMY);
            try{
                KOMEProgressionNpcRef ref=new KOMEProgressionNpcRef(f.lord.getUniqueID().toString(),"Marshal","rohan",LOTRDimension.MIDDLE_EARTH.dimensionID,0,65,0);
                for(Type type:Arrays.asList(Type.RAID_CAMP,Type.WAR_PLANS))assertNull("Distance inside friendly land cannot manufacture an enemy post",KOMEKnightCommissionLocations.choose(ref,f.s.f.world,type,"fixed",null,3));
                KOMEProgressionGameplayFixture.border(f.s.f.world,LOTRFaction.DUNLAND);
                for(Type type:Arrays.asList(Type.RAID_CAMP,Type.WAR_PLANS)){
                    KOMEKnightCommissionLocations.Destination d=KOMEKnightCommissionLocations.choose(ref,f.s.f.world,type,"fixed",null,3);assertNotNull(type.name(),d);
                    LOTRFaction enemy=KOMEProgressionFactionResolver.resolve(d.proof.getString("HostileFaction"));assertEquals(LOTRFaction.DUNLAND,enemy);assertTrue(d.z>600);
                    assertTrue(KOMEKnightCommissionLocations.hostileTerritory(f.s.f.world,LOTRFaction.ROHAN,enemy,d.x,d.z));assertFalse(KOMEKnightCommissionLocations.usable(f.s.f.world,LOTRFaction.ROHAN,d.x,d.z));
                    assertEquals(d.x,KOMEKnightCommissionLocations.choose(ref,f.s.f.world,type,"fixed",null,3).x,0);assertEquals(0,f.s.f.world.terrainProbes);
                    f.s.f.world.provider.dimensionId=12345;assertFalse(KOMEKnightCommissionLocations.hostileTerritory(f.s.f.world,LOTRFaction.ROHAN,enemy,d.x,d.z));f.s.f.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;
                }
            }finally{LOTRFactionRelations.overrideRelations(LOTRFaction.ROHAN,LOTRFaction.DUNLAND,LOTRFactionRelations.Relation.NEUTRAL);}
        }
    }
    @Test public void serializedLegacyIdsStayInTheirOriginalOrder(){assertArrayEquals(new Type[]{Type.SETTLEMENT_DEFENSE,Type.STOLEN_GOODS,Type.BORDER_INCURSION,Type.DANGEROUS_ESCORT,Type.RELIEF,Type.NATIVE_INVASION},Arrays.copyOf(Type.values(),6));}
}
