package net.paraguacraft.mobile;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.GLInfoUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lectura de GPU / RAM / pantalla. No usa el nombre comercial del teléfono.
 * A25 = Mali-G68 → media; Redmi Pad Adreno 610 → débil+tablet; Poco X8 Pro Mali-G720 → alta.
 */
public final class DeviceProbe {
    public enum GpuClass { WEAK, MID, STRONG }

    public final String gpuName;
    public final String soc;
    public final int ramTotalMb;
    public final int ramAvailMb;
    public final int refreshHz;
    public final boolean tablet;
    public final int widthPx;
    public final int heightPx;
    public final GpuClass gpuClass;

    private DeviceProbe(String gpuName, String soc, int ramTotalMb, int ramAvailMb,
                        int refreshHz, boolean tablet, int widthPx, int heightPx, GpuClass gpuClass) {
        this.gpuName = gpuName;
        this.soc = soc;
        this.ramTotalMb = ramTotalMb;
        this.ramAvailMb = ramAvailMb;
        this.refreshHz = refreshHz;
        this.tablet = tablet;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.gpuClass = gpuClass;
    }

    public static DeviceProbe capture(Context ctx) {
        String gpu = gpuRenderer();
        String soc = socModel();
        int ramTotal = Tools.getTotalDeviceMemory(ctx);
        int ramAvail = Tools.getFreeDeviceMemory(ctx);
        int[] px = screenPx(ctx);
        int hz = refreshHz(ctx);
        boolean tablet = isTablet(ctx);
        GpuClass g = classifyGpu(gpu, soc, Build.DEVICE, Build.MODEL, Build.PRODUCT);
        return new DeviceProbe(gpu, soc, ramTotal, ramAvail, hz, tablet, px[0], px[1], g);
    }

    public String summary() {
        String form = tablet ? "tablet" : "celular";
        String g = gpuClass == GpuClass.STRONG ? "GPU alta"
                : gpuClass == GpuClass.MID ? "GPU media" : "GPU baja";
        return gpuName + " · " + g + " · " + form + " "
                + widthPx + "×" + heightPx + " @" + refreshHz + "Hz\n"
                + "RAM " + ramTotalMb + " MB (" + ramAvailMb + " libres) · SoC " + soc
                + " · " + Runtime.getRuntime().availableProcessors() + " hilos";
    }

    public int megapixelsTenths() {
        return (int) ((widthPx * (long) heightPx) / 100_000L);
    }

    static String gpuRenderer() {
        try {
            GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
            if (info != null && info.renderer != null && !info.renderer.isEmpty()) {
                return info.renderer;
            }
        } catch (Throwable ignored) {}
        return "Unknown";
    }

