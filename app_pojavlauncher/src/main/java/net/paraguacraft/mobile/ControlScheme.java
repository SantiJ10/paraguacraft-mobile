package net.paraguacraft.mobile;

import android.content.Context;
import android.util.DisplayMetrics;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;

/** Controles clásicos (compactos) vs modernos (zonas más grandes), según el tamaño de pantalla. */
public final class ControlScheme {
    public static final String PREF = "pg_control_scheme";
    public static final String CLASSIC = "classic";
    public static final String MODERN = "modern";
    public static final String FILE_CLASSIC = "pg_classic.json";
    public static final String FILE_MODERN = "pg_modern.json";

    private ControlScheme() {}

    public static String current() {
        return LauncherPreferences.DEFAULT_PREF.getString(PREF, MODERN);
    }

    public static boolean isModern() {
        return MODERN.equals(current());
    }

    public static File layoutFile(String scheme) {
        String name = CLASSIC.equals(scheme) ? FILE_CLASSIC : FILE_MODERN;
        return new File(Tools.CTRLMAP_PATH, name);
    }

    /** Escala 62–140 según el lado corto en dp (celular chico vs tablet). */
    public static int buttonScale(Context ctx, String scheme) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int shortPx = Math.min(dm.widthPixels, dm.heightPixels);
        int shortDp = Math.round(shortPx / dm.density);
        float base;
        if (shortDp >= 720) base = 116;
        else if (shortDp >= 600) base = 104;
        else if (shortDp >= 480) base = 88;
        else if (shortDp >= 400) base = 76;
        else if (shortDp >= 360) base = 68;
        else base = 62;
        if (!CLASSIC.equals(scheme)) base *= 1.16f;
        else base *= 0.96f;
        int scale = Math.round(base);
        if (scale < 62) scale = 62;
        if (scale > 140) scale = 140;
        return scale;
    }

    public static void applyCurrent(Context ctx) {
        apply(ctx, current());
    }

    public static void apply(Context ctx, String scheme) {
        if (!CLASSIC.equals(scheme)) scheme = MODERN;
        ensureLayoutFiles(ctx);
        int scale = buttonScale(ctx, scheme);
        File layout = layoutFile(scheme);
        LauncherPreferences.PREF_BUTTONSIZE = scale;
        LauncherPreferences.PREF_DEFAULTCTRL_PATH = layout.getAbsolutePath();
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(PREF, scheme)
                .putInt("buttonscale", scale)
                .putString("defaultCtrl", layout.getAbsolutePath())
                .commit();
    }

    public static void ensureLayoutFiles(Context ctx) {
        File dir = new File(Tools.CTRLMAP_PATH);
        if (!dir.exists() && !dir.mkdirs()) return;
        File classic = new File(dir, FILE_CLASSIC);
        File modern = new File(dir, FILE_MODERN);
        File fallback = new File(Tools.CTRLDEF_FILE);
        copyIfMissing(ctx, classic, fallback);
        copyIfMissing(ctx, modern, fallback);
    }

    private static void copyIfMissing(Context ctx, File dest, File fallback) {
        if (dest.isFile()) return;
        try {
            if (fallback != null && fallback.isFile()) {
                SkinHelper.copyFile(fallback, dest);
                return;
            }
            Tools.copyAssetFile(ctx, "default.json", Tools.CTRLMAP_PATH, dest.getName(), false);
        } catch (Exception ignored) {}
    }
}
