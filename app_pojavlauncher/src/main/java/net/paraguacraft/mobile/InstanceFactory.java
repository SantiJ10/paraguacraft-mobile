package net.paraguacraft.mobile;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.FabricVersion;
import net.kdt.pojavlaunch.modloaders.FabriclikeDownloadTask;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;

/** Crea una instancia vanilla/Fabric para instalar un mundo o pack. */
public final class InstanceFactory {
    private static final String TAG = "PgInstance";

    private InstanceFactory() {}

    public static File create(Context ctx, String mc, String loader) throws Exception {
        String kind = loader == null ? "vanilla" : loader.toLowerCase();
        String folder = ("fabric".equals(kind) ? "fabric-" : "forge".equals(kind) ? "forge-" : "mc-") + safe(mc);
        File gameDir = new File(Tools.DIR_GAME_HOME, "instances/" + folder);
        //noinspection ResultOfMethodCallIgnored
        gameDir.mkdirs();
        String versionId = mc;
        String name = "Minecraft " + mc;
        if ("fabric".equals(kind)) {
            versionId = installFabric(mc);
            name = "Fabric " + mc;
        } else if ("forge".equals(kind)) {
            throw new IOException("Forge hay que instalarlo desde Versiones. Creá Fabric o Vanilla para este mundo.");
        }
        LauncherProfiles.load();
        MinecraftProfile p = new MinecraftProfile();
        p.name = name;
        p.lastVersionId = versionId;
        p.gameDir = "./instances/" + folder;
        DeviceTier.stampProfile(p);
        LauncherProfiles.insertMinecraftProfile(p);
        LauncherProfiles.write();
        StoreDest.selectProfile(p);
        Log.i(TAG, "Creada " + name + " -> " + versionId);
        return gameDir;
    }

    private static String installFabric(String mc) throws IOException {
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

    private static String safe(String s) {
        return s == null ? "mc" : s.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
