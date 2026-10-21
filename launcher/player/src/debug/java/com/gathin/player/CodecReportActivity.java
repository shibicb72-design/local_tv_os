package com.gathin.player;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Debug-only codec inventory (plan Op-P0): every decoder in {@code MediaCodecList(REGULAR_CODECS)} with its MIME
 * types, hardware/software and maximum size / frame rate, then the audio passthrough encodings. Shown on screen
 * (D-pad scrolls) and written to logcat tag {@code PlayerCodecs}: {@code adb logcat -d -s PlayerCodecs}.
 */
public class CodecReportActivity extends Activity {

    private static final String TAG = "PlayerCodecs";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String report = buildReport();
        for (String line : report.split("\n")) Log.i(TAG, line);

        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(13f);
        tv.setTextColor(Color.WHITE);
        tv.setFocusable(true);
        tv.setPadding(48, 32, 48, 32);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(tv);
        setContentView(scroll);
        tv.requestFocus();
    }

    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("== Decoders (REGULAR_CODECS) ==\n");
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            String kind = info.isHardwareAccelerated() ? "HW" : "SW";
            if (info.isSoftwareOnly()) kind = "SW-only";
            for (String type : info.getSupportedTypes()) {
                if (!type.startsWith("video/") && !type.startsWith("audio/")) continue;
                sb.append(info.getName()).append(" | ").append(type).append(" | ").append(kind);
                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
                    MediaCodecInfo.VideoCapabilities v = caps.getVideoCapabilities();
                    if (v != null) {
                        Range<Integer> w = v.getSupportedWidths();
                        Range<Integer> h = v.getSupportedHeights();
                        Range<Integer> fps = v.getSupportedFrameRates();
                        sb.append(" | max ").append(w.getUpper()).append('x').append(h.getUpper())
                                .append(" @").append(fps.getUpper()).append(" fps");
                    }
                    MediaCodecInfo.AudioCapabilities a = caps.getAudioCapabilities();
                    if (a != null) {
                        sb.append(" | max ").append(a.getMaxInputChannelCount()).append(" ch");
                    }
                } catch (RuntimeException e) {
                    sb.append(" | caps unavailable");
                }
                sb.append('\n');
            }
        }

        sb.append("\n== Audio passthrough ==\n");
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build();
        int[] encodings = {
                AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3, AudioFormat.ENCODING_E_AC3_JOC,
                AudioFormat.ENCODING_DTS, AudioFormat.ENCODING_DTS_HD, AudioFormat.ENCODING_DOLBY_TRUEHD,
                AudioFormat.ENCODING_AAC_LC, AudioFormat.ENCODING_PCM_16BIT};
        for (int enc : encodings) {
            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(enc)
                    .setSampleRate(48000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1)
                    .build();
            sb.append("direct playback ").append(encodingName(enc)).append(": ")
                    .append(AudioTrack.isDirectPlaybackSupported(fmt, attrs)).append('\n');
        }

        AudioManager am = getSystemService(AudioManager.class);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            sb.append("output device type ").append(d.getType()).append(" \"").append(d.getProductName())
                    .append("\" encodings:");
            for (int enc : d.getEncodings()) sb.append(' ').append(encodingName(enc));
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String encodingName(int enc) {
        switch (enc) {
            case AudioFormat.ENCODING_AC3: return "AC-3";
            case AudioFormat.ENCODING_E_AC3: return "E-AC-3";
            case AudioFormat.ENCODING_E_AC3_JOC: return "E-AC-3-JOC";
            case AudioFormat.ENCODING_DTS: return "DTS";
            case AudioFormat.ENCODING_DTS_HD: return "DTS-HD";
            case AudioFormat.ENCODING_DOLBY_TRUEHD: return "TrueHD";
            case AudioFormat.ENCODING_AAC_LC: return "AAC-LC";
            case AudioFormat.ENCODING_PCM_16BIT: return "PCM16";
            case AudioFormat.ENCODING_PCM_FLOAT: return "PCM-float";
            case AudioFormat.ENCODING_PCM_8BIT: return "PCM8";
            case AudioFormat.ENCODING_MP3: return "MP3";
            case AudioFormat.ENCODING_AC4: return "AC-4";
            default: return "enc" + enc;
        }
    }
}
