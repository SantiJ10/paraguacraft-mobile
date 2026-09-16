package net.paraguacraft.mobile;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.NewJREUtil;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.FabricVersion;
import net.kdt.pojavlaunch.modloaders.FabriclikeDownloadTask;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.modloaders.ForgeDownloadTask;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

/**
 * Instala un pack Optimized (Fabric directo; Forge 1.8.9 silencioso; 1.12.2 con instalador Java NPS).
 * Fabric: Sodium + Iris. Shaders hasta gama media (nunca Unbound / Photon).
 */
public final class OptimizedInstaller {
    private static final String TAG = "PgOptimized";
    /** Valor con el que Minecraft muestra "Ilimitado" en las opciones de video. */
    private static final int UNLIMITED_FPS = 260;
    /** Ultimo maxFps que escribimos, para distinguirlo de uno puesto por el jugador. */
    private static final String PREF_LAST_FPS = "pg_last_written_fps";

    public interface Progress {
        void onMessage(String msg);
    }

    /** El instalador de Forge tiene que correr en JavaGUILauncherActivity (agente NPS). */
    public static final class ForgeGuiNeeded extends Exception {
        public final OptimizedManifest.Pack pack;
        public final File installerJar;
        public ForgeGuiNeeded(OptimizedManifest.Pack pack, File installerJar) {
            super("Instalador Forge");
            this.pack = pack;
            this.installerJar = installerJar;
        }
    }

    public static final String PREF_PENDING_FORGE_MC = "pg_pending_forge_mc";

    private OptimizedInstaller() {}

    public static String profileName(String mc) {
        return "Optimized " + mc;
    }

    public static synchronized void install(Context ctx, OptimizedManifest.Pack pack, Progress progress) throws Exception {
        String tier = DeviceTier.current(ctx);
        boolean lite = DeviceTier.LITE.equals(tier);
        progress.onMessage("Instalando Optimized " + pack.mc + " (" + pack.loader + ")");

        String versionId;
        MinecraftProfile existingProfile = findProfile(pack.mc);
        if ("forge".equals(pack.loader)) {
            versionId = findForgeVersionId(pack);
            if (versionId == null) {
                progress.onMessage("Preparando Java 8 para Forge…");
                ensureJava8(ctx);
                File staleShared = new File(Tools.DIR_CACHE, "forge-installer.jar");
                if (staleShared.isFile()) {
                    //noinspection ResultOfMethodCallIgnored
                    staleShared.delete();
                }
                if (ForgeLegacyInstall.isLegacyMc(pack.mc)) {
                    versionId = ForgeLegacyInstall.install(ctx, pack, progress);
                } else {
                    progress.onMessage("Descargando instalador Forge " + pack.mc + " " + pack.loaderVersion);
                    File installer = downloadForgeInstaller(pack);
                    throw new ForgeGuiNeeded(pack, installer);
                }
            }
            progress.onMessage("Forge listo: " + versionId);
        } else if (existingProfile != null && existingProfile.lastVersionId != null
                && existingProfile.lastVersionId.contains(pack.mc)
                && versionJsonExists(existingProfile.lastVersionId)) {
            versionId = existingProfile.lastVersionId;
            progress.onMessage("Loader ya instalado: " + versionId);
        } else if ("fabric".equals(pack.loader)) {
            versionId = installFabric(pack.mc, progress);
        } else {
            throw new IOException("Loader no soportado: " + pack.loader);
        }

        File gameDir = new File(Tools.DIR_GAME_HOME, "optimized-" + pack.mc);
        //noinspection ResultOfMethodCallIgnored
        gameDir.mkdirs();
        File mods = new File(gameDir, "mods");
        //noinspection ResultOfMethodCallIgnored
        mods.mkdirs();

        if (pack.modrinth != null) {
            for (int i = 0; i < pack.modrinth.length(); i++) {
                JSONObject pin = pack.modrinth.getJSONObject(i);
                String slug = pin.getString("slug");
                if (pin.optBoolean("mobileSkip", false)) continue;
                if ("voxy".equals(slug) || "nvidium".equals(slug) || "oculus".equals(slug)) continue;
                if (lite && !pin.optBoolean("mobileLite", false) && !"iris".equals(slug)) continue;
                progress.onMessage("Mod " + slug);
                downloadModrinthVersion(pin.getString("versionId"), mods);
            }
        }
        if (pack.optifine != null) {
            downloadOptiFine(pack, mods, progress);
        }
        if (pack.curseforge != null) {
            for (int i = 0; i < pack.curseforge.length(); i++) {
                JSONObject pin = pack.curseforge.getJSONObject(i);
                if (lite && !pin.optBoolean("mobileLite", false)) continue;
                progress.onMessage("Mod CF " + pin.optString("slug"));
                try {
                    downloadCurseForge(pin, mods);
                } catch (Exception e) {
                    Log.w(TAG, "CF skip " + pin.optString("slug"), e);
                    progress.onMessage("Saltado " + pin.optString("slug") + " (CurseForge)");
                }
            }
        }

        applyOptions(ctx, gameDir, pack.mc, tier);
        writeSodiumHints(ctx, gameDir, pack, tier);
        if ("fabric".equals(pack.loader)) {
            installIrisShaders(ctx, gameDir, pack.mc, tier, progress, true);
        }
        BrandPack.inject(ctx, gameDir, pack.mc);
        MobileCompat.sanitizeGameDir(gameDir);
        writeProfile(ctx, pack, versionId, gameDir, tier);
        progress.onMessage("Listo: " + profileName(pack.mc));
    }

