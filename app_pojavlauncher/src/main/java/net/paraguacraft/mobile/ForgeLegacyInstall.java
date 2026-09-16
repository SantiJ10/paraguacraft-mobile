package net.paraguacraft.mobile;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;
import net.kdt.pojavlaunch.value.MinecraftClientInfo;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.zip.ZipFile;

/**
 * Forge ≤ 1.10 cannot share cache/forge-installer.jar with 1.12.2: HTTP Range resume
 * treats the leftover 1.12.2 jar as a partial 1.8.9 download (HTTP 416 → "done").
 * Install by extracting install_profile.json like the PC launcher, then download vanilla.
 */
public final class ForgeLegacyInstall {
    private static final String TAG = "PgForgeLegacy";
    private static final String MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge";
    private static final String BMCL = "https://bmclapi2.bangbang93.com/maven/net/minecraftforge/forge";

    private ForgeLegacyInstall() {}

    public static boolean isLegacyMc(String mc) {
        String[] p = mc.split("\\.");
        int major = p.length > 0 ? parseInt(p[0], 99) : 99;
        int minor = p.length > 1 ? parseInt(p[1], 99) : 99;
        return major < 1 || (major == 1 && minor <= 10);
    }

    public static String mavenId(String mc, String loaderVersion) {
        String v = loaderVersion.trim();
        if (v.startsWith(mc + "-")) return v;
        if (v.endsWith("-" + mc)) return mc + "-" + v;
        if (isLegacyMc(mc)) return mc + "-" + v + "-" + mc;
        return mc + "-" + v;
    }

    public static String install(Context ctx, OptimizedManifest.Pack pack, OptimizedInstaller.Progress progress) throws Exception {
        String maven = mavenId(pack.mc, pack.loaderVersion);
        progress.onMessage("Forge " + pack.mc + ": bajando instalador…");
        File installer = downloadInstaller(maven);
        progress.onMessage("Forge " + pack.mc + ": extrayendo perfil…");
        String versionId;
        try {
            versionId = extractLegacy(installer, maven);
        } catch (Exception e) {
            if (installer.exists()) //noinspection ResultOfMethodCallIgnored
                installer.delete();
            throw e;
        }
        progress.onMessage("Minecraft " + pack.mc + " (vanilla)…");
        downloadVanilla(pack.mc);
        return versionId;
    }

    static File installerCacheFile(String mavenId) {
        return new File(Tools.DIR_CACHE, "forge-" + mavenId.replace(':', '_') + "-installer.jar");
    }

    private static File downloadInstaller(String maven) throws IOException {
        File dest = installerCacheFile(maven);
        if (dest.isFile() && dest.length() > 64 * 1024) return dest;
        if (dest.exists()) dest.delete();
        IOException last = null;
        for (String base : new String[]{MAVEN, BMCL}) {
            String url = base + "/" + maven + "/forge-" + maven + "-installer.jar";
            try {
                DownloadUtils.downloadFile(url, dest);
                if (dest.isFile() && dest.length() > 64 * 1024) return dest;
            } catch (IOException e) {
                last = e;
                Log.w(TAG, "Installer fail " + url, e);
                if (dest.exists()) dest.delete();
            }
        }
        throw last != null ? last : new IOException("No se descargó el instalador Forge " + maven);
    }

    private static String extractLegacy(File installer, String maven) throws Exception {
        try (ZipFile zip = new ZipFile(installer)) {
            String profileRaw;
            try (InputStream in = ZipUtils.getEntryStream(zip, "install_profile.json");
                 Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                profileRaw = s.hasNext() ? s.next() : "";
            }
            JSONObject profile = new JSONObject(profileRaw);
            JSONObject versionInfo = profile.optJSONObject("versionInfo");
            if (versionInfo == null) {
                throw new IOException("install_profile.json sin versionInfo (¿instalador nuevo?)");
            }
            String versionId = versionInfo.getString("id");
            File versionDir = new File(Tools.DIR_HOME_VERSION, versionId);
            //noinspection ResultOfMethodCallIgnored
            versionDir.mkdirs();
            File jsonOut = new File(versionDir, versionId + ".json");
            Tools.write(jsonOut.getAbsolutePath(), versionInfo.toString());

            String universalName = "forge-" + maven + "-universal.jar";
            JSONObject install = profile.optJSONObject("install");
            if (install != null) {
                String filePath = install.optString("filePath", "");
                if (!filePath.isEmpty()) universalName = filePath;
            }
            File libDir = new File(Tools.DIR_HOME_LIBRARY,
                    "net/minecraftforge/forge/" + maven);
            //noinspection ResultOfMethodCallIgnored
            libDir.mkdirs();
            File universal = new File(libDir, universalName);
            extractZipEntry(zip, universalName, universal);
            File standard = new File(libDir, "forge-" + maven + ".jar");
            if (!standard.getCanonicalFile().equals(universal.getCanonicalFile())) {
                try (InputStream copyIn = new java.io.FileInputStream(universal);
                     FileOutputStream copyOut = new FileOutputStream(standard)) {
                    IOUtils.copy(copyIn, copyOut);
                }
            }

            downloadLegacyLibraries(versionInfo);
            Log.i(TAG, "Installed " + versionId);
            return versionId;
        }
    }

