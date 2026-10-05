package kome.common.tactical.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded read-only browser page. Polygon ownership stays with tile areas, not editor scopes. */
public final class KOMETacticalAreaCatalog {
    public static final int PAGE_SIZE = 5;
    public final String tileId;
    public final int dimension, page, total;
    public final long revision;
    public final List<Row> rows;
    public KOMETacticalAreaCatalog(String tile, int dimension, long revision, int page, int total, List<Row> rows) {
        this.tileId = KOMETacticalEditScope.canonicalId(tile);
        if (revision < 0 || page < 0 || total < 0 || rows == null || rows.size() > PAGE_SIZE
                || (long) page * PAGE_SIZE + rows.size() > total) throw new IllegalArgumentException("Invalid area page.");
        this.dimension = dimension; this.revision = revision; this.page = page; this.total = total;
        this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
    }
    public static final class Row {
        public final String id, label;
        public final long revision;
        public Row(String id, String label, long revision) {
            KOMETacticalEditScope.validateId(id);
            if (label == null || label.length() > 256 || revision < 0) throw new IllegalArgumentException("Invalid area row.");
            this.id = id; this.label = label; this.revision = revision;
        }
    }
}
