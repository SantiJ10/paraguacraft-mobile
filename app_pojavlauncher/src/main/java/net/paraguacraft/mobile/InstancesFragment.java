package net.paraguacraft.mobile;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lista de instancias (Optimized, modpacks, vanilla) con icono, nombre, usar / jugar / mods / renombrar / borrar.
 */
public class InstancesFragment extends Fragment {
    public static final String TAG = "PG_INSTANCES";

    public InstancesFragment() {
        super(R.layout.fragment_pg_optimized);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        LinearLayout list = view.findViewById(R.id.pg_optimized_list);
        rebuild(list);
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v != null) rebuild(v.findViewById(R.id.pg_optimized_list));
    }

    /** Qué se dibujó la última vez, para no repetir el trabajo si nada cambió. */
    private String mBuiltSignature;

    /**
     * Identifica el contenido visible de la lista: instancias, sus nombres y
     * cuál está activa. Todas las mutaciones del fragment (usar, renombrar,
     * borrar) cambian esta firma.
     */
    private String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, ""));
        if (LauncherProfiles.mainProfileJson != null && LauncherProfiles.mainProfileJson.profiles != null) {
            List<String> keys = new ArrayList<>(LauncherProfiles.mainProfileJson.profiles.keySet());
            java.util.Collections.sort(keys);
            for (String k : keys) {
                MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(k);
                sb.append('|').append(k).append('=').append(p == null ? "" : p.name);
            }
        }
        return sb.toString();
    }

    private void rebuild(LinearLayout list) {
        if (list == null) return;
        LauncherProfiles.load();
        // Volver de otra pantalla recreaba todas las tarjetas aunque nada
        // hubiera cambiado: decenas de vistas nuevas y un layout completo en
        // cada onResume.
        String signature = signature();
        if (list.getChildCount() > 0 && signature.equals(mBuiltSignature)) return;
        mBuiltSignature = signature;

        list.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        TextView header = new TextView(requireContext());
        header.setTextColor(0xFFFFFFFF);
        header.setPadding(0, 0, 0, (int) (12 * d));
        int n = LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null
                ? 0 : LauncherProfiles.mainProfileJson.profiles.size();
        header.setText("Instancias · " + n + "\nTocá una para usarla, ver mods, renombrar o borrar.");
        list.addView(header);

        if (n == 0) return;
        String selected = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        List<Map.Entry<String, MinecraftProfile>> entries = new ArrayList<>(
                LauncherProfiles.mainProfileJson.profiles.entrySet());
        entries.sort((a, b) -> {
            String na = a.getValue() != null && a.getValue().name != null ? a.getValue().name : a.getKey();
            String nb = b.getValue() != null && b.getValue().name != null ? b.getValue().name : b.getKey();
            return na.compareToIgnoreCase(nb);
        });
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        LinearLayout row = null;
        int col = 0;
        for (Map.Entry<String, MinecraftProfile> e : entries) {
            if (e.getValue() == null) continue;
            View card = card(e.getKey(), e.getValue(), selected.equals(e.getKey()), d);
            if (!tablet) {
                list.addView(card);
                continue;
            }
            if (row == null) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = (int) (8 * d);
                row.setLayoutParams(rlp);
            }
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMarginEnd((int) (6 * d));
            card.setLayoutParams(clp);
            row.addView(card);
            col++;
            if (col == 2) {
                list.addView(row);
                row = null;
                col = 0;
            }
        }
        if (row != null) list.addView(row);
    }

    private View card(String key, MinecraftProfile p, boolean selected, float d) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding((int) (8 * d), (int) (8 * d), (int) (8 * d), (int) (8 * d));
        row.setBackgroundColor(selected ? 0x33FFFFFF : 0x22FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * d);
        row.setLayoutParams(lp);

        ImageView icon = new ImageView(requireContext());
        int sz = (int) (56 * d);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
        ilp.rightMargin = (int) (12 * d);
        icon.setLayoutParams(ilp);
        icon.setImageDrawable(ProfileIconCache.fetchIcon(getResources(), key, p.icon));
        row.addView(icon);

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = new TextView(requireContext());
        String name = (p.name != null && !p.name.isEmpty() && !"New".equalsIgnoreCase(p.name)) ? p.name : key;
        title.setText(name + (selected ? "  ·  activa" : ""));
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        TextView sub = new TextView(requireContext());
        File dir = Tools.getGameDirPath(p);
        int mods = countContent(dir);
        sub.setText((p.lastVersionId != null ? p.lastVersionId : "?")
                + "\n" + dir.getName() + " · " + mods + " archivos de contenido");
        sub.setTextColor(0xFFBBBBBB);
        sub.setTextSize(12);
        col.addView(title);
        col.addView(sub);
        row.addView(col);
        row.setOnClickListener(v -> showActions(key, p));
        return row;
    }

    private static int countContent(File dir) {
        int n = 0;
        for (String sub : new String[]{"mods", "resourcepacks", "shaderpacks", "datapacks"}) {
            File f = new File(dir, sub);
            File[] files = f.listFiles();
            if (files != null) n += files.length;
        }
        return n;
    }

    private void showActions(String key, MinecraftProfile p) {
        String name = p.name != null ? p.name : key;
        File dir = Tools.getGameDirPath(p);
        CharSequence[] items = {
                "Usar esta instancia",
                "Jugar",
                "Contenido (mods / packs)",
                "Renombrar",
                "Abrir carpeta",
                "Eliminar"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(name)
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0:
                            select(key);
                            Toast.makeText(requireContext(), "Instancia activa.", Toast.LENGTH_SHORT).show();
                            rebuild(requireView().findViewById(R.id.pg_optimized_list));
                            break;
                        case 1:
                            select(key);
                            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                            break;
                        case 2:
                            showContent(dir);
                            break;
                        case 3:
                            rename(key, p);
                            break;
                        case 4:
                            Tools.openPath(requireContext(), dir, false);
                            break;
                        case 5:
                            confirmDelete(key, p, dir);
                            break;
                    }
                })
                .show();
    }

    private void select(String key) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, key)
                .apply();
    }

    private void rename(String key, MinecraftProfile p) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(p.name);
        new AlertDialog.Builder(requireContext())
                .setTitle("Renombrar")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (n.isEmpty()) return;
                    LauncherProfiles.load();
                    MinecraftProfile cur = LauncherProfiles.mainProfileJson.profiles.get(key);
                    if (cur != null) {
                        cur.name = n;
                        LauncherProfiles.write();
                    }
                    rebuild(requireView().findViewById(R.id.pg_optimized_list));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String key, MinecraftProfile p, File dir) {
        boolean extraDir = p.gameDir != null && (p.gameDir.contains("optimized-") || p.gameDir.contains("custom_instances"));
        new AlertDialog.Builder(requireContext())
                .setTitle("Eliminar " + p.name)
                .setMessage(extraDir
                        ? "Se borra el perfil y la carpeta de la instancia. No se puede deshacer."
                        : "Se borra solo el perfil. Los archivos de Minecraft quedan.")
                .setPositiveButton("Eliminar", (d, w) -> {
                    LauncherProfiles.load();
                    LauncherProfiles.mainProfileJson.profiles.remove(key);
                    LauncherProfiles.write();
                    String selected = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
                    if (key.equals(selected) && !LauncherProfiles.mainProfileJson.profiles.isEmpty()) {
                        String first = LauncherProfiles.mainProfileJson.profiles.keySet().iterator().next();
                        select(first);
                    }
                    if (extraDir && dir.isDirectory() && !dir.equals(new File(Tools.DIR_GAME_NEW))) {
                        deleteTree(dir);
                    }
                    ProfileIconCache.dropIcon(key);
                    rebuild(requireView().findViewById(R.id.pg_optimized_list));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showContent(File dir) {
        List<File> files = new ArrayList<>();
        for (String sub : new String[]{"mods", "resourcepacks", "shaderpacks", "datapacks"}) {
            File folder = new File(dir, sub);
            File[] list = folder.listFiles((d, n) -> !n.startsWith("."));
            if (list != null) {
                for (File f : list) {
                    if (f.isFile()) files.add(f);
                }
            }
        }
        if (files.isEmpty()) {
            Toast.makeText(requireContext(), "No hay mods ni packs en esta instancia.", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(requireContext(), "Leyendo mods…", Toast.LENGTH_SHORT).show();
        net.kdt.pojavlaunch.PojavApplication.sExecutorService.execute(() -> {
            List<ModMeta> metas = new ArrayList<>();
            for (File f : files) metas.add(ModMeta.fromFile(f));
            Tools.runOnUiThread(() -> showContentUi(dir, files, metas));
        });
    }

    private void showContentUi(File dir, List<File> files, List<ModMeta> metas) {
        if (!isAdded()) return;
        float d = getResources().getDisplayMetrics().density;
        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (8 * d), (int) (8 * d), (int) (8 * d), (int) (8 * d));
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            ModMeta meta = metas.get(i);
            boolean off = f.getName().endsWith(".disabled");
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (6 * d));
            row.setBackgroundColor(off ? 0x22AA4444 : 0x2222AA66);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = (int) (6 * d);
            row.setLayoutParams(rlp);

            ImageView icon = new ImageView(requireContext());
            int sz = (int) (40 * d);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
            ilp.setMarginEnd((int) (10 * d));
            icon.setLayoutParams(ilp);
            if (meta.icon != null) icon.setImageBitmap(meta.icon);
            else icon.setImageResource(R.drawable.ic_menu_store);
            row.addView(icon);

            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView name = new TextView(requireContext());
            name.setText((off ? "[off] " : "") + meta.title);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(15);
            TextView sub = new TextView(requireContext());
            sub.setText(meta.subtitle + " · " + displayName(f));
            sub.setTextColor(0xFFBBBBBB);
            sub.setTextSize(11);
            col.addView(name);
            col.addView(sub);
            row.addView(col);
            row.setOnClickListener(v -> {
                toggleDisabled(f);
                showContent(dir);
            });
            box.addView(row);
        }
        scroll.addView(box);
        new AlertDialog.Builder(requireContext())
                .setTitle("Contenido (tocá para on/off)")
                .setView(scroll)
                .setNeutralButton("Borrar uno…", (dlg, w) -> {
                    CharSequence[] labels = new CharSequence[files.size()];
                    for (int i = 0; i < files.size(); i++) labels[i] = metas.get(i).title;
                    pickDelete(files, labels);
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void pickDelete(List<File> files, CharSequence[] labels) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Eliminar archivo")
                .setItems(labels, (d, w) -> {
                    files.get(w).delete();
                    Toast.makeText(requireContext(), "Borrado.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String displayName(File f) {
        String n = f.getName();
        if (n.endsWith(".disabled")) n = n.substring(0, n.length() - ".disabled".length());
        return n;
    }

    private static void toggleDisabled(File f) {
        String n = f.getName();
        File dest;
        if (n.endsWith(".disabled")) {
            dest = new File(f.getParentFile(), n.substring(0, n.length() - ".disabled".length()));
        } else {
            dest = new File(f.getParentFile(), n + ".disabled");
        }
        //noinspection ResultOfMethodCallIgnored
        f.renameTo(dest);
    }

    private static void deleteTree(File f) {
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) for (File x : c) deleteTree(x);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
