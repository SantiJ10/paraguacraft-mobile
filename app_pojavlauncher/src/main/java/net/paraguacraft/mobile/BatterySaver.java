package net.paraguacraft.mobile;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.MCOptionUtils;

import java.io.File;

/**
 * Modo ahorro de batería / térmico: baja FPS, distancia de render y partículas antes de iniciar,
 * y avisa si el celular se calienta. Solo baja valores, nunca los sube.
 */
public final class BatterySaver {
    public static final String PREF_ENABLED = "pgBatterySaver";
    public static final String PREF_FPS = "pgBatterySaverFps";
    private static final String TAG = "PgBatterySaver";
    private static boolean sThermalRegistered = false;

    private BatterySaver() {}

    public static boolean isEnabled() {
        return LauncherPreferences.DEFAULT_PREF != null
                && LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_ENABLED, false);
    }

    public static int fpsCap() {
        try {
            return Integer.parseInt(LauncherPreferences.DEFAULT_PREF.getString(PREF_FPS, "45"));
        } catch (Exception e) {
            return 45;
        }
    }

    public static void setConfig(boolean enabled, int fps) {
        if (LauncherPreferences.DEFAULT_PREF == null) return;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(PREF_ENABLED, enabled)
                .putString(PREF_FPS, String.valueOf(fps))
                .apply();
    }

    /** Aplica los límites al options.txt de la instancia. Llamar antes de iniciar el juego. */
    public static void applyToGameDir(File gameDir) {
        if (!isEnabled() || gameDir == null) return;
        try {
            MCOptionUtils.load(gameDir.getAbsolutePath());
            int cap = fpsCap();
            lowerTo("maxFps", cap, 260);
            lowerTo("renderDistance", 6, 12);
            lowerTo("simulationDistance", 5, 12);
            MCOptionUtils.set("enableVsync", "false");
            // particles: 0 = todas, 1 = reducidas, 2 = mínimas
            String particles = MCOptionUtils.get("particles");
            int p = parse(particles, 0);
            if (p < 1) MCOptionUtils.set("particles", "1");
            MCOptionUtils.save();
            Log.i(TAG, "Ahorro de batería aplicado (FPS " + cap + ")");
        } catch (Throwable e) {
            Log.w(TAG, "No se pudo aplicar ahorro de batería", e);
        }
    }

    private static void lowerTo(String key, int max, int defaultValue) {
        int current = parse(MCOptionUtils.get(key), defaultValue);
        if (current <= 0 || current > max) MCOptionUtils.set(key, String.valueOf(max));
    }

    private static int parse(String s, int def) {
        try {
            return s == null ? def : (int) Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** Avisa cuando el celular está caliente (Android 10+). */
    public static void watchThermal(Activity activity) {
        if (!isEnabled() || sThermalRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            pm.addThermalStatusListener(activity.getMainExecutor(), status -> {
                if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
                    Toast.makeText(activity,
                            "El celular está muy caliente: bajá la distancia de render o hacé una pausa.",
                            Toast.LENGTH_LONG).show();
                } else if (status == PowerManager.THERMAL_STATUS_MODERATE) {
                    Toast.makeText(activity,
                            "El celular se está calentando. Puede bajar el rendimiento.",
                            Toast.LENGTH_SHORT).show();
                }
            });
            sThermalRegistered = true;
        } catch (Throwable e) {
            Log.w(TAG, "Thermal listener no disponible", e);
        }
    }
}
