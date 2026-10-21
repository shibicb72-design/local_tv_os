package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockGeometryTest {
    @Test
    public void spanCountIsLeastCommonMultiple() {
        assertEquals(20, DockGeometry.spanCount(5, 4));
        assertEquals(4, DockGeometry.spanCount(4, 4));
        assertEquals(6, DockGeometry.spanCount(6, 6));
        assertEquals(12, DockGeometry.spanCount(3, 4));
        assertEquals(1, DockGeometry.spanCount(0, 0));
    }

    @Test
    public void spanSizesFillExactlyOneRow() {
        assertEquals(4, DockGeometry.spanSize(0, 5, 4));
        assertEquals(4, DockGeometry.spanSize(4, 5, 4));
        assertEquals(5, DockGeometry.spanSize(5, 5, 4));
        assertEquals(5, DockGeometry.spanSize(40, 5, 4));
        assertEquals(1, DockGeometry.spanSize(2, 4, 4));
    }

    @Test
    public void equalCountsBehaveLikeTheOldGrid() {
        int count = 14;
        assertTrue(DockGeometry.blocksLeft(0, 4, 4));
        assertTrue(DockGeometry.blocksLeft(4, 4, 4));
        assertFalse(DockGeometry.blocksLeft(5, 4, 4));
        assertTrue(DockGeometry.blocksRight(3, count, 4, 4));
        assertTrue(DockGeometry.blocksRight(7, count, 4, 4));
        assertFalse(DockGeometry.blocksRight(2, count, 4, 4));
        assertEquals(6, DockGeometry.down(2, count, 4, 4));
        assertEquals(10, DockGeometry.down(6, count, 4, 4));
        assertEquals(13, DockGeometry.down(10, count, 4, 4));
        assertEquals(13, DockGeometry.down(13, count, 4, 4));
        assertEquals(2, DockGeometry.up(6, 4, 4, -1));
        assertEquals(6, DockGeometry.up(10, 4, 4, -1));
        assertEquals(DockGeometry.TOP_BAR, DockGeometry.up(2, 4, 4, -1));
    }

    @Test
    public void rightWalksAllFiveDockTilesThenStops() {
        int count = 20;
        for (int p = 0; p < 4; p++) assertFalse("tile " + p, DockGeometry.blocksRight(p, count, 5, 4));
        assertTrue(DockGeometry.blocksRight(4, count, 5, 4));
        assertTrue(DockGeometry.blocksLeft(0, 5, 4));
        for (int p = 1; p < 5; p++) assertFalse(DockGeometry.blocksLeft(p, 5, 4));
    }

    @Test
    public void gridRowsKeepTheirOwnWidthBelowAFiveWideDock() {
        int count = 5 + 9;   // 5 dock tiles, then rows of 4, 4, 1
        assertTrue(DockGeometry.blocksLeft(5, 5, 4));
        assertTrue(DockGeometry.blocksRight(8, count, 5, 4));
        assertFalse(DockGeometry.blocksRight(7, count, 5, 4));
        assertTrue(DockGeometry.blocksRight(count - 1, count, 5, 4));
        assertEquals(9, DockGeometry.down(5, count, 5, 4));
        assertEquals(13, DockGeometry.down(9, count, 5, 4));
        assertEquals(13, DockGeometry.down(13, count, 5, 4));
        assertEquals(5, DockGeometry.up(9, 5, 4, -1));
    }

    @Test
    public void downFromEveryDockTileLandsOnTheGridRowBelow() {
        int count = 25;
        // Tile centres at 10%, 30%, 50%, 70%, 90% of the row; grid columns span 25% each.
        int[] cols = {0, 1, 2, 2, 3};
        for (int c = 0; c < 5; c++) {
            assertEquals("dock " + c, 5 + cols[c], DockGeometry.down(c, count, 5, 4));
        }
    }

    @Test
    public void downWithNoGridStaysOnTheDock() {
        assertEquals(2, DockGeometry.down(2, 5, 5, 4));
        assertEquals(4, DockGeometry.down(4, 3, 5, 4));
    }

    @Test
    public void downClampsToTheLastTile() {
        // Dock of 5 and a single grid tile: every dock tile goes to it.
        for (int c = 0; c < 5; c++) assertEquals(5, DockGeometry.down(c, 6, 5, 4));
    }

    @Test
    public void upRestoresTheRememberedDockTile() {
        // Dock 2 goes down to column 2 (position 7); Up must return to 2, not the nearest-by-column tile.
        int below = DockGeometry.down(2, 20, 5, 4);
        assertEquals(7, below);
        assertEquals(2, DockGeometry.up(below, 5, 4, 2));
        // Dock 3 also lands on column 2; remembered focus wins over the nearest tile.
        int below3 = DockGeometry.down(3, 20, 5, 4);
        assertEquals(7, below3);
        assertEquals(3, DockGeometry.up(below3, 5, 4, 3));
        // A stale memory over another column is ignored.
        assertEquals(DockGeometry.dockForGridColumn(0, 5, 4), DockGeometry.up(5, 5, 4, 4));
    }

    @Test
    public void dockIndexForMapsGridTilesToTheTileAbove() {
        assertEquals(3, DockGeometry.dockIndexFor(3, 5, 4));
        assertEquals(0, DockGeometry.dockIndexFor(5, 5, 4));
        assertEquals(4, DockGeometry.dockIndexFor(8, 5, 4));
        assertEquals(0, DockGeometry.dockIndexFor(-1, 5, 4));
        // Same counts: the old position % columns.
        assertEquals(2, DockGeometry.dockIndexFor(10, 4, 4));
    }

    @Test
    public void wideGridUnderNarrowDock() {
        // dock_columns below columns works too: 3 dock tiles over a 6 column grid.
        assertEquals(6, DockGeometry.spanCount(3, 6));
        assertEquals(2, DockGeometry.spanSize(1, 3, 6));
        assertEquals(1, DockGeometry.spanSize(3, 3, 6));
        assertEquals(6, DockGeometry.down(1, 12, 3, 6));   // tile 1's centre is over column 3
        assertEquals(1, DockGeometry.up(6, 3, 6, 1));
    }

    @Test
    public void dockTilesFitFiveAcross1080p() {
        float density = 2f;
        int inner = Math.round(848 * density);   // grid inner width on the XGIMI S Max
        int w = AppAdapter.tileWidthPx(inner, 5, density);
        int cell = inner / 5;
        assertTrue("tile plus focus zoom fits its cell", w * AppAdapter.FOCUS_SCALE <= cell);
        assertTrue(w >= Math.round(80 * density));
        assertTrue(w < Math.round(180 * density));
        int h = AppAdapter.tileHeightPx(w);
        assertEquals(180f / 101f, (float) w / h, 0.02f);   // banner aspect kept
        // 4 columns stay at the design size.
        assertEquals(Math.round(180 * density), AppAdapter.tileWidthPx(inner, 4, density));
    }
}
