package com.gathin.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class VolumeModelTest {

    private static VolumeModel.Raw internal() {
        return new VolumeModel.Raw(null, "Internal shared storage", "/storage/emulated/0", false, true,
                "mounted", false);
    }

    private static VolumeModel.Raw usb(String uuid, String desc, String state, boolean ro) {
        return new VolumeModel.Raw(uuid, desc, "/storage/" + uuid, true, false, state, ro);
    }

    private static List<VolumeModel.Raw> list(VolumeModel.Raw... v) {
        List<VolumeModel.Raw> l = new ArrayList<>();
        for (VolumeModel.Raw r : v) l.add(r);
        return l;
    }

    @Test
    public void usbFirstThenInternal() {
        List<VolumeModel.Root> r = VolumeModel.roots(list(internal(), usb("1234-ABCD", "Kingston", "mounted", false)));
        assertEquals(2, r.size());
        assertEquals("Kingston", r.get(0).label);
        assertTrue(r.get(0).removable);
        assertEquals("Internal storage", r.get(1).label);
        assertFalse(r.get(1).removable);
    }

    @Test
    public void labelFallsBackToUuid() {
        List<VolumeModel.Root> r = VolumeModel.roots(list(usb("1234-ABCD", "  ", "mounted", false)));
        assertEquals("USB 1234-ABCD", r.get(0).label);
        r = VolumeModel.roots(list(usb("1234-ABCD", null, "mounted", false)));
        assertEquals("USB 1234-ABCD", r.get(0).label);
    }

    @Test
    public void unmountedVolumesAreHidden() {
        List<VolumeModel.Root> r = VolumeModel.roots(list(
                usb("AAAA-0001", "A", "unmounted", false),
                usb("AAAA-0002", "B", "ejecting", false),
                usb("AAAA-0003", "C", "checking", false),
                internal()));
        assertEquals(1, r.size());
        assertEquals("Internal storage", r.get(0).label);
    }

    @Test
    public void readOnlyFlagIsKept() {
        List<VolumeModel.Root> r = VolumeModel.roots(list(
                usb("AAAA-0001", "Ntfs", "mounted", true),
                usb("AAAA-0002", "Fat", "mounted", false),
                usb("AAAA-0003", "Ro", "mounted_ro", false)));
        assertTrue(r.get(0).readOnly);
        assertFalse(r.get(1).readOnly);
        assertTrue(r.get(2).readOnly);
    }

    @Test
    public void primaryInternalComesBeforeOtherFixedVolumes() {
        VolumeModel.Raw other = new VolumeModel.Raw("X", "Data", "/storage/X", false, false, "mounted", false);
        List<VolumeModel.Root> r = VolumeModel.roots(list(other, internal()));
        assertEquals("Internal storage", r.get(0).label);
        assertEquals("Data", r.get(1).label);
    }

    @Test
    public void emptyAndPathlessInput() {
        assertTrue(VolumeModel.roots(new ArrayList<VolumeModel.Raw>()).isEmpty());
        VolumeModel.Raw noPath = new VolumeModel.Raw("U", "x", null, true, false, "mounted", false);
        assertTrue(VolumeModel.roots(list(noPath)).isEmpty());
    }
}
