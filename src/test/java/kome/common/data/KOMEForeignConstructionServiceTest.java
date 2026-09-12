package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEForeignConstructionServiceTest {
    @Test public void rulerGrantRevokeAndPlacementAreExplicit() {
        KOMEWorldData data = data(); UUID ruler = UUID.randomUUID(); KOMERulerService.assignRuler(data, "rohan", ruler, "Ruler");
        assertFalse(KOMEForeignConstructionService.canConstruct(data, "T100", UUID.randomUUID(), "gondor").allowed);
        assertFalse(KOMEForeignConstructionService.grant(data, "T100", UUID.randomUUID(), "gondor", 1L).allowed);
        assertTrue(KOMEForeignConstructionService.grant(data, "T100", ruler, "gondor", 2L).allowed);
        assertTrue(KOMEForeignConstructionService.canConstruct(data, "T100", UUID.randomUUID(), "gondor").allowed);
        assertTrue(KOMEForeignConstructionService.revoke(data, "T100", ruler, "gondor").allowed);
        assertFalse(KOMEForeignConstructionService.canConstruct(data, "T100", UUID.randomUUID(), "gondor").allowed);
    }
    @Test public void permissionsPersistAndRevocationDoesNotAlterExistingBuild() {
        KOMEWorldData data = data(); UUID ruler = UUID.randomUUID(), builder = UUID.randomUUID(); KOMERulerService.assignRuler(data,"rohan",ruler,"Ruler");
        assertTrue(KOMEForeignConstructionService.grant(data,"T100",ruler,"gondor",4L).allowed);
        KOMEPlayerBuild build = KOMEBuildService.create(data,"Foreign Hall","T100",0,0,64,0,builder,"Builder","gondor","gondor",KOMEBuildType.NORMAL,2,5L);
        NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag); KOMEWorldData restored = new KOMEWorldData("restored"); restored.readFromNBT(tag);
        assertEquals(1,restored.foreignConstructionPermissions.size()); assertNotNull(restored.getBuild(build.id));
        assertTrue(KOMEForeignConstructionService.revoke(restored,"T100",ruler,"gondor").allowed);
        assertNotNull(restored.getBuild(build.id)); assertEquals(2,restored.getBuild(build.id).approvedHalfHours()); assertEquals("gondor",restored.getBuild(build.id).populationFaction);
    }
    @Test public void oldDiplomacyDoesNotBypassGrant() {
        KOMEWorldData data=data(); UUID ruler=UUID.randomUUID(); KOMERulerService.assignRuler(data,"rohan",ruler,"Ruler");
        KOMEDiplomacyService.requestIncrease(data,"rohan","gondor",KOMEDiplomacyRelation.ALLIES,ruler,1L);
        assertFalse(KOMEForeignConstructionService.canConstruct(data,"T100",UUID.randomUUID(),"gondor").allowed);
    }
    private static KOMEWorldData data() { KOMEWorldData data=new KOMEWorldData("foreign"); KOMEConquestTile tile=new KOMEConquestTile("T100"); tile.defaultRulingFaction="rohan"; tile.claim("rohan",1L); data.conquestTiles.put(tile.id,tile); return data; }
}
