package net.paraguacraft.mobile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class OptimizedFragment extends Fragment {
    public static final String TAG = "PG_OPTIMIZED";

    private TextView mStatus;
    private TextView mHeader;
    private ProgressBar mBar;
    private Button mFpsBtn;

    public OptimizedFragment() {
        super(R.layout.fragment_pg_optimized);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        LinearLayout list = view.findViewById(R.id.pg_optimized_list);
        mHeader = new TextView(requireContext());
        mHeader.setTextColor(0xFFFFFFFF);
        mHeader.setPadding(0, 0, 0, 16);
        list.addView(mHeader);
        refreshHeader();

        mFpsBtn = new Button(requireContext());
        mFpsBtn.setAllCaps(false);
        refreshFpsButton();
        mFpsBtn.setOnClickListener(v -> {
            boolean next = !DeviceTier.uncapFps(requireContext());
            DeviceTier.setUncapFps(requireContext(), next);
            applyFpsToActiveProfile();
            refreshFpsButton();
            Toast.makeText(requireContext(),
                    next ? "FPS ilimitado (260). OptiFine muestra el contador."
                            : "FPS limitado a la pantalla (" + DeviceTier.fpsCap(requireContext()) + " Hz).",
                    Toast.LENGTH_LONG).show();
        });
        list.addView(mFpsBtn);

        mStatus = new TextView(requireContext());
        mStatus.setTextColor(0xFF2ECC71);
        mStatus.setPadding(0, 16, 0, 8);
        list.addView(mStatus);

        mBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        mBar.setIndeterminate(true);
        mBar.setVisibility(View.GONE);
        list.addView(mBar);

        try {
            for (OptimizedManifest.Pack pack : OptimizedManifest.load(requireContext()).packs()) {
                Button b = new Button(requireContext());
                refreshPackButton(b, pack);
                b.setOnClickListener(v -> onPackClicked(pack, b));
                list.addView(b);
            }
        } catch (Exception e) {
            mStatus.setText("No se pudo leer el manifesto Optimized: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHeader();
        refreshFpsButton();
        String pending = LauncherPreferences.DEFAULT_PREF.getString(OptimizedInstaller.PREF_PENDING_FORGE_MC, null);
        if (pending == null || pending.isEmpty()) return;
        try {
            for (OptimizedManifest.Pack pack : OptimizedManifest.load(requireContext()).packs()) {
                if (!pending.equals(pack.mc)) continue;
                if (OptimizedInstaller.findForgeVersionId(pack) == null) {
                    if (mStatus != null) mStatus.setText("Forge " + pack.mc + ": terminá el instalador y volvé.");
                    return;
                }
                if (mStatus != null) mStatus.setText("Forge listo, bajando OptiFine y mods…");
                startInstall(pack, null, false);
                return;
            }
        } catch (Exception e) {
            if (mStatus != null) mStatus.setText(e.getMessage());
        }
    }

    private void refreshHeader() {
        if (mHeader == null) return;
        DeviceProbe p = DeviceTier.probe(requireContext());
        String tier = DeviceTier.current(requireContext());
        String renderer = DeviceTier.renderer(requireContext(), tier);
        boolean uncap = DeviceTier.uncapFps(requireContext());
        mHeader.setText("Paraguacraft Optimized · gama " + tier
                + " · res " + DeviceTier.resolutionPercent(requireContext(), tier) + "%"
                + " · Xmx " + DeviceTier.xmxMb(requireContext(), tier) + "M\n"
                + p.summary()
                + "\nRenderer: " + (renderer.contains("gl4es") || renderer.equals("opengles2") ? "GL4ES" : "MobileGlues")
                + "\nFPS: " + (uncap
                ? "ilimitado (como PC). Si querés tope, usá este botón o el slider del juego."
                : DeviceTier.fpsCap(requireContext()) + " Hz — tocá el botón para ilimitado")
                + "\n1.8.9 instala Forge solo. 1.12.2 abre una ventana Java; cuando cierre, volvé acá.");
    }

    private void refreshFpsButton() {
        if (mFpsBtn == null) return;
        boolean uncap = DeviceTier.uncapFps(requireContext());
        int cap = DeviceTier.fpsCap(requireContext());
        mFpsBtn.setText(uncap
                ? "FPS: ilimitado (tocá para limitar a la pantalla)"
                : "FPS: " + cap + " Hz (tocá para ilimitado)");
    }

    private void applyFpsToActiveProfile() {
        try {
            String id = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles.load();
            if (id == null || net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles.mainProfileJson == null) return;
            net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile p =
                    net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles.mainProfileJson.profiles.get(id);
            if (p == null || p.name == null || !p.name.startsWith("Optimized ")) return;
            String mc = p.name.substring("Optimized ".length()).trim();
            OptimizedInstaller.applyFpsOnly(requireContext(), Tools.getGameDirPath(p), mc, DeviceTier.current(requireContext()));
        } catch (Exception ignored) {}
    }

    private static String loaderHint(OptimizedManifest.Pack pack) {
        return "fabric".equals(pack.loader) ? "Fabric" : "Forge " + pack.loaderVersion;
    }

    private static void refreshPackButton(Button b, OptimizedManifest.Pack pack) {
        boolean installed = OptimizedInstaller.isInstalled(pack);
        if (installed) {
            b.setText("Optimized " + pack.mc + "  ·  Ya instalado");
        } else {
            b.setText("Instalar Optimized " + pack.mc + "  ·  " + loaderHint(pack));
        }
    }

    private void onPackClicked(OptimizedManifest.Pack pack, Button b) {
        if (OptimizedInstaller.isInstalled(pack)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Optimized " + pack.mc)
                    .setMessage("Esta versión ya está instalada. Si pulsás instalar de nuevo se vuelven a bajar los mods.")
                    .setPositiveButton("Usar este perfil", (d, w) -> {
                        OptimizedInstaller.selectProfile(pack.mc);
                        Toast.makeText(requireContext(), "Perfil activo. Pulsá Jugar.", Toast.LENGTH_LONG).show();
                    })
                    .setNeutralButton("Reinstalar", (d, w) -> startInstall(pack, b, true))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        startInstall(pack, b, false);
    }

    private void startInstall(OptimizedManifest.Pack pack, Button b, boolean reinstall) {
        if (mBar != null) mBar.setVisibility(View.VISIBLE);
        if (b != null) b.setEnabled(false);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                if (reinstall) {
                    OptimizedInstaller.reinstallMods(requireContext(), pack, msg ->
                            Tools.runOnUiThread(() -> { if (mStatus != null) mStatus.setText(msg); }));
                } else {
                    OptimizedInstaller.install(requireContext(), pack, msg ->
                            Tools.runOnUiThread(() -> { if (mStatus != null) mStatus.setText(msg); }));
                }
                LauncherPreferences.DEFAULT_PREF.edit().remove(OptimizedInstaller.PREF_PENDING_FORGE_MC).apply();
                Tools.runOnUiThread(() -> {
                    if (mBar != null) mBar.setVisibility(View.GONE);
                    if (b != null) {
                        b.setEnabled(true);
                        refreshPackButton(b, pack);
                    }
                    Toast.makeText(requireContext(), "Listo. Pulsá Jugar.", Toast.LENGTH_LONG).show();
                });
            } catch (OptimizedInstaller.ForgeGuiNeeded forge) {
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(OptimizedInstaller.PREF_PENDING_FORGE_MC, pack.mc)
                        .commit();
                Tools.runOnUiThread(() -> {
                    if (mBar != null) mBar.setVisibility(View.GONE);
                    if (b != null) b.setEnabled(true);
                    if (mStatus != null) mStatus.setText("Abriendo instalador Forge " + pack.mc + "…");
                    startActivity(OptimizedInstaller.forgeInstallIntent(requireContext(), forge.installerJar, pack));
                    Toast.makeText(requireContext(), "Cuando el instalador diga éxito, volvé a Optimized.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    if (mBar != null) mBar.setVisibility(View.GONE);
                    if (b != null) b.setEnabled(true);
                    if (mStatus != null) mStatus.setText(e.getMessage());
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
