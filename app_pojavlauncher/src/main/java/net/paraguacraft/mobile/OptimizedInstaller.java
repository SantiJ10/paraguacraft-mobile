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

    public static String profileName(OptimizedManifest.Pack pack) {
        return pack != null ? "Optimized " + pack.displayName() : "Optimized";
    }

    public static String profileName(String mcOrKey) {
        if ("1.20.1-forge".equalsIgnoreCase(mcOrKey) || "1.20.1 Forge".equalsIgnoreCase(mcOrKey)) {
            return "Optimized 1.20.1 Forge";
        }
        return "Optimized " + mcOrKey;
    }

    public static String gameDirName(OptimizedManifest.Pack pack) {
        return "optimized-" + (pack != null ? pack.key() : "default");
    }

    public static synchronized void install(Context ctx, OptimizedManifest.Pack pack, Progress progress) throws Exception {
        String tier = DeviceTier.current(ctx);
        boolean lite = DeviceTier.LITE.equals(tier);
        progress.onMessage("Instalando Optimized " + pack.displayName() + " (" + pack.loader + ")");

        try {
            LauncherProfiles.load();
            LauncherProfiles.write();
        } catch (Throwable ignored) {}

        ensureJavaRuntime(ctx, pack.javaMajor, progress);

        String versionId;
        MinecraftProfile existingProfile = findProfile(pack);
        if ("forge".equals(pack.loader)) {
            versionId = findForgeVersionId(pack);
            if (versionId == null) {
                File staleShared = new File(Tools.DIR_CACHE, "forge-installer.jar");
                if (staleShared.isFile()) {
                    //noinspection ResultOfMethodCallIgnored
                    staleShared.delete();
                }
                if (ForgeLegacyInstall.isLegacyMc(pack.mc)) {
                    versionId = ForgeLegacyInstall.install(ctx, pack, progress);
                } else {
                    progress.onMessage("Descargando instalador y librerías Forge " + pack.mc + " " + pack.loaderVersion + "…");
                    File installer = downloadForgeInstaller(pack, progress);
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

        File gameDir = new File(Tools.DIR_GAME_HOME, gameDirName(pack));
        //noinspection ResultOfMethodCallIgnored
        gameDir.mkdirs();
        File mods = new File(gameDir, "mods");
        //noinspection ResultOfMethodCallIgnored
        mods.mkdirs();

        if (pack.hosted != null && ctx != null) {
            for (int i = 0; i < pack.hosted.length(); i++) {
                String filename = pack.hosted.optString(i, "");
                if (filename.isEmpty()) continue;
                installHostedAssetMod(ctx, filename, mods, progress);
            }
        }

        if (pack.modrinth != null) {
            downloadModrinthPinsParallel(pack.modrinth, lite, mods, progress);
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

        applyOptionsIfNeeded(ctx, gameDir, pack.mc, tier);
        writeSodiumHints(ctx, gameDir, pack, tier);
        if ("fabric".equals(pack.loader)) {
            installIrisShaders(ctx, gameDir, pack.mc, tier, progress, false);
        }
        BrandPack.inject(ctx, gameDir, pack.mc);
        MobileCompat.sanitizeGameDir(gameDir);
        writeProfile(ctx, pack, versionId, gameDir, tier);
        progress.onMessage("Listo: " + profileName(pack));
    }

    public static void reinstallMods(Context ctx, OptimizedManifest.Pack pack, Progress progress) throws Exception {
        File gameDir = new File(Tools.DIR_GAME_HOME, gameDirName(pack));
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
        MCOptionUtils.set("syncChunkWrites", "false");
        MCOptionUtils.set("useNativeTransport", "false");
        MCOptionUtils.set("pauseOnLostFocus", "false");
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
            MCOptionUtils.set("mipmapLevels", gpu == DeviceProbe.GpuClass.STRONG && alta ? "1" : "0");
            MCOptionUtils.set("biomeBlendRadius", gpu == DeviceProbe.GpuClass.STRONG ? "1" : "0");
            MCOptionUtils.set("prioritizeChunkUpdates", "0");
        }
        MCOptionUtils.save();
        disableDynamicFps(gameDir);
        if (legacy) writeOptiFineOptions(gameDir, gpu, uncap);
        writeTunedMarker(ctx, gameDir, mc, tier);
    }

    public static final String TUNED_MARKER = ".paraguacraft_optimized_tuned";
    public static final String TUNED_VERSION = "v14";

    /**
     * Aplica la configuración inicial por gama solo una vez por instancia.
     * Si el marcador ya existe y options.txt está presente, respeta cualquier cambio
     * manual que haya hecho el jugador dentro del juego o en los mods.
     */
    public static void applyOptionsIfNeeded(Context ctx, File gameDir, String mc, String tier) {
        File marker = new File(gameDir, TUNED_MARKER);
        File optionsFile = new File(gameDir, "options.txt");
        if (marker.isFile() && optionsFile.isFile()) {
            return;
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
        MinecraftProfile p = findProfile(pack);
        if (p == null) return false;
        File mods = new File(new File(Tools.DIR_GAME_HOME, gameDirName(pack)), "mods");
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
        if (versionJsonExists(canonical)
                && net.kdt.pojavlaunch.modloaders.ForgeUtils.verifyForgeInstallation(canonical, true)) {
            return canonical;
        }
        File versions = new File(Tools.DIR_HOME_VERSION);
        File[] dirs = versions.listFiles();
        if (dirs == null) return null;
        for (File d : dirs) {
            String n = d.getName();
            if (n.toLowerCase().contains("forge") && n.contains(pack.mc)
                    && n.contains(pack.loaderVersion) && versionJsonExists(n)
                    && net.kdt.pojavlaunch.modloaders.ForgeUtils.verifyForgeInstallation(n, true)) {
                return n;
            }
        }
        return null;
    }

    private static void ensureJavaRuntime(Context ctx, int requiredMajor, Progress progress) {
        if (ctx == null) return;
        int canonical = NewJREUtil.mapRequiredJavaMajor(requiredMajor);
        try {
            if (MultiRTUtils.getExactJreName(canonical) != null) return;
            if (progress != null) {
                progress.onMessage("Descargando Java " + canonical + " para Minecraft…");
            }
            NewJREUtil.ensureRuntimeInstalled(ctx, canonical);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo bajar JRE " + canonical + "; se intentará al iniciar", e);
        }
    }

    public static File downloadForgeInstaller(OptimizedManifest.Pack pack) throws IOException {
        return downloadForgeInstaller(pack, null);
    }

    public static File downloadForgeInstaller(OptimizedManifest.Pack pack, Progress progress) throws IOException {
        final String[] err = {null};
        final File[] out = {null};
        ForgeDownloadTask task = new ForgeDownloadTask(new ModloaderDownloadListener() {
            @Override public void onDownloadFinished(File downloadedFile) { out[0] = downloadedFile; }
            @Override public void onDataNotAvailable() { err[0] = "No hay instalador Forge para " + pack.mc; }
            @Override public void onDownloadError(Exception e) { err[0] = e.getMessage(); }
        }, pack.mc, pack.loaderVersion) {
            @Override
            public void updateProgress(int curr, int max) {
                super.updateProgress(curr, max);
                if (progress != null && max > 0) {
                    int pct = (int) (((float) curr / (float) max) * 100f);
                    progress.onMessage("Descargando archivos Forge " + pack.mc + " (" + pct + "%)…");
                }
            }
        };
        task.run();
        if (err[0] != null) throw new IOException(err[0]);
        if (out[0] == null || !out[0].isFile()) throw new IOException("No se descargó el instalador Forge");
        return out[0];
    }

    public static android.content.Intent forgeInstallIntent(Context ctx, File installer, OptimizedManifest.Pack pack) {
        android.content.Intent intent = new android.content.Intent(ctx, net.kdt.pojavlaunch.JavaGUILauncherActivity.class);
        net.kdt.pojavlaunch.modloaders.ForgeUtils.addAutoInstallArgs(intent, installer, true);
        return intent;
    }

    private static void installHostedAssetMod(Context ctx, String filename, File modsDir, Progress progress) {
        File dest = new File(modsDir, filename);
        if (dest.isFile() && DownloadUtils.isValidZipOrJar(dest)) return;
        if (dest.exists()) dest.delete();
        progress.onMessage("Mod " + filename);
        File part = new File(modsDir, filename + ".part");
        try (java.io.InputStream in = ctx.getAssets().open("paraguacraft/optimized-mods/" + filename);
             java.io.FileOutputStream out = new java.io.FileOutputStream(part)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            part.delete();
            Log.w(TAG, "Hosted mod skip " + filename, e);
            return;
        }
        if (!part.renameTo(dest)) {
            try {
                org.apache.commons.io.FileUtils.copyFile(part, dest);
            } catch (Exception ignored) {}
            part.delete();
        }
    }

    private static void downloadOptiFine(OptimizedManifest.Pack pack, File mods, Progress progress) throws Exception {
        JSONObject of = pack.optifine;
        if (of == null) return;
        String type = of.optString("type", "HD_U");
        String patch = of.optString("patch", "");
        String name = "OptiFine_" + pack.mc + "_" + type + "_" + patch + ".jar";
        File dest = new File(mods, name);
        if (dest.isFile() && DownloadUtils.isValidZipOrJar(dest)) {
            progress.onMessage("OptiFine ya está");
            return;
        }
        if (dest.exists()) dest.delete();
        progress.onMessage("OptiFine " + type + "_" + patch);
        String url = of.optString("bmcl", "https://bmclapi2.bangbang93.com/optifine/" + pack.mc + "/" + type + "/" + patch);
        DownloadUtils.downloadFile(url, dest);
        if (!DownloadUtils.isValidZipOrJar(dest)) throw new IOException("OptiFine vacío o corrupto");
    }

    private static void downloadCurseForge(JSONObject pin, File modsDir) throws Exception {
        int projectId = pin.getInt("projectId");
        int fileId = pin.getInt("fileId");
        String slug = pin.optString("slug", "mod");
        File dest = new File(modsDir, slug + "-" + fileId + ".jar");
        if (dest.isFile() && DownloadUtils.isValidZipOrJar(dest)) return;
        if (dest.exists()) dest.delete();
        String url = "https://www.curseforge.com/api/v1/mods/" + projectId + "/files/" + fileId + "/download";
        DownloadUtils.downloadFile(url, dest);
    }

    private static void writeIfMissing(File file, String content) {
        if (file.isFile() && file.length() > 0) return;
        try {
            Tools.write(file.getAbsolutePath(), content);
        } catch (Exception e) {
            Log.w(TAG, "writeIfMissing " + file.getName(), e);
        }
    }

    private static void writeSodiumHints(Context ctx, File gameDir, OptimizedManifest.Pack pack, String tier) {
        File cfg = new File(gameDir, "config");
        //noinspection ResultOfMethodCallIgnored
        cfg.mkdirs();
        DeviceProbe.GpuClass gpu = ctx != null ? DeviceTier.probe(ctx).gpuClass : DeviceProbe.GpuClass.MID;
        int threads = gpu == DeviceProbe.GpuClass.WEAK ? 1 : gpu == DeviceProbe.GpuClass.STRONG ? 3 : 2;
        try {
            if ("fabric".equals(pack.loader)) {
                writeIfMissing(new File(cfg, "sodium-options.json"),
                        "{\"quality\":{\"pixel_filtering_mode\":\"NEAREST\"},\"performance\":{\"chunk_builder_threads\":"
                                + threads
                                + ",\"chunk_build_defer_mode\":\"ALWAYS\",\"animate_only_visible_textures\":true,\"use_entity_culling\":true,\"use_fog_occlusion\":true,\"use_block_face_culling\":true,\"use_no_error_g_l_context\":false},\"advanced\":{\"cpu_render_ahead_limit\":3}}");
                boolean uncap = ctx != null && DeviceTier.uncapFps(ctx);
                int fps = ctx != null ? DeviceTier.fpsCap(ctx) : 60;
                writeIfMissing(new File(cfg, "sodium-extra-options.json"),
                        "{\"overlay\":{\"show_fps\":true},\"extra\":{\"steady_debug_fps\":false},\"performance\":{\"fps_limit\":"
                                + (uncap ? 0 : fps) + "}}");
            } else if ("forge".equals(pack.loader) && !ForgeLegacyInstall.isLegacyMc(pack.mc) && !"1.12.2".equals(pack.mc)) {
                writeIfMissing(new File(cfg, "embeddium-options.json"),
                        "{\"quality\":{\"weather_quality\":\"FAST\",\"leaves_quality\":\"FAST\",\"enable_vignette\":false},\"performance\":{\"chunk_builder_threads\":"
                                + threads
                                + ",\"always_defer_chunk_updates_v2\":true,\"animate_only_visible_textures\":true,\"use_entity_culling\":true,\"use_fog_occlusion\":true,\"use_block_face_culling\":true,\"use_compact_vertex_format\":true,\"use_no_error_g_l_context\":false},\"advanced\":{\"use_advanced_staging_buffers\":false,\"cpu_render_ahead_limit\":3}}");
            }
            if (!ForgeLegacyInstall.isLegacyMc(pack.mc) && !"1.12.2".equals(pack.mc)) {
                writeIfMissing(new File(cfg, "modernfix-mixins.properties"),
                        "mixin.perf.dynamic_resources=true\nmixin.perf.faster_item_rendering=true\nmixin.launch.class_search_cache=true\nmixin.perf.clear_mixin_classinfo=true\n");
                writeIfMissing(new File(cfg, "immediatelyfast.json"),
                        "{\"font_atlas_resizing\":false,\"map_atlas_generation\":true,\"hud_batching\":true,\"fast_text_lookup\":true,\"fast_buffer_upload\":true,\"experimental_screen_batching\":false}\n");
                writeIfMissing(new File(cfg, "smoothboot.json"),
                        "{\"threadCount\":{\"bootstrap\":1,\"main\":" + Math.max(2, threads)
                                + "},\"threadPriority\":{\"game\":5,\"bootstrap\":5,\"main\":5,\"io\":5,\"integratedServer\":5,\"modLoading\":5}}\n");
            }
        } catch (Exception e) {
            Log.w(TAG, "sodium-options", e);
        }
    }

    public static boolean hasIrisJar(File gameDir) {
        return hasModJar(new File(gameDir, "mods"), "iris", new String[]{"oculus"});
    }

    public static boolean needsIris(File gameDir) {
        File mods = new File(gameDir, "mods");
        if (hasModJar(mods, "optifine", null) || hasModJar(mods, "oculus", null) || hasModJar(mods, "embeddium", null)) return false;
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
        if (dest.isFile() && (!DownloadUtils.isZipOrJarExtension(dest) ? dest.length() > 1024 : DownloadUtils.isValidZipOrJar(dest))) return name;
        if (dest.exists()) dest.delete();
        DownloadUtils.downloadFile(url, dest);
        return dest.isFile() ? name : null;
    }

    private static void downloadModrinthPinsParallel(JSONArray pins, boolean lite, File modsDir, Progress progress) throws Exception {
        java.util.List<JSONObject> wanted = new java.util.ArrayList<>();
        StringBuilder idsParam = new StringBuilder("[");
        for (int i = 0; i < pins.length(); i++) {
            JSONObject pin = pins.getJSONObject(i);
            String slug = pin.getString("slug");
            if (pin.optBoolean("mobileSkip", false)) continue;
            if ("voxy".equals(slug) || "nvidium".equals(slug) || "oculus".equals(slug)
                    || "crash-assistant".equals(slug) || "kryptonreforged".equals(slug)) continue;
            if (lite && !pin.optBoolean("mobileLite", false) && !"iris".equals(slug)) continue;
            if (!wanted.isEmpty()) idsParam.append(',');
            idsParam.append('"').append(pin.getString("versionId")).append('"');
            wanted.add(pin);
        }
        idsParam.append(']');
        if (wanted.isEmpty()) return;

        progress.onMessage("Consultando " + wanted.size() + " mods en Modrinth…");
        java.util.Map<String, JSONObject> versionMap = new java.util.HashMap<>();
        try {
            String encoded = java.net.URLEncoder.encode(idsParam.toString(), StandardCharsets.UTF_8.name());
            String batchBody = httpGet("https://api.modrinth.com/v2/versions?ids=" + encoded);
            JSONArray arr = new JSONArray(batchBody);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject v = arr.getJSONObject(i);
                versionMap.put(v.getString("id"), v);
            }
        } catch (Exception e) {
            Log.w(TAG, "Batch Modrinth fallback", e);
        }

        final int total = wanted.size();
        final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicReference<Exception> firstErr = new java.util.concurrent.atomic.AtomicReference<>(null);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(Math.min(6, total));
        for (JSONObject pin : wanted) {
            final String slug = pin.getString("slug");
            final String vid = pin.getString("versionId");
            final JSONObject preloaded = versionMap.get(vid);
            pool.execute(() -> {
                if (firstErr.get() != null) return;
                try {
                    if (preloaded != null) {
                        downloadModrinthVersionJson(preloaded, modsDir);
                    } else {
                        downloadModrinthVersion(vid, modsDir);
                    }
                    int c = done.incrementAndGet();
                    progress.onMessage("Mods (" + c + "/" + total + "): " + slug);
                } catch (Exception e) {
                    firstErr.compareAndSet(null, e);
                }
            });
        }
        pool.shutdown();
        //noinspection ResultOfMethodCallIgnored
        pool.awaitTermination(15, java.util.concurrent.TimeUnit.MINUTES);
        if (firstErr.get() != null) throw firstErr.get();
    }

    private static void downloadModrinthVersion(String versionId, File modsDir) throws Exception {
        String body = httpGet("https://api.modrinth.com/v2/version/" + versionId);
        downloadModrinthVersionJson(new JSONObject(body), modsDir);
    }

    private static void downloadModrinthVersionJson(JSONObject json, File modsDir) throws Exception {
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
        boolean validZip = !DownloadUtils.isZipOrJarExtension(dest) || DownloadUtils.isValidZipOrJar(dest);
        if (dest.isFile() && dest.length() > 0 && validZip && sha1 != null && Tools.compareSHA1(dest, sha1)) {
            Log.i(TAG, "Ya está: " + name);
            return;
        }
        if (dest.isFile() && dest.length() > 0 && validZip && sha1 == null) {
            Log.i(TAG, "Ya está (sin hash): " + name);
            return;
        }
        if (dest.exists()) dest.delete();
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
        MinecraftProfile existing = findProfile(pack);
        MinecraftProfile p = existing != null ? existing : new MinecraftProfile();
        p.name = profileName(pack);
        p.lastVersionId = versionId;
        p.gameDir = "./" + gameDirName(pack);
        p.pojavRendererName = DeviceTier.renderer(ctx, tier);
        int targetJava = NewJREUtil.mapRequiredJavaMajor(pack.javaMajor);
        String exactJre = MultiRTUtils.getExactJreName(targetJava);
        if (exactJre != null) {
            p.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + exactJre;
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
        selectProfile(pack);
        Log.i(TAG, "Profile " + p.name + " -> " + versionId);
    }

    public static MinecraftProfile findProfile(OptimizedManifest.Pack pack) {
        return pack != null ? findProfile(pack.key()) : null;
    }

    public static MinecraftProfile findProfile(String mcOrKey) {
        LauncherProfiles.load();
        if (LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null)
            return null;
        String want = profileName(mcOrKey);
        for (MinecraftProfile p : LauncherProfiles.mainProfileJson.profiles.values()) {
            if (p != null && want.equals(p.name)) return p;
        }
        return null;
    }

    public static void selectProfile(OptimizedManifest.Pack pack) {
        if (pack != null) selectProfile(pack.key());
    }

    public static void selectProfile(String mcOrKey) {
        LauncherProfiles.load();
        if (LauncherProfiles.mainProfileJson == null) return;
        String want = profileName(mcOrKey);
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
