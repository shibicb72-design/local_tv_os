package com.gathin.player;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class SrtTextTest {

    private static final Charset CP1256 = Charset.forName("windows-1256");
    private static final Charset CP1252 = Charset.forName("windows-1252");

    private static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    private static byte[] withBom(int b0, int b1, byte[] body) {
        byte[] out = new byte[body.length + 2];
        out[0] = (byte) b0;
        out[1] = (byte) b1;
        System.arraycopy(body, 0, out, 2, body.length);
        return out;
    }

    @Test
    public void windows1256BytesDecodeToPersian() {
        // "salam" in Windows-1256: sin lam alef meem
        byte[] in = {(byte) 0xD3, (byte) 0xE1, (byte) 0xC7, (byte) 0xE3};
        assertEquals("سلام", utf8(SrtText.toUtf8(in, CP1256)));
    }

    @Test
    public void invalidUtf8UsesTheFallbackCharset() {
        byte[] in = {'c', 'a', 'f', (byte) 0xE9};
        assertFalse(SrtText.isValidUtf8(in));
        assertEquals("café", utf8(SrtText.toUtf8(in, CP1252)));
    }

    @Test
    public void validUtf8IsKeptUnchanged() {
        byte[] in = "café س".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(in, SrtText.toUtf8(in, CP1256));
    }

    @Test
    public void utf8BomIsStripped() {
        byte[] body = "1\n00:00:01,000 --> 00:00:02,000\nس".getBytes(StandardCharsets.UTF_8);
        byte[] in = new byte[body.length + 3];
        in[0] = (byte) 0xEF;
        in[1] = (byte) 0xBB;
        in[2] = (byte) 0xBF;
        System.arraycopy(body, 0, in, 3, body.length);
        assertArrayEquals(body, SrtText.toUtf8(in, CP1256));
    }

    @Test
    public void utf16BomsAreConverted() {
        byte[] le = withBom(0xFF, 0xFE, "aس".getBytes(StandardCharsets.UTF_16LE));
        assertEquals("aس", utf8(SrtText.toUtf8(le, CP1256)));
        byte[] be = withBom(0xFE, 0xFF, "aس".getBytes(StandardCharsets.UTF_16BE));
        assertEquals("aس", utf8(SrtText.toUtf8(be, CP1256)));
    }

    @Test
    public void asciiAndEmptyInputPassThrough() {
        assertTrue(SrtText.isValidUtf8(new byte[0]));
        assertEquals(0, SrtText.toUtf8(new byte[0], CP1256).length);
        assertEquals("hello", utf8(SrtText.toUtf8("hello".getBytes(StandardCharsets.US_ASCII), CP1256)));
    }
}
