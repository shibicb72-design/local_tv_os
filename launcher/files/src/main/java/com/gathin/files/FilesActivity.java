package com.gathin.files;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanStyle;

/** Skeleton (F3): the Cinzel gold title and an empty list. The browser comes in F7. */
public final class FilesActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(RomanStyle.STONE_INK);
        int pad = (int) (48 * dp);
        root.setPadding(pad, (int) (32 * dp), pad, pad / 2);

        TextView title = new TextView(this);
        RomanLook.text(title, 22f, 0.16f, RomanStyle.DEFAULT_GOLD);
        title.setText(FilesInfo.TITLE);
        title.setGravity(Gravity.START);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setBackgroundColor(Color.TRANSPARENT);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }
}
