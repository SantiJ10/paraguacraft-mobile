package net.paraguacraft.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

/**
 * Gama móvil según GPU + forma (celular/tablet) + RAM libre.
 * RAM alta no implica GPU alta: Adreno 610 con 8 GB sigue siendo gama media/baja.
 */
public final class DeviceTier {
    public static final String PREF_TIER = "paraguacraft_tier";
    public static final String PREF_TIER_MANUAL = "pg_tier_manual";
    public static final String PREF_UNCAP_FPS = "pg_uncap_fps";
    public static final String PREF_GPU_SEEN = "pg_gpu_seen";
    /** Ultima asignacion de RAM automatica, para no pisar la que ajusto el jugador. */
    public static final String PREF_LAST_ALLOC = "pg_last_auto_alloc";
    public static final String LITE = "lite";
    public static final String MEDIA = "media";
    public static final String ALTA = "alta";

    private DeviceTier() {}

    public static DeviceProbe probe(Context ctx) {
        return DeviceProbe.capture(ctx);
    }

    public static int deviceRamMb(Context ctx) {
        return Tools.getTotalDeviceMemory(ctx);
    }

    public static boolean isWeakGpu() {
        return DeviceProbe.classifyGpu(
                DeviceProbe.gpuRenderer(),
                DeviceProbe.socModel(),
                Build.DEVICE,
                Build.MODEL,
                Build.PRODUCT) == DeviceProbe.GpuClass.WEAK;
    }

    public static boolean isWeakGpu(Context ctx) {
        return probe(ctx).gpuClass == DeviceProbe.GpuClass.WEAK;
    }

