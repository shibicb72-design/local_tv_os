package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DockSpecTest {

    private static final List<String> HDMI = Arrays.asList("mtk/HDMI/HW5", "mtk/HDMI/HW6");
    private static final List<String> ALL = Arrays.asList("mtk/HDMI/HW5", "mtk/HDMI/HW6", "mtk/AV/HW1");

    private static DockSpec.InputResolver resolver() {
        return arg -> DockSpec.resolveInput(arg, HDMI, ALL);
    }

    private static Set<String> set(String... pkgs) {
        return new HashSet<>(Arrays.asList(pkgs));
    }

    @Test
    public void hdmiLabelsGetASpace() {
        assertEquals("HDMI 1", DockSpec.normalizeHdmiLabel("HDMI1"));
        assertEquals("HDMI 2", DockSpec.normalizeHdmiLabel("hdmi2"));
        assertEquals("HDMI 1", DockSpec.normalizeHdmiLabel("HDMI 1"));
        assertEquals("HDMI 12", DockSpec.normalizeHdmiLabel("HDMI   12"));
        assertEquals("HDMI 1 (ARC)", DockSpec.normalizeHdmiLabel("HDMI 1 (ARC)"));
        assertEquals("HDMI", DockSpec.normalizeHdmiLabel("HDMI"));
        assertEquals("AV1", DockSpec.normalizeHdmiLabel("AV1"));
        assertNull(DockSpec.normalizeHdmiLabel(null));
    }

    @Test
    public void mixedHdmiStylesSortByPortNumber() {
        assertEquals("HDMI 1", DockSpec.inputLabel("HDMI1", true));
        assertEquals("HDMI 2", DockSpec.inputLabel("2", true));
        assertEquals("AV1", DockSpec.inputLabel("AV1", false));
        String[] labels = {DockSpec.inputLabel("HDMI 2", true), DockSpec.inputLabel("HDMI1", true)};
        Arrays.sort(labels, DockSpec::compareLabels);
        assertEquals("HDMI 1", labels[0]);
        assertEquals("HDMI 2", labels[1]);
    }

    @Test
    public void inputEntriesAreRecognised() {
        assertTrue(DockSpec.isInput("input:hdmi1"));
        assertTrue(DockSpec.isInput("  INPUT:hdmi1 "));
        assertFalse(DockSpec.isInput("com.netflix.ninja"));
        assertFalse(DockSpec.isInput(null));
        assertEquals("hdmi1", DockSpec.inputArg(" input: hdmi1 "));
        assertEquals("", DockSpec.inputArg("com.netflix.ninja"));
    }

    @Test
    public void hdmiShortcutPicksTheNthHdmiInput() {
        assertEquals("mtk/HDMI/HW5", DockSpec.resolveInput("hdmi1", HDMI, ALL));
        assertEquals("mtk/HDMI/HW6", DockSpec.resolveInput("HDMI2", HDMI, ALL));
        assertNull(DockSpec.resolveInput("hdmi3", HDMI, ALL));
        assertNull(DockSpec.resolveInput("hdmi0", HDMI, ALL));
        assertNull(DockSpec.resolveInput("hdmi99999999999", HDMI, ALL));
        assertNull(DockSpec.resolveInput("hdmi1", Collections.emptyList(), ALL));
    }

    @Test
    public void explicitIdsMustBeRegistered() {
        assertEquals("mtk/AV/HW1", DockSpec.resolveInput("mtk/AV/HW1", HDMI, ALL));
        assertNull(DockSpec.resolveInput("mtk/AV/HW9", HDMI, ALL));
        assertNull(DockSpec.resolveInput("", HDMI, ALL));
        assertNull(DockSpec.resolveInput(null, HDMI, ALL));
        assertNull(DockSpec.resolveInput("hdmix", HDMI, ALL));
    }

    @Test
    public void placeholdersOnMissingAppStaysAsPlaceholderAndUnregisteredInputIsSkipped() {
        List<DockSpec.Slot> slots = DockSpec.plan(
                Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja", "input:hdmi1", "input:hdmi9"),
                set("com.google.android.youtube.tv"), set(), true, resolver());
        assertEquals(3, slots.size());
        assertEquals(DockSpec.Slot.APP, slots.get(0).kind);
        assertEquals(DockSpec.Slot.MISSING, slots.get(1).kind);
        assertEquals("com.netflix.ninja", slots.get(1).key);
        assertEquals(DockSpec.Slot.INPUT, slots.get(2).kind);
        assertEquals("mtk/HDMI/HW5", slots.get(2).key);
    }

    @Test
    public void placeholdersOffSkipsMissingApps() {
        List<DockSpec.Slot> slots = DockSpec.plan(
                Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja", "input:hdmi1"),
                set("com.google.android.youtube.tv"), set(), false, resolver());
        assertEquals(2, slots.size());
        assertEquals(DockSpec.Slot.APP, slots.get(0).kind);
        assertEquals(DockSpec.Slot.INPUT, slots.get(1).kind);
    }

    @Test
    public void hiddenAppWinsOverDockWithAndWithoutPlaceholders() {
        for (boolean placeholders : new boolean[]{true, false}) {
            // Installed but hidden, and not installed but hidden: no slot either way.
            List<DockSpec.Slot> slots = DockSpec.plan(
                    Arrays.asList("a.installed", "b.missing", "c.shown"),
                    set("c.shown"), set("a.installed", "b.missing"), placeholders, resolver());
            assertEquals(1, slots.size());
            assertEquals("c.shown", slots.get(0).key);
        }
    }

    @Test
    public void hiddenInstalledAppIsNotAPlaceholder() {
        // The adapter's installed set leaves hidden apps out, so this is the "Not installed" trap.
        List<DockSpec.Slot> slots = DockSpec.plan(
                Collections.singletonList("a.b"), set(), set("a.b"), true, resolver());
        assertTrue(slots.isEmpty());
    }

    @Test
    public void labelsSortNumerically() {
        assertTrue(DockSpec.compareLabels("HDMI 2", "HDMI 10") < 0);
        assertTrue(DockSpec.compareLabels("HDMI 10", "HDMI 2") > 0);
        assertEquals(0, DockSpec.compareLabels("hdmi 1", "HDMI 1"));
        assertTrue(DockSpec.compareLabels("AV", "HDMI 1") < 0);
        assertTrue(DockSpec.compareLabels("HDMI", "HDMI 1") < 0);
    }

    @Test
    public void netflixInstalledIsAnOrdinaryApp() {
        List<DockSpec.Slot> slots = DockSpec.plan(
                Collections.singletonList("com.netflix.ninja"), set("com.netflix.ninja"), set(), false, resolver());
        assertEquals(1, slots.size());
        assertEquals(DockSpec.Slot.APP, slots.get(0).kind);
    }

    @Test
    public void blanksAndDuplicatesAreDropped() {
        List<DockSpec.Slot> slots = DockSpec.plan(
                Arrays.asList("", "  ", null, "a.b", "a.b", "input:hdmi1", " input:hdmi1"),
                set("a.b"), set(), true, resolver());
        assertEquals(2, slots.size());
        assertEquals("a.b", slots.get(0).key);
        assertEquals(DockSpec.Slot.INPUT, slots.get(1).kind);
    }

    @Test
    public void noInputsRegisteredDropsEveryInputSlot() {
        List<DockSpec.Slot> slots = DockSpec.plan(Arrays.asList("input:hdmi1", "x.y"), set(), set(), true,
                arg -> DockSpec.resolveInput(arg, Collections.emptyList(), Collections.emptyList()));
        assertEquals(1, slots.size());
        assertEquals(DockSpec.Slot.MISSING, slots.get(0).kind);
    }

    @Test
    public void friendlyNames() {
        assertEquals("Netflix", DockSpec.friendlyName("com.netflix.ninja"));
        assertEquals("some.other.app", DockSpec.friendlyName("some.other.app"));
    }
}
