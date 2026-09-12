package com.enovak.lotrmoremobs.siege.gate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Side-effect-free 2D enclosure finder used by gate creation/edit shortcuts.
 *
 * A gate is vertical and at most two blocks thick, so a fill always occurs in
 * one X/Y or Z/Y layer. Existing same-leaf selections form the boundary. The
 * seed is the block the player shift-right-clicked inside that boundary.
 */
public final class GateEnclosedAreaFill {

    private GateEnclosedAreaFill() {
    }

    public static Result find(
            Collection<Position> boundaryPositions,
            Position seed,
            GateOrientation forcedOrientation
    ) {
        if (boundaryPositions == null
                || boundaryPositions.isEmpty()
                || seed == null) {
            return Result.notEnclosed();
        }

        if (forcedOrientation != null) {
            Candidate candidate = findForOrientation(
                    boundaryPositions,
                    seed,
                    forcedOrientation
            );
            return candidate == null
                    ? Result.notEnclosed()
                    : Result.success(
                            forcedOrientation,
                            candidate.positions
                    );
        }

        Candidate widthX = findForOrientation(
                boundaryPositions,
                seed,
                GateOrientation.WIDTH_X
        );
        Candidate widthZ = findForOrientation(
                boundaryPositions,
                seed,
                GateOrientation.WIDTH_Z
        );

        if (widthX != null && widthZ != null) {
            return Result.ambiguous();
        }
        if (widthX != null) {
            return Result.success(
                    GateOrientation.WIDTH_X,
                    widthX.positions
            );
        }
        if (widthZ != null) {
            return Result.success(
                    GateOrientation.WIDTH_Z,
                    widthZ.positions
            );
        }
        return Result.notEnclosed();
    }

    private static Candidate findForOrientation(
            Collection<Position> boundaryPositions,
            Position seed,
            GateOrientation orientation
    ) {
        int fixedCoordinate = orientation == GateOrientation.WIDTH_X
                ? seed.z
                : seed.x;
        int seedU = orientation == GateOrientation.WIDTH_X
                ? seed.x
                : seed.z;

        Set<Cell> boundary = new HashSet<Cell>();
        int minU = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Position position : boundaryPositions) {
            if (position == null) {
                continue;
            }
            int fixed = orientation == GateOrientation.WIDTH_X
                    ? position.z
                    : position.x;
            if (fixed != fixedCoordinate) {
                continue;
            }
            int u = orientation == GateOrientation.WIDTH_X
                    ? position.x
                    : position.z;
            Cell cell = new Cell(u, position.y);
            boundary.add(cell);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minY = Math.min(minY, position.y);
            maxY = Math.max(maxY, position.y);
        }

        if (boundary.size() < 4
                || minU == Integer.MAX_VALUE
                || maxU - minU + 1 < 3
                || maxY - minY + 1 < 3
                || maxU - minU + 1 > GateStructureValidator.MAX_GATE_WIDTH
                || maxY - minY + 1 > GateStructureValidator.MAX_GATE_HEIGHT) {
            return null;
        }

        Cell seedCell = new Cell(seedU, seed.y);
        if (boundary.contains(seedCell)
                || seedU <= minU
                || seedU >= maxU
                || seed.y <= minY
                || seed.y >= maxY) {
            return null;
        }

        int searchMinU = minU - 1;
        int searchMaxU = maxU + 1;
        int searchMinY = minY - 1;
        int searchMaxY = maxY + 1;

        Queue<Cell> pending = new ArrayDeque<Cell>();
        Set<Cell> visited = new HashSet<Cell>();
        pending.add(seedCell);
        visited.add(seedCell);

        while (!pending.isEmpty()) {
            Cell current = pending.remove();

            if (current.u == searchMinU
                    || current.u == searchMaxU
                    || current.y == searchMinY
                    || current.y == searchMaxY) {
                return null;
            }

            visitNeighbor(
                    current.u - 1,
                    current.y,
                    searchMinU,
                    searchMaxU,
                    searchMinY,
                    searchMaxY,
                    boundary,
                    visited,
                    pending
            );
            visitNeighbor(
                    current.u + 1,
                    current.y,
                    searchMinU,
                    searchMaxU,
                    searchMinY,
                    searchMaxY,
                    boundary,
                    visited,
                    pending
            );
            visitNeighbor(
                    current.u,
                    current.y - 1,
                    searchMinU,
                    searchMaxU,
                    searchMinY,
                    searchMaxY,
                    boundary,
                    visited,
                    pending
            );
            visitNeighbor(
                    current.u,
                    current.y + 1,
                    searchMinU,
                    searchMaxU,
                    searchMinY,
                    searchMaxY,
                    boundary,
                    visited,
                    pending
            );
        }

        if (visited.isEmpty()
                || visited.size() > GateStructureValidator.MAX_GATE_PARTS) {
            return null;
        }

        List<Position> positions = new ArrayList<Position>(visited.size());
        for (Cell cell : visited) {
            positions.add(
                    orientation == GateOrientation.WIDTH_X
                            ? new Position(cell.u, cell.y, fixedCoordinate)
                            : new Position(fixedCoordinate, cell.y, cell.u)
            );
        }
        return new Candidate(positions);
    }

    private static void visitNeighbor(
            int u,
            int y,
            int minU,
            int maxU,
            int minY,
            int maxY,
            Set<Cell> boundary,
            Set<Cell> visited,
            Queue<Cell> pending
    ) {
        if (u < minU || u > maxU || y < minY || y > maxY) {
            return;
        }
        Cell next = new Cell(u, y);
        if (!boundary.contains(next) && visited.add(next)) {
            pending.add(next);
        }
    }

    public static final class Position {
        public final int x;
        public final int y;
        public final int z;

        public Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Position)) {
                return false;
            }
            Position position = (Position)other;
            return x == position.x
                    && y == position.y
                    && z == position.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return 31 * result + z;
        }
    }

    public static final class Result {
        private final boolean success;
        private final boolean ambiguous;
        private final GateOrientation orientation;
        private final List<Position> positions;

        private Result(
                boolean success,
                boolean ambiguous,
                GateOrientation orientation,
                List<Position> positions
        ) {
            this.success = success;
            this.ambiguous = ambiguous;
            this.orientation = orientation;
            this.positions = positions == null
                    ? java.util.Collections.<Position>emptyList()
                    : java.util.Collections.unmodifiableList(positions);
        }

        private static Result success(
                GateOrientation orientation,
                List<Position> positions
        ) {
            return new Result(true, false, orientation, positions);
        }

        private static Result ambiguous() {
            return new Result(false, true, null, null);
        }

        private static Result notEnclosed() {
            return new Result(false, false, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isAmbiguous() {
            return ambiguous;
        }

        public GateOrientation getOrientation() {
            return orientation;
        }

        public List<Position> getPositions() {
            return positions;
        }
    }

    private static final class Candidate {
        private final List<Position> positions;

        private Candidate(List<Position> positions) {
            this.positions = positions;
        }
    }

    private static final class Cell {
        private final int u;
        private final int y;

        private Cell(int u, int y) {
            this.u = u;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Cell)) {
                return false;
            }
            Cell cell = (Cell)other;
            return u == cell.u && y == cell.y;
        }

        @Override
        public int hashCode() {
            return 31 * u + y;
        }
    }
}
