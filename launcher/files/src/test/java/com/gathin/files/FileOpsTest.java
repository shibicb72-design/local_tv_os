package com.gathin.files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileOpsTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File write(File f, int size) throws IOException {
        f.getParentFile().mkdirs();
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = (byte) (i * 31 + size);
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(data);
        }
        return f;
    }

    private OpPlan plan(File dest, File... src) throws IOException {
        return OpPlan.forTransfer(Arrays.asList(src), dest, true, () -> Long.MAX_VALUE);
    }

    private File tree() throws IOException {
        File root = tmp.newFolder("src", "Show");
        write(new File(root, "a.mkv"), 1000);
        write(new File(root, "sub/b.srt"), 10);
        write(new File(root, "sub/deep/c.bin"), 300_000);
        new File(root, "empty").mkdirs();
        return root;
    }

    private void assertSameTree(File a, File b) throws IOException {
        assertEquals(a.isDirectory(), b.isDirectory());
        if (a.isDirectory()) {
            String[] an = a.list();
            String[] bn = b.list();
            Arrays.sort(an);
            Arrays.sort(bn);
            assertArrayEquals(an, bn);
            for (String n : an) assertSameTree(new File(a, n), new File(b, n));
        } else {
            assertArrayEquals(Files.readAllBytes(a.toPath()), Files.readAllBytes(b.toPath()));
        }
    }

    @Test
    public void copyTreeEqualsSource() throws IOException {
        File src = tree();
        File dest = tmp.newFolder("dest");
        FileOps.Result r = new FileOps(null).copy(plan(dest, src), FileOps.Conflict.KEEP_BOTH);
        assertTrue(r.ok());
        assertEquals(1, r.done);
        assertSameTree(src, new File(dest, "Show"));
        assertTrue(src.exists());
    }

    @Test
    public void copyReportsProgressToTotal() throws IOException {
        File src = tree();
        File dest = tmp.newFolder("dest");
        long[] last = new long[2];
        OpPlan p = plan(dest, src);
        assertEquals(3, p.files);
        assertEquals(300_000 + 1000 + 10, p.bytes);
        new FileOps((d, t, n) -> {
            last[0] = d;
            last[1] = t;
        }).copy(p, FileOps.Conflict.KEEP_BOTH);
        assertEquals(p.bytes, last[0]);
        assertEquals(p.bytes, last[1]);
    }

    @Test
    public void moveRemovesSource() throws IOException {
        File src = tree();
        File copy = tmp.newFolder("ref");
        new FileOps(null).copy(plan(copy, src), FileOps.Conflict.KEEP_BOTH);
        File dest = tmp.newFolder("dest");
        FileOps.Result r = new FileOps(null).move(plan(dest, src), FileOps.Conflict.KEEP_BOTH);
        assertTrue(r.ok());
        assertFalse(src.exists());
        assertSameTree(new File(copy, "Show"), new File(dest, "Show"));
    }

    @Test
    public void cancelLeavesNoPartialFile() throws IOException {
        File big = write(new File(tmp.newFolder("src"), "big.bin"), 1_000_000);
        File dest = tmp.newFolder("dest");
        FileOps[] ops = new FileOps[1];
        ops[0] = new FileOps((d, t, n) -> ops[0].cancel());
        FileOps.Result r = ops[0].copy(plan(dest, big), FileOps.Conflict.KEEP_BOTH);
        assertTrue(r.cancelled);
        assertFalse(r.ok());
        assertFalse(new File(dest, "big.bin").exists());
        assertTrue(big.exists());
    }

    @Test
    public void conflictNaming() {
        assertEquals("movie (2).mkv", ConflictNamer.next("movie.mkv", false, n -> false));
        assertEquals("movie (3).mkv", ConflictNamer.next("movie.mkv", false, n -> n.equals("movie (2).mkv")));
        assertEquals("Show (2)", ConflictNamer.next("Show", true, n -> false));
        assertEquals("v1.2 (2)", ConflictNamer.next("v1.2", true, n -> false));
        assertEquals("a (3).txt", ConflictNamer.next("a (2).txt", false, n -> n.equals("a (2).txt")));
        assertEquals(".hidden (2)", ConflictNamer.next(".hidden", false, n -> false));
    }

    @Test
    public void keepBothSkipAndReplace() throws IOException {
        File a = write(new File(tmp.newFolder("src"), "f.txt"), 5);
        File dest = tmp.newFolder("dest");
        File existing = write(new File(dest, "f.txt"), 9);

        FileOps.Result r = new FileOps(null).copy(plan(dest, a), FileOps.Conflict.KEEP_BOTH);
        assertTrue(r.ok());
        assertEquals(5, new File(dest, "f (2).txt").length());
        assertEquals(9, existing.length());

        r = new FileOps(null).copy(plan(dest, a), FileOps.Conflict.SKIP);
        assertEquals(1, r.skipped);
        assertEquals(0, r.done);
        assertEquals(9, existing.length());

        r = new FileOps(null).copy(plan(dest, a), FileOps.Conflict.REPLACE);
        assertTrue(r.ok());
        assertEquals(5, existing.length());
    }

    @Test
    public void copyOntoItselfNeverReplacesTheSource() throws IOException {
        File a = write(new File(tmp.newFolder("d"), "f.txt"), 5);
        FileOps.Result r = new FileOps(null).copy(plan(a.getParentFile(), a), FileOps.Conflict.REPLACE);
        assertTrue(r.ok());
        assertEquals(5, a.length());
        assertEquals(5, new File(a.getParentFile(), "f (2).txt").length());
    }

    @Test
    public void replaceNeverDeletesAnAncestorOfTheSource() throws IOException {
        File inner = write(new File(tmp.newFolder("v", "Movies", "Movies"), "a.bin"), 7).getParentFile();
        File outerDir = inner.getParentFile();
        File dest = outerDir.getParentFile();
        assertEquals(0, OpRules.conflicts(Collections.singletonList(inner), dest));
        for (boolean move : new boolean[] {false, true}) {
            OpPlan p = plan(dest, inner);
            FileOps.Result r = move ? new FileOps(null).move(p, FileOps.Conflict.REPLACE)
                    : new FileOps(null).copy(p, FileOps.Conflict.REPLACE);
            assertFalse(r.ok());
            assertEquals(Collections.singletonList(inner.getAbsolutePath()), r.failed);
            assertEquals(7, new File(inner, "a.bin").length());
        }
    }

    @Test
    public void moveOntoItselfIsSkipped()throws IOException {
        File a = write(new File(tmp.newFolder("d"), "f.txt"), 5);
        FileOps.Result r = new FileOps(null).move(plan(a.getParentFile(), a), FileOps.Conflict.REPLACE);
        assertEquals(1, r.skipped);
        assertEquals(5, a.length());
    }

    @Test
    public void folderIntoItselfOrDescendantRefused() throws IOException {
        File src = tree();
        for (File dest : new File[] {src, new File(src, "sub/deep")}) {
            try {
                plan(dest, src);
                fail("expected refusal for " + dest);
            } catch (OpPlan.Refused e) {
                assertEquals(OpPlan.Reason.INTO_ITSELF, e.reason);
            }
        }
        plan(src.getParentFile(), src); // into its own parent is allowed
    }

    @Test
    public void insufficientSpaceRefusedBeforeAnyByte() throws IOException {
        File src = tree();
        File dest = tmp.newFolder("dest");
        try {
            OpPlan.forTransfer(Collections.singletonList(src), dest, true, () -> 100);
            fail("expected refusal");
        } catch (OpPlan.Refused e) {
            assertEquals(OpPlan.Reason.NO_SPACE, e.reason);
        }
        assertEquals(0, dest.list().length);
        // a same-volume move needs no space
        OpPlan.forTransfer(Collections.singletonList(src), dest, false, () -> 0);
    }

    @Test
    public void deleteRemovesTree() throws IOException {
        File src = tree();
        FileOps.Result r = new FileOps(null).delete(OpPlan.forDelete(Collections.singletonList(src)));
        assertTrue(r.ok());
        assertFalse(src.exists());
    }

    @Test
    public void deleteOfProtectedFileReportsFailureWithoutThrowing() throws IOException {
        // Windows ignores directory write bits and still deletes read-only files
        assumeFalse(System.getProperty("os.name", "").startsWith("Windows"));
        File dir = tmp.newFolder("locked");
        File f = write(new File(dir, "ro.txt"), 3);
        f.setWritable(false);
        dir.setWritable(false);
        try {
            assumeTrue(!dir.canWrite() || !f.canWrite()); // root can delete anything
            List<File> one = Collections.singletonList(f);
            FileOps.Result r = new FileOps(null).delete(OpPlan.forDelete(one));
            assertFalse(r.ok());
            assertEquals(Collections.singletonList(f.getAbsolutePath()), r.failed);
            assertTrue(f.exists());
        } finally {
            dir.setWritable(true);
            f.setWritable(true);
        }
    }

    @Test
    public void rename() throws IOException {
        File a = write(new File(tmp.newFolder("d"), "a.txt"), 1);
        File b = write(new File(a.getParentFile(), "b.txt"), 1);
        File c = FileOps.rename(a, "c.txt");
        assertEquals("c.txt", c.getName());
        assertTrue(c.exists());
        assertFalse(a.exists());
        for (String bad : new String[] {"", " ", "..", "x/y", "b.txt"}) {
            try {
                FileOps.rename(c, bad);
                fail("expected IOException for '" + bad + "'");
            } catch (IOException expected) {
                assertTrue(c.exists());
            }
        }
        assertTrue(b.exists());
    }
}