    private static void extractZipEntry(ZipFile zip, String name, File dest) throws IOException {
        FileUtils.ensureParentDirectory(dest);
        try (InputStream in = ZipUtils.getEntryStream(zip, name);
             FileOutputStream out = new FileOutputStream(dest)) {
            IOUtils.copy(in, out);
        }
        if (dest.length() < 1024) throw new IOException("JAR Forge vacío: " + name);
    }

    private static void downloadLegacyLibraries(JSONObject versionInfo) {
        JSONArray libs = versionInfo.optJSONArray("libraries");
        if (libs == null) return;
        for (int i = 0; i < libs.length(); i++) {
            JSONObject lib = libs.optJSONObject(i);
            if (lib == null) continue;
            if (!lib.optBoolean("clientreq", true)) continue;
            String name = lib.optString("name", "");
            if (name.isEmpty() || name.startsWith("org.lwjgl")) continue;
            if (name.startsWith("net.minecraftforge:forge:")) continue;
            String path = mavenPath(name);
            if (path == null) continue;
            File dest = new File(Tools.DIR_HOME_LIBRARY, path);
            if (dest.isFile() && dest.length() > 0) continue;
            String base = lib.optString("url", "https://libraries.minecraft.net/");
            if (base.isEmpty()) base = "https://libraries.minecraft.net/";
            String url = trimSlash(base) + "/" + path.replace('\\', '/');
            try {
                DownloadUtils.downloadFile(url, dest);
            } catch (Exception e) {
                Log.w(TAG, "Lib skip " + name, e);
            }
        }
    }

    private static String mavenPath(String name) {
        String[] p = name.split(":");
        if (p.length < 3) return null;
        String group = p[0].replace('.', '/');
        String artifact = p[1];
        String version = p[2];
        String classifier = p.length >= 4 ? "-" + p[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private static void downloadVanilla(String mc) throws Exception {
        File jsonFile = new File(Tools.DIR_HOME_VERSION, mc + File.separator + mc + ".json");
        File jarFile = new File(Tools.DIR_HOME_VERSION, mc + File.separator + mc + ".jar");
        FileUtils.ensureParentDirectory(jsonFile);
        if (jsonFile.isFile() && jsonFile.length() > 0 && jarFile.isFile() && jarFile.length() > 1024) return;

        JMinecraftVersionList.Version listed = AsyncMinecraftDownloader.getListedVersion(mc);
        if (listed == null || listed.url == null || listed.url.isEmpty()) {
            throw new IOException("Minecraft " + mc + " no está en la lista de versiones. Abrí el menú principal y reintentá.");
        }
        if (!jsonFile.isFile() || jsonFile.length() < 1) {
            DownloadUtils.downloadFile(listed.url, jsonFile);
        }
        JMinecraftVersionList.Version info = Tools.GLOBAL_GSON.fromJson(
                Tools.read(jsonFile.getAbsolutePath()), JMinecraftVersionList.Version.class);
        if (info == null || info.downloads == null || !info.downloads.containsKey("client")) {
            throw new IOException("JSON de Minecraft " + mc + " sin cliente");
        }
        MinecraftClientInfo client = info.downloads.get("client");
        if (client == null || client.url == null) {
            throw new IOException("URL del cliente " + mc + " ausente");
        }
        if (!jarFile.isFile() || jarFile.length() < 1024) {
            DownloadUtils.downloadFile(client.url, jarFile);
        }
    }

    private static String trimSlash(String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
