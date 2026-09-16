package net.paraguacraft.mobile;

import android.util.Log;

import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/** Instala un mod / pack / shader / datapack / mundo suelto sobre la instancia elegida. */
public final class StoreInstall {
    private static final String TAG = "PgStore";
    /** "mod" | "resourcepack" | "shader" | "datapack" | "world" | "modpack" */
    public static volatile String pendingType = "mod";
    public static volatile File pendingGameDir;
    public static volatile File pendingWorldDir;

    private StoreInstall() {}

    public static File installSingle(ModDetail detail, int selectedVersion) throws IOException {
        if (detail.versionUrls == null || selectedVersion < 0 || selectedVersion >= detail.versionUrls.length) {
            throw new IOException("Sin URL de descarga");
        }
        String url = detail.versionUrls[selectedVersion];
        if (url == null || url.trim().isEmpty()) {
            throw new IOException("CurseForge no dio URL de descarga. Probá otra versión.");
        }
        String type = pendingType;
        String name = filenameFor(detail, selectedVersion, url, type);
        File gameDir = pendingGameDir != null ? pendingGameDir : OptimizedInstaller.activeGameDir();
        File destDir;
        if ("datapack".equals(type) && pendingWorldDir != null) {
            destDir = new File(pendingWorldDir, "datapacks");
        } else {
            destDir = new File(gameDir, contentDir(type, name));
        }
        //noinspection ResultOfMethodCallIgnored
        destDir.mkdirs();
        File dest = new File(destDir, name);
        DownloadUtils.downloadFile(url, dest);
        if ("world".equals(type) || "worlds".equals(type)) {
            File world = extractWorld(dest, destDir, slug(detail.title));
            repairSaves(gameDir);
            return world;
        }
        return dest;
    }

    /** Repara mundos mal extraídos (ZIP suelto o carpeta anidada) para que Minecraft los liste. */
    public static int repairSaves(File gameDir) {
        if (gameDir == null) return 0;
        File saves = new File(gameDir, "saves");
        if (!saves.isDirectory()) return 0;
        int n = 0;
        File[] kids = saves.listFiles();
        if (kids == null) return 0;
        for (File f : kids) {
            if (f.getName().startsWith(".")) continue;
            if (f.isFile() && looksLikeZip(f)) {
                try {
                    extractWorld(f, saves, slug(stripExt(f.getName())));
                    n++;
                } catch (Exception e) {
                    Log.w(TAG, "No se pudo extraer " + f.getName(), e);
                }
            } else if (f.isDirectory() && !new File(f, "level.dat").isFile()) {
                File nested = findWorldRoot(f);
                if (nested != null && !nested.equals(f)) {
                    File dest = uniqueDir(saves, nested.getName());
                    if (moveDir(nested, dest)) {
                        deleteTree(f);
                        n++;
                        Log.i(TAG, "Mundo izado a saves/" + dest.getName());
                    }
                }
            }
        }
        return n;
    }

    static String contentDir(String type, String filename) {
        if ("shader".equals(type) || "shaderpack".equals(type)) return "shaderpacks";
        if ("resourcepack".equals(type) || "resourcepacks".equals(type)) return "resourcepacks";
        if ("datapack".equals(type) || "datapacks".equals(type)) return "datapacks";
        if ("world".equals(type) || "worlds".equals(type)) return "saves";
        if (filename != null) {
            String n = filename.toLowerCase();
            if (n.endsWith(".zip") && n.contains("shader")) return "shaderpacks";
            if (n.endsWith(".zip") && (n.contains("pack") || n.contains("resource"))) return "resourcepacks";
        }
        return "mods";
    }

    private static String filenameFor(ModDetail detail, int selectedVersion, String url, String type) {
        String fromUrl = filenameFromUrl(url);
        boolean world = "world".equals(type) || "worlds".equals(type);
        boolean zipType = world || "resourcepack".equals(type) || "shader".equals(type) || "datapack".equals(type);
        if (fromUrl != null && fromUrl.contains(".") && !fromUrl.endsWith(".")) {
            return sanitizeFileName(fromUrl);
        }
        String name = detail.versionNames != null && selectedVersion < detail.versionNames.length
                ? detail.versionNames[selectedVersion] : detail.title;
        name = new File(name).getName();
        if (!name.contains(".") || (zipType && !hasArchiveExt(name))) {
            name = slug(detail.title) + (zipType ? ".zip" : ".jar");
        }
        return sanitizeFileName(name);
    }

