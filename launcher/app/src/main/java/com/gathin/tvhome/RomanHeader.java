package com.gathin.tvhome;

import com.gathin.roman.RomanLook;
import com.gathin.roman.RomanStyle;

import android.app.Activity;
import android.widget.TextView;

/** Roman look v2 on the launcher's header (ids from activity_main); the generic helpers live in RomanLook. */
final class RomanHeader {

    private RomanHeader() {
    }

    /** Header, Roman date and hero name (not the pills or the tiles). */
    static void applyHeader(Activity a, RomanStyle s) {
        float density = a.getResources().getDisplayMetrics().density;

        RomanLook.text(a.findViewById(R.id.greeting), 13f, 0.30f, s.gold);

        TextView clock = a.findViewById(R.id.clock);
        RomanLook.text(clock, 56f, 0.02f, RomanStyle.IVORY);
        clock.setShadowLayer(4 * density, 0, 2 * density, RomanStyle.TEXT_SHADOW);

        RomanLook.text(a.findViewById(R.id.ampm), 14f, 0.12f, RomanStyle.IVORY_DIM_AMPM);

        a.findViewById(R.id.headerRule).setBackgroundColor(RomanStyle.withAlpha(s.gold, 0x99));

        RomanLook.text(a.findViewById(R.id.shamsiDate), 18f, 0.16f, s.goldLight);

        TextView date = a.findViewById(R.id.date);
        RomanLook.text(date, 11f, 0.20f, RomanStyle.IVORY_DIM);
        date.setPadding(0, Math.round(4 * density), 0, 0);

        TextView hero = a.findViewById(R.id.heroName);
        RomanLook.text(hero, 44f, 0.08f, RomanStyle.IVORY);
        hero.setShadowLayer(6 * density, 0, 3 * density, RomanStyle.HERO_SHADOW);
        RomanLook.gild(hero, s);
    }
}
