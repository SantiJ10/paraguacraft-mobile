package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.paraguacraft.mobile.HorizonFragment;
import net.paraguacraft.mobile.OptimizedFragment;

public class ProfileTypeSelectFragment extends Fragment {
    public static final String TAG = "ProfileTypeSelectFragment";
    public ProfileTypeSelectFragment() {
        super(R.layout.fragment_profile_type);
    }
    public ProfileTypeSelectFragment(int layout) {
        super(layout);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.vanilla_profile).setOnClickListener(v -> Tools.swapFragment(requireActivity(), ProfileEditorFragment.class,
                ProfileEditorFragment.TAG, new Bundle(1)));

        view.findViewById(R.id.optifine_profile).setOnClickListener(v ->
                tryInstall(OptiFineInstallFragment.class, OptiFineInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_fabric).setOnClickListener((v)->
                tryInstall(FabricInstallFragment.class, FabricInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_forge).setOnClickListener((v)->
                tryInstall(ForgeInstallFragment.class, ForgeInstallFragment.TAG));

        View neoforge = view.findViewById(R.id.modded_profile_neoforge);
        if (neoforge != null) {
            neoforge.setOnClickListener(v ->
                    tryInstall(NeoForgeInstallFragment.class, NeoForgeInstallFragment.TAG));
        }

        View quilt = view.findViewById(R.id.modded_profile_quilt);
        if (quilt != null) {
            quilt.setOnClickListener(v ->
                    tryInstall(QuiltInstallFragment.class, QuiltInstallFragment.TAG));
        }

        view.findViewById(R.id.modded_profile_modpack).setOnClickListener((v)->
                tryInstall(ModpackCreateFragment.class, ModpackCreateFragment.TAG));
        view.findViewById(R.id.pg_optimized_profile).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), OptimizedFragment.class,
                        OptimizedFragment.TAG, null));

        View horizon = view.findViewById(R.id.pg_horizon_profile);
        if (horizon != null) {
            horizon.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(), HorizonFragment.class,
                            HorizonFragment.TAG, null));
        }
    }

    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
    }
}
