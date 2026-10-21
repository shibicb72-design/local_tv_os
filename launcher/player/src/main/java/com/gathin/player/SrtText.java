package com.gathin.player;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Pure charset sniffing for subtitle files. A byte-order mark decides; else valid UTF-8 is UTF-8; else the
 * fallback charset (Windows-1256 by default, common for Persian and Arabic; Windows-1252 for Western files).
 * The result is always UTF-8 bytes without a BOM, so ExoPlayer's parsers never have to guess.
 */
final class SrtText {

    private SrtText() {}

    static byte[] toUtf8(byte[] in, Charset fallback) {
        int n = in.length;
        if (n >= 3 && (in[0] & 0xFF) == 0xEF && (in[1] & 0xFF) == 0xBB && (in[2] & 0xFF) == 0xBF) {
            return Arrays.copyOfRange(in, 3, n);
        }
        if (n >= 2 && (in[0] & 0xFF) == 0xFE && (in[1] & 0xFF) == 0xFF) {
            return new String(in, 2, n - 2, StandardCharsets.UTF_16BE).getBytes(StandardCharsets.UTF_8);
        }
        if (n >= 2 && (in[0] & 0xFF) == 0xFF && (in[1] & 0xFF) == 0xFE) {
            return new String(in, 2, n - 2, StandardCharsets.UTF_16LE).getBytes(StandardCharsets.UTF_8);
        }
        if (isValidUtf8(in)) return in;
        return new String(in, fallback).getBytes(StandardCharsets.UTF_8);
    }

    static boolean isValidUtf8(byte[] in) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(in));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
