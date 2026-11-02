package net.paraguacraft.mobile;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Integración estilo Paraguacraft con Horizon (https://github.com/horizon-rust/horizon):
 * motor cliente nativo escrito en Rust + wgpu (Vulkan) para Minecraft 26.3,
 * cumpliendo estrictamente el requisito de cuenta oficial de Microsoft con Minecraft: Java Edition.
 */
public class HorizonFragment extends Fragment {
    public static final String TAG = "PG_HORIZON";
    private static final int ACCENT = 0xFF2ECC71;
    private static final int WARN = 0xFFF1C40F;
    private static final int BAD = 0xFFE74C3C;

    private static final String HORIZON_REPO_URL = "https://github.com/horizon-rust/horizon";
    private static final String HORIZON_RELEASES_API = "https://api.github.com/repos/horizon-rust/horizon/releases";
    private static final String[] KNOWN_HORIZON_PACKAGES = {
            "rs.horizon.client",
            "com.horizonrust.horizon",
            "org.horizon_rust.horizon",
            "io.github.horizon_rust.horizon"
    };

    private LinearLayout mBody;
    private String mReleaseNote = "Consultando estado oficial de horizon-rust/horizon…";
    private String mApkDownloadUrl = null;
    private String mLatestTag = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        mBody = new LinearLayout(requireContext());
        mBody.setOrientation(LinearLayout.VERTICAL);
        mBody.setPadding(dp(20), dp(14), dp(20), dp(28));
        scroll.addView(mBody, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        render();
        checkUpstreamReleasesAsync();
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBody != null) render();
    }

    private void render() {
        if (!isAdded() || mBody == null) return;
        Context ctx = requireContext();
        mBody.removeAllViews();

        TextView kicker = text("MOTOR NATIVO EXPERIMENTAL · RUST + VULKAN", ACCENT, 11, true);
        kicker.setLetterSpacing(0.08f);
        mBody.addView(kicker);

        TextView title = text("Paraguacraft × Horizon", 0xFFFFFFFF, 25, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(6);
        mBody.addView(title, tp);

        TextView intro = text(
                "Horizon es una reimplementación de código abierto de Minecraft: Java Edition 26.3 escrita 100 % en Rust (sin JVM ni recolector de basura) con renderizador moderno wgpu sobre Vulkan.",
                0xFFC7D4DF, 13, false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(6);
        ip.bottomMargin = dp(14);
        mBody.addView(intro, ip);

        // Estado de requisitos reales
        MinecraftAccount activeAcc = PojavProfile.getCurrentProfileContent(ctx, null);
        boolean activeOfficial = activeAcc != null && !activeAcc.isLocal() && !activeAcc.isDemo();
        boolean anyOfficial = Tools.hasOnlineProfile();
        boolean arm64 = Architecture.is64BitsDevice();
        boolean vulkan = Tools.checkVulkanSupport(ctx.getPackageManager());
        File jar263 = new File(Tools.DIR_HOME_VERSION, "26.3/26.3.jar");
        boolean has263Assets = jar263.isFile() && jar263.length() > 1_000_000L;
        String installedPkg = detectInstalledHorizonPackage(ctx);

        LinearLayout reqCard = glassCard(true);
        reqCard.addView(text("Diagnóstico de compatibilidad en tu celular", 0xFFFFFFFF, 16, true));
        addCheckRow(reqCard,
                "Cuenta oficial Microsoft (Minecraft: Java Edition)",
                activeOfficial
                        ? "Activa: " + activeAcc.username + " · verificada"
                        : (anyOfficial
                        ? "Tenés una cuenta oficial guardada, pero ahora está seleccionada una cuenta local. Cambiala arriba antes de iniciar Horizon."
                        : "Requerida por Horizon. Las cuentas sin conexión (offline) no pueden iniciar el motor Horizon por política oficial del proyecto."),
                activeOfficial ? ACCENT : (anyOfficial ? WARN : BAD));

        addCheckRow(reqCard,
                "Procesador ARM64 + GPU Vulkan (wgpu)",
                (arm64 ? "ARM64 listo" : "Dispositivo 32 bits (no soportado)")
                        + " · " + (vulkan ? "Vulkan disponible" : "Sin soporte Vulkan completo")
                        + "\n" + DeviceTier.probe(ctx).summary(),
                (arm64 && vulkan) ? ACCENT : WARN);

        addCheckRow(reqCard,
                "Recursos oficiales Minecraft 26.3",
                has263Assets
                        ? "Instalados y listos en .minecraft/versions/26.3"
                        : "Todavía no descargados. Podés prepararlos legalmente con el botón de abajo.",
                has263Assets ? ACCENT : WARN);

        addCheckRow(reqCard,
                "Canal oficial Horizon Android (ARM64)",
                installedPkg != null
                        ? "Paquete nativo detectado (" + installedPkg + ")"
                        : mReleaseNote,
                installedPkg != null || mApkDownloadUrl != null ? ACCENT : 0xFFB8C4BC);

        mBody.addView(reqCard);

        // Acciones principales según estado de cuenta
        if (!activeOfficial) {
            mBody.addView(primaryButton(
                    "Iniciar sesión con cuenta Microsoft oficial",
                    "Necesario para usar Horizon y verificar propiedad legítima de Minecraft.",
                    false,
                    v -> {
                        ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
                        Tools.backToMainMenu(requireActivity());
                    }));
        }

        if (installedPkg != null) {
            mBody.addView(primaryButton(
                    "Abrir Horizon Nativo (ARM64)",
                    "Inicia el motor Rust instalado en tu dispositivo usando tus recursos 26.3.",
                    activeOfficial,
                    v -> {
                        if (!activeOfficial) {
                            showOfficialRequiredDialog();
                            return;
                        }
                        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(installedPkg);
                        if (launch != null) {
                            launch.putExtra("minecraft_dir", Tools.DIR_GAME_NEW);
                            startActivity(launch);
                        }
                    }));
        } else if (mApkDownloadUrl != null) {
            mBody.addView(primaryButton(
                    "Descargar Horizon Android oficial (" + (mLatestTag != null ? mLatestTag : "APK") + ")",
                    "Descarga directa desde el repositorio oficial horizon-rust/horizon.",
                    activeOfficial,
                    v -> {
                        if (!activeOfficial) {
                            showOfficialRequiredDialog();
                            return;
                        }
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(mApkDownloadUrl)));
                    }));
        }

        mBody.addView(primaryButton(
                has263Assets
                        ? "Jugar Minecraft 26.3 ahora (Motor Paraguacraft SDL3 + MobileGlues)"
                        : "Preparar e iniciar perfil Minecraft 26.3",
                "Configura automáticamente una instancia limpia 26.3 con orientación horizontal bloqueada y OpenGL 3.2 directo.",
                true,
                v -> prepareAndSelect263Profile(activeOfficial)));

        mBody.addView(secondaryButton(
                "Ver proyecto oficial Horizon en GitHub",
                "Código fuente en Rust, avances de compatibilidad 26.3 y builds oficiales.",
                v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(HORIZON_REPO_URL)))));

        // Nota informativa clara sobre diferencias
        LinearLayout infoCard = glassCard(false);
        LinearLayout.LayoutParams infLp = (LinearLayout.LayoutParams) infoCard.getLayoutParams();
        infLp.topMargin = dp(14);
        infoCard.addView(text("¿En qué cambia Horizon frente a Minecraft clásico?", 0xFFFFFFFF, 15, true));
        TextView bullets = text(
                "• Cero Java / JVM: consume una fracción de RAM y arranca casi al instante.\n"
                        + "• Renderizador wgpu sobre Vulkan nativo en ARM64.\n"
                        + "• Compatible con servidores Vanilla 26.3 (no carga mods .jar de Forge o Fabric).\n"
                        + "• Exige cuenta oficial de Microsoft; para jugar con mods o cuenta local usá los perfiles Vanilla, Fabric, Forge, NeoForge, Quilt u Optimized de Paraguacraft.",
                0xFFB8C4BC, 12, false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(6);
        infoCard.addView(bullets, blp);
        mBody.addView(infoCard);
    }

    private void prepareAndSelect263Profile(boolean hasOfficialActive) {
        try {
            LauncherProfiles.load();
            if (LauncherProfiles.mainProfileJson == null) return;
            String foundKey = null;
            for (java.util.Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
                MinecraftProfile p = entry.getValue();
                if (p != null && "26.3".equals(p.lastVersionId) && p.name != null && p.name.contains("26.3")) {
                    foundKey = entry.getKey();
                    break;
                }
            }
            if (foundKey == null) {
                foundKey = "pg-26-3-" + UUID.randomUUID().toString().substring(0, 8);
                MinecraftProfile p = new MinecraftProfile();
                p.name = "Paraguacraft 26.3";
                p.lastVersionId = "26.3";
                p.gameDir = "./instances/pg-26-3";
                DeviceTier.stampProfile(p);
                LauncherProfiles.mainProfileJson.profiles.put(foundKey, p);
            }
            LauncherProfiles.write();
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, foundKey)
                    .commit();
            ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, foundKey);

            File jar263 = new File(Tools.DIR_HOME_VERSION, "26.3/26.3.jar");
            if (!hasOfficialActive && !jar263.isFile()) {
                Toast.makeText(requireContext(),
                        "Perfil 26.3 seleccionado. Iniciá sesión con Microsoft si todavía no descargaste los archivos base.",
                        Toast.LENGTH_LONG).show();
                Tools.backToMainMenu(requireActivity());
                return;
            }
            Tools.backToMainMenu(requireActivity());
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        } catch (Throwable t) {
            Toast.makeText(requireContext(), "No se pudo crear el perfil 26.3: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showOfficialRequiredDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Cuenta oficial requerida")
                .setMessage("Horizon verifica la propiedad legítima de Minecraft: Java Edition con tu cuenta de Microsoft antes de descargar recursos o iniciar el motor.\n\nIniciá sesión con tu cuenta oficial desde el selector de cuentas.")
                .setPositiveButton("Iniciar sesión", (d, w) -> {
                    ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
                    Tools.backToMainMenu(requireActivity());
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void checkUpstreamReleasesAsync() {
        PojavApplication.sExecutorService.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(HORIZON_RELEASES_API).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "Paraguacraft-Mobile");
                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }
                    JSONArray arr = new JSONArray(sb.toString());
                    if (arr.length() == 0) {
                        mReleaseNote = "En desarrollo activo para 26.3 (aún sin APK estable publicado en GitHub Releases).";
                    } else {
                        JSONObject latest = arr.getJSONObject(0);
                        mLatestTag = latest.optString("tag_name", "latest");
                        JSONArray assets = latest.optJSONArray("assets");
                        String foundApk = null;
                        if (assets != null) {
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject a = assets.getJSONObject(i);
                                String name = a.optString("name", "").toLowerCase();
                                if (name.endsWith(".apk")) {
                                    foundApk = a.optString("browser_download_url", null);
                                    break;
                                }
                            }
                        }
                        mApkDownloadUrl = foundApk;
                        mReleaseNote = foundApk != null
                                ? "Versión oficial disponible (" + mLatestTag + ") · lista para descargar"
                                : "Última versión publicada: " + mLatestTag + " (build Android ARM64 en preparación)";
                    }
                } else {
                    mReleaseNote = "Motor Horizon 26.3 en desarrollo activo (GitHub: horizon-rust/horizon).";
                }
            } catch (Exception ignored) {
                mReleaseNote = "Sin conexión a GitHub Releases · podés revisar horizon-rust/horizon cuando estés conectado.";
            } finally {
                if (conn != null) conn.disconnect();
            }
            if (isAdded()) {
                requireActivity().runOnUiThread(this::render);
            }
        });
    }

    @Nullable
    private static String detectInstalledHorizonPackage(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : KNOWN_HORIZON_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private LinearLayout glassCard(boolean selected) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(selected ? R.drawable.pg_glass_card_selected : R.drawable.pg_glass_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lp);
        return card;
    }

    private void addCheckRow(LinearLayout parent, String title, String detail, int dotColor) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);

        TextView t = text("●  " + title, dotColor, 13, true);
        TextView d = text(detail, 0xFFD4E0D8, 12, false);
        LinearLayout.LayoutParams dpLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dpLp.topMargin = dp(2);
        row.addView(t);
        row.addView(d, dpLp);
        parent.addView(row);
    }

    private View primaryButton(String title, String sub, boolean highlighted, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        card.setBackgroundResource(highlighted ? R.drawable.pg_glass_card_selected : R.drawable.pg_glass_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);

        card.addView(text(title, highlighted ? ACCENT : 0xFFFFFFFF, 15, true));
        TextView s = text(sub, 0xFFB8C4BC, 12, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(3);
        card.addView(s, sp);
        card.setOnClickListener(click);
        return card;
    }

    private View secondaryButton(String title, String sub, View.OnClickListener click) {
        return primaryButton(title, sub, false, click);
    }

    private TextView text(String value, int color, int sp, boolean bold) {
        TextView t = new TextView(requireContext());
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
