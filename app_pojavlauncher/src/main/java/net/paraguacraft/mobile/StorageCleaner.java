package net.paraguacraft.mobile;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Limpia archivos que el launcher y el juego regeneran solos: logs viejos, crash-reports,
 * descargas a medias (.part) e instaladores en caché.
 * NUNCA toca mundos (saves), mods, resourcepacks, shaders, capturas, opciones ni runtimes de Java.
 */
public final class StorageCleaner {
    private static final int MAX_DEPTH = 3;

    private StorageCleaner() {}

    public static final class Result {
        public final List<File> files = new ArrayList<>();
        public long bytes = 0;

        public String humanSize() {
            return formatSize(bytes);
        }
    }

    public static Result scan() {
        Result r = new Result();
        File home = new File(Tools.DIR_GAME_HOME);
        // Logs y crash-reports de cada instancia (incluye .minecraft)
        collectGameDirs(home, 0, r);
        // Caché del launcher: instaladores y descargas temporales
        File cache = Tools.DIR_CACHE;
        if (cache != null && cache.isDirectory()) {
            File[] list = cache.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (!f.isFile()) continue;
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".jar") || n.endsWith(".tar.xz") || n.endsWith(".zip")
                            || n.endsWith(".part") || n.endsWith(".tmp") || n.endsWith(".log")) {
                        add(r, f);
                    }
                }
            }
        }
        // Descargas a medias en librerías
        collectPartFiles(new File(Tools.DIR_HOME_LIBRARY), 0, r);
        return r;
    }

    public static long clean(Result r) {
        long freed = 0;
        for (File f : r.files) {
            long len = f.length();
            if (f.delete()) freed += len;
        }
        return freed;
    }

    private static void collectGameDirs(File dir, int depth, Result r) {
        if (dir == null || !dir.isDirectory() || depth > MAX_DEPTH) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (!c.isDirectory()) continue;
            String name = c.getName();
            if ("logs".equals(name)) {
                File[] logs = c.listFiles();
                if (logs != null) {
                    for (File l : logs) {
                        // Se conserva latest.log (la sesión actual) y debug.log
                        if (l.isFile() && (l.getName().endsWith(".log.gz") || l.getName().matches(".*-\\d+\\.log"))) {
                            add(r, l);
                        }
                    }
                }
            } else if ("crash-reports".equals(name)) {
                File[] reports = c.listFiles();
                if (reports != null) for (File f : reports) if (f.isFile()) add(r, f);
            } else if (isProtected(name)) {
                // no entrar a carpetas de contenido del jugador
            } else {
                collectGameDirs(c, depth + 1, r);
            }
        }
    }

    private static boolean isProtected(String name) {
        switch (name) {
            case "saves": case "mods": case "resourcepacks": case "shaderpacks": case "screenshots":
            case "config": case "assets": case "libraries": case "versions": case "schematics":
                return true;
            default:
                return false;
        }
    }

    private static void collectPartFiles(File dir, int depth, Result r) {
        if (dir == null || !dir.isDirectory() || depth > 12) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) collectPartFiles(c, depth + 1, r);
            else if (c.getName().endsWith(".part")) add(r, c);
        }
    }

    private static void add(Result r, File f) {
        r.files.add(f);
        r.bytes += f.length();
    }

    public static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%d KB", bytes / 1024);
    }
}
