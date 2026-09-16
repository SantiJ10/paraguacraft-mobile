package net.paraguacraft.mobile;

import android.util.Log;

import java.io.File;

/**
 * Mods de PC que crashean en Android (nativos Linux / NVIDIA).
 * Voxy: {@code libc.so.6} / {@code LinuxLibrary}. Nvidium: GPU NVIDIA.
 */
public final class MobileCompat {
    private static final String TAG = "PgCompat";
    private static final String[] DISABLE = {
            "voxy",
            "nvidium",
            "flashback",
    };

    private MobileCompat() {}

    public static int sanitizeGameDir(File gameDir) {
        if (gameDir == null) return 0;
        File mods = new File(gameDir, "mods");
        File[] jars = mods.listFiles();
        if (jars == null) return 0;
        int n = 0;
        for (File f : jars) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".jar")) continue;
            if (!isUnsafe(name)) continue;
            File dest = new File(mods, f.getName() + ".disabled");
            if (dest.exists()) dest.delete();
            if (f.renameTo(dest)) {
                n++;
                Log.i(TAG, "Desactivado en Android: " + f.getName());
            }
        }
        return n;
    }

    public static boolean isUnsafe(String filename) {
        if (filename == null) return false;
        String n = filename.toLowerCase();
        for (String needle : DISABLE) {
            if (n.contains(needle)) return true;
        }
        return false;
    }
}
