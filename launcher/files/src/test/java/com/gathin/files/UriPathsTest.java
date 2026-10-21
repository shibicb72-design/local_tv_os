package com.gathin.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

public class UriPathsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void roundTripUnicodeAndSpaces() {
        String p = "/storage/1234-ABCD/Films/Léa & Sønne – Ep 2 (日本語).mkv";
        String seg = UriPaths.encode(p);
        assertFalse(seg.contains("/") || seg.contains("+") || seg.contains("=") || seg.contains(" "));
        assertEquals(p, UriPaths.decode(seg));
    }

    @Test
    public void decodeRejectsGarbageAndUnsafePaths() {
        assertNull(UriPaths.decode(null));
        assertNull(UriPaths.decode(""));
        assertNull(UriPaths.decode("***"));
        assertNull(UriPaths.decode(UriPaths.encode("relative/path")));
        assertNull(UriPaths.decode(UriPaths.encode("/storage/x/../../etc/passwd")));
        assertNull(UriPaths.decode(UriPaths.encode("/storage/x\0/y")));
        assertNotNull(UriPaths.decode(UriPaths.encode("/storage/x/..y/z")));
    }

    @Test
    public void isInsideMatchesWholeComponents() {
        List<String> roots = Collections.singletonList("/storage/AAAA-1111");
        assertTrue(UriPaths.isInside("/storage/AAAA-1111", roots));
        assertTrue(UriPaths.isInside("/storage/AAAA-1111/a/b.mkv", roots));
        assertFalse(UriPaths.isInside("/storage/AAAA-11112/a.mkv", roots));
        assertFalse(UriPaths.isInside("/etc/passwd", roots));
        assertFalse(UriPaths.isInside(null, roots));
        assertFalse(UriPaths.isInside("/storage/AAAA-1111/a", Collections.<String>emptyList()));
    }

    @Test
    public void resolveAcceptsFileInsideRoot() throws Exception {
        Assume.assumeTrue("POSIX paths only", File.separatorChar == '/');
        File root = tmp.newFolder("vol").getCanonicalFile();
        File f = new File(root, "a b.mkv");
        assertTrue(f.createNewFile());
        List<String> roots = Collections.singletonList(root.getPath());
        assertEquals(f, UriPaths.resolve(UriPaths.encode(f.getPath()), roots));
    }

    @Test
    public void resolveRejectsOutsideMissingAndDirectories() throws Exception {
        File root = tmp.newFolder("vol").getCanonicalFile();
        File outside = tmp.newFile("outside.txt").getCanonicalFile();
        File dir = new File(root, "d");
        assertTrue(dir.mkdir());
        List<String> roots = Collections.singletonList(root.getPath());
        assertNull(UriPaths.resolve(UriPaths.encode(outside.getPath()), roots));
        assertNull(UriPaths.resolve(UriPaths.encode(new File(root, "missing.mkv").getPath()), roots));
        assertNull(UriPaths.resolve(UriPaths.encode(dir.getPath()), roots));
    }

    @Test
    public void resolveRejectsSymlinkThatEscapesTheRoot() throws Exception {
        File root = tmp.newFolder("vol").getCanonicalFile();
        File outside = tmp.newFile("secret.txt").getCanonicalFile();
        File link = new File(root, "link.mkv");
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath());
        } catch (UnsupportedOperationException | java.io.IOException e) {
            Assume.assumeNoException("symlinks not available here", e);
        }
        List<String> roots = Collections.singletonList(root.getPath());
        assertNull(UriPaths.resolve(UriPaths.encode(link.getPath()), roots));
    }
}