    public static void reinstallMods(Context ctx, OptimizedManifest.Pack pack, Progress progress) throws Exception {
        File gameDir = new File(Tools.DIR_GAME_HOME, "optimized-" + pack.mc);
        File mods = new File(gameDir, "mods");
        if (mods.isDirectory()) {
            File[] jars = mods.listFiles();
            if (jars != null) {
                for (File f : jars) {
                    if (f.getName().endsWith(".jar")) //noinspection ResultOfMethodCallIgnored
                        f.delete();
                }
            }
        }
        install(ctx, pack, progress);
    }

    public static File latestLog(MinecraftProfile profile) {
        File dir = Tools.getGameDirPath(profile);
        return new File(dir, "logs/latest.log");
    }

    public static void applyOptions(File gameDir, String mc, String tier) {
        applyOptions(null, gameDir, mc, tier);
    }

    public static void applyOptions(Context ctx, File gameDir, String mc, String tier) {
        DeviceProbe probe = ctx != null ? DeviceTier.probe(ctx) : null;
        DeviceProbe.GpuClass gpu = probe != null ? probe.gpuClass : DeviceProbe.GpuClass.MID;
        boolean tablet = probe != null && probe.tablet;
        boolean legacy = mc.equals("1.8.9") || mc.equals("1.12.2");
        boolean lite = DeviceTier.LITE.equals(tier);
        boolean alta = DeviceTier.ALTA.equals(tier);
        int fps = ctx != null ? DeviceTier.fpsCap(ctx) : 60;
        boolean uncap = ctx != null && DeviceTier.uncapFps(ctx);

        String rd = gpu == DeviceProbe.GpuClass.WEAK ? "4"
                : gpu == DeviceProbe.GpuClass.MID ? "6" : (alta ? "8" : "6");
        String sim = gpu == DeviceProbe.GpuClass.WEAK ? "4" : gpu == DeviceProbe.GpuClass.MID ? "5" : "6";
        String entity = gpu == DeviceProbe.GpuClass.WEAK ? "0.5"
                : gpu == DeviceProbe.GpuClass.MID ? "0.6" : "0.75";

        MCOptionUtils.load(gameDir.getAbsolutePath());
        MCOptionUtils.set("renderDistance", rd);
        rememberWrittenFps(fps);
        MCOptionUtils.set("maxFps", String.valueOf(fps));
        MCOptionUtils.set("enableVsync", "false");
        MCOptionUtils.set("inactivityFpsLimit", "minimized");
        MCOptionUtils.set("fullscreen", "false");
        MCOptionUtils.set("useVbo", "true");
        MCOptionUtils.set("fboEnable", lite ? "false" : "true");
        MCOptionUtils.set("guiScale", tablet ? "3" : "2");
        MCOptionUtils.set("entityShadows", "false");
        MCOptionUtils.set("renderClouds", "false");
        if (legacy) {
            MCOptionUtils.set("fancyGraphics", lite || gpu == DeviceProbe.GpuClass.WEAK ? "false" : "true");
            MCOptionUtils.set("ao", lite || gpu == DeviceProbe.GpuClass.WEAK ? "0" : "1");
            MCOptionUtils.set("particles", gpu == DeviceProbe.GpuClass.WEAK ? "0" : "1");
        } else {
            MCOptionUtils.set("simulationDistance", sim);
            MCOptionUtils.set("particles", gpu == DeviceProbe.GpuClass.WEAK ? "2" : "1");
            MCOptionUtils.set("graphicsMode", lite || gpu == DeviceProbe.GpuClass.WEAK ? "0" : "1");
            MCOptionUtils.set("ao", lite || gpu == DeviceProbe.GpuClass.WEAK ? "false" : "true");
            MCOptionUtils.set("entityDistanceScaling", entity);
            MCOptionUtils.set("mipmapLevels", gpu == DeviceProbe.GpuClass.WEAK ? "0" : "1");
            MCOptionUtils.set("biomeBlendRadius", gpu == DeviceProbe.GpuClass.STRONG ? "1" : "0");
            MCOptionUtils.set("prioritizeChunkUpdates", "0");
        }
        MCOptionUtils.save();
        disableDynamicFps(gameDir);
        if (legacy) writeOptiFineOptions(gameDir, gpu, uncap);
        writeTunedMarker(ctx, gameDir, mc, tier);
    }

