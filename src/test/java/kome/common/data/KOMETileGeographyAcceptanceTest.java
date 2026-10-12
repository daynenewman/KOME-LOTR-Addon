package kome.common.data;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMETileResolution.Status.*;

/** Independent whole-raster pixel oracle on current bundled geometry, with only the separately approved mountain-cell classifications. */
public class KOMETileGeographyAcceptanceTest {
    @Test public void everyCanonicalCellAgreesWithIndependentImageIdentity() throws Exception {
        BufferedImage image = ImageIO.read(getClass().getClassLoader().getResource(KOMETileWorldResolver.MASK));
        KOMETileRasterSnapshot s = KOMETileTestResources.real(); int d = s.transform.dimension;
        java.util.Map<Integer,KOMEMountainSeparationTest.Cell> approved = KOMEMountainSeparationTest.cells();
        long tiles = 0, gaps = 0, classified = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                String id = (argb >>> 24) <= 24 ? null : KOMEConquestTileDefaults.getTileIdsByColor().get(argb & 0xFFFFFF);
                if (id != null && KOMEConquestTileDefaults.getRetiredTileIds().contains(id)) id = null;
                // Independent packaged control transform: same dimensions; 128 blocks/cell.
                KOMETileResolution actual = s.resolve(d, (x - 810) * 128, (y - 730) * 128);
                KOMEMountainSeparationTest.Cell cell = approved.get(y * 3200 + x);
                if (cell != null) { assertEquals(0, argb); KOMEMountainSeparationTest.assertMountain(actual, cell.zone); classified++; continue; }
                if (actual.status != (id == null ? IN_BOUNDS_GAP : RESOLVED)
                        || !actual.tileId.equals(id == null ? "" : id)
                        || actual.exclusion().isPresent() || actual.capturable().isPresent()
                        || actual.traversable().isPresent()) fail("Cell mismatch at " + x + "," + y);
                if (id == null) gaps++; else tiles++;
            }
        }
        assertEquals(621, s.colorsById().size());
        assertEquals(8732021L, gaps); assertEquals(4067934L, tiles); assertEquals(45L, classified);
        System.out.println("KOM63_WHOLE_RASTER cells=" + (tiles + gaps + classified) + " tiles=" + tiles + " gaps=" + gaps
            + " classified=" + classified + " mountainBatchApproved=true otherGapsUnclassified=true");
    }
}
