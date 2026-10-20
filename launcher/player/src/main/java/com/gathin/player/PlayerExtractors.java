package com.gathin.player;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.avi.AviExtractor;
import androidx.media3.extractor.flac.FlacExtractor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.mp3.Mp3Extractor;
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

    @Override
    public Extractor[] createExtractors() {
        return new Extractor[]{
                new MatroskaExtractor(),
                new Mp4Extractor(),
                new FragmentedMp4Extractor(),
                new TsExtractor(),
                new Mp3Extractor(),
                new FlacExtractor(),
                new OggExtractor(),
                new WavExtractor(),
                new AviExtractor(),
        };
    }
}
