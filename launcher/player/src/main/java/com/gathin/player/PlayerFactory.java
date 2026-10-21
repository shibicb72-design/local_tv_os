package com.gathin.player;

import android.content.Context;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/** Builds the one ExoPlayer the app uses: our extractors and the buffer caps of plan section 2.2. */
@UnstableApi
final class PlayerFactory {

    static final int MIN_BUFFER_MS = 15_000;
    static final int MAX_BUFFER_MS = 30_000;
    static final int START_BUFFER_MS = 1_500;
    static final int REBUFFER_MS = 3_000;
    static final int TARGET_BUFFER_BYTES = 32 * 1024 * 1024;

    private PlayerFactory() {}

    static ExoPlayer create(Context ctx) {
        Context app = ctx.getApplicationContext();
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, START_BUFFER_MS, REBUFFER_MS)
                .setTargetBufferBytes(TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build();
        return new ExoPlayer.Builder(app,
                new DefaultRenderersFactory(app),
                new DefaultMediaSourceFactory(new DefaultDataSource.Factory(app), new PlayerExtractors()))
                .setLoadControl(loadControl)
                .build();
    }
}