    public static final String TUNED_MARKER = ".paraguacraft_optimized_tuned";
    public static final String TUNED_VERSION = "v13";

    public static void applyOptionsIfNeeded(Context ctx, File gameDir, String mc, String tier) {
        File marker = new File(gameDir, TUNED_MARKER);
        String wantUncap = String.valueOf(ctx != null && DeviceTier.uncapFps(ctx));
        if (marker.isFile()) {
            try {
                String have = Tools.read(marker.getAbsolutePath());
                if (have.contains("version=" + TUNED_VERSION)
                        && have.contains("tier=" + tier)
                        && have.contains("mc=" + mc)
                        && have.contains("uncap=" + wantUncap)) {
                    return;
                }
            } catch (Exception ignored) {}
        }
        applyOptions(ctx, gameDir, mc, tier);
    }

    public static void applyFpsOnly(Context ctx, File gameDir, String mc, String tier) {
        if (ctx == null) return;
        MCOptionUtils.load(gameDir.getAbsolutePath());

        // El botón "FPS ilimitado" del launcher manda. Antes, si options.txt
        // ya tenía 60 (el valor que escribimos al instalar) y pg_last_written_fps
        // no existía, se interpretaba como elección del jugador y el toggle no
        // hacía nada: Optimized 1.21.11 quedaba clavado a 60.
        int fps = DeviceTier.fpsCap(ctx);
        rememberWrittenFps(fps);
        MCOptionUtils.set("maxFps", String.valueOf(fps));
        MCOptionUtils.set("enableVsync", "false");
        MCOptionUtils.set("inactivityFpsLimit", "minimized");
        MCOptionUtils.save();

        boolean uncap = DeviceTier.uncapFps(ctx) || fps >= UNLIMITED_FPS;
        syncSodiumFpsLimit(gameDir, uncap ? 0 : fps);
        disableDynamicFps(gameDir);
        if (mc.equals("1.8.9") || mc.equals("1.12.2")) {
            patchColonFile(new File(gameDir, "optionsof.txt"), new String[][]{
                    {"ofSmoothFps", uncap ? "false" : "true"},
                    {"ofShowFps", "true"}
            });
        }
        writeTunedMarker(ctx, gameDir, mc, tier);
    }

    /**
     * Dynamic FPS recorta FPS si cree que la ventana está tapada (los controles
     * de Pojav cuentan). El tope lo pone el launcher o el juego, no este mod.
     */
    private static void disableDynamicFps(File gameDir) {
        File cfg = new File(gameDir, "config");
        //noinspection ResultOfMethodCallIgnored
        cfg.mkdirs();
        String body = "{\"enabled\":false}";
        for (String name : new String[]{"dynamic_fps.json", "dynamicfps.json"}) {
            try {
                Tools.write(new File(cfg, name).getAbsolutePath(), body);
            } catch (Exception e) {
                Log.w(TAG, "dynamic-fps " + name, e);
            }
        }
    }

    private static void rememberWrittenFps(int fps) {
        LauncherPreferences.DEFAULT_PREF.edit().putInt(PREF_LAST_FPS, fps).apply();
    }

