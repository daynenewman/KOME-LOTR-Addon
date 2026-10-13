package kome.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kome.common.data.KOMEConquestTile;

/** Client interaction only. No company eligibility, movement orders, or server queries. */
final class KOMEMapRadialMenu {
    static final double INNER_RADIUS = 42, OUTER_RADIUS = 72;
    static final int PIXEL_GRID = 2;
    static final double FONT_SCALE = 0.9;
    private static final double MAX_PHYSICAL_GUI_FACTOR = 2.55;
    enum State { ROOT, MOVEMENT, BATTLE }
    enum Action {
        MOVEMENT("Move", true, "Open movement prototype"),
        BATTLE("Battle", true, "Open battle shell"),
        COMPANIES("Units", false, "Company browsing is not wired in this prototype"),
        INFO("Info", true, "Open existing tile information"),
        CLOSE("Close", true, "Dismiss this ring"),
        MOVE_HERE("Move Here", true, "Choose a company and preview its route"),
        REINFORCE("Reinforce", true, "Choose a company to move to this battle"),
        ATTACK("Attack", true, "Choose a company and preview hostile movement"),
        BATTLE_ACTIONS("Battle", false, "Battle actions are not wired in this prototype"),
        BACK("Back", true, "Return to the first ring");
        final String label, description;
        final boolean enabled;
        Action(String label, boolean enabled, String description) {
            this.label = label; this.enabled = enabled; this.description = description;
        }
    }
    static final double MIN_GAP_DEGREES = 8;
    // Clockwise ordering; priority changes placement, never sibling size.
    // Root starts upper-left, flows through upper-right/lower-right/bottom/lower-left.
    private static final List<Segment> ROOT = layout(44, -126,
        Action.MOVEMENT, Action.BATTLE, Action.INFO, Action.CLOSE, Action.COMPANIES);
    private static final List<Segment> MOVEMENT = layout(56, -90, Action.MOVE_HERE, Action.BACK);
    private static final List<Segment> BATTLE = layout(44, -90, Action.BATTLE_ACTIONS, Action.BACK);
    private static final List<Segment> REINFORCEMENT = layout(56,-90,Action.REINFORCE,Action.BACK);
    private static final List<Segment> ATTACK = layout(56,-90,Action.ATTACK,Action.BACK);
    private boolean activeBattle, knownHostile;
    private String tileId = "";
    private State state = State.ROOT;
    boolean open(String tile) {
        if (!KOMEConquestTile.isCanonicalTileId(tile)) return false;
        tileId = tile; state = State.ROOT; activeBattle=false;knownHostile=false;return true;
    }
    void close() { tileId = ""; state = State.ROOT; }
    boolean isOpen() { return !tileId.isEmpty(); }
    String tileId() { return tileId; }
    State state() { return state; }
    void battleContext(boolean active,boolean hostile) {activeBattle=active;knownHostile=hostile;}
    List<Segment> segments() { return state == State.MOVEMENT ? MOVEMENT : state == State.BATTLE ? activeBattle?REINFORCEMENT:knownHostile?ATTACK:BATTLE : ROOT; }
    Action activate(int index) {
        if (!isOpen() || index < 0 || index >= segments().size()) return null;
        Action action = segments().get(index).action;
        if (!action.enabled) return null;
        if (action == Action.MOVEMENT) state = State.MOVEMENT;
        else if (action == Action.BATTLE) state = State.BATTLE;
        else if (action == Action.BACK) state = State.ROOT;
        else if (action == Action.CLOSE) close();
        return action;
    }
    int hit(double dx, double dy) { return hit(dx, dy, 1); }
    int hit(double dx, double dy, double scale) {
        if (!isOpen() || !Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(scale) || scale <= 0) return -1;
        dx /= scale; dy /= scale;
        if (Math.abs(dx) >= OUTER_RADIUS || Math.abs(dy) >= OUTER_RADIUS) return -1;
        int gx = (int)Math.floor(dx / PIXEL_GRID), gy = (int)Math.floor(dy / PIXEL_GRID);
        for (int i = 0; i < segments().size(); i++) if (segments().get(i).containsCell(gx, gy)) return i;
        return -1;
    }
    /** Preserve Small/Normal; cap physical-pixel growth above Normal, independently of map zoom. */
    static double scaleForGuiFactor(int factor) {
        return Math.min(1, MAX_PHYSICAL_GUI_FACTOR / Math.max(1, factor));
    }
    /** Keep comfortable sibling widths, consume gaps first, then shrink every sibling uniformly. */
    static List<Segment> layout(double preferredWidth, double firstCenter, Action... actions) {
        if (actions.length == 0) return Collections.emptyList();
        double spacing = 360.0 / actions.length;
        double width = Math.min(preferredWidth, spacing - MIN_GAP_DEGREES);
        if (width <= 0 || width >= 180) throw new IllegalArgumentException("Unsupported radial layout");
        List<Segment> result = new ArrayList<Segment>();
        for (int i = 0; i < actions.length; i++)
            result.add(new Segment(actions[i], firstCenter + i * spacing, width));
        return Collections.unmodifiableList(result);
    }
    /** Immutable shared drawing/hit geometry. Only complete grid cells inside the arc/annulus are occupied. */
    static final class Segment {
        final Action action;
        final double centerDegrees, widthDegrees;
        final List<Cell> cells;
        private final Set<Long> occupied;
        Segment(Action action, double centerDegrees, double widthDegrees) {
            this.action = action; this.centerDegrees = centerDegrees; this.widthDegrees = widthDegrees;
            Set<Long> mask = new HashSet<Long>();
            int bound = (int)Math.ceil(OUTER_RADIUS / PIXEL_GRID);
            for (int gy = -bound; gy < bound; gy++) for (int gx = -bound; gx < bound; gx++) {
                double x = gx * PIXEL_GRID, y = gy * PIXEL_GRID;
                double nearX = Math.max(x, Math.min(0, x + PIXEL_GRID));
                double nearY = Math.max(y, Math.min(0, y + PIXEL_GRID));
                if (Math.hypot(nearX, nearY) < INNER_RADIUS) continue;
                boolean inside = true;
                for (int corner = 0; corner < 4; corner++) {
                    double cx = x + (corner % 2) * PIXEL_GRID, cy = y + (corner / 2) * PIXEL_GRID;
                    double angle = Math.toDegrees(Math.atan2(cy, cx));
                    double delta = ((angle - centerDegrees + 540) % 360) - 180;
                    if (Math.hypot(cx, cy) > OUTER_RADIUS || Math.abs(delta) > widthDegrees / 2) inside = false;
                }
                if (inside) mask.add(key(gx, gy));
            }
            occupied = Collections.unmodifiableSet(mask);
            List<Cell> pixels = new ArrayList<Cell>();
            for (int gy = -bound; gy < bound; gy++) for (int gx = -bound; gx < bound; gx++) {
                if (!containsCell(gx, gy)) continue;
                int edges = (!containsCell(gx-1,gy) ? Cell.LEFT : 0) | (!containsCell(gx+1,gy) ? Cell.RIGHT : 0)
                    | (!containsCell(gx,gy-1) ? Cell.TOP : 0) | (!containsCell(gx,gy+1) ? Cell.BOTTOM : 0);
                pixels.add(new Cell(gx * PIXEL_GRID, gy * PIXEL_GRID, edges));
            }
            cells = Collections.unmodifiableList(pixels);
        }
        boolean containsCell(int gx, int gy) { return occupied.contains(key(gx, gy)); }
        double labelX() { return Math.cos(Math.toRadians(centerDegrees)) * (INNER_RADIUS + OUTER_RADIUS) / 2; }
        double labelY() { return Math.sin(Math.toRadians(centerDegrees)) * (INNER_RADIUS + OUTER_RADIUS) / 2; }
        private static long key(int gx, int gy) { return ((long)gx << 32) ^ (gy & 0xFFFFFFFFL); }
    }
    static final class Cell {
        static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM = 8;
        final int x, y, edges;
        Cell(int x, int y, int edges) { this.x = x; this.y = y; this.edges = edges; }
    }
}
