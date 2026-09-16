package net.paraguacraft.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Elige instancia (y mundo, si es datapack) como en el launcher de PC. */
public final class StoreDest {
    private StoreDest() {}

    public static void promptThenInstall(Context ctx, ModDetail detail, int selectedVersion) {
        Activity act = activityOf(ctx);
        StoreInstall.pendingGameDir = null;
        StoreInstall.pendingWorldDir = null;
        if (act == null) {
            runInstall(ctx, detail, selectedVersion);
            return;
        }
        String type = StoreInstall.pendingType;
        if ("modpack".equals(type)) {
            runInstall(ctx, detail, selectedVersion);
            return;
        }
        String wantMc = mcOf(detail, selectedVersion);
        List<MinecraftProfile> all = profiles();
        List<MinecraftProfile> match = new ArrayList<>();
        for (MinecraftProfile p : all) {
            if (wantMc != null && versionMatches(p.lastVersionId, wantMc)) match.add(p);
        }
        List<MinecraftProfile> shown = match.isEmpty() ? all : match;
        List<String> labels = new ArrayList<>();
        for (MinecraftProfile p : shown) {
            labels.add(label(p));
        }
        labels.add("Crear instancia nueva" + (wantMc != null ? " (" + wantMc + ")" : ""));
        new AlertDialog.Builder(act)
                .setTitle("¿En qué instancia instalar?")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which >= shown.size()) {
                        askLoaderThenCreate(act, ctx, detail, selectedVersion, wantMc);
                        return;
                    }
                    MinecraftProfile p = shown.get(which);
                    File dir = Tools.getGameDirPath(p);
                    if ("datapack".equals(type)) {
                        pickWorld(act, ctx, detail, selectedVersion, p, dir);
                    } else {
                        StoreInstall.pendingGameDir = dir;
                        StoreInstall.pendingWorldDir = null;
                        selectProfile(p);
                        runInstall(ctx, detail, selectedVersion);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        if (wantMc != null && match.isEmpty() && !all.isEmpty()) {
            Toast.makeText(act, "Ninguna instancia es " + wantMc + ". Elegí una o creá una nueva.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static void pickWorld(Activity act, Context ctx, ModDetail detail, int ver,
                                  MinecraftProfile p, File gameDir) {
        File saves = new File(gameDir, "saves");
        File[] worlds = saves.listFiles(f -> f.isDirectory() && new File(f, "level.dat").isFile());
        List<String> labels = new ArrayList<>();
        List<File> dirs = new ArrayList<>();
        labels.add("Carpeta datapacks de la instancia");
        dirs.add(null);
        if (worlds != null) {
            for (File w : worlds) {
                labels.add("Mundo: " + w.getName());
                dirs.add(w);
            }
        }
        new AlertDialog.Builder(act)
                .setTitle("¿En qué mundo?")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    StoreInstall.pendingGameDir = gameDir;
                    StoreInstall.pendingWorldDir = dirs.get(which);
                    selectProfile(p);
                    runInstall(ctx, detail, ver);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void askLoaderThenCreate(Activity act, Context ctx, ModDetail detail, int ver, String mc) {
        if (mc == null || mc.isEmpty()) {
            Toast.makeText(act, "Este archivo no trae versión de Minecraft.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] loaders = {"Vanilla (sin loader)", "Fabric", "Forge"};
        new AlertDialog.Builder(act)
                .setTitle("Nueva instancia " + mc)
                .setItems(loaders, (d, which) -> {
                    String loader = which == 1 ? "fabric" : which == 2 ? "forge" : "vanilla";
                    ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
                    PojavApplication.sExecutorService.execute(() -> {
                        try {
                            File dir = InstanceFactory.create(ctx, mc, loader);
                            StoreInstall.pendingGameDir = dir;
                            StoreInstall.pendingWorldDir = null;
                            File dest = StoreInstall.installSingle(detail, ver);
                            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                            String where = dest != null ? dest.getParentFile().getName() + "/" + dest.getName() : mc;
                            Tools.runOnUiThread(() -> Toast.makeText(ctx,
                                    "Instalado en " + where, Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                            Tools.showErrorRemote(ctx, R.string.modpack_install_download_failed, e);
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    static void runInstall(Context ctx, ModDetail detail, int selectedVersion) {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File dest = StoreInstall.installSingle(detail, selectedVersion);
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                String where = dest != null ? dest.getParentFile().getName() + "/" + dest.getName() : "la instancia";
                Tools.runOnUiThread(() -> Toast.makeText(ctx,
                        "Instalado en " + where, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                Tools.showErrorRemote(ctx, R.string.modpack_install_download_failed, e);
            }
        });
    }

    static String mcOf(ModDetail detail, int selectedVersion) {
        if (detail == null || detail.mcVersionNames == null) return null;
        if (selectedVersion < 0 || selectedVersion >= detail.mcVersionNames.length) return null;
        String v = detail.mcVersionNames[selectedVersion];
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    static boolean versionMatches(String lastVersionId, String wantMc) {
        if (lastVersionId == null || wantMc == null) return false;
        String id = lastVersionId.toLowerCase();
        String want = wantMc.toLowerCase();
        if (id.contains(want)) return true;
        // 1.21 coincide con 1.21.11
        if (want.matches("1\\.\\d+") && id.contains(want + ".")) return true;
        String major = want.contains("-") ? want.substring(0, want.indexOf('-')) : want;
        return !major.equals(want) && id.contains(major);
    }

    static List<MinecraftProfile> profiles() {
        LauncherProfiles.load();
        List<MinecraftProfile> out = new ArrayList<>();
        if (LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null) {
            return out;
        }
        for (MinecraftProfile p : LauncherProfiles.mainProfileJson.profiles.values()) {
            if (p != null && p.name != null) out.add(p);
        }
        return out;
    }

    static String label(MinecraftProfile p) {
        String v = p.lastVersionId != null ? p.lastVersionId : "?";
        return p.name + "  ·  " + v;
    }

    static void selectProfile(MinecraftProfile want) {
        LauncherProfiles.load();
        if (LauncherProfiles.mainProfileJson == null || want == null) return;
        for (java.util.Map.Entry<String, MinecraftProfile> e : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            if (e.getValue() == want || (e.getValue() != null && want.name != null
                    && want.name.equals(e.getValue().name))) {
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, e.getKey())
                        .apply();
                return;
            }
        }
    }

    static Activity activityOf(Context ctx) {
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }
}