    /**
     * Sodium Extra limita los FPS por su cuenta y solo se escribía al instalar,
     * así que su tope viejo seguía vivo aunque maxFps quedara en ilimitado.
     */
    private static void syncSodiumFpsLimit(File gameDir, int limit) {
        File cfg = new File(gameDir, "config");
        File file = new File(cfg, "sodium-extra-options.json");
        try {
            //noinspection ResultOfMethodCallIgnored
            cfg.mkdirs();
            if (!file.isFile()) {
                Tools.write(file.getAbsolutePath(),
                        "{\"overlay\":{\"show_fps\":true},\"extra\":{\"steady_debug_fps\":false},\"performance\":{\"fps_limit\":"
                                + limit + "}}");
                return;
            }
            String json = Tools.read(file);
            if (json == null || json.isEmpty()) return;
            String patched;
            if (json.contains("\"fps_limit\"")) {
                patched = json.replaceAll("\"fps_limit\"\\s*:\\s*\\d+", "\"fps_limit\":" + limit);
            } else if (json.contains("\"fpsLimit\"")) {
                patched = json.replaceAll("\"fpsLimit\"\\s*:\\s*\\d+", "\"fpsLimit\":" + limit);
            } else if (json.contains("\"performance\"")) {
                patched = json.replaceFirst("\"performance\"\\s*:\\s*\\{",
                        "\"performance\":{\"fps_limit\":" + limit + ",");
            } else {
                patched = json.replaceFirst("\\{", "{\"performance\":{\"fps_limit\":" + limit + "},");
            }
            if (!patched.equals(json)) {
                Tools.write(file.getAbsolutePath(), patched);
            }
        } catch (Exception e) {
            Log.w(TAG, "sodium fps_limit", e);
        }
    }

    private static void writeTunedMarker(Context ctx, File gameDir, String mc, String tier) {
        try {
            Tools.write(new File(gameDir, TUNED_MARKER).getAbsolutePath(),
                    "version=" + TUNED_VERSION
                            + "\ntier=" + (tier != null ? tier : "")
                            + "\nmc=" + (mc != null ? mc : "")
                            + "\nuncap=" + (ctx != null && DeviceTier.uncapFps(ctx))
                            + "\n");
        } catch (Exception e) {
            Log.w(TAG, "tuned marker", e);
        }
    }

    private static void writeOptiFineOptions(File gameDir, DeviceProbe.GpuClass gpu, boolean uncap) {
        boolean weak = gpu == DeviceProbe.GpuClass.WEAK;
        boolean mid = gpu == DeviceProbe.GpuClass.MID;
        String[][] pairs = {
                {"ofFastRender", weak || mid ? "true" : "false"},
                {"ofFastMath", "true"},
                {"ofSmoothFps", uncap ? "false" : "true"},
                {"ofSmoothWorld", weak ? "false" : "true"},
                {"ofAaLevel", "0"},
                {"ofAfLevel", weak ? "1" : (mid ? "2" : "4")},
                {"ofClouds", "3"},
                {"ofTrees", weak ? "0" : "1"},
                {"ofDroppedItems", weak ? "0" : "1"},
                {"ofVignette", "0"},
                {"ofDynamicLights", weak ? "3" : "2"},
                {"ofAnimatedTerrain", weak ? "false" : "true"},
                {"ofAnimatedTextures", weak ? "false" : "true"},
                {"ofAnimatedWater", weak ? "2" : "1"},
                {"ofAnimatedFire", weak ? "false" : "true"},
                {"ofAnimatedExplosion", weak ? "false" : "true"},
                {"ofShowFps", "true"},
                {"ofLazyChunkLoading", "true"},
                {"ofChunkUpdates", "1"},
                {"ofRenderRegions", "true"},
                {"ofFogType", "3"},
        };
        patchColonFile(new File(gameDir, "optionsof.txt"), pairs);
    }

