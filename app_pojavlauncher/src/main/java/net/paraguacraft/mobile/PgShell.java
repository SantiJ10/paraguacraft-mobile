package net.paraguacraft.mobile;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.fragments.SearchModFragment;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import android.content.Intent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Diálogos y navegación del shell tipo biblioteca / tienda. */
public final class PgShell {
    public static final String PREF_ONBOARDED = "pg_onboarded";
    public static final String PREF_RENDERER = "pg_renderer";
    public static final String PREF_GFX = "pg_graphics_profile";
    public static final String GFX_AUTO = "auto";
    public static final String GFX_PERF = "lite";
    public static final String GFX_BAL = "media";
    public static final String GFX_QUAL = "alta";
    public static final String GFX_CUSTOM = "custom";
    public static final String RENDER_AUTO = "auto";

    private PgShell() {}

    private static boolean sWizardOpen;
    private static AlertDialog sWizardDlg;

    public static void maybeOnboard(FragmentActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_ONBOARDED, false)) return;
        if (sWizardOpen) return;
        sWizardOpen = true;
        // Persist immediately so recreating the home fragment cannot stack the wizard again.
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_ONBOARDED, true).commit();
        showWallpaperPicker(activity, true);
    }

    private static void present(AlertDialog.Builder b) {
        if (sWizardDlg != null) {
            try { sWizardDlg.dismiss(); } catch (Exception ignored) {}
        }
        sWizardDlg = b.show();
    }

    public static void openStore(FragmentActivity activity, String type, boolean modpack) {
        Bundle b = new Bundle();
        b.putBoolean("paraguacraft_store", true);
        b.putString("type", modpack ? "modpack" : type);
        fillInstanceArgs(b);
        StoreInstall.pendingType = modpack ? "modpack" : type;
        Tools.swapFragment(activity, SearchModFragment.class, SearchModFragment.TAG, b);
    }

    public static void fillInstanceArgs(Bundle b) {
        String pid = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if (pid == null) return;
        LauncherProfiles.load();
        if (LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null) return;
        MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(pid);
        if (p == null) return;
        if (p.name != null && p.name.startsWith("Optimized ")) {
            b.putString("instance_mc", p.name.substring("Optimized ".length())
                    .replace("Forge", "")
                    .replace("Fabric", "")
                    .trim());
        } else if (p.lastVersionId != null) {
            Matcher m = Pattern.compile("(1\\.\\d+(?:\\.\\d+)?|26\\.\\d+)").matcher(p.lastVersionId);
            if (m.find()) b.putString("instance_mc", m.group(1));
        }
        if (p.lastVersionId != null) {
            String id = p.lastVersionId.toLowerCase();
            if (id.contains("forge")) b.putString("instance_loader", "forge");
            else if (id.contains("fabric") || id.contains("quilt")) b.putString("instance_loader", "fabric");
        } else if (p.name != null && p.name.toLowerCase().contains("forge")) {
            b.putString("instance_loader", "forge");
        }
    }

    public static void importModpack(Activity activity) {
        if (activity instanceof LauncherActivity) {
            ((LauncherActivity) activity).modpackImportLauncher.launch(null);
        } else {
            Toast.makeText(activity, "Abrí esta opción desde el menú principal.", Toast.LENGTH_LONG).show();
        }
    }

    public static void showWallpaperPicker(FragmentActivity activity, boolean onboard) {
        Context ctx = activity;
        float d = ctx.getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (16 * d), (int) (12 * d), (int) (16 * d), (int) (8 * d));
        TextView title = new TextView(ctx);
        title.setText("Personalizá tu fondo");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);
        TextView sub = new TextView(ctx);
        sub.setText("Elegí la imagen detrás de los paneles. Después la podés cambiar en Ajustes.");
        sub.setTextColor(0xFFB8C4BC);
        sub.setTextSize(13);
        sub.setPadding(0, (int) (4 * d), 0, (int) (12 * d));
        box.addView(sub);

        int current = Wallpaper.index(ctx);
        LinearLayout row = null;
        for (int i = 0; i < Wallpaper.DRAWABLES.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = (int) (8 * d);
                row.setLayoutParams(rlp);
                box.addView(row);
            }
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (8 * d));
            card.setBackgroundResource(i == current ? R.drawable.pg_glass_card_selected : R.drawable.pg_glass_card);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMarginEnd((int) (6 * d));
            card.setLayoutParams(clp);
            ImageView img = new ImageView(ctx);
            img.setImageResource(Wallpaper.DRAWABLES[i]);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (88 * d)));
            card.addView(img);
            TextView name = new TextView(ctx);
            name.setText(i == current ? Wallpaper.NAMES[i] + "  · activo" : Wallpaper.NAMES[i]);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(12);
            name.setPadding(0, (int) (6 * d), 0, 0);
            card.addView(name);
            final int idx = i;
            card.setOnClickListener(v -> Wallpaper.set(activity, idx));
            row.addView(card);
        }
        scroll.addView(box);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setView(scroll)
                .setPositiveButton(onboard ? "Siguiente" : "Listo", (d1, w) -> {
                    if (onboard) showControlPicker(activity, true);
                });
        if (onboard) {
            b.setCancelable(false);
            b.setNeutralButton("Saltar", (d1, w) -> finishOnboard(activity));
        }
        b.setNegativeButton("Atrás", (d1, w) -> {
            if (onboard) finishOnboard(activity);
        });
        present(b);
    }

    public static void showControlPicker(FragmentActivity activity, boolean onboard) {
        Context ctx = activity;
        float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (16 * d), (int) (12 * d), (int) (16 * d), (int) (8 * d));
        TextView title = new TextView(ctx);
        title.setText("Elegí tus controles");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);
        TextView sub = new TextView(ctx);
        sub.setText("Clásicos (botones compactos) o modernos (zonas más grandes). Después lo cambiás en Ajustes.");
        sub.setTextColor(0xFFB8C4BC);
        sub.setTextSize(13);
        sub.setPadding(0, (int) (4 * d), 0, (int) (12 * d));
        box.addView(sub);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final View[] cards = new View[2];
        cards[0] = schemeCard(ctx, d, "Controles clásicos",
                "Botones compactos y etiquetas conocidas.", R.drawable.pg_ctrl_classic, !ControlScheme.isModern(), v -> {
                    ControlScheme.apply(ctx, ControlScheme.CLASSIC);
                    paintSchemeCards(cards, false);
                });
        cards[1] = schemeCard(ctx, d, "Controles modernos",
                "Más limpio, zonas táctiles más grandes y menos etiquetas.", R.drawable.pg_ctrl_modern, ControlScheme.isModern(), v -> {
                    ControlScheme.apply(ctx, ControlScheme.MODERN);
                    paintSchemeCards(cards, true);
                });
        row.addView(cards[0]);
        row.addView(cards[1]);
        box.addView(row);

        TextView edit = new TextView(ctx);
        edit.setText("Personalizar botones…");
        edit.setTextColor(0xFF2ECC71);
        edit.setPadding(0, (int) (12 * d), 0, 0);
        edit.setOnClickListener(v -> {
            ControlScheme.ensureLayoutFiles(ctx);
            ctx.startActivity(new Intent(ctx, CustomControlsActivity.class));
        });
        box.addView(edit);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setView(box)
                .setPositiveButton(onboard ? "Listo" : "Cerrar", (d1, w) -> {
                    if (onboard) finishOnboard(activity);
                });
        if (onboard) {
            b.setCancelable(false);
            b.setNeutralButton("Saltar", (d1, w) -> finishOnboard(activity));
        }
        b.setNegativeButton("Atrás", (d1, w) -> {
            if (onboard) showWallpaperPicker(activity, true);
        });
        present(b);
    }

    private static View schemeCard(Context ctx, float d, String title, String desc, int preview, boolean selected,
                                   View.OnClickListener click) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (12 * d), (int) (12 * d), (int) (12 * d), (int) (12 * d));
        card.setBackgroundResource(selected ? R.drawable.pg_glass_card_selected : R.drawable.pg_glass_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd((int) (8 * d));
        card.setLayoutParams(lp);
        TextView t = new TextView(ctx);
        t.setText(selected ? title + " seleccionado" : title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        ImageView previewIv = new ImageView(ctx);
        previewIv.setImageResource(preview);
        previewIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (88 * d));
        plp.topMargin = (int) (8 * d);
        previewIv.setLayoutParams(plp);
        TextView s = new TextView(ctx);
        s.setText(desc);
        s.setTextColor(0xFFB8C4BC);
        s.setTextSize(12);
        s.setPadding(0, (int) (4 * d), 0, 0);
        card.addView(t);
        card.addView(previewIv);
        card.addView(s);
        card.setOnClickListener(click);
        return card;
    }

    private static void paintSchemeCards(View[] cards, boolean modern) {
        if (cards == null || cards.length < 2) return;
        styleSchemeCard(cards[0], "Controles clásicos", !modern);
        styleSchemeCard(cards[1], "Controles modernos", modern);
    }

    private static void styleSchemeCard(View card, String title, boolean selected) {
        if (card == null) return;
        card.setBackgroundResource(selected ? R.drawable.pg_glass_card_selected : R.drawable.pg_glass_card);
        if (card instanceof LinearLayout && ((LinearLayout) card).getChildCount() > 0) {
            View first = ((LinearLayout) card).getChildAt(0);
            if (first instanceof TextView) {
                ((TextView) first).setText(selected ? title + " seleccionado" : title);
            }
        }
    }

    private static void finishOnboard(FragmentActivity activity) {
        sWizardOpen = false;
        if (sWizardDlg != null) {
            try { sWizardDlg.dismiss(); } catch (Exception ignored) {}
            sWizardDlg = null;
        }
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_ONBOARDED, true).commit();
        Wallpaper.apply(activity);
    }

    public static void showGraphicsProfile(FragmentActivity activity) {
        String cur = LauncherPreferences.DEFAULT_PREF.getString(PREF_GFX,
                LauncherPreferences.DEFAULT_PREF.getBoolean(DeviceTier.PREF_TIER_MANUAL, false)
                        ? DeviceTier.current(activity) : GFX_AUTO);
        String[] keys = {GFX_AUTO, GFX_PERF, GFX_BAL, GFX_QUAL, GFX_CUSTOM};
        String[] labels = {
                "Automático (recomendado)",
                "Rendimiento",
                "Equilibrado",
                "Calidad",
                "Ajustes personalizados"
        };
        int checked = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(cur)) checked = i;
        new AlertDialog.Builder(activity)
                .setTitle("Perfil gráfico adaptativo")
                .setMessage(DeviceTier.probe(activity).summary()
                        + (DeviceTier.uncapFps(activity) ? "\nFPS: ilimitado" : "\nFPS tope: " + DeviceTier.fpsCap(activity)))
                .setSingleChoiceItems(labels, checked, (d, w) -> {
                    applyGraphics(activity, keys[w]);
                    d.dismiss();
                    if (GFX_CUSTOM.equals(keys[w])) {
                        Tools.swapFragment(activity, OptimizedFragment.class, OptimizedFragment.TAG, null);
                    } else {
                        Toast.makeText(activity, "Perfil: " + labels[w], Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    public static void applyGraphics(Context ctx, String profile) {
        LauncherPreferences.DEFAULT_PREF.edit().putString(PREF_GFX, profile).apply();
        if (GFX_AUTO.equals(profile)) {
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean(DeviceTier.PREF_TIER_MANUAL, false).apply();
            DeviceTier.reconcile(ctx);
            return;
        }
        if (GFX_CUSTOM.equals(profile)) return;
        DeviceTier.setTier(ctx, profile);
    }

    public static void showRendererPicker(FragmentActivity activity) {
        String[] values = activity.getResources().getStringArray(R.array.renderer_values);
        String[] names = activity.getResources().getStringArray(R.array.renderer);
        String[] labels = new String[values.length + 1];
        String[] keys = new String[values.length + 1];
        labels[0] = "Automático";
        keys[0] = RENDER_AUTO;
        for (int i = 0; i < values.length; i++) {
            keys[i + 1] = values[i];
            labels[i + 1] = prettyRenderer(values[i], names.length > i ? names[i] : values[i]);
        }
        String cur = LauncherPreferences.DEFAULT_PREF.getString(PREF_RENDERER, RENDER_AUTO);
        if (cur == null || cur.isEmpty()) cur = RENDER_AUTO;
        int checked = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(cur)) checked = i;

        RadioGroup group = new RadioGroup(activity);
        float d = activity.getResources().getDisplayMetrics().density;
        group.setPadding((int) (16 * d), (int) (8 * d), (int) (16 * d), (int) (8 * d));
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(activity);
            rb.setText(labels[i]);
            rb.setTextColor(0xFFFFFFFF);
            rb.setId(i + 1);
            rb.setChecked(i == checked);
            group.addView(rb);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(group);

        new AlertDialog.Builder(activity)
                .setTitle("Renderizador")
                .setMessage("Elegí el backend gráfico al iniciar Minecraft.")
                .setView(scroll)
                .setPositiveButton("Aplicar", (dlg, w) -> {
                    int id = group.getCheckedRadioButtonId();
                    int idx = Math.max(0, id - 1);
                    applyRenderer(activity, keys[idx]);
                    Toast.makeText(activity, labels[idx], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static String prettyRenderer(String value, String fallback) {
        switch (value) {
            case "opengles2":
                return "Holy GL4ES / Krypton — todas las versiones, rápido";
            case "opengles_mobileglues":
                return "MobileGlues — OpenGL ES 3";
            case "opengles3_desktopgl_zink_kopper":
                return "Kopper Zink (Vulkan) — todas las versiones, medio";
            case "vulkan_zink":
                return "Zink (Vulkan) — todas las versiones, medio";
            case "opengles3_ltw":
                return "LTW (OpenGL ES 3) — solo 1.17+";
            default:
                return fallback;
        }
    }

    public static void applyRenderer(Context ctx, String renderer) {
        if (renderer == null || renderer.isEmpty()) renderer = RENDER_AUTO;
        LauncherPreferences.DEFAULT_PREF.edit().putString(PREF_RENDERER, renderer).apply();
        try {
            LauncherProfiles.load();
            String pid = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            if (pid != null && LauncherProfiles.mainProfileJson != null) {
                MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(pid);
                if (p != null) {
                    p.pojavRendererName = RENDER_AUTO.equals(renderer)
                            ? DeviceTier.renderer(ctx, DeviceTier.current(ctx))
                            : renderer;
                    LauncherProfiles.write();
                }
            }
        } catch (Throwable ignored) {}
    }

    public static String instanceLabel() {
        String hint = SkinHelper.instanceMcHint();
        if (hint != null && !hint.isEmpty()) return hint;
        String pid = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if (pid == null) return "Sin instancia";
        LauncherProfiles.load();
        if (LauncherProfiles.mainProfileJson == null) return "Sin instancia";
        MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(pid);
        if (p == null) return "Sin instancia";
        if (p.name != null && !p.name.isEmpty() && !"New".equalsIgnoreCase(p.name)) return p.name;
        return p.lastVersionId != null ? p.lastVersionId : pid;
    }
}