    private static boolean hasArchiveExt(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".mrpack");
    }

    private static String filenameFromUrl(String url) {
        if (url == null) return null;
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        if (path.isEmpty()) return null;
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {}
        return path.contains(".") ? path : null;
    }

    private static File extractWorld(File zip, File saves, String fallbackName) throws IOException {
        if (zip == null || !zip.isFile()) throw new IOException("No hay ZIP de mundo");
        File tmp = uniqueDir(saves, ".pg-world-tmp");
        try {
            //noinspection ResultOfMethodCallIgnored
            tmp.mkdirs();
            try (ZipFile zf = new ZipFile(zip)) {
                ZipUtils.zipExtract(zf, "", tmp);
            }
            File worldSrc = findWorldRoot(tmp);
            if (worldSrc == null) {
                throw new IOException("El ZIP no trae level.dat. No es un mundo de Minecraft.");
            }
            String wantName = worldSrc.equals(tmp) ? fallbackName : worldSrc.getName();
            wantName = sanitizeFileName(wantName);
            if (wantName.isEmpty() || wantName.startsWith(".")) wantName = fallbackName;
            File dest = uniqueDir(saves, wantName);
            if (worldSrc.equals(tmp)) {
                if (!moveDir(tmp, dest)) throw new IOException("No se pudo mover el mundo a saves/");
            } else {
                if (!moveDir(worldSrc, dest)) throw new IOException("No se pudo mover el mundo a saves/");
                deleteTree(tmp);
            }
            //noinspection ResultOfMethodCallIgnored
            zip.delete();
            Log.i(TAG, "Mundo instalado: saves/" + dest.getName());
            return dest;
        } catch (IOException e) {
            deleteTree(tmp);
            throw e;
        } catch (Exception e) {
            deleteTree(tmp);
            throw new IOException("No se pudo extraer el mundo: " + e.getMessage(), e);
        }
    }

    private static File findWorldRoot(File root) {
        if (root == null || !root.isDirectory()) return null;
        List<File> hits = new ArrayList<>();
        collectLevelDatParents(root, hits, 0);
        File withRegion = null;
        for (File f : hits) {
            if (new File(f, "region").isDirectory() || new File(f, "db").isDirectory()) {
                withRegion = f;
                break;
            }
        }
        return withRegion != null ? withRegion : (hits.isEmpty() ? null : hits.get(0));
    }

    private static void collectLevelDatParents(File dir, List<File> out, int depth) {
        if (depth > 8 || dir == null) return;
        String name = dir.getName();
        if (depth > 0 && ("__MACOSX".equals(name) || name.startsWith("."))) return;
        if (new File(dir, "level.dat").isFile()) {
            out.add(dir);
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collectLevelDatParents(k, out, depth + 1);
        }
    }

    static boolean looksLikeZip(File f) {
        if (f == null || !f.isFile()) return false;
        String n = f.getName().toLowerCase();
        if (n.endsWith(".zip")) return true;
        if (n.endsWith(".jar") || n.endsWith(".disabled")) return false;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[2];
            return in.read(b) == 2 && b[0] == 'P' && b[1] == 'K';
        } catch (Exception e) {
            return false;
        }
    }

    private static File uniqueDir(File parent, String name) {
        File dest = new File(parent, name);
        if (!dest.exists()) return dest;
        return new File(parent, name + "-" + System.currentTimeMillis());
    }

    private static boolean moveDir(File from, File to) {
        if (from == null || to == null) return false;
        if (from.renameTo(to)) return true;
        try {
            copyTree(from, to);
            deleteTree(from);
            return to.exists();
        } catch (Exception e) {
            Log.w(TAG, "moveDir", e);
            return false;
        }
    }

    private static void copyTree(File from, File to) throws IOException {
        if (from.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            to.mkdirs();
            File[] kids = from.listFiles();
            if (kids == null) return;
            for (File k : kids) copyTree(k, new File(to, k.getName()));
            return;
        }
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
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

    private static String slug(String title) {
        if (title == null || title.trim().isEmpty()) return "world";
        String s = title.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "world" : s;
    }

    private static String sanitizeFileName(String name) {
        if (name == null) return "world";
        String s = name.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "world" : s;
    }

    private static String stripExt(String name) {
        int d = name.lastIndexOf('.');
        return d > 0 ? name.substring(0, d) : name;
    }
}
