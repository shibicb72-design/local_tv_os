package com.gathin.player;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.avi.AviExtractor;
import androidx.media3.extractor.flac.FlacExtractor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.mp3.Mp3Extractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.ogg.OggExtractor;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.extractor.wav.WavExtractor;

/**
 * The nine containers the Player reads, and nothing else. Naming them here (instead of the library's
 * default factory) keeps the other extractors out of the APK after R8.
 */
@UnstableApi
final class PlayerExtractors implements ExtractorsFactory {

    private SubtitleParser.Factory subtitleParserFactory = new DefaultSubtitleParserFactory();
    private boolean textTrackTranscoding;

    /** Media3 1.4 asks the factory to convert embedded text itself; without it the text renderer rejects raw samples. */
    @Override
    public ExtractorsFactory setSubtitleParserFactory(SubtitleParser.Factory factory) {
        subtitleParserFactory = factory;
        return this;
    }

    @Override
    public ExtractorsFactory experimentalSetTextTrackTranscodingEnabled(boolean enabled) {
        textTrackTranscoding = enabled;
        return this;
    }

    @Override
    public Extractor[] createExtractors() {
        SubtitleParser.Factory f = subtitleParserFactory;
        int mkv = textTrackTranscoding ? 0 : MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA;
        int mp4 = textTrackTranscoding ? 0 : Mp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA;
        int fmp4 = textTrackTranscoding ? 0 : FragmentedMp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA;
        return new Extractor[]{
                new MatroskaExtractor(f, mkv),
                new Mp4Extractor(f, mp4),
                new FragmentedMp4Extractor(f, fmp4),
                new TsExtractor(0, f),
                new Mp3Extractor(),
                new FlacExtractor(),
                new OggExtractor(),
                new WavExtractor(),
                new AviExtractor(0, f),
        };
    }
}
