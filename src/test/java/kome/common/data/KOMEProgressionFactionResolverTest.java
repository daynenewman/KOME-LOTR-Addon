package kome.common.data;

import java.util.UUID;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionFactionResolverTest {
    @Test public void everyPlayableFactionRoundTripsThroughPersistedNpcAndAllianceKeys() {
        int checked=0;
        for(LOTRFaction faction:LOTRFaction.getPlayableAlignmentFactions()) {
            KOMEProgressionNpcRef ref=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"NPC",faction.codeName(),0,0,64,0);
            KOMEProgressionNpcRef reloaded=KOMEProgressionNpcRef.readFromNBT(ref.writeToNBT());
            assertSame(faction.codeName(),faction,KOMEProgressionFactionResolver.resolve(reloaded.factionKey));
            assertSame(faction.codeName(),faction,KOMEProgressionFactionResolver.resolve(KOMEAlliance.normalizeFactionKey(faction.codeName())));
            checked++;
        }
        assertTrue(checked>20);
    }

    @Test public void dorwinionRohanAndMalformedKeys() {
        assertNull(LOTRFaction.forName("dorwinion")); // Native lookup is case-sensitive.
        assertEquals("DORWINION",LOTRFaction.DORWINION.codeName());
        assertSame(LOTRFaction.DORWINION,KOMEProgressionFactionResolver.resolve("dorwinion"));
        assertSame(LOTRFaction.ROHAN,KOMEProgressionFactionResolver.resolve("rohan"));
        assertSame(LOTRFaction.ROHAN,KOMEProgressionFactionResolver.resolve("ROHAN"));
        assertNull(KOMEProgressionFactionResolver.resolve(null));
        assertNull(KOMEProgressionFactionResolver.resolve(""));
        assertNull(KOMEProgressionFactionResolver.resolve("dorwinion!"));
        assertNull(KOMEProgressionFactionResolver.resolve("unknown_faction"));
    }

    @Test public void existingCourierSaveKeepsItsKeyAndResolves() {
        KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","DORWINION",0,1000,64,1000);
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.create(master,LOTRWaypoint.DORWINION_COURT);
        NBTTagCompound old=assignment.writeToNBT();old.setInteger("Version",5);
        old.setString("MasterFactionKey","dorwinion");old.setString("DestinationFactionKey","dorwinion");
        KOMESerfCourierAssignment restored=KOMESerfCourierAssignment.readFromNBT(old);
        assertNotNull(restored);
        assertEquals("dorwinion",restored.destinationFactionKey);
        assertSame(LOTRFaction.DORWINION,KOMEProgressionFactionResolver.resolve(restored.destinationFactionKey));
        assertEquals(LOTRFaction.DORWINION.factionName()+" Dispatch",KOMECourierService.dispatchTitle(restored));
    }
}
