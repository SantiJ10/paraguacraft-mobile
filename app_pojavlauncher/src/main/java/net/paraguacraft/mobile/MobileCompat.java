package net.paraguacraft.mobile;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.MCOptionUtils;

import java.io.File;

/**
 * Mods y configuraciones de PC que crashean o cuelgan el arranque en Android:
 * - Voxy: {@code libc.so.6} / {@code LinuxLibrary}.
 * - Nvidium: GPU NVIDIA.
 * - Oculus: compila pipelines de shaders de escritorio en la pantalla roja de Mojang y crashea GL4ES/MobileGlues.
 * - CrashAssistant: lanza un proceso watchdog secundario de escritorio + DLL nativa de Windows.
 * - GpuMemLeakFix: consulta extensiones GLX/NVX de escritorio.
 * - KryptonReforged: rompe el pipeline Netty en servidores Forge («Bad packet ID») y usa epoll nativo.
 */
public final class MobileCompat {
    private static final String TAG = "PgCompat";
    private static final String[] DISABLE = {
            "voxy",
            "nvidium",
            "flashback",
            "oculus",
            "crashassistant",
            "crash_assistant",
            "gpumemleakfix",
            "kryptonreforged",
    };

    private MobileCompat() {}

    public static int sanitizeGameDir(File gameDir) {
        if (gameDir == null) return 0;
        int n = 0;
        File mods = new File(gameDir, "mods");
        File[] jars = mods.listFiles();
        if (jars != null) {
            for (File f : jars) {
                String name = f.getName().toLowerCase();
                if (!name.endsWith(".jar")) continue;
                if (!isUnsafe(name)) continue;
                File dest = new File(mods, f.getName() + ".disabled");
                if (dest.exists()) //noinspection ResultOfMethodCallIgnored
                    dest.delete();
                if (f.renameTo(dest)) {
                    n++;
                    Log.i(TAG, "Desactivado en Android: " + f.getName());
                }
            }
        }
        deleteTree(new File(gameDir, "local/crash_assistant"));
        sanitizeMobileConfigs(gameDir);
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

    private static void sanitizeMobileConfigs(File gameDir) {
        File cfg = new File(gameDir, "config");
        //noinspection ResultOfMethodCallIgnored
        cfg.mkdirs();

        // 1. Limpiar mixin.features.chunk_rendering=false que rompia Embeddium/Sodium al terminar la pantalla de Mojang
        for (String name : new String[]{"sodium-mixins.properties", "embeddium-mixins.properties", "rubidium-mixins.properties"}) {
            File f = new File(cfg, name);
            if (!f.isFile()) continue;
            try {
                String raw = Tools.read(f.getAbsolutePath());
                if (raw != null && raw.contains("mixin.features.chunk_rendering=false")) {
                    String fixed = raw.replace("mixin.features.chunk_rendering=false\r\n", "")
                            .replace("mixin.features.chunk_rendering=false\n", "")
                            .replace("mixin.features.chunk_rendering=false", "");
                    Tools.write(f.getAbsolutePath(), fixed);
                }
            } catch (Exception ignored) {}
        }

        // 2. ModernFix: activar dynamic_resources evita hornear miles de modelos en RAM durante la pantalla roja de Mojang
        File mfix = new File(cfg, "modernfix-mixins.properties");
        try {
            if (mfix.isFile()) {
                String raw = Tools.read(mfix.getAbsolutePath());
                if (raw != null && !raw.contains("\nmixin.perf.dynamic_resources=true")
                        && !raw.startsWith("mixin.perf.dynamic_resources=true")) {
                    StringBuilder sb = new StringBuilder(raw);
                    if (!raw.endsWith("\n")) sb.append('\n');
                    sb.append("mixin.perf.dynamic_resources=true\n");
                    sb.append("mixin.perf.faster_item_rendering=true\n");
                    sb.append("mixin.launch.class_search_cache=true\n");
                    sb.append("mixin.perf.clear_mixin_classinfo=true\n");
                    Tools.write(mfix.getAbsolutePath(), sb.toString());
                }
            } else if (new File(gameDir, "mods").isDirectory()) {
                Tools.write(mfix.getAbsolutePath(),
                        "mixin.perf.dynamic_resources=true\nmixin.perf.faster_item_rendering=true\nmixin.launch.class_search_cache=true\nmixin.perf.clear_mixin_classinfo=true\n");
            }
        } catch (Exception ignored) {}

        // 3. SmoothBoot: en Keo 1.20.1 trae main=11 hilos y prioridad=1 (MIN_PRIORITY), lo que estrangula la CPU en Android
        File sbFile = new File(cfg, "smoothboot.json");
        if (sbFile.isFile()) {
            try {
                String raw = Tools.read(sbFile.getAbsolutePath());
                if (raw != null && (raw.contains("\"main\": 11") || raw.contains("\"main\":11")
                        || raw.contains("\"modLoading\": 1") || raw.contains("\"modLoading\":1"))) {
                    Tools.write(sbFile.getAbsolutePath(),
                            "{\"threadCount\":{\"bootstrap\":1,\"main\":2},\"threadPriority\":{\"game\":5,\"bootstrap\":5,\"main\":5,\"io\":5,\"integratedServer\":5,\"modLoading\":5}}\n");
                }
            } catch (Exception ignored) {}
        }

        // 4. Embeddium: desactivar use_no_error_g_l_context y staging buffers avanzados que crashean GL4ES/MobileGlues
        File emb = new File(cfg, "embeddium-options.json");
        if (emb.isFile()) {
            try {
                String raw = Tools.read(emb.getAbsolutePath());
                if (raw != null) {
                    String fixed = raw
                            .replace("\"use_no_error_g_l_context\": true", "\"use_no_error_g_l_context\": false")
                            .replace("\"use_no_error_g_l_context\":true", "\"use_no_error_g_l_context\":false")
                            .replace("\"use_advanced_staging_buffers\": true", "\"use_advanced_staging_buffers\": false")
                            .replace("\"use_advanced_staging_buffers\":true", "\"use_advanced_staging_buffers\":false");
                    if (!fixed.equals(raw)) {
                        Tools.write(emb.getAbsolutePath(), fixed);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 5. Forge 1.14+ fml.toml: desactivar earlyWindowControl para no abrir doble contexto EGL
        File fml = new File(cfg, "fml.toml");
        try {
            if (fml.isFile()) {
                String raw = Tools.read(fml.getAbsolutePath());
                if (raw != null && raw.contains("earlyWindowControl = true")) {
                    String fixed = raw.replace("earlyWindowControl = true", "earlyWindowControl = false")
                            .replace("maxThreads = -1", "maxThreads = 2");
                    Tools.write(fml.getAbsolutePath(), fixed);
                }
            } else {
                Tools.write(fml.getAbsolutePath(),
                        "earlyWindowControl = false\nmaxThreads = 2\ndisableOptimizedDFU = true\nversionCheck = false\ndefaultConfigPath = \"defaultconfigs\"\n");
            }
        } catch (Exception ignored) {}

        // 6. Si es un modpack importado sin marcador de tuning y trae mipmapLevels:4 de PC, ajustarlo una sola vez
        File tuned = new File(gameDir, OptimizedInstaller.TUNED_MARKER);
        File opts = new File(gameDir, "options.txt");
        if (!tuned.isFile() && opts.isFile()) {
            try {
                MCOptionUtils.load(gameDir.getAbsolutePath());
                String mip = MCOptionUtils.get("mipmapLevels");
                if (mip != null && Integer.parseInt(mip.trim()) > 1) {
                    MCOptionUtils.set("mipmapLevels", "0");
                }
                MCOptionUtils.set("syncChunkWrites", "false");
                MCOptionUtils.set("useNativeTransport", "false");
                MCOptionUtils.set("pauseOnLostFocus", "false");
                MCOptionUtils.save();
                Tools.write(tuned.getAbsolutePath(), "version=" + OptimizedInstaller.TUNED_VERSION + "\nimported=true\n");
            } catch (Exception ignored) {}
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteTree(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
