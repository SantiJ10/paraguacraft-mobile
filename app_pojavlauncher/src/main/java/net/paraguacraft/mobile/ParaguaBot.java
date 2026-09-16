package net.paraguacraft.mobile;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

/**
 * Pre-launch only. Bottom sheet + chips. Nunca in-game.
 */
public final class ParaguaBot {
    private ParaguaBot() {}

    public static void show(@NonNull Context ctx) {
        Dialog sheet = new Dialog(ctx);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(ctx);
        title.setText("Paraguabot");
        title.setTextSize(20);
        title.setTextColor(0xFF9B59B6);
        root.addView(title);

        TextView ctxView = new TextView(ctx);
        ctxView.setText(contextSummary(ctx));
        ctxView.setTextColor(0xFFCCCCCC);
        ctxView.setPadding(0, 8, 0, 16);
        root.addView(ctxView);

        addChip(ctx, root, "Reparar instancia", () -> repair(ctx, sheet));
        addChip(ctx, root, "Reinstalar Optimized", () -> reinstall(ctx, sheet));
        addChip(ctx, root, "Cambiar gama", () -> changeTier(ctx, sheet));
        addChip(ctx, root, DeviceTier.uncapFps(ctx)
                ? "Limitar FPS a la pantalla"
                : "FPS ilimitado", () -> toggleFps(ctx, sheet));
        addChip(ctx, root, "Explicar último crash", () -> explainCrash(ctx, sheet));

        sheet.setContentView(root);
        Window win = sheet.getWindow();
        if (win != null) {
            win.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            win.setGravity(Gravity.BOTTOM);
        }
        sheet.show();
    }

    private static void addChip(Context ctx, LinearLayout root, String label, Runnable action) {
        android.widget.Button b = new android.widget.Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        root.addView(b);
    }

    public static String contextSummary(Context ctx) {
        String tier = DeviceTier.current(ctx);
        String pack = "(ninguno)";
        String id = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        LauncherProfiles.load();
        if (id != null && LauncherProfiles.mainProfileJson != null) {
            MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(id);
            if (p != null) pack = p.name + " / " + p.lastVersionId;
        }
        DeviceProbe p = DeviceTier.probe(ctx);
        return "Pack: " + pack + "\n" + p.summary()
                + "\ngama " + tier + " · Xmx " + LauncherPreferences.PREF_RAM_ALLOCATION + "M"
                + " · FPS " + (DeviceTier.uncapFps(ctx) ? "ilimitado" : DeviceTier.fpsCap(ctx) + " Hz");
    }

