package net.kdt.pojavlaunch.prefs.screens;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.paraguacraft.mobile.StorageCleaner;

public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_misc);
        Preference driverPreference = requirePreference("zinkPreferSystemDriver");
        PackageManager packageManager = driverPreference.getContext().getPackageManager();
        boolean supportsTurnip = Tools.checkVulkanSupport(packageManager) && GLInfoUtils.getGlInfo().isAdreno();
        driverPreference.setVisible(supportsTurnip);

        Preference cleaner = requirePreference("pgStorageCleaner");
        cleaner.setOnPreferenceClickListener(p -> {
            runStorageCleaner();
            return true;
        });
    }

    private void runStorageCleaner() {
        Toast.makeText(requireContext(), "Buscando archivos para borrar…", Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            StorageCleaner.Result result = StorageCleaner.scan();
            Tools.runOnUiThread(() -> {
                if (!isAdded()) return;
                if (result.files.isEmpty()) {
                    Toast.makeText(requireContext(), "No hay nada para borrar.", Toast.LENGTH_LONG).show();
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("Liberar espacio")
                        .setMessage("Se pueden borrar " + result.files.size() + " archivos (" + result.humanSize()
                                + ").\n\nSon logs viejos, crash-reports, descargas a medias e instaladores en caché. "
                                + "Tus mundos, mods y capturas no se tocan.")
                        .setPositiveButton("Borrar", (d, w) -> PojavApplication.sExecutorService.execute(() -> {
                            long freed = StorageCleaner.clean(result);
                            Tools.runOnUiThread(() -> {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(),
                                            "Liberados " + StorageCleaner.formatSize(freed), Toast.LENGTH_LONG).show();
                                }
                            });
                        }))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }
}
