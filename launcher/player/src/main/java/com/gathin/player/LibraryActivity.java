package com.gathin.player;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanStyle;

/** Launcher entry (P1 placeholder): the library with "Continue watching" arrives in P8. */
public class LibraryActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setText("PLAYER\nOpen a video or song from Files");
        RomanLook.text(tv, 28, 0.08f, RomanStyle.IVORY);
        setContentView(tv);
    }
}