    static String socModel() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                String s = String.valueOf(Build.SOC_MODEL);
                if (s != null && !s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
            } catch (Throwable ignored) {}
        }
        String board = Build.BOARD != null ? Build.BOARD : "";
        String hw = Build.HARDWARE != null ? Build.HARDWARE : "";
        return (board + " " + hw).trim();
    }

    static boolean isTablet(Context ctx) {
        int sw = ctx.getResources().getConfiguration().smallestScreenWidthDp;
        if (sw >= 600) return true;
        int type = ctx.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK;
        return type >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    static int refreshHz(Context ctx) {
        try {
            int user = userRefreshSetting(ctx);
            if (user >= 50) return user;
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return 60;
            Display d = null;
            if (Build.VERSION.SDK_INT >= 30) {
                try { d = ctx.getDisplay(); } catch (Throwable ignored) {}
            }
            if (d == null) d = wm.getDefaultDisplay();
            float current = d.getRefreshRate();
            float max = current;
            if (Build.VERSION.SDK_INT >= 23) {
                Display.Mode mode = d.getMode();
                if (mode != null) current = Math.max(current, mode.getRefreshRate());
                Display.Mode[] modes = d.getSupportedModes();
                if (modes != null) {
                    for (Display.Mode m : modes) {
                        if (m.getRefreshRate() > max) max = m.getRefreshRate();
                    }
                }
            }
            // Sin setting del usuario: si el panel da 90 y la app quedó en 60, usar el panel.
            if (max > current + 5f) return clampHz(Math.round(max));
            return clampHz(Math.round(current));
        } catch (Throwable t) {
            return 60;
        }
    }

    /** Hz que eligió el usuario en el sistema (90 en tablet, 60 si lo bajó). No el máximo del panel. */
    static int userRefreshSetting(Context ctx) {
        try {
            android.content.ContentResolver cr = ctx.getContentResolver();
            float peak = android.provider.Settings.System.getFloat(cr, "peak_refresh_rate", 0f);
            float user = android.provider.Settings.System.getFloat(cr, "user_refresh_rate", 0f);
            float min = android.provider.Settings.System.getFloat(cr, "min_refresh_rate", 0f);
            float v = 0;
            if (user >= 50) v = user;
            else if (peak >= 50) v = peak;
            else if (min >= 50) v = min;
            if (v >= 50) return clampHz(Math.round(v));
        } catch (Throwable ignored) {}
        return 0;
    }

    static int clampHz(int v) {
        if (v < 50) return 60;
        if (v > 165) return 165;
        return v;
    }

    /** Pide a Android el Hz actual del usuario (si no, la app se queda en 60). */
    public static void applyToWindow(android.app.Activity act) {
        if (act == null) return;
        try {
            int want = capture(act).refreshHz;
            android.view.Window w = act.getWindow();
            if (w == null) return;
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            lp.preferredRefreshRate = want;
            if (Build.VERSION.SDK_INT >= 23) {
                android.view.Display d = act.getWindowManager().getDefaultDisplay();
                android.view.Display.Mode[] modes = d.getSupportedModes();
                android.view.Display.Mode best = d.getMode();
                float bestDelta = 999f;
                for (android.view.Display.Mode m : modes) {
                    float delta = Math.abs(m.getRefreshRate() - want);
                    if (delta < bestDelta && m.getPhysicalWidth() >= best.getPhysicalWidth() * 0.9f) {
                        bestDelta = delta;
                        best = m;
                    }
                }
                lp.preferredDisplayModeId = best.getModeId();
            }
            w.setAttributes(lp);
        } catch (Throwable ignored) {}
    }

    static int[] screenPx(Context ctx) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        int h = Math.min(dm.widthPixels, dm.heightPixels);
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics real = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(real);
                w = Math.max(real.widthPixels, real.heightPixels);
                h = Math.min(real.widthPixels, real.heightPixels);
            }
        } catch (Throwable ignored) {}
        return new int[]{w, h};
    }

    static GpuClass classifyGpu(String renderer, String soc, String device, String model, String product) {
        String r = safe(renderer).toLowerCase(Locale.US);
        String blob = (safe(renderer) + " " + safe(soc) + " " + safe(device) + " "
                + safe(model) + " " + safe(product) + " " + safe(Build.HARDWARE)
                + " " + safe(Build.BOARD)).toLowerCase(Locale.US);

        Matcher adreno = Pattern.compile("adreno[^0-9]{0,8}([0-9]{3,4})").matcher(r);
        if (!adreno.find()) adreno = Pattern.compile("adreno[^0-9]{0,8}([0-9]{3,4})").matcher(blob);
        if (adreno.find()) {
            int n = parseInt(adreno.group(1), 0);
            if (n >= 660) return GpuClass.STRONG;
            if (n >= 630) return GpuClass.MID;
            return GpuClass.WEAK;
        }

        if (r.contains("immortalis") || blob.contains("immortalis")) return GpuClass.STRONG;
        if (r.contains("xclipse") || blob.contains("xclipse")) return GpuClass.STRONG;

        Matcher mali = Pattern.compile("mali-?g([0-9]{2,3})").matcher(r);
        if (!mali.find()) mali = Pattern.compile("mali-?g([0-9]{2,3})").matcher(blob);
        if (mali.find()) {
            int n = parseInt(mali.group(1), 0);
            if (n >= 700) return GpuClass.STRONG;
            if (n >= 310 && n <= 510) return GpuClass.MID;
            if (n >= 610 && n < 700) return GpuClass.MID;
            if (n >= 68) return GpuClass.MID;
            return GpuClass.WEAK;
        }

        if (blob.contains("powervr") || blob.contains("sgx") || blob.contains("ge8300")) {
            return GpuClass.WEAK;
        }

        if (blob.contains("sm8750") || blob.contains("sm8650") || blob.contains("sm8550")
                || blob.contains("sm8475") || blob.contains("sm7450") || blob.contains("sm7475")) {
            return GpuClass.STRONG;
        }
        if (blob.contains("sm6225") || blob.contains("sm6220") || blob.contains("sm6115")
                || blob.contains("sm6125") || blob.contains("sm4350") || blob.contains("sm4375")
                || blob.contains("sm4450") || blob.contains("sm6375")) {
            return GpuClass.WEAK;
        }
        if (blob.contains("s5e8825") || blob.contains("exynos 1280") || blob.contains("exynos1280")
                || blob.contains("8825")) {
            return GpuClass.MID;
        }
        if (blob.contains("8500") || blob.contains("dimensity 8") || blob.contains("dimensity 9")
                || blob.contains("mt6899") || blob.contains("mt6897") || blob.contains("mt6989")
                || blob.contains("mt6991")) {
            return GpuClass.STRONG;
        }
        if (blob.contains("dimensity 7") || blob.contains("helio g8") || blob.contains("helio g9")
                || blob.contains("mt6789") || blob.contains("mt6833") || blob.contains("mt6768")
                || blob.contains("mt6769") || blob.contains("yunluo") || blob.contains("23073rpbfl")
                || blob.contains("redmi pad")) {
            return GpuClass.WEAK;
        }
        if (blob.contains("a25") || blob.contains("sm-a256") || blob.contains("sm-a253")) {
            return GpuClass.MID;
        }
        return GpuClass.MID;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return fallback;
        }
    }
}
