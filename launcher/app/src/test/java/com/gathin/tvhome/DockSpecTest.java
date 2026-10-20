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
    public void missingAppStaysAsPlaceholderAndUnregisteredInputIsSkipped() {
        List<DockSpec.Slot> slots = DockSpec.plan(
                Arrays.asList("com.google.android.youtube.tv", "com.netflix.ninja", "input:hdmi1", "input:hdmi9"),
                set("com.google.android.youtube.tv"), resolver());
        assertEquals(3, slots.size());
        assertEquals(DockSpec.Slot.APP, slots.get(0).kind);
        assertEquals(DockSpec.Slot.MISSING, slots.get(1).kind);
        assertEquals("com.netflix.ninja", slots.get(1).key);
        assertEquals(DockSpec.Slot.INPUT, slots.get(2).kind);
        assertEquals("mtk/HDMI/HW5", slots.get(2).key);
    }

    @Test
    public void netflixInstalledIsAnOrdinaryApp() {
        List<DockSpec.Slot> slots = DockSpec.plan(
                Collections.singletonList("com.netflix.ninja"), set("com.netflix.ninja"), resolver());
        assertEquals(1, slots.size());
        assertEquals(DockSpec.Slot.APP, slots.get(0).kind);
    }

    @Test
    public void blanksAndDuplicatesAreDropped() {
        List<DockSpec.Slot> slots = DockSpec.plan(
                Arrays.asList("", "  ", null, "a.b", "a.b", "input:hdmi1", " input:hdmi1"),
                set("a.b"), resolver());
        assertEquals(2, slots.size());
        assertEquals("a.b", slots.get(0).key);
        assertEquals(DockSpec.Slot.INPUT, slots.get(1).kind);
    }

    @Test
    public void noInputsRegisteredDropsEveryInputSlot() {
        List<DockSpec.Slot> slots = DockSpec.plan(Arrays.asList("input:hdmi1", "x.y"), set(),
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
