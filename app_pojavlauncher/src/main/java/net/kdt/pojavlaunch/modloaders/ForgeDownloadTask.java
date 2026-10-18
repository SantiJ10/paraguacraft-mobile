package net.kdt.pojavlaunch.modloaders;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.MinecraftClientInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ForgeDownloadTask implements Runnable, Tools.DownloaderFeedback {
    private String mDownloadUrl;
    private String mFullVersion;
    private String mLoaderVersion;
    private String mGameVersion;
    private final ModloaderDownloadListener mListener;
    public ForgeDownloadTask(ModloaderDownloadListener listener, String forgeVersion) {
        this.mListener = listener;
        this.mDownloadUrl = ForgeUtils.getInstallerUrl(forgeVersion);
        this.mFullVersion = forgeVersion;
        if (forgeVersion != null && forgeVersion.contains("-")) {
            this.mGameVersion = forgeVersion.substring(0, forgeVersion.indexOf('-'));
        }
    }

    public ForgeDownloadTask(ModloaderDownloadListener listener, String gameVersion, String loaderVersion) {
        this.mListener = listener;
        this.mLoaderVersion = loaderVersion;
        this.mGameVersion = gameVersion;
    }
    @Override
    public void run() {
        if(determineDownloadUrl()) {
            downloadForge();
        }
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
    }

    @Override
    public void updateProgress(int curr, int max) {
        int progress100 = (int)(((float)curr / (float)max)*100f);
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, progress100, R.string.forge_dl_progress, mFullVersion);
    }

    private void downloadForge() {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.forge_dl_progress, mFullVersion);
        try {
            File destinationFile = new File(Tools.DIR_CACHE,
                    "forge-" + (mFullVersion != null ? mFullVersion : "installer") + "-installer.jar");
            byte[] buffer = new byte[65536];
            DownloadUtils.downloadFileMonitored(mDownloadUrl, destinationFile, buffer, this);
            prewarmForgeInstaller(destinationFile);
            mListener.onDownloadFinished(destinationFile);
        }catch (FileNotFoundException e) {
            mListener.onDataNotAvailable();
        } catch (IOException e) {
            mListener.onDownloadError(e);
        }
    }

    /**
     * Pre-extracts bundled maven/ artifacts and downloads all Forge installer libraries + vanilla
     * client JAR in parallel on 8 threads so the single-threaded Swing installer inside
     * JavaGUILauncherActivity finishes in seconds instead of ~5 minutes.
     */
    private void prewarmForgeInstaller(File installerJar) {
        prewarmInstallerJar(installerJar, mGameVersion, mFullVersion != null ? mFullVersion : "Forge", this);
    }

    /**
     * Pre-extracts bundled maven/ artifacts, cleans any corrupt processor output JARs, and downloads
     * all Forge/NeoForge installer libraries + vanilla client JAR in parallel on 8 threads so the
     * single-threaded Swing/CLI installer inside JavaGUILauncherActivity finishes in seconds.
     */
    public static void prewarmInstallerJar(File installerJar, String gameVersion, String label,
                                           Tools.DownloaderFeedback feedback) {
        if (installerJar == null || !installerJar.isFile()) return;
        try {
            net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles.load();
            net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles.write();
        } catch (Throwable ignored) {}
        List<LibEntry> toDownload = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        String detectedMc = gameVersion;

        try (ZipFile zip = new ZipFile(installerJar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] copyBuf = new byte[65536];
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.isDirectory()) continue;
                String name = ze.getName();
                if (name.startsWith("maven/") && name.length() > "maven/".length()) {
                    String rel = name.substring("maven/".length());
                    File out = new File(Tools.DIR_HOME_LIBRARY, rel);
                    boolean valid = out.isFile() && out.length() > 0
                            && (!DownloadUtils.isZipOrJarExtension(out) || DownloadUtils.isValidZipOrJar(out));
                    if (!valid) {
                        if (out.exists()) out.delete();
                        FileUtils.ensureParentDirectory(out);
                        File part = new File(out.getParentFile(), out.getName() + ".part");
                        try (InputStream in = zip.getInputStream(ze);
                             FileOutputStream fos = new FileOutputStream(part)) {
                            int n;
                            while ((n = in.read(copyBuf)) > 0) fos.write(copyBuf, 0, n);
                        }
                        if (!part.renameTo(out)) {
                            org.apache.commons.io.FileUtils.copyFile(part, out);
                            part.delete();
                        }
                    }
                }
            }

            detectedMc = collectLibsFromZipJson(zip, "install_profile.json", toDownload, seenPaths, detectedMc);
            detectedMc = collectLibsFromZipJson(zip, "version.json", toDownload, seenPaths, detectedMc);
        } catch (Exception e) {
            Log.w("ForgePrewarm", "Failed reading installer zip", e);
        }

        if (detectedMc != null && !detectedMc.isEmpty()) {
            ensureVanillaClientPrewarmed(detectedMc, feedback);
        }

        if (toDownload.isEmpty()) return;

        int total = toDownload.size();
        AtomicInteger done = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>(total);
        for (LibEntry lib : toDownload) {
            futures.add(pool.submit(() -> {
                try {
                    FileUtils.ensureParentDirectory(lib.target);
                    DownloadUtils.ensureSha1(lib.target, lib.sha1, () -> {
                        DownloadUtils.downloadFile(lib.url, lib.target);
                        return null;
                    });
                } catch (Exception e) {
                    Log.w("ForgePrewarm", "Skipped prewarm of " + lib.url + ": " + e.getMessage());
                } finally {
                    int c = done.incrementAndGet();
                    int pct = Math.min(99, (c * 100) / Math.max(1, total));
                    ProgressKeeper.submitProgress(
                            ProgressLayout.INSTALL_MODPACK,
                            pct,
                            R.string.forge_dl_progress,
                            (label != null ? label : "Forge") + " (" + c + "/" + total + ")"
                    );
                }
            }));
        }
        pool.shutdown();
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
    }

    private static String collectLibsFromZipJson(ZipFile zip, String entryName, List<LibEntry> out,
                                                 Set<String> seenPaths, String currentMc) {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) return currentMc;
        try (InputStream in = zip.getInputStream(entry)) {
            String raw = Tools.read(in);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (currentMc == null || currentMc.isEmpty()) {
                if (root.has("minecraft") && root.get("minecraft").isJsonPrimitive()) {
                    currentMc = root.get("minecraft").getAsString();
                } else if (root.has("inheritsFrom") && root.get("inheritsFrom").isJsonPrimitive()) {
                    currentMc = root.get("inheritsFrom").getAsString();
                }
            }
            if (root.has("data") && root.get("data").isJsonObject()) {
                JsonObject data = root.getAsJsonObject("data");
                for (String key : data.keySet()) {
                    JsonElement valEl = data.get(key);
                    if (!valEl.isJsonObject()) continue;
                    JsonObject valObj = valEl.getAsJsonObject();
                    if (valObj.has("client") && valObj.get("client").isJsonPrimitive()) {
                        String clientVal = valObj.get("client").getAsString();
                        if (clientVal.startsWith("[") && clientVal.endsWith("]") && clientVal.length() > 2) {
                            String coord = clientVal.substring(1, clientVal.length() - 1);
                            String relPath = mavenCoordinateToPath(coord);
                            if (relPath != null) {
                                File procTarget = new File(Tools.DIR_HOME_LIBRARY, relPath);
                                if (procTarget.exists() && DownloadUtils.isZipOrJarExtension(procTarget)
                                        && !DownloadUtils.isValidZipOrJar(procTarget)) {
                                    Log.w("ForgePrewarm", "Deleting corrupt processor output: " + procTarget.getAbsolutePath());
                                    procTarget.delete();
                                }
                            }
                        }
                    }
                }
            }
            collectLibsFromArray(root.getAsJsonArray("libraries"), out, seenPaths);
            if (root.has("versionInfo") && root.get("versionInfo").isJsonObject()) {
                JsonObject vi = root.getAsJsonObject("versionInfo");
                collectLibsFromArray(vi.getAsJsonArray("libraries"), out, seenPaths);
            }
        } catch (Exception e) {
            Log.w("ForgePrewarm", "Failed parsing " + entryName, e);
        }
        return currentMc;
    }

    private static void collectLibsFromArray(JsonArray libs, List<LibEntry> out, Set<String> seenPaths) {
        if (libs == null) return;
        for (JsonElement el : libs) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            String path = null;
            String url = null;
            String sha1 = null;
            if (obj.has("downloads") && obj.get("downloads").isJsonObject()) {
                JsonObject dl = obj.getAsJsonObject("downloads");
                if (dl.has("artifact") && dl.get("artifact").isJsonObject()) {
                    JsonObject art = dl.getAsJsonObject("artifact");
                    if (art.has("path")) path = art.get("path").getAsString();
                    if (art.has("url")) url = art.get("url").getAsString();
                    if (art.has("sha1")) sha1 = art.get("sha1").getAsString();
                }
            }
            if ((path == null || path.isEmpty()) && obj.has("name")) {
                path = mavenCoordinateToPath(obj.get("name").getAsString());
            }
            if ((url == null || url.isEmpty()) && path != null) {
                String defaultMaven = path.startsWith("net/neoforged/")
                        ? "https://maven.neoforged.net/releases/"
                        : "https://maven.minecraftforge.net/";
                String base = obj.has("url") && !obj.get("url").getAsString().isEmpty()
                        ? obj.get("url").getAsString()
                        : defaultMaven;
                if (!base.endsWith("/")) base += "/";
                url = base + path;
            }
            if (path == null || url == null || url.isEmpty()) continue;
            if (!seenPaths.add(path)) continue;
            File target = new File(Tools.DIR_HOME_LIBRARY, path);
            if (target.isFile() && target.length() > 0) {
                if (!DownloadUtils.isZipOrJarExtension(target) || DownloadUtils.isValidZipOrJar(target)) {
                    continue;
                }
                Log.w("ForgePrewarm", "Deleting corrupt library JAR before prewarm: " + target.getAbsolutePath());
                target.delete();
            }
            out.add(new LibEntry(target, url, sha1));
        }
    }

    public static String mavenCoordinateToPath(String name) {
        if (name == null || name.isEmpty()) return null;
        String ext = "jar";
        int at = name.indexOf('@');
        if (at >= 0) {
            ext = name.substring(at + 1);
            name = name.substring(0, at);
        }
        String[] parts = name.split(":");
        if (parts.length < 3) return null;
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length >= 4 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + "." + ext;
    }

    private static String findVanillaVersionUrl(String mcVersion) {
        JMinecraftVersionList.Version listed = AsyncMinecraftDownloader.getListedVersion(mcVersion);
        if (listed != null && listed.url != null) return listed.url;
        try {
            String raw = DownloadUtils.downloadStringCached(
                    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
                    "version_manifest_v2",
                    input -> input
            );
            JMinecraftVersionList list = Tools.GLOBAL_GSON.fromJson(raw, JMinecraftVersionList.class);
            if (list != null && list.versions != null) {
                for (JMinecraftVersionList.Version v : list.versions) {
                    if (v != null && mcVersion.equals(v.id) && v.url != null) {
                        return v.url;
                    }
                }
            }
        } catch (Exception e) {
            Log.w("ForgePrewarm", "Failed to lookup version manifest for " + mcVersion, e);
        }
        return null;
    }

    private static void ensureVanillaClientPrewarmed(String mcVersion, Tools.DownloaderFeedback feedback) {
        try {
            File verDir = new File(Tools.DIR_HOME_VERSION, mcVersion);
            File verJson = new File(verDir, mcVersion + ".json");
            File verJar = new File(verDir, mcVersion + ".jar");
            if (!verJson.isFile() || verJson.length() == 0) {
                String url = findVanillaVersionUrl(mcVersion);
                if (url != null) {
                    FileUtils.ensureParentDirectory(verJson);
                    DownloadUtils.downloadFile(url, verJson);
                }
            }
            if ((!verJar.isFile() || !DownloadUtils.isValidZipOrJar(verJar)) && verJson.isFile()) {
                if (verJar.exists()) verJar.delete();
                JMinecraftVersionList.Version parsed = Tools.GLOBAL_GSON.fromJson(
                        Tools.read(verJson.getAbsolutePath()), JMinecraftVersionList.Version.class);
                if (parsed != null && parsed.downloads != null) {
                    MinecraftClientInfo client = parsed.downloads.get("client");
                    if (client != null && client.url != null) {
                        FileUtils.ensureParentDirectory(verJar);
                        DownloadUtils.ensureSha1(verJar, client.sha1, () -> {
                            DownloadUtils.downloadFileMonitored(client.url, verJar, new byte[65536], feedback);
                            return null;
                        });
                    }
                }
            }
        } catch (Exception e) {
            Log.w("ForgePrewarm", "Vanilla prewarm skipped for " + mcVersion, e);
        }
    }

    private static final class LibEntry {
        final File target;
        final String url;
        final String sha1;
        LibEntry(File target, String url, String sha1) {
            this.target = target;
            this.url = url;
            this.sha1 = sha1;
        }
    }

    public boolean determineDownloadUrl() {
        if(mDownloadUrl != null && mFullVersion != null) return true;
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.forge_dl_searching);
        try {
            if(!findVersion()) {
                mListener.onDataNotAvailable();
                return false;
            }
        }catch (IOException e) {
            mListener.onDownloadError(e);
            return false;
        }
        return true;
    }

    public boolean findVersion() throws IOException {
        List<String> forgeVersions = ForgeUtils.downloadForgeVersions();
        if(forgeVersions == null) return false;
        String exactLegacy = mGameVersion + "-" + mLoaderVersion + "-" + mGameVersion;
        String versionStart = mGameVersion + "-" + mLoaderVersion;
        String prefixMatch = null;
        for (String versionName : forgeVersions) {
            if (versionName.equals(exactLegacy) || versionName.equals(versionStart)) {
                mFullVersion = versionName;
                mDownloadUrl = ForgeUtils.getInstallerUrl(mFullVersion);
                return true;
            }
            if (prefixMatch == null && versionName.startsWith(versionStart)
                    && (versionName.length() == versionStart.length()
                    || versionName.charAt(versionStart.length()) == '-')) {
                prefixMatch = versionName;
            }
        }
        if (prefixMatch != null) {
            mFullVersion = prefixMatch;
            mDownloadUrl = ForgeUtils.getInstallerUrl(mFullVersion);
            return true;
        }
        return false;
    }

}
