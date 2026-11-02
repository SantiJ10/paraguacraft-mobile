package net.paraguacraft.mobile;

import android.content.Context;
import android.util.DisplayMetrics;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Esquemas táctiles adaptativos de Paraguacraft Mobile:
 * - AUTO: elige automáticamente PvP (1.7/1.8) o Survival moderno (1.9+ / 26.3) al lanzar.
 * - MODERN: zonas táctiles amplias y limpias.
 * - PVP: pensado para 1.8.9 / PvP (Atacar/Usar grandes, F5 en pulgar en vez de 2ª mano, Drop rápido, Sprint fijo).
 * - SURVIVAL: pensado para 1.16–26.3 (2ª mano F, Chat directo, Agacharse/Correr cómodos).
 * - CLASSIC: compacto tradicional.
 */
public final class ControlScheme {
    public static final String PREF = "pg_control_scheme";
    public static final String PREF_CUSTOM_SCALE = "pg_button_scale_override";
    private static final String PREF_LAYOUT_REV = "pg_ctrl_layout_rev";
    private static final int LAYOUT_REV = 2;

    public static final String AUTO = "auto";
    public static final String CLASSIC = "classic";
    public static final String MODERN = "modern";
    public static final String PVP = "pvp";
    public static final String SURVIVAL = "survival";

    public static final String FILE_CLASSIC = "pg_classic.json";
    public static final String FILE_MODERN = "pg_modern.json";
    public static final String FILE_PVP = "pg_pvp.json";
    public static final String FILE_SURVIVAL = "pg_survival.json";

    private ControlScheme() {}

    public static String current() {
        return normalizeScheme(LauncherPreferences.DEFAULT_PREF.getString(PREF, AUTO));
    }

    public static boolean isModern() {
        String c = current();
        return !CLASSIC.equals(c);
    }

    public static String normalizeScheme(String scheme) {
        if (scheme == null) return AUTO;
        switch (scheme) {
            case AUTO:
            case CLASSIC:
            case MODERN:
            case PVP:
            case SURVIVAL:
                return scheme;
            default:
                return AUTO;
        }
    }

    public static String label(String scheme) {
        switch (normalizeScheme(scheme)) {
            case AUTO:
                return "Automático por versión";
            case PVP:
                return "PvP / 1.8.9";
            case SURVIVAL:
                return "Survival 1.20+ / 26.3";
            case CLASSIC:
                return "Controles clásicos";
            case MODERN:
            default:
                return "Controles modernos";
        }
    }

    /** Resuelve qué esquema concreto usar para una versión de Minecraft dada. */
    public static String resolveForVersion(String scheme, String versionId) {
        String norm = normalizeScheme(scheme);
        if (!AUTO.equals(norm)) return norm;
        if (versionId != null) {
            String v = versionId.toLowerCase();
            if (v.startsWith("1.7.") || v.equals("1.7") || v.contains("1.7.10")
                    || v.startsWith("1.8.") || v.equals("1.8") || v.contains("1.8.9") || v.contains("1.8.8")) {
                return PVP;
            }
        }
        return SURVIVAL;
    }

    public static File layoutFile(String concreteScheme) {
        String name;
        switch (concreteScheme) {
            case CLASSIC:
                name = FILE_CLASSIC;
                break;
            case PVP:
                name = FILE_PVP;
                break;
            case SURVIVAL:
                name = FILE_SURVIVAL;
                break;
            case MODERN:
            default:
                name = FILE_MODERN;
                break;
        }
        return new File(Tools.CTRLMAP_PATH, name);
    }

    public static boolean hasCustomScale() {
        return LauncherPreferences.DEFAULT_PREF.getInt(PREF_CUSTOM_SCALE, -1) > 0;
    }