    public static boolean uncapFps(Context ctx) {
        return LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_UNCAP_FPS, false);
    }

    public static void setUncapFps(Context ctx, boolean uncap) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(PREF_UNCAP_FPS, uncap)
                .putBoolean("force_vsync", false)
                .putBoolean("vsync_in_zink", false)
                .apply();
        LauncherPreferences.PREF_FORCE_VSYNC = false;
        LauncherPreferences.PREF_VSYNC_IN_ZINK = false;
    }

    /** 260 = ilimitado (vanilla/OptiFine). Si no, el Hz de la pantalla (60/90/120). */
    public static int fpsCap(Context ctx) {
        if (uncapFps(ctx)) return 260;
        int hz = probe(ctx).refreshHz;
        if (hz < 60) hz = 60;
        return hz;
    }

    public static String detect(Context ctx) {
        DeviceProbe p = probe(ctx);
        int ram = p.ramTotalMb;
        switch (p.gpuClass) {
            case WEAK:
                if (ram <= 4096) return LITE;
                if (p.tablet) return ram <= 6144 ? LITE : MEDIA;
                return MEDIA;
            case MID:
                if (ram <= 3584) return LITE;
                return MEDIA;
            default:
                if (ram <= 4096) return MEDIA;
                return ALTA;
        }
    }

    public static int xmxMb(Context ctx, String tier) {
        DeviceProbe p = probe(ctx);
        int leave = p.tablet ? 1792 : 1280;
        if (p.ramTotalMb <= 4096) leave = 1024;
        int maxByTotal = Math.max(768, p.ramTotalMb - leave);
        int maxByAvail = Math.max(768, p.ramAvailMb - 384);
        // La RAM libre es una foto del momento. Si el celular estaba ocupado justo
        // cuando se calculo esto, el valor queda guardado y el juego se queda con un
        // heap chico para siempre, asi que no se permite bajar de la mitad del tope
        // por RAM total.
        int max = Math.max(Math.min(maxByTotal, maxByAvail), maxByTotal / 2);
        int want;
        switch (tier) {
            case ALTA:
                want = p.ramTotalMb >= 7168 ? 2560 : 2304;
                break;
            case MEDIA:
                if (p.ramTotalMb >= 7168) want = 2176;
                else if (p.ramTotalMb >= 5632) want = 1920;
                else if (p.ramTotalMb >= 4096) want = 1664;
                else want = 1536;
                break;
            default:
                want = p.ramTotalMb >= 5632 ? 1408 : (p.ramTotalMb >= 4096 ? 1280 : 1024);
                break;
        }
        return Math.min(want, max);
    }

    /** Zink/MobileGlues en GPU débil = negro / 10 fps. 1.8.9/1.12.2 siempre GL4ES. */
    public static String renderer(String tier) {
        return renderer(null, tier);
    }

    public static String renderer(Context ctx, String tier) {
        String forced = LauncherPreferences.DEFAULT_PREF.getString("pg_renderer", "auto");
        if (forced != null && !forced.isEmpty() && !"auto".equals(forced)) return forced;
        boolean weak = ctx != null ? isWeakGpu(ctx) : isWeakGpu();
        if (weak || LITE.equals(tier)) return "opengles2";
        return "opengles_mobileglues";
    }

    public static boolean shouldUseGl4es(String versionId) {
        if (isWeakGpu()) return true;
        if (versionId == null) return false;
        String v = versionId.toLowerCase();
        return v.contains("1.12.2") || v.contains("1.8.9") || v.contains("1.7.10");
    }

    /**
     * GL4ES es el único backend confiable antes de 1.17: MobileGlues y Zink traducen
     * GL de escritorio moderno y en versiones viejas dan pantalla negra o texturas rotas.
     *
     * Mirar solo el ID de versión dejaba afuera 1.9 a 1.16, y parsearlo no sirve porque
     * IDs como "1.21.1-neoforge-21.1.42" engañan a cualquier regex. El Java que pide el
     * JSON de la versión sí es fiable: Java 8 equivale a 1.16 o anterior.
     */
    public static boolean shouldUseGl4es(String versionId, int requiredJavaMajor) {
        if (requiredJavaMajor > 0 && requiredJavaMajor <= 8) return true;
        return shouldUseGl4es(versionId);
    }

    public static int resolutionPercent(Context ctx, String tier) {
        DeviceProbe p = probe(ctx);
        int base;
        switch (p.gpuClass) {
            case STRONG:
                base = ALTA.equals(tier) ? 100 : 90;
                break;
            case MID:
                base = p.tablet ? 75 : 85;
                break;
            default:
                base = p.tablet ? 55 : 68;
                break;
        }
        if (p.widthPx * (long) p.heightPx > 3_000_000L) base -= 8;
        if (base < 50) base = 50;
        if (base > 100) base = 100;
        return base;
    }

    /** @deprecated usar {@link #resolutionPercent(Context, String)} */
    public static int resolutionPercent(String tier) {
        if (ALTA.equals(tier) && !isWeakGpu()) return 100;
        if (MEDIA.equals(tier)) return 80;
        return 70;
    }

    public static void applyFirstLaunch(Context ctx) {
        reconcile(ctx);
    }

    public static void reconcile(Context ctx) {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        DeviceProbe p = probe(ctx);
        String detected = detect(ctx);
        String stored = prefs.getString(PREF_TIER, null);
        boolean manual = prefs.getBoolean(PREF_TIER_MANUAL, false);
        String seenGpu = prefs.getString(PREF_GPU_SEEN, "");
        boolean gpuRefined = p.gpuName != null && !p.gpuName.contains("Unknown")
                && !p.gpuName.equals(seenGpu);
        boolean unsafeAlta = ALTA.equals(stored) && !ALTA.equals(detected);
        boolean mustApply = stored == null || unsafeAlta || (gpuRefined && !manual);
        String tier = mustApply ? detected : (stored != null ? stored : detected);
        if (!prefs.getBoolean("pg_vsync_off_v11", false)) {
            prefs.edit().putBoolean("force_vsync", false).putBoolean("pg_vsync_off_v11", true).apply();
            LauncherPreferences.PREF_FORCE_VSYNC = false;
        }
        if (mustApply) {
            int res = resolutionPercent(ctx, tier);
            // Si el jugador movio el slider de RAM, su valor manda: solo pisamos la
            // asignacion mientras siga siendo la que dejamos nosotros.
            int storedAlloc = prefs.getInt("allocation", 0);
            int lastAuto = prefs.getInt(PREF_LAST_ALLOC, 0);
            boolean allocTouchedByUser = storedAlloc > 0 && storedAlloc != lastAuto;
            int xmx = allocTouchedByUser ? storedAlloc : xmxMb(ctx, tier);
            SharedPreferences.Editor edit = prefs.edit()
                    .putString(PREF_TIER, tier)
                    .putString(PREF_GPU_SEEN, p.gpuName)
                    .putBoolean(PREF_TIER_MANUAL, false)
                    .putInt("resolutionRatio", res)
                    .putBoolean("sustainedPerformance", true);
            if (!allocTouchedByUser) {
                edit.putInt("allocation", xmx).putInt(PREF_LAST_ALLOC, xmx);
            }
            edit.apply();
            LauncherPreferences.PREF_RAM_ALLOCATION = xmx;
            LauncherPreferences.PREF_SCALE_FACTOR = res / 100f;
            LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = true;
        } else {
            prefs.edit().putString(PREF_GPU_SEEN, p.gpuName).apply();
        }
        syncProfilesRenderer(ctx, tier);
    }

    private static void syncProfilesRenderer(String tier) {
        syncProfilesRenderer(null, tier);
    }

    private static void syncProfilesRenderer(Context ctx, String tier) {
        try {
            LauncherProfiles.load();
            if (LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null)
                return;
            String renderer = ctx != null ? renderer(ctx, tier) : renderer(tier);
            boolean changed = false;
            for (MinecraftProfile p : LauncherProfiles.mainProfileJson.profiles.values()) {
                if (p == null) continue;
                boolean optimized = p.name != null && p.name.startsWith("Optimized ");
                boolean badGpuRenderer = isWeakGpu() && p.pojavRendererName != null
                        && (p.pojavRendererName.contains("mobileglues") || p.pojavRendererName.contains("zink"));
                if (!optimized && !badGpuRenderer) continue;
                if (!renderer.equals(p.pojavRendererName)) {
                    p.pojavRendererName = renderer;
                    changed = true;
                }
            }
            if (changed) LauncherProfiles.write();
        } catch (Throwable ignored) {}
    }

    public static String current(Context ctx) {
        return LauncherPreferences.DEFAULT_PREF.getString(PREF_TIER, detect(ctx));
    }

    public static void stampProfile(MinecraftProfile p) {
        if (p == null) return;
        String tier = LauncherPreferences.DEFAULT_PREF.getString(PREF_TIER, LITE);
        // En versiones viejas MobileGlues arranca en negro. El launcher igual lo
        // corrige al arrancar, pero marcarlo bien desde el arranque evita que la
        // instancia muestre un renderer que despues no se va a usar.
        p.pojavRendererName = shouldUseGl4es(p.lastVersionId)
                ? "opengles2"
                : renderer(null, tier);
    }

    public static void setTier(Context ctx, String tier) {
        int xmx = xmxMb(ctx, tier);
        int res = resolutionPercent(ctx, tier);
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(PREF_TIER, tier)
                .putBoolean(PREF_TIER_MANUAL, true)
                .putInt("allocation", xmx)
                .putInt(PREF_LAST_ALLOC, xmx)
                .putInt("resolutionRatio", res)
                .apply();
        LauncherPreferences.PREF_RAM_ALLOCATION = xmx;
        LauncherPreferences.PREF_SCALE_FACTOR = res / 100f;
        syncProfilesRenderer(ctx, tier);
    }
}