    private static void repair(Context ctx, Dialog sheet) {
        sheet.dismiss();
        Toast.makeText(ctx, "Verificando carpeta de juego…", Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            File dir = OptimizedInstaller.activeGameDir();
            int disabled = MobileCompat.sanitizeGameDir(dir);
            int worlds = StoreInstall.repairSaves(dir);
            try {
                String id = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
                LauncherProfiles.load();
                MinecraftProfile p = id != null && LauncherProfiles.mainProfileJson != null
                        ? LauncherProfiles.mainProfileJson.profiles.get(id) : null;
                if (OptimizedInstaller.needsIris(dir)) {
                    OptimizedInstaller.ensureFabricIris(ctx, dir,
                            net.kdt.pojavlaunch.LauncherActivity.mcVersionOf(p), msg -> {});
                }
            } catch (Exception ignored) {}
            File mods = new File(dir, "mods");
            int n = mods.isDirectory() && mods.list() != null ? mods.list().length : 0;
            int finalDisabled = disabled;
            int finalWorlds = worlds;
            boolean iris = OptimizedInstaller.hasIrisJar(dir);
            Tools.runOnUiThread(() ->
                    Toast.makeText(ctx, "Instancia: " + dir.getName() + " · " + n + " en mods/"
                            + (iris ? " · Iris OK" : "")
                            + (finalWorlds > 0 ? " · " + finalWorlds + " mundo(s) reparado(s)" : "")
                            + (finalDisabled > 0 ? " · " + finalDisabled + " mods de PC desactivados" : "")
                            + ". Si falla, usá Reinstalar Optimized.", Toast.LENGTH_LONG).show());
        });
    }

    private static void reinstall(Context ctx, Dialog sheet) {
        sheet.dismiss();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                OptimizedManifest man = OptimizedManifest.load(ctx);
                String id = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
                LauncherProfiles.load();
                MinecraftProfile p = id != null ? LauncherProfiles.mainProfileJson.profiles.get(id) : null;
                OptimizedManifest.Pack pack = null;
                if (p != null && p.name != null && p.name.startsWith("Optimized ")) {
                    String mc = p.name.substring("Optimized ".length()).trim();
                    for (OptimizedManifest.Pack cand : man.packs()) {
                        if (cand.mc.equals(mc)) { pack = cand; break; }
                    }
                }
                if (pack == null) pack = man.packs().get(2); // 1.18.2 default-ish
                OptimizedInstaller.reinstallMods(ctx, pack, msg -> {});
                Tools.runOnUiThread(() -> Toast.makeText(ctx, "Optimized reinstalado.", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Tools.runOnUiThread(() -> Toast.makeText(ctx, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void toggleFps(Context ctx, Dialog sheet) {
        boolean next = !DeviceTier.uncapFps(ctx);
        DeviceTier.setUncapFps(ctx, next);
        String id = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        LauncherProfiles.load();
        MinecraftProfile p = id != null && LauncherProfiles.mainProfileJson != null
                ? LauncherProfiles.mainProfileJson.profiles.get(id) : null;
        if (p != null && p.name != null && p.name.startsWith("Optimized ")) {
            String mc = p.name.substring("Optimized ".length()).trim();
            OptimizedInstaller.applyFpsOnly(ctx, Tools.getGameDirPath(p), mc, DeviceTier.current(ctx));
        }
        sheet.dismiss();
        Toast.makeText(ctx, next ? "FPS ilimitado." : "FPS limitado a " + DeviceTier.fpsCap(ctx) + " Hz.",
                Toast.LENGTH_LONG).show();
    }

    private static void changeTier(Context ctx, Dialog sheet) {
        new AlertDialog.Builder(ctx)
                .setTitle("Gama")
                .setItems(new CharSequence[]{"lite (2–3 GB)", "media", "alta"}, (d, w) -> {
                    String t = w == 0 ? DeviceTier.LITE : w == 1 ? DeviceTier.MEDIA : DeviceTier.ALTA;
                    DeviceTier.setTier(ctx, t);
                    sheet.dismiss();
                    Toast.makeText(ctx, "Gama " + t + " · Xmx " + LauncherPreferences.PREF_RAM_ALLOCATION + "M", Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private static void explainCrash(Context ctx, Dialog sheet) {
        sheet.dismiss();
        String id = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        LauncherProfiles.load();
        MinecraftProfile p = id != null && LauncherProfiles.mainProfileJson != null
                ? LauncherProfiles.mainProfileJson.profiles.get(id) : null;
        File log = p != null ? OptimizedInstaller.latestLog(p) : new File(Tools.DIR_GAME_NEW, "logs/latest.log");
        String text = "No hay latest.log";
        if (log.isFile()) {
            try {
                String all = Tools.read(log.getAbsolutePath());
                int from = Math.max(0, all.length() - 4000);
                text = heuristic(all.substring(from));
            } catch (Exception e) {
                text = e.getMessage();
            }
        }
        new AlertDialog.Builder(ctx)
                .setTitle("Último crash")
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    static String heuristic(String tail) {
        String lower = tail.toLowerCase();
        if (lower.contains("outofmemory") || lower.contains("java.lang.outofmemoryerror")) {
            return "La JVM se quedó sin RAM. Bajá la gama (Paraguabot → Cambiar gama) o cerrá apps de Android. No uses ZGC.";
        }
        if (lower.contains("could not find or load main class") || lower.contains("no main manifest")) {
            return "El loader o el JAR de Minecraft está incompleto. Reinstalá Optimized.";
        }
        if (lower.contains("voxy") || lower.contains("libc.so.6") || lower.contains("linuxlibrary")) {
            return "Voxy (y similares) no corren en Android. Paraguacraft los desactiva al jugar. Volvé a entrar.";
        }
        if (lower.contains("mixin") || lower.contains("incompatible")) {
            return "Conflicto de mods. En Android se desactivan Voxy/Nvidium (nativos de PC). Reinstalá Optimized si sigue fallando.";
        }
        if (lower.contains("opengl") || lower.contains("glfw") || lower.contains("zink")) {
            return "Fallo de renderer. En gama baja usá GL4ES (opengles2), no Vulkan/Zink.";
        }
        return "Últimas líneas de latest.log:\n\n" + tail;
    }
}