    /** Escala 55–165 según el lado corto en dp o el ajuste manual del usuario. */
    public static int buttonScale(Context ctx, String scheme) {
        int override = LauncherPreferences.DEFAULT_PREF.getInt(PREF_CUSTOM_SCALE, -1);
        if (override >= 55 && override <= 165) {
            return override;
        }
        return autoButtonScale(ctx, scheme);
    }

    public static int autoButtonScale(Context ctx, String scheme) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int shortPx = Math.min(dm.widthPixels, dm.heightPixels);
        int shortDp = Math.round(shortPx / dm.density);
        float base;
        if (shortDp >= 720) base = 116;
        else if (shortDp >= 600) base = 104;
        else if (shortDp >= 480) base = 88;
        else if (shortDp >= 400) base = 76;
        else if (shortDp >= 360) base = 68;
        else base = 62;

        if (CLASSIC.equals(scheme)) base *= 0.96f;
        else if (PVP.equals(scheme)) base *= 1.20f;
        else base *= 1.15f;

        int scale = Math.round(base);
        if (scale < 60) scale = 60;
        if (scale > 145) scale = 145;
        return scale;
    }

    public static int adjustScale(Context ctx, int delta) {
        String concrete = resolveForVersion(current(), activeProfileVersion());
        int cur = buttonScale(ctx, concrete);
        int next = Math.max(55, Math.min(165, cur + delta));
        LauncherPreferences.DEFAULT_PREF.edit()
                .putInt(PREF_CUSTOM_SCALE, next)
                .putInt("buttonscale", next)
                .commit();
        LauncherPreferences.PREF_BUTTONSIZE = next;
        return next;
    }

    public static int resetScale(Context ctx) {
        LauncherPreferences.DEFAULT_PREF.edit().remove(PREF_CUSTOM_SCALE).commit();
        String concrete = resolveForVersion(current(), activeProfileVersion());
        int auto = autoButtonScale(ctx, concrete);
        LauncherPreferences.DEFAULT_PREF.edit().putInt("buttonscale", auto).commit();
        LauncherPreferences.PREF_BUTTONSIZE = auto;
        return auto;
    }

    public static void applyCurrent(Context ctx) {
        apply(ctx, current());
    }

    /** Llamado justo al iniciar Minecraft para adaptar el esquema sin pisar PREF = "auto". */
    public static void applyForLaunch(Context ctx, String versionId) {
        ensureLayoutFiles(ctx);
        String savedPref = current();
        String concrete = resolveForVersion(savedPref, versionId != null ? versionId : activeProfileVersion());
        int scale = buttonScale(ctx, concrete);
        File layout = layoutFile(concrete);
        LauncherPreferences.PREF_BUTTONSIZE = scale;
        LauncherPreferences.PREF_DEFAULTCTRL_PATH = layout.getAbsolutePath();
        LauncherPreferences.DEFAULT_PREF.edit()
                .putInt("buttonscale", scale)
                .putString("defaultCtrl", layout.getAbsolutePath())
                .commit();
    }

    public static void apply(Context ctx, String scheme) {
        String normalized = normalizeScheme(scheme);
        ensureLayoutFiles(ctx);
        String concrete = resolveForVersion(normalized, activeProfileVersion());
        int scale = buttonScale(ctx, concrete);
        File layout = layoutFile(concrete);
        LauncherPreferences.PREF_BUTTONSIZE = scale;
        LauncherPreferences.PREF_DEFAULTCTRL_PATH = layout.getAbsolutePath();
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(PREF, normalized)
                .putInt("buttonscale", scale)
                .putString("defaultCtrl", layout.getAbsolutePath())
                .commit();
    }

    /** Restaura el archivo JSON del esquema activo a los valores originales de Paraguacraft. */
    public static void resetActiveLayout(Context ctx) {
        String concrete = resolveForVersion(current(), activeProfileVersion());
        File dest = layoutFile(concrete);
        if (dest.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
        }
        ensureLayoutFiles(ctx);
        applyCurrent(ctx);
    }

    public static void ensureLayoutFiles(Context ctx) {
        File dir = new File(Tools.CTRLMAP_PATH);
        if (!dir.exists() && !dir.mkdirs()) return;

        int savedRev = LauncherPreferences.DEFAULT_PREF.getInt(PREF_LAYOUT_REV, 0);
        boolean upgrade = savedRev < LAYOUT_REV;

        File classic = new File(dir, FILE_CLASSIC);
        File modern = new File(dir, FILE_MODERN);
        File pvp = new File(dir, FILE_PVP);
        File survival = new File(dir, FILE_SURVIVAL);

        String baseJson = readDefaultAsset(ctx);
        if (baseJson != null) {
            writeGeneratedIfMissing(classic, baseJson, CLASSIC, false);
            writeGeneratedIfMissing(modern, baseJson, MODERN, false);
            writeGeneratedIfMissing(pvp, baseJson, PVP, upgrade);
            writeGeneratedIfMissing(survival, baseJson, SURVIVAL, upgrade);
            if (upgrade) {
                LauncherPreferences.DEFAULT_PREF.edit().putInt(PREF_LAYOUT_REV, LAYOUT_REV).apply();
            }
        } else {
            File fallback = new File(Tools.CTRLDEF_FILE);
            copyIfMissing(ctx, classic, fallback);
            copyIfMissing(ctx, modern, fallback);
            copyIfMissing(ctx, pvp, fallback);
            copyIfMissing(ctx, survival, fallback);
        }
    }

    private static void writeGeneratedIfMissing(File dest, String baseJson, String variant, boolean overwrite) {
        if (!overwrite && dest.isFile() && dest.length() > 64) return;
        try {
            String tailored = buildVariantJson(baseJson, variant);
            Tools.write(dest.getAbsolutePath(), tailored);
        } catch (Exception ignored) {
            try {
                Tools.write(dest.getAbsolutePath(), baseJson);
            } catch (Exception ignored2) {}
        }
    }

    private static String buildVariantJson(String baseJson, String variant) throws Exception {
        JSONObject root = new JSONObject(baseJson);
        JSONArray controls = root.optJSONArray("mControlDataList");
        if (controls != null) {
            for (int i = 0; i < controls.length(); i++) {
                JSONObject btn = controls.getJSONObject(i);
                String name = btn.optString("name", "");
                // Translate labels to concise Spanish & tune sizes by preset
                switch (name) {
                    case "Jump":
                        btn.put("name", CLASSIC.equals(variant) ? "Saltar" : "▲");
                        if (PVP.equals(variant) || SURVIVAL.equals(variant) || MODERN.equals(variant)) {
                            btn.put("width", 73.0);
                            btn.put("height", 73.0);
                        }
                        break;
                    case "Off\nHand":
                        if (PVP.equals(variant)) {
                            // 1.7 / 1.8 has no Offhand: turn this ergonomic thumb button into F5 (Third Person)!
                            btn.put("name", "F5");
                            JSONArray kc = new JSONArray();
                            kc.put(294);
                            kc.put(0);
                            kc.put(0);
                            kc.put(0);
                            btn.put("keycodes", kc);
                        } else {
                            btn.put("name", CLASSIC.equals(variant) ? "2ª\nMano" : "F");
                        }
                        break;
                    case "Menu":
                        btn.put("name", "Menú");
                        break;
                    case "Crouch":
                        btn.put("name", CLASSIC.equals(variant) ? "Agach." : "Shift");
                        break;
                    case "Right\nClick":
                        btn.put("name", PVP.equals(variant) ? "Usar\nBlock" : "Usar");
                        if (PVP.equals(variant)) {
                            btn.put("width", 76.0);
                            btn.put("height", 76.0);
                            btn.put("strokeColor", 0x662ECC71);
                            btn.put("strokeWidth", 1.2);
                        } else if (SURVIVAL.equals(variant) || MODERN.equals(variant)) {
                            btn.put("width", 72.5);
                            btn.put("height", 72.5);
                        }
                        break;
                    case "Left\nClick":
                        btn.put("name", "Atacar");
                        if (PVP.equals(variant)) {
                            btn.put("width", 78.0);
                            btn.put("height", 78.0);
                            btn.put("strokeColor", 0x882ECC71);
                            btn.put("strokeWidth", 1.5);
                        } else if (SURVIVAL.equals(variant) || MODERN.equals(variant)) {
                            btn.put("width", 73.0);
                            btn.put("height", 73.0);
                        }
                        break;
                    case "Middle\nClick":
                        btn.put("name", "Pick");
                        break;
                    case "Sprint":
                        btn.put("name", "Correr");
                        break;
                    case "Hide\nGUI":
                        btn.put("name", "HUD");
                        break;
                    case "Pause":
                        btn.put("name", "Pausa");
                        break;
                    case "Inventory":
                        btn.put("name", "Inv.");
                        break;
                    case "Exit\nMenu":
                        btn.put("name", "Cerrar");
                        break;
                }
            }

            if (PVP.equals(variant)) {
                controls.put(quickTopLeftButton("Soltar", 81));
            } else if (SURVIVAL.equals(variant)) {
                controls.put(quickTopLeftButton("Chat", 84));
            }
        }

        JSONArray drawers = root.optJSONArray("mDrawerDataList");
        if (drawers != null && drawers.length() > 0) {
            JSONObject firstDrawer = drawers.getJSONObject(0);
            JSONObject props = firstDrawer.optJSONObject("properties");
            if (props != null) props.put("name", "Más");
        }
        return root.toString();
    }

    private static JSONObject quickTopLeftButton(String label, int keycode) throws Exception {
        JSONObject btn = new JSONObject();
        btn.put("bgColor", 1291845632);
        btn.put("cornerRadius", 100);
        btn.put("displayInGame", true);
        btn.put("displayInMenu", false);
        btn.put("dynamicX", "0 * ${screen_width} + ${margin}");
        btn.put("dynamicY", "0 * ${screen_height} + (px(46.0) / 100.0 * ${preferred_scale}) + ${margin}");
        btn.put("width", 78.0);
        btn.put("height", 38.0);
        btn.put("isSwipeable", false);
        btn.put("isToggle", false);
        JSONArray kc = new JSONArray();
        kc.put(keycode);
        kc.put(0);
        kc.put(0);
        kc.put(0);
        btn.put("keycodes", kc);
        btn.put("name", label);
        btn.put("opacity", 0.95);
        btn.put("passThruEnabled", false);
        btn.put("strokeColor", -1);
        btn.put("strokeWidth", 0);
        return btn;
    }

    private static String readDefaultAsset(Context ctx) {
        try (InputStream is = ctx.getAssets().open("default.json")) {
            byte[] buf = new byte[is.available()];
            int read = 0;
            while (read < buf.length) {
                int r = is.read(buf, read, buf.length - read);
                if (r <= 0) break;
                read += r;
            }
            return new String(buf, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String activeProfileVersion() {
        try {
            String hint = SkinHelper.instanceMcHint();
            if (hint != null && !hint.isEmpty()) return hint;
            LauncherProfiles.load();
            String pid = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            if (pid != null && LauncherProfiles.mainProfileJson != null && LauncherProfiles.mainProfileJson.profiles != null) {
                MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(pid);
                if (p != null && p.lastVersionId != null) return p.lastVersionId;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void copyIfMissing(Context ctx, File dest, File fallback) {
        if (dest.isFile()) return;
        try {
            if (fallback != null && fallback.isFile()) {
                SkinHelper.copyFile(fallback, dest);
                return;
            }
            Tools.copyAssetFile(ctx, "default.json", Tools.CTRLMAP_PATH, dest.getName(), false);
        } catch (Exception ignored) {}
    }
}