    private static void patchColonFile(File file, String[][] pairs) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        if (file.isFile()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int c = line.indexOf(':');
                    if (c < 1) continue;
                    map.put(line.substring(0, c), line.substring(c + 1));
                }
            } catch (Exception ignored) {}
        }
        for (String[] kv : pairs) map.put(kv[0], kv[1]);
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            sb.append(e.getKey()).append(':').append(e.getValue()).append('\n');
        }
        try {
            Tools.write(file.getAbsolutePath(), sb.toString());
        } catch (Exception e) {
            Log.w(TAG, "optionsof", e);
        }
    }

    private static String installFabric(String mc, Progress progress) throws IOException {
        progress.onMessage("Fabric loader para " + mc);
        FabricVersion[] versions = FabriclikeUtils.FABRIC_UTILS.downloadLoaderVersions(mc);
        if (versions == null || versions.length == 0) {
            throw new IOException("No hay Fabric loader para " + mc);
        }
        String loader = versions[0].version;
        for (FabricVersion v : versions) {
            if (v.stable) {
                loader = v.version;
                break;
            }
        }
        final String[] err = {null};
        final boolean[] ok = {false};
        FabriclikeDownloadTask task = new FabriclikeDownloadTask(new ModloaderDownloadListener() {
            @Override public void onDownloadFinished(File downloadedFile) { ok[0] = true; }
            @Override public void onDataNotAvailable() { err[0] = "Fabric no disponible"; }
            @Override public void onDownloadError(Exception e) { err[0] = e.getMessage(); }
        }, FabriclikeUtils.FABRIC_UTILS, mc, loader, false);
        task.run();
        if (err[0] != null) throw new IOException(err[0]);
        if (!ok[0]) throw new IOException("Fabric no se instaló");
        return "fabric-loader-" + loader + "-" + mc;
    }

    public static boolean isInstalled(OptimizedManifest.Pack pack) {
        MinecraftProfile p = findProfile(pack.mc);
        if (p == null) return false;
        File mods = new File(new File(Tools.DIR_GAME_HOME, "optimized-" + pack.mc), "mods");
        if (!mods.isDirectory()) return false;
        File[] jars = mods.listFiles((d, n) -> n.endsWith(".jar"));
        return jars != null && jars.length > 0;
    }

    private static boolean versionJsonExists(String versionId) {
        File json = new File(Tools.DIR_HOME_VERSION, versionId + File.separator + versionId + ".json");
        return json.isFile() && json.length() > 0;
    }

    public static String findForgeVersionId(OptimizedManifest.Pack pack) {
        String canonical = pack.mc + "-forge-" + pack.loaderVersion;
        if (versionJsonExists(canonical)) return canonical;
        File versions = new File(Tools.DIR_HOME_VERSION);
        File[] dirs = versions.listFiles();
        if (dirs == null) return null;
        for (File d : dirs) {
            String n = d.getName();
            if (n.toLowerCase().contains("forge") && n.contains(pack.mc)
                    && n.contains(pack.loaderVersion) && versionJsonExists(n)) {
                return n;
            }
        }
        return null;
    }

    private static void ensureJava8(Context ctx) {
        try {
            for (Runtime r : MultiRTUtils.getInstalledRuntimes()) {
                if (r != null && r.javaVersion == 8) return;
            }
            NewJREUtil.ExternalRuntime.JRE_8.downloadRuntime(ctx);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo bajar JRE 8; Forge usará el Java que haya", e);
        }
    }

    public static File downloadForgeInstaller(OptimizedManifest.Pack pack) throws IOException {
        final String[] err = {null};
        final File[] out = {null};
        ForgeDownloadTask task = new ForgeDownloadTask(new ModloaderDownloadListener() {
            @Override public void onDownloadFinished(File downloadedFile) { out[0] = downloadedFile; }
            @Override public void onDataNotAvailable() { err[0] = "No hay instalador Forge para " + pack.mc; }
            @Override public void onDownloadError(Exception e) { err[0] = e.getMessage(); }
        }, pack.mc, pack.loaderVersion);
        task.run();
        if (err[0] != null) throw new IOException(err[0]);
        if (out[0] == null || !out[0].isFile()) throw new IOException("No se descargó el instalador Forge");
        return out[0];
    }

    public static android.content.Intent forgeInstallIntent(Context ctx, File installer, OptimizedManifest.Pack pack) {
        ModLoader loader = new ModLoader(ModLoader.MOD_LOADER_FORGE, pack.loaderVersion, pack.mc);
        return loader.getInstallationIntent(ctx, installer);
    }

    private static void downloadOptiFine(OptimizedManifest.Pack pack, File mods, Progress progress) throws Exception {
        JSONObject of = pack.optifine;
        if (of == null) return;
        String type = of.optString("type", "HD_U");
        String patch = of.optString("patch", "");
        String name = "OptiFine_" + pack.mc + "_" + type + "_" + patch + ".jar";
        File dest = new File(mods, name);
        if (dest.isFile() && dest.length() > 1024) {
            progress.onMessage("OptiFine ya está");
            return;
        }
        progress.onMessage("OptiFine " + type + "_" + patch);
        String url = of.optString("bmcl", "https://bmclapi2.bangbang93.com/optifine/" + pack.mc + "/" + type + "/" + patch);
        DownloadUtils.downloadFile(url, dest);
        if (dest.length() < 4) throw new IOException("OptiFine vacío");
    }

    private static void downloadCurseForge(JSONObject pin, File modsDir) throws Exception {
        int projectId = pin.getInt("projectId");
        int fileId = pin.getInt("fileId");
        String slug = pin.optString("slug", "mod");
        File dest = new File(modsDir, slug + "-" + fileId + ".jar");
        if (dest.isFile() && dest.length() > 0) return;
        String url = "https://www.curseforge.com/api/v1/mods/" + projectId + "/files/" + fileId + "/download";
        DownloadUtils.downloadFile(url, dest);
    }

    private static void writeSodiumHints(Context ctx, File gameDir, OptimizedManifest.Pack pack, String tier) {
        if (!"fabric".equals(pack.loader)) return;
        File cfg = new File(gameDir, "config");
        //noinspection ResultOfMethodCallIgnored
        cfg.mkdirs();
        DeviceProbe.GpuClass gpu = ctx != null ? DeviceTier.probe(ctx).gpuClass : DeviceProbe.GpuClass.MID;
        int threads = gpu == DeviceProbe.GpuClass.WEAK ? 1 : gpu == DeviceProbe.GpuClass.STRONG ? 3 : 2;
        try {
            Tools.write(new File(cfg, "sodium-options.json").getAbsolutePath(),
                    "{\"quality\":{\"pixel_filtering_mode\":\"NEAREST\"},\"performance\":{\"chunk_builder_threads\":"
                            + threads
                            + ",\"chunk_build_defer_mode\":\"ALWAYS\",\"animate_only_visible_textures\":true,\"use_entity_culling\":true,\"use_fog_occlusion\":true,\"use_block_face_culling\":true,\"use_no_error_g_l_context\":false},\"advanced\":{\"cpu_render_ahead_limit\":3}}");
            boolean uncap = ctx != null && DeviceTier.uncapFps(ctx);
            int fps = ctx != null ? DeviceTier.fpsCap(ctx) : 60;
            Tools.write(new File(cfg, "sodium-extra-options.json").getAbsolutePath(),
                    "{\"overlay\":{\"show_fps\":true},\"extra\":{\"steady_debug_fps\":false},\"performance\":{\"fps_limit\":"
                            + (uncap ? 0 : fps) + "}}");
        } catch (Exception e) {
            Log.w(TAG, "sodium-options", e);
        }
    }

    public static boolean hasIrisJar(File gameDir) {
        return hasModJar(new File(gameDir, "mods"), "iris", new String[]{"oculus"});
    }

    public static boolean needsIris(File gameDir) {
        File mods = new File(gameDir, "mods");
        if (hasModJar(mods, "optifine", null) || hasModJar(mods, "oculus", null)) return false;
        if (hasIrisJar(gameDir)) return false;
        return hasModJar(mods, "sodium", new String[]{"extra", "options", "embed"});
    }

    /** Instala Iris + shaders mid si faltan (Optimized ya creado o KEO con Sodium). */
    public static void ensureFabricIris(Context ctx, File gameDir, String mc, Progress progress) throws Exception {
        if (gameDir == null) return;
        File mods = new File(gameDir, "mods");
        //noinspection ResultOfMethodCallIgnored
        mods.mkdirs();
        if (!hasIrisJar(gameDir)) {
            String versionId = irisVersionId(ctx, mc);
            if (progress != null) progress.onMessage("Iris");
            if (versionId != null) {
                downloadModrinthVersion(versionId, mods);
            } else {
                downloadModrinthProject("iris", mc, new String[]{"fabric"}, mods);
            }
        }
        File packs = new File(gameDir, "shaderpacks");
        boolean emptyPacks = !packs.isDirectory() || packs.list() == null || packs.list().length == 0;
        if (emptyPacks) {
            String tier = ctx != null ? DeviceTier.current(ctx) : DeviceTier.MEDIA;
            installIrisShaders(ctx, gameDir, mc, tier, progress != null ? progress : msg -> {}, false);
        }
    }

    private static String irisVersionId(Context ctx, String mc) {
        if (ctx == null || mc == null) return null;
        try {
            for (OptimizedManifest.Pack p : OptimizedManifest.load(ctx).packs()) {
                if (!mc.equals(p.mc) || p.modrinth == null) continue;
                for (int i = 0; i < p.modrinth.length(); i++) {
                    JSONObject pin = p.modrinth.getJSONObject(i);
                    if ("iris".equals(pin.optString("slug"))) return pin.optString("versionId", null);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "iris pin", e);
        }
        return null;
    }

    private static boolean hasModJar(File mods, String family, String[] exclude) {
        File[] files = mods.listFiles();
        if (files == null) return false;
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if (!n.endsWith(".jar")) continue;
            if (!n.contains(family)) continue;
            if (exclude != null) {
                boolean skip = false;
                for (String x : exclude) {
                    if (n.contains(x)) { skip = true; break; }
                }
                if (skip) continue;
            }
            return true;
        }
        return false;
    }

    /** Iris + shaders de gama baja/media (nunca Unbound / Photon / Rethinking). */
    private static void installIrisShaders(Context ctx, File gameDir, String mc, String tier, Progress progress,
                                           boolean overwriteProps) {
        DeviceProbe.GpuClass gpu = ctx != null ? DeviceTier.probe(ctx).gpuClass : DeviceProbe.GpuClass.MID;
        boolean weak = gpu == DeviceProbe.GpuClass.WEAK || DeviceTier.LITE.equals(tier);
        String[] slugs = weak
                ? new String[]{"makeup-ultra-fast-shaders", "potato-shaders"}
                : new String[]{"complementary-reimagined", "bsl-shaders", "makeup-ultra-fast-shaders"};
        File packs = new File(gameDir, "shaderpacks");
        //noinspection ResultOfMethodCallIgnored
        packs.mkdirs();
        String installedName = null;
        for (String slug : slugs) {
            try {
                if (progress != null) progress.onMessage("Shader " + slug);
                String fname = downloadModrinthProject(slug, mc, new String[]{"iris", "minecraft"}, packs);
                if (fname != null && installedName == null) installedName = fname;
            } catch (Exception e) {
                Log.w(TAG, "shader " + slug, e);
            }
        }
        File props = new File(new File(gameDir, "config"), "iris.properties");
        if (!overwriteProps && props.isFile()) return;
        File cfg = new File(gameDir, "config");
        //noinspection ResultOfMethodCallIgnored
        cfg.mkdirs();
        String pack = installedName != null ? installedName : "OFF";
        String body = "shaderPack=" + pack
                + "\nenableShaders=false\nmaxShadowRenderDistance=" + (weak ? "8" : "12")
                + "\ncolorSpace=SRGB\nallowUnknownShaders=false\n";
        try {
            Tools.write(props.getAbsolutePath(), body);
        } catch (Exception e) {
            Log.w(TAG, "iris.properties", e);
        }
    }

    private static String downloadModrinthProject(String slug, String mc, String[] loaders, File destDir) throws Exception {
        StringBuilder q = new StringBuilder("https://api.modrinth.com/v2/project/")
                .append(slug).append("/version?game_versions=%5B%22").append(mc).append("%22%5D");
        if (loaders != null && loaders.length > 0) {
            q.append("&loaders=%5B");
            for (int i = 0; i < loaders.length; i++) {
                if (i > 0) q.append("%2C");
                q.append("%22").append(loaders[i]).append("%22");
            }
            q.append("%5D");
        }
        String body = httpGet(q.toString());
        JSONArray arr = new JSONArray(body);
        if (arr.length() == 0) {
            body = httpGet("https://api.modrinth.com/v2/project/" + slug + "/version");
            arr = new JSONArray(body);
        }
        JSONObject ver = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject cand = arr.getJSONObject(i);
            JSONArray gvs = cand.optJSONArray("game_versions");
            if (gvs == null) continue;
            for (int g = 0; g < gvs.length(); g++) {
                if (mc.equals(gvs.optString(g))) {
                    ver = cand;
                    break;
                }
            }
            if (ver != null) break;
        }
        if (ver == null && arr.length() > 0) ver = arr.getJSONObject(0);
        if (ver == null) return null;
        JSONArray files = ver.getJSONArray("files");
        JSONObject file = files.getJSONObject(0);
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            if (f.optBoolean("primary", false)) {
                file = f;
                break;
            }
        }
        String url = file.getString("url");
        String name = file.getString("filename");
        File dest = new File(destDir, name);
        if (dest.isFile() && dest.length() > 1024) return name;
        DownloadUtils.downloadFile(url, dest);
        return dest.isFile() ? name : null;
    }

    private static void downloadModrinthVersion(String versionId, File modsDir) throws Exception {
        String body = httpGet("https://api.modrinth.com/v2/version/" + versionId);
        JSONObject json = new JSONObject(body);
        JSONArray files = json.getJSONArray("files");
        JSONObject file = files.getJSONObject(0);
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            if (f.optBoolean("primary", false)) {
                file = f;
                break;
            }
        }
        String url = file.getString("url");
        String name = file.getString("filename");
        File dest = new File(modsDir, name);
        String sha1 = null;
        JSONObject hashes = file.optJSONObject("hashes");
        if (hashes != null) {
            String h = hashes.optString("sha1", "");
            if (!h.isEmpty()) sha1 = h;
        }
        if (dest.isFile() && dest.length() > 0 && sha1 != null && Tools.compareSHA1(dest, sha1)) {
            Log.i(TAG, "Ya está: " + name);
            return;
        }
        if (dest.isFile() && dest.length() > 0 && sha1 == null) {
            Log.i(TAG, "Ya está (sin hash): " + name);
            return;
        }
        DownloadUtils.ensureSha1(dest, sha1, () -> {
            DownloadUtils.downloadFile(url, dest);
            return null;
        });
    }

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "Paraguacraft-Mobile");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        try (Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    private static void writeProfile(Context ctx, OptimizedManifest.Pack pack, String versionId, File gameDir, String tier) {
        LauncherProfiles.load();
        MinecraftProfile existing = findProfile(pack.mc);
        MinecraftProfile p = existing != null ? existing : new MinecraftProfile();
        p.name = profileName(pack.mc);
        p.lastVersionId = versionId;
        p.gameDir = "./optimized-" + pack.mc;
        p.pojavRendererName = DeviceTier.renderer(ctx, tier);
        if (pack.mc.equals("1.8.9") || pack.mc.equals("1.12.2")) {
            String java8 = MultiRTUtils.getExactJreName(8);
            if (java8 != null) p.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + java8;
        }
        p.javaArgs = "-Xms" + (LauncherPreferences.PREF_RAM_ALLOCATION / 2) + "M";
        p.icon = BrandPack.profileIconDataUri(ctx);
        if (existing == null) {
            LauncherProfiles.insertMinecraftProfile(p);
        } else {
            for (Map.Entry<String, MinecraftProfile> e : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
                if (e.getValue() == p) {
                    ProfileIconCache.dropIcon(e.getKey());
                    break;
                }
            }
        }
        LauncherProfiles.write();
        selectProfile(pack.mc);
        Log.i(TAG, "Profile " + p.name + " -> " + versionId);
    }

    public static MinecraftProfile findProfile(String mc) {
        LauncherProfiles.load();
        if (LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null)
            return null;
        String want = profileName(mc);
        for (MinecraftProfile p : LauncherProfiles.mainProfileJson.profiles.values()) {
            if (p != null && want.equals(p.name)) return p;
        }
        return null;
    }

    public static void selectProfile(String mc) {
        LauncherProfiles.load();
        if (LauncherProfiles.mainProfileJson == null) return;
        String want = profileName(mc);
        for (java.util.Map.Entry<String, MinecraftProfile> e : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            if (e.getValue() != null && want.equals(e.getValue().name)) {
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, e.getKey())
                        .apply();
                return;
            }
        }
    }

    public static File activeGameDir() {
        String id = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        LauncherProfiles.load();
        if (id != null && LauncherProfiles.mainProfileJson != null) {
            MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(id);
            if (p != null) return Tools.getGameDirPath(p);
        }
        return new File(Tools.DIR_GAME_NEW);
    }
}
