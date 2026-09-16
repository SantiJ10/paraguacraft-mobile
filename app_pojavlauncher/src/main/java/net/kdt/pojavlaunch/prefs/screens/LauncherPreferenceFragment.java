package net.kdt.pojavlaunch.prefs.screens;


import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setBackgroundColor(getResources().getColor(R.color.background_app));
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();
        setupParaguacraftPreferences();
    }

    private void setupParaguacraftPreferences() {
        Preference wallpaper = findPreference("pg_wallpaper_setting");
        Preference graphics = findPreference("pg_graphics_setting");
        Preference renderer = findPreference("pg_renderer_setting");
        Preference controls = findPreference("pg_controls_setting");
        if (wallpaper != null) {
            wallpaper.setOnPreferenceClickListener(p -> {
                Tools.swapFragment(requireActivity(),
                        net.paraguacraft.mobile.BackgroundSettingsFragment.class,
                        net.paraguacraft.mobile.BackgroundSettingsFragment.TAG, null);
                return true;
            });
        }
        if (graphics != null) {
            graphics.setOnPreferenceClickListener(p -> {
                net.paraguacraft.mobile.PgShell.showGraphicsProfile(requireActivity());
                return true;
            });
        }
        if (renderer != null) {
            renderer.setOnPreferenceClickListener(p -> {
                net.paraguacraft.mobile.PgShell.showRendererPicker(requireActivity());
                return true;
            });
        }
        if (controls != null) {
            controls.setOnPreferenceClickListener(p -> {
                Tools.swapFragment(requireActivity(),
                        net.paraguacraft.mobile.ControlHubFragment.class,
                        net.paraguacraft.mobile.ControlHubFragment.TAG, null);
                return true;
            });
        }
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Preference mMicrophonePermissionPreference = requirePreference("microphone_permission_request");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            LauncherActivity launcherActivity = (LauncherActivity)activity;
            mRequestNotificationPermissionPreference.setVisible(!launcherActivity.checkForNotificationPermission());
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                launcherActivity.askForNotificationPermission(()->mRequestNotificationPermissionPreference.setVisible(false));
                return true;
            });
            mMicrophonePermissionPreference.setVisible(!launcherActivity.checkForMicrophonePermission());
            mMicrophonePermissionPreference.setOnPreferenceClickListener(preference -> {
                launcherActivity.askForMicrophonePermission(()->mMicrophonePermissionPreference.setVisible(false));
                return true;
            });
        }else{
            mRequestNotificationPermissionPreference.setVisible(false);
            mMicrophonePermissionPreference.setVisible(false);
        }
        Preference about = findPreference("paraguacraft_about");
        if (about != null) {
            about.setOnPreferenceClickListener(p -> {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Paraguacraft")
                        .setMessage("Paraguacraft Mobile está basado en Amethyst (sucesor de PojavLauncher / Boardwalk).\n\n" +
                                "Licencia: GNU LGPL-3.0.\n" +
                                "Créditos: AngelAuraMC / Amethyst, PojavLauncherTeam, Boardwalk.\n" +
                                "No afiliado a Mojang ni Microsoft.\n\n" +
                                "Cuentas: Microsoft (premium) y cuenta local (nick), igual que el launcher de PC.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if(preference != null) return preference;
        throw new IllegalStateException("Preference "+key+" is null");
    }
    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if(preferenceClass.isInstance(preference)) return (T)preference;
        throw new IllegalStateException("Preference "+key+" is not an instance of "+preferenceClass.getSimpleName());
    }
}
