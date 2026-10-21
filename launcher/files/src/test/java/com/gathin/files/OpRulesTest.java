package com.gathin.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class OpRulesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void roots_screen_disables_everything_but_open() {
        for (OpRules.Action a : OpRules.Action.values()) {
            String r = OpRules.disabledReason(a, true, true, false, false);
            if (a == OpRules.Action.OPEN) assertNull(r); else assertNotNull(a.name(), r);
        }
    }

    @Test
    public void read_only_volume_allows_copy_but_not_changes() {
        assertNull(OpRules.disabledReason(OpRules.Action.COPY, false, true, false, true));
        assertNull(OpRules.disabledReason(OpRules.Action.OPEN, false, true, false, true));
        assertNotNull(OpRules.disabledReason(OpRules.Action.RENAME, false, true, false, true));
        assertNotNull(OpRules.disabledReason(OpRules.Action.MOVE, false, true, false, true));
        assertNotNull(OpRules.disabledReason(OpRules.Action.DELETE, false, true, false, true));
    }

    @Test
    public void no_selection_leaves_only_sort_and_hidden() {
        assertNull(OpRules.disabledReason(OpRules.Action.SORT, false, false, false, false));
        assertNull(OpRules.disabledReason(OpRules.Action.HIDDEN, false, false, false, false));
        assertNotNull(OpRules.disabledReason(OpRules.Action.DELETE, false, false, false, false));
    }

    @Test
    public void open_with_is_for_files_only() {
        assertNotNull(OpRules.disabledReason(OpRules.Action.OPEN_WITH, false, true, true, false));
        assertNull(OpRules.disabledReason(OpRules.Action.OPEN_WITH, false, true, false, false));
    }

    @Test
    public void rootOf_picks_the_longest_match_and_respects_separators() {
        java.util.List<String> roots = Arrays.asList("/storage/emulated/0", "/storage/ABCD-1234", "/storage");
        assertEquals("/storage/ABCD-1234", OpRules.rootOf("/storage/ABCD-1234/x/y.mkv", roots));
        assertEquals("/storage/emulated/0", OpRules.rootOf("/storage/emulated/0", roots));
        assertEquals("/storage", OpRules.rootOf("/storage/emulated/1/a", roots));
        assertNull(OpRules.rootOf("/mnt/x", roots));
        assertNull(OpRules.rootOf("/storage2/x", Collections.singletonList("/storage")));
    }

    @Test
    public void conflicts_count_existing_targets_but_not_the_source_itself() throws Exception {
        File a = tmp.newFolder("a");
        File b = tmp.newFolder("b");
        File inA = new File(a, "x.txt");
        File inB = new File(b, "x.txt");
        File other = new File(a, "y.txt");
        assertEquals(true, inA.createNewFile());
        assertEquals(true, other.createNewFile());
        assertEquals(0, OpRules.conflicts(Arrays.asList(inA, other), b));
        assertEquals(true, inB.createNewFile());
        assertEquals(1, OpRules.conflicts(Arrays.asList(inA, other), b));
        // same folder: the source is its own target, not a conflict
        assertEquals(0, OpRules.conflicts(Collections.singletonList(inA), a));
    }
}
