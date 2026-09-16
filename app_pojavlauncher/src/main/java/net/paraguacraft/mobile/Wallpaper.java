package net.paraguacraft.mobile;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.Toast;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Fondos del launcher. Arte propio + foto importada por el usuario. */
public final class Wallpaper {
    public static final String PREF = "pg_wallpaper";
    public static final int CUSTOM = -1;

    public static final int[] DRAWABLES = {
            R.drawable.pg_wall_logo,
            R.drawable.pg_wall_river,
            R.drawable.pg_wall_cherry,
            R.drawable.pg_wall_nether
    };
    public static final String[] NAMES = {
            "Paraguacraft",
            "Río al atardecer",
            "Cerezos",
            "Nether"
    };

    private Wallpaper() {}

    public static File customFile(Context ctx) {
        return new File(ctx.getFilesDir(), "pg_custom_wallpaper.jpg");
    }

    public static int index(Context ctx) {
        int i = LauncherPreferences.DEFAULT_PREF.getInt(PREF, 0);
        if (i == CUSTOM) return CUSTOM;
        if (i < 0 || i >= DRAWABLES.length) return 0;
        return i;
    }

    public static boolean isCustom(Context ctx) {
        return index(ctx) == CUSTOM && customFile(ctx).isFile();
    }

    public static void apply(Activity activity) {
        if (activity == null) return;
        ImageView iv = activity.findViewById(R.id.pg_wallpaper);
        if (iv == null) return;
        if (isCustom(activity)) {
            Bitmap bmp = decodeFile(customFile(activity), iv.getWidth(), iv.getHeight());
            if (bmp != null) {
                iv.setImageBitmap(bmp);
                return;
            }
        }
        int i = index(activity);
        if (i == CUSTOM) i = 0;
        iv.setImageResource(DRAWABLES[i]);
    }

    public static void set(Activity activity, int i) {
        if (i != CUSTOM && (i < 0 || i >= DRAWABLES.length)) i = 0;
        LauncherPreferences.DEFAULT_PREF.edit().putInt(PREF, i).apply();
        apply(activity);
    }

    public static boolean importPhoto(Context ctx, android.net.Uri uri) {
        if (uri == null) return false;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            Bitmap bmp = BitmapFactory.decodeStream(in);
            if (bmp == null) return false;
            File dest = customFile(ctx);
            try (FileOutputStream out = new FileOutputStream(dest)) {
                Bitmap scaled = scaleDown(bmp, 1920);
                if (scaled != bmp) bmp.recycle();
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out);
                if (scaled != bmp) scaled.recycle();
            }
            LauncherPreferences.DEFAULT_PREF.edit().putInt(PREF, CUSTOM).apply();
            if (ctx instanceof Activity) apply((Activity) ctx);
            return true;
        } catch (Exception e) {
            Toast.makeText(ctx, "Esa foto no se pudo usar.", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static Bitmap scaleDown(Bitmap src, int max) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= max && h <= max) return src;
        float s = Math.min(max / (float) w, max / (float) h);
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)), true);
    }

    private static Bitmap decodeFile(File file, int reqW, int reqH) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        if (reqW > 0 && reqH > 0 && bounds.outWidth > 0) {
            int side = Math.max(bounds.outWidth, bounds.outHeight);
            int want = Math.max(reqW, reqH);
            while (side / (opts.inSampleSize * 2) >= want) opts.inSampleSize *= 2;
        } else {
            opts.inSampleSize = 2;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }
}
