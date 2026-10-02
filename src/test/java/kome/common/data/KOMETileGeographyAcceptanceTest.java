package kome.common.data;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMETileResolution.Status.*;

/** Independent whole-raster pixel oracle on current bundled geometry, never a geography classification. */
public class KOMETileGeographyAcceptanceTest {
    @Test public void everyCanonicalCellAgreesWithIndependentImageIdentity() throws Exception {
        BufferedImage image = ImageIO.read(getClass().getClassLoader().getResource(KOMETileWorldResolver.MASK));
        KOMETileRasterSnapshot s = KOMETileTestResources.real(); int d = s.transform.dimension;
        long tiles = 0, gaps = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                String id = (argb >>> 24) <= 24 ? null : KOMEConquestTileDefaults.getTileIdsByColor().get(argb & 0xFFFFFF);
                if (id != null && KOMEConquestTileDefaults.getRetiredTileIds().contains(id)) id = null;
                // Independent packaged control transform: same dimensions; 128 blocks/cell.
                KOMETileResolution actual = s.resolve(d, (x - 810) * 128, (y - 730) * 128);
                if (actual.status != (id == null ? IN_BOUNDS_GAP : RESOLVED)
                        || !actual.tileId.equals(id == null ? "" : id)
                        || actual.exclusion().isPresent() || actual.capturable().isPresent()
                        || actual.traversable().isPresent()) fail("Cell mismatch at " + x + "," + y);
                if (id == null) gaps++; else tiles++;
            }
        }
        assertEquals(621, s.colorsById().size());
        assertEquals(8732021L, gaps); assertEquals(4067979L, tiles);
        System.out.println("KOM63_WHOLE_RASTER cells=" + (tiles + gaps) + " tiles=" + tiles + " gaps=" + gaps
            + " classified=0 classificationsApproved=false");
    }
}
