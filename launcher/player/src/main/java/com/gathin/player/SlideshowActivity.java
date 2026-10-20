package com.gathin.player;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanStyle;

/** Handles VIEW image/* (P1 placeholder): the slideshow arrives in P7. */
public class SlideshowActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setText("Photo slideshow is not ready yet");
        RomanLook.text(tv, 28, 0.08f, RomanStyle.IVORY);
        setContentView(tv);
    }
}
