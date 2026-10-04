package kome.common.tactical.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded advisory pages for the shared shell. No Build accounting or mutable world records. */
public final class KOMETacticalComplexCatalog {
    public enum Kind { COMPLEXES, PREFERRED_AREAS, BUILDS }
    public static final int PAGE_SIZE = 5;
    public final Kind kind;
    public final String tileId, complexId;
    public final int dimension, page, total;
    public final long revision;
    public final List<Row> rows;
    public KOMETacticalComplexCatalog(Kind kind, String tile, String complex, int dimension,
            long revision, int page, int total, List<Row> rows) {
        if (kind == null || revision < 0 || page < 0 || total < 0 || rows == null || rows.size() > PAGE_SIZE
                || (long) page * PAGE_SIZE + rows.size() > total || rows.contains(null))
            throw new IllegalArgumentException("Invalid complex editor page.");
        this.kind = kind; this.tileId = KOMETacticalEditScope.canonicalId(tile);
        this.complexId = complex == null ? null : KOMETacticalEditScope.canonicalId(complex);
        if (kind == Kind.COMPLEXES ? complex != null : complex == null) throw new IllegalArgumentException("Invalid page scope.");
        this.dimension = dimension; this.revision = revision; this.page = page; this.total = total;
        this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
    }
    public static final class Row {
        public final String id, label, detail, relatedId;
        public final long revision;
        public final int assignedBuildCount;
        public Row(String id, String label, String detail, String related, long revision, int count) {
            KOMETacticalEditScope.validateId(id);
            if (related != null) KOMETacticalEditScope.validateId(related);
            if (label == null || label.length() > 256 || detail == null || detail.length() > 512 || revision < 0 || count < 0)
                throw new IllegalArgumentException("Invalid editor row.");
            this.id = id; this.label = label; this.detail = detail; this.relatedId = related;
            this.revision = revision; this.assignedBuildCount = count;
        }
    }
}
