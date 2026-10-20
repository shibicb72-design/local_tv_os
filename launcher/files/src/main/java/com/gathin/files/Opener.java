package com.gathin.files;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

/** Opens a file (F8): our Player for video/audio/image when installed, the system chooser otherwise. */
final class Opener {

    static final String PLAYER_PACKAGE = "com.gathin.player";
    static final String EXTRA_PATH = "com.gathin.extra.PATH";

    private Opener() {}

    /** Default action for OK on a file. */
    static void open(Context ctx, String path) {
        MimeKinds.Kind kind = MimeKinds.kindOf(new java.io.File(path).getName());
        boolean media = kind == MimeKinds.Kind.VIDEO || kind == MimeKinds.Kind.AUDIO || kind == MimeKinds.Kind.IMAGE;
        if (media && playerInstalled(ctx)) {
            Intent view = viewIntent(path);
            view.setPackage(PLAYER_PACKAGE);
            view.putExtra(EXTRA_PATH, path);
            if (start(ctx, view)) return;
        }
        openWith(ctx, path);
    }

    /** "Open with...": always the chooser. */
    static void openWith(Context ctx, String path) {
        Intent chooser = Intent.createChooser(viewIntent(path), null);
        if (!start(ctx, chooser)) {
            Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_LONG).show();
        }
    }

    private static Intent viewIntent(String path) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(StreamProvider.uriFor(path), MimeKinds.mimeOf(new java.io.File(path).getName()));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    private static boolean playerInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(PLAYER_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean start(Context ctx, Intent i) {
        try {
            ctx.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
