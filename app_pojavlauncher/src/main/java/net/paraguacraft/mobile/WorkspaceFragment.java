package net.paraguacraft.mobile;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

/**
 * Biblioteca / Espacio: importar, tienda, skins, instancias, fondos, controles, contenido instalado.
 */
public class WorkspaceFragment extends Fragment {
    public static final String TAG = "PG_WORKSPACE";

    public WorkspaceFragment() {
        super(R.layout.fragment_pg_optimized);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rebuild(view.findViewById(R.id.pg_optimized_list));
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v != null) rebuild(v.findViewById(R.id.pg_optimized_list));
    }

    /** Qué se dibujó la última vez, para no repetir el trabajo si nada cambió. */
    private String mBuiltSignature;

    private void rebuild(LinearLayout list) {
        if (list == null || !isAdded()) return;
        float d = getResources().getDisplayMetrics().density;
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        int cols = tablet ? 3 : 2;
        FragmentActivity act = requireActivity();
        String instName = PgShell.instanceLabel();

        boolean saverOn = BatterySaver.isEnabled();
        int saverFps = BatterySaver.fpsCap();

        // Los tiles son fijos: lo único que varía es la instancia activa o el estado de ahorro.
        String signature = instName + "|" + cols + "|" + saverOn + "|" + saverFps;
        if (list.getChildCount() > 0 && signature.equals(mBuiltSignature)) return;
        mBuiltSignature = signature;
        list.removeAllViews();

        LinearLayout banner = glass(d);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding((int) (16 * d), (int) (14 * d), (int) (16 * d), (int) (14 * d));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = (int) (12 * d);
        banner.setLayoutParams(blp);
        ImageView bIcon = new ImageView(requireContext());
        int bsz = (int) (36 * d);
        LinearLayout.LayoutParams bilp = new LinearLayout.LayoutParams(bsz, bsz);
        bilp.setMarginEnd((int) (12 * d));
        bIcon.setLayoutParams(bilp);
        bIcon.setImageResource(R.drawable.ic_menu_store);
        banner.addView(bIcon);
        LinearLayout bcol = new LinearLayout(requireContext());
        bcol.setOrientation(LinearLayout.VERTICAL);
        TextView bt = new TextView(requireContext());
        bt.setText("Biblioteca");
        bt.setTextColor(0xFFFFFFFF);
        bt.setTextSize(18);
        bt.setTypeface(Typeface.DEFAULT_BOLD);
        TextView bs = new TextView(requireContext());
        bs.setText("Importá modpacks, descargá contenido y revisá lo instalado en la instancia activa.");
        bs.setTextColor(0xFFB8C4BC);
        bs.setTextSize(12);
        bcol.addView(bt);
        bcol.addView(bs);
        banner.addView(bcol);
        banner.setBackgroundResource(R.drawable.pg_glass_card_selected);
        list.addView(banner);

        LinearLayout inst = glass(d);
        inst.setOrientation(LinearLayout.VERTICAL);
        inst.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));
        LinearLayout.LayoutParams ilp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp2.bottomMargin = (int) (12 * d);
        inst.setLayoutParams(ilp2);
        TextView instTitle = new TextView(requireContext());
        instTitle.setText("Instancia: " + instName);
        instTitle.setTextColor(0xFF8FD4A8);
        instTitle.setTextSize(14);
        instTitle.setTypeface(Typeface.DEFAULT_BOLD);
        TextView instSub = new TextView(requireContext());
        instSub.setText(versionLine() + " · tocá para cambiar");
        instSub.setTextColor(0xFFB8C4BC);
        instSub.setTextSize(12);
        inst.addView(instTitle);
        inst.addView(instSub);
        inst.setOnClickListener(v ->
                Tools.swapFragment(act, InstancesFragment.class, InstancesFragment.TAG, null));
        list.addView(inst);

        Item[] items = {
                new Item("Importar modpack", "Abrí un .zip o .mrpack y creá una instancia lista.", R.drawable.ic_menu_install_jar,
                        v -> PgShell.importModpack(act)),
                new Item("Buscar modpacks", "Modrinth y CurseForge, instalación lista para jugar.", R.drawable.ic_menu_store,
                        v -> PgShell.openStore(act, "modpack", true)),
                new Item("Buscar mods", "Instalá .jar en la instancia que elijas.", R.drawable.ic_menu_store,
                        v -> PgShell.openStore(act, "mod", false)),
                new Item("Buscar paquetes de recursos", "Texturas y recursos para tus perfiles.", R.drawable.ic_menu_news,
                        v -> PgShell.openStore(act, "resourcepack", false)),
                new Item("Buscar shaders", "Paquetes de shaders en la instancia que elijas.", R.drawable.ic_menu_optimized,
                        v -> PgShell.openStore(act, "shader", false)),
                new Item("Buscar datapacks", "Datapacks para la instancia activa.", R.drawable.ic_menu_install_jar,
                        v -> PgShell.openStore(act, "datapack", false)),
                new Item("Buscar mundos", "Mundos jugables en la instancia que elijas.", R.drawable.ic_folder,
                        v -> PgShell.openStore(act, "world", false)),
                new Item("Skins de Paraguacraft", "Subí, previsualizá y cambiá skins y capas.", R.drawable.ic_menu_skins,
                        v -> Tools.swapFragment(act, ParaguaSkinsFragment.class, ParaguaSkinsFragment.TAG, null)),
                new Item("Instancias y mundos", "Duplicá, mové o repará cada instalación por separado.", R.drawable.ic_folder,
                        v -> Tools.swapFragment(act, InstancesFragment.class, InstancesFragment.TAG, null)),
                new Item("Descargar Minecraft", "Vanilla, Forge, NeoForge, Fabric, Quilt, OptiFine y Optimized.", R.drawable.ic_menu_install_jar,
                        v -> Tools.swapFragment(act, ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null)),
                new Item("Horizon · Motor Rust", "Motor nativo experimental 26.3 sin Java (cuenta oficial).", R.drawable.ic_menu_optimized,
                        v -> Tools.swapFragment(act, HorizonFragment.class, HorizonFragment.TAG, null)),
                new Item("Limpiar almacenamiento", "Borrá caché, logs viejos y crash-reports sin tocar mundos ni mods.", R.drawable.ic_folder,
                        v -> showCleanerDialog(act)),
                new Item("Ahorro de batería", saverOn ? "Activo · tope " + saverFps + " FPS y control térmico." : "Desactivado · tocá para limitar FPS y temperatura.", R.drawable.ic_menu_settings,
                        v -> showBatterySaverDialog(act, list)),
                new Item("Archivos", "Instancias, mundos, mods y registros sin salir.", R.drawable.ic_folder,
                        v -> Tools.openPath(act, OptimizedInstaller.activeGameDir(), false)),
                new Item("Paraguabot", "Ayuda y atajos. Mantener para compartir logs.", R.drawable.ic_menu_paraguabot,
                        v -> ParaguaBot.show(act)),
        };

        LinearLayout row = null;
        int col = 0;
        for (Item item : items) {
            if (row == null) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = (int) (8 * d);
                row.setLayoutParams(rlp);
            }
            View card = actionCard(item, d);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            clp.setMarginEnd((int) (8 * d));
            card.setLayoutParams(clp);
            row.addView(card);
            col++;
            if (col == cols) {
                list.addView(row);
                row = null;
                col = 0;
            }
        }
        if (row != null) {
            while (col < cols) {
                View spacer = new View(requireContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                row.addView(spacer);
                col++;
            }
            list.addView(row);
        }

        TextView installedTitle = new TextView(requireContext());
        installedTitle.setText("Contenido instalado");
        installedTitle.setTextColor(0xFFFFFFFF);
        installedTitle.setTextSize(16);
        installedTitle.setTypeface(Typeface.DEFAULT_BOLD);
        installedTitle.setPadding(0, (int) (16 * d), 0, (int) (4 * d));
        list.addView(installedTitle);
        TextView installedHint = new TextView(requireContext());
        installedHint.setText("Tocá una sección para abrir su carpeta en el almacenamiento.");
        installedHint.setTextColor(0xFFB8C4BC);
        installedHint.setTextSize(12);
        installedHint.setPadding(0, 0, 0, (int) (8 * d));
        list.addView(installedHint);

        File dir = OptimizedInstaller.activeGameDir();
        addContentRow(list, d, "Instancia: " + instName, versionLine(),
                v -> Tools.swapFragment(act, InstancesFragment.class, InstancesFragment.TAG, null));
        addContentRow(list, d, "Mods", countLabel(dir, "mods"),
                v -> openOrToast(new File(dir, "mods")));
        addContentRow(list, d, "Paquetes de recursos", countLabel(dir, "resourcepacks"),
                v -> openOrToast(new File(dir, "resourcepacks")));
        addContentRow(list, d, "Shaders", countLabel(dir, "shaderpacks"),
                v -> openOrToast(new File(dir, "shaderpacks")));
        addContentRow(list, d, "Datapacks", countLabel(dir, "datapacks"),
                v -> openOrToast(new File(dir, "datapacks")));
        addContentRow(list, d, "Mundos", countLabel(dir, "saves"),
                v -> openOrToast(new File(dir, "saves")));
    }

    private void addContentRow(LinearLayout list, float d, String title, String count, View.OnClickListener click) {
        LinearLayout row = glass(d);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * d);
        row.setLayoutParams(lp);
        LinearLayout head = new LinearLayout(requireContext());
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = new TextView(requireContext());
        t.setText(title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView c = new TextView(requireContext());
        c.setText(count);
        c.setTextColor(0xFFB8C4BC);
        c.setTextSize(12);
        head.addView(t);
        head.addView(c);
        row.addView(head);
        row.setOnClickListener(click);
        list.addView(row);
    }

    private View actionCard(Item item, float d) {
        LinearLayout card = glass(d);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (12 * d), (int) (12 * d), (int) (12 * d), (int) (12 * d));
        card.setMinimumHeight((int) (108 * d));
        ImageView icon = new ImageView(requireContext());
        int sz = (int) (28 * d);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
        ilp.bottomMargin = (int) (8 * d);
        icon.setLayoutParams(ilp);
        icon.setImageResource(item.icon);
        TextView title = new TextView(requireContext());
        title.setText(item.title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub = new TextView(requireContext());
        sub.setText(item.sub);
        sub.setTextColor(0xFFB8C4BC);
        sub.setTextSize(11);
        sub.setPadding(0, (int) (4 * d), 0, 0);
        card.addView(icon);
        card.addView(title);
        card.addView(sub);
        card.setOnClickListener(item.click);
        if (item.title.equals("Controles")) {
            card.setOnLongClickListener(v -> {
                Tools.swapFragment(requireActivity(),
                        net.kdt.pojavlaunch.fragments.GamepadMapperFragment.class,
                        net.kdt.pojavlaunch.fragments.GamepadMapperFragment.TAG, null);
                return true;
            });
        }
        return card;
    }

    private LinearLayout glass(float d) {
        LinearLayout l = new LinearLayout(requireContext());
        l.setBackgroundResource(R.drawable.pg_glass_card);
        return l;
    }

    private void showCleanerDialog(FragmentActivity act) {
        Toast.makeText(act, "Analizando archivos temporales…", Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            StorageCleaner.Result res = StorageCleaner.scan();
            if (!isAdded()) return;
            act.runOnUiThread(() -> {
                if (res.files.isEmpty()) {
                    new AlertDialog.Builder(act)
                            .setTitle("Almacenamiento limpio")
                            .setMessage("No hay instaladores en caché, descargas incompletas ni logs viejos para borrar.")
                            .setPositiveButton("Listo", null)
                            .show();
                    return;
                }
                new AlertDialog.Builder(act)
                        .setTitle("Limpiar almacenamiento")
                        .setMessage("Se encontraron " + res.files.size() + " archivos temporales (" + res.humanSize() + ").\n\n"
                                + "• Incluye logs rotados, crash-reports, instaladores en caché y descargas .part.\n"
                                + "• Tus mundos, mods, texturas, shaders, capturas y ajustes quedan intactos.")
                        .setPositiveButton("Liberar " + res.humanSize(), (d, w) ->
                                PojavApplication.sExecutorService.execute(() -> {
                                    long freed = StorageCleaner.clean(res);
                                    if (isAdded()) {
                                        act.runOnUiThread(() -> Toast.makeText(act,
                                                "Liberados " + StorageCleaner.formatSize(freed),
                                                Toast.LENGTH_LONG).show());
                                    }
                                }))
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        });
    }

    private void showBatterySaverDialog(FragmentActivity act, LinearLayout list) {
        String[] labels = {
                "Desactivado (rendimiento normal)",
                "Activado · tope 30 FPS (máximo ahorro y menos calor)",
                "Activado · tope 45 FPS (equilibrado recomendado)",
                "Activado · tope 60 FPS (fluido sin calentar de más)"
        };
        int curIdx = 0;
        if (BatterySaver.isEnabled()) {
            int cap = BatterySaver.fpsCap();
            if (cap <= 30) curIdx = 1;
            else if (cap <= 45) curIdx = 2;
            else curIdx = 3;
        }
        new AlertDialog.Builder(act)
                .setTitle("Modo ahorro de batería y temperatura")
                .setSingleChoiceItems(labels, curIdx, (d, which) -> {
                    if (which == 0) BatterySaver.setConfig(false, 45);
                    else if (which == 1) BatterySaver.setConfig(true, 30);
                    else if (which == 2) BatterySaver.setConfig(true, 45);
                    else BatterySaver.setConfig(true, 60);
                    d.dismiss();
                    mBuiltSignature = null;
                    rebuild(list);
                    Toast.makeText(act, labels[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void openOrToast(File dir) {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        Tools.openPath(requireContext(), dir, false);
    }

    private static String countLabel(File root, String sub) {
        File f = new File(root, sub);
        File[] files = f.listFiles((d, n) -> !n.startsWith("."));
        int n = files == null ? 0 : files.length;
        return n + (n == 1 ? " archivo" : " archivos");
    }

    private static String versionLine() {
        String pid = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        LauncherProfiles.load();
        if (pid == null || LauncherProfiles.mainProfileJson == null) return "Elegí una instancia";
        MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(pid);
        if (p == null) return "Elegí una instancia";
        return p.lastVersionId != null ? p.lastVersionId : "sin versión";
    }

    private static final class Item {
        final String title;
        final String sub;
        final int icon;
        final View.OnClickListener click;

        Item(String title, String sub, int icon, View.OnClickListener click) {
            this.title = title;
            this.sub = sub;
            this.icon = icon;
            this.click = click;
        }
    }
}
