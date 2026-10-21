package com.gathin.tvhome;

/**
 * Pure column and D-pad maths for a dock row that has its own column count (config dock_columns), apart
 * from the grid's (columns). The first {@code dockCols} adapter positions are the dock; the rest is the grid.
 * One GridLayoutManager serves both rows: its span count is the least common multiple of the two counts, and
 * a dock tile spans {@code total / dockCols} cells, a grid tile {@code total / cols}. No Android types, so
 * JUnit can run it.
 */
final class DockGeometry {
    private DockGeometry() {
    }

    /** Returned by {@link #up} when focus should leave the tiles for the top bar. */
    static final int TOP_BAR = -1;

    static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.abs(a);
    }

    /** Span count for the GridLayoutManager. */
    static int spanCount(int dockCols, int cols) {
        dockCols = Math.max(1, dockCols);
        cols = Math.max(1, cols);
        return dockCols / gcd(dockCols, cols) * cols;
    }

    static boolean isDock(int position, int dockCols) {
        return position < Math.max(1, dockCols);
    }

    /** Cells one tile at {@code position} spans. */
    static int spanSize(int position, int dockCols, int cols) {
        int total = spanCount(dockCols, cols);
        return total / Math.max(1, isDock(position, dockCols) ? dockCols : cols);
    }

    /** Grid column (0-based) of a grid position. */
    static int gridColumn(int position, int dockCols, int cols) {
        return (position - Math.max(1, dockCols)) % Math.max(1, cols);
    }

    /** The dock tile whose centre is nearest the middle of grid column {@code gridCol}. */
    static int dockForGridColumn(int gridCol, int dockCols, int cols) {
        dockCols = Math.max(1, dockCols);
        cols = Math.max(1, cols);
        return clamp((2 * gridCol + 1) * dockCols / (2 * cols), 0, dockCols - 1);
    }

    /** The grid column under the middle of dock tile {@code dockIndex}. */
    static int gridColumnForDock(int dockIndex, int dockCols, int cols) {
        dockCols = Math.max(1, dockCols);
        cols = Math.max(1, cols);
        return clamp((2 * dockIndex + 1) * cols / (2 * dockCols), 0, cols - 1);
    }

    /** The dock tile a position stands for: itself in the dock, the one above its column in the grid. */
    static int dockIndexFor(int position, int dockCols, int cols) {
        if (position < 0) return 0;
        if (isDock(position, dockCols)) return position;
        return dockForGridColumn(gridColumn(position, dockCols, cols), dockCols, cols);
    }

    /** True when a Left press at {@code position} must be swallowed (a row's left end). */
    static boolean blocksLeft(int position, int dockCols, int cols) {
        if (isDock(position, dockCols)) return position == 0;
        return gridColumn(position, dockCols, cols) == 0;
    }

    /** True when a Right press at {@code position} must be swallowed (a row's right end or the last tile). */
    static boolean blocksRight(int position, int count, int dockCols, int cols) {
        if (position >= count - 1) return true;
        if (isDock(position, dockCols)) return position == dockCols - 1;
        return gridColumn(position, dockCols, cols) == cols - 1;
    }

    /**
     * Target of Up, or {@link #TOP_BAR} from the dock. From the first grid row it returns to the dock tile
     * Down came from ({@code rememberedDock}) when that tile sits over this column, else the nearest one.
     */
    static int up(int position, int dockCols, int cols, int rememberedDock) {
        if (isDock(position, dockCols)) return TOP_BAR;
        int firstGrid = Math.max(1, dockCols);
        if (position - firstGrid >= cols) return position - cols;
        int gc = gridColumn(position, dockCols, cols);
        if (rememberedDock >= 0 && rememberedDock < firstGrid
                && gridColumnForDock(rememberedDock, dockCols, cols) == gc) {
            return rememberedDock;
        }
        return dockForGridColumn(gc, dockCols, cols);
    }

    /** Target of Down; the position itself when there is nothing below. */
    static int down(int position, int count, int dockCols, int cols) {
        int firstGrid = Math.max(1, dockCols);
        if (count <= firstGrid) return position;
        if (isDock(position, dockCols)) {
            int dockIndex = Math.min(position, dockCols - 1);
            return Math.min(firstGrid + gridColumnForDock(dockIndex, dockCols, cols), count - 1);
        }
        int lastRowStart = firstGrid + ((count - 1 - firstGrid) / cols) * cols;
        if (position >= lastRowStart) return position;
        return Math.min(position + cols, count - 1);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
