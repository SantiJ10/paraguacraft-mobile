package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import net.paraguacraft.mobile.ControlHubFragment;
import net.paraguacraft.mobile.ControlScheme;
import net.paraguacraft.mobile.ParaguaSkinsFragment;
import net.paraguacraft.mobile.PgShell;
import net.paraguacraft.mobile.Wallpaper;
import net.paraguacraft.mobile.WorkspaceFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Home al estilo Battly Mobile 2: noticias propias (no wiki de Minecraft),
 * Versiones + Jugar. Los atajos viven en la cabecera de la activity.
 */
public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";
    private static final String NEWS_URL =
            "https://api.github.com/repos/SantiJ10/Paraguacraft/releases?per_page=8";

    private mcVersionSpinner mVersionSpinner;
    private NewsAdapter mNewsAdapter;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        View library = requireActivity().findViewById(R.id.pg_hub_library);
        View controls = requireActivity().findViewById(R.id.pg_hub_controls);
        View skins = requireActivity().findViewById(R.id.pg_hub_skins);
        if (library != null) library.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), WorkspaceFragment.class, WorkspaceFragment.TAG, null));
        if (controls != null) {
            controls.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(), ControlHubFragment.class, ControlHubFragment.TAG, null));
            controls.setOnLongClickListener(v -> {
                Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
                return true;
            });
        }
        if (skins != null) skins.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ParaguaSkinsFragment.class, ParaguaSkinsFragment.TAG, null));

        view.findViewById(R.id.pg_versions_button).setOnClickListener(v -> {
            if (mVersionSpinner != null) mVersionSpinner.performClick();
        });
        view.findViewById(R.id.pg_versions_button).setOnLongClickListener(v -> {
            if (mVersionSpinner != null) mVersionSpinner.openProfileEditor(requireActivity());
            return true;
        });
        view.findViewById(R.id.play_button).setOnClickListener(v ->
                ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        RecyclerView news = view.findViewById(R.id.pg_news_list);
        mNewsAdapter = new NewsAdapter();
        news.setLayoutManager(new LinearLayoutManager(requireContext()));
        news.setAdapter(mNewsAdapter);
        mNewsAdapter.setCards(NewsCard.loading("Cargando novedades de Paraguacraft…"));
        loadNews();

        bindPlayLabel(view);
        PgShell.maybeOnboard(requireActivity());
        if (!LauncherPreferences.DEFAULT_PREF.contains(ControlScheme.PREF)) {
            ControlScheme.apply(requireContext(), ControlScheme.MODERN);
        }
    }

    private void bindPlayLabel(View view) {
        TextView playSub = view.findViewById(R.id.pg_play_subtitle);
        if (playSub != null) playSub.setText(versionLabel());
    }

    private String versionLabel() {
        try {
            LauncherProfiles.load();
            String pid = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            MinecraftProfile p = pid == null || LauncherProfiles.mainProfileJson == null
                    ? null : LauncherProfiles.mainProfileJson.profiles.get(pid);
            if (p == null) return "Elegí una versión";
            String name = p.lastVersionId;
            if (name == null || name.isEmpty() || "Unknown".equals(name)) {
                return p.name != null ? p.name : "Elegí una versión";
            }
            return "Versión " + name;
        } catch (Exception e) {
            return "Elegí una versión";
        }
    }

    private void loadNews() {
        PojavApplication.sExecutorService.execute(() -> {
            List<NewsCard> cards;
            try {
                cards = DownloadUtils.downloadStringCached(NEWS_URL, "pg_mobile_news.json", input -> {
                    try {
                        JsonArray arr = Tools.GLOBAL_GSON.fromJson(input, JsonArray.class);
                        ArrayList<NewsCard> out = new ArrayList<>();
                        if (arr != null) {
                            for (int i = 0; i < arr.size(); i++) {
                                JsonObject o = arr.get(i).getAsJsonObject();
                                String title = str(o, "name");
                                if (title.isEmpty()) title = str(o, "tag_name");
                                String body = str(o, "body");
                                if (body.length() > 900) body = body.substring(0, 900) + "…";
                                String author = "";
                                if (o.has("author") && o.get("author").isJsonObject()) {
                                    author = str(o.getAsJsonObject("author"), "login");
                                }
                                String date = str(o, "published_at");
                                if (date.length() >= 10) date = date.substring(0, 10);
                                out.add(new NewsCard(title, body, author, date));
                            }
                        }
                        if (out.isEmpty()) {
                            out.add(new NewsCard("Paraguacraft Mobile",
                                    "Instalá instancias, mods, shaders y packs desde Espacio. Sin noticias de Minecraft.",
                                    "Paraguacraft", ""));
                        }
                        return out;
                    } catch (Exception e) {
                        throw new DownloadUtils.ParseException(e);
                    }
                });
            } catch (Exception e) {
                cards = new ArrayList<>();
                cards.add(new NewsCard("Paraguacraft Mobile",
                        "Sin conexión o el feed no respondió. Las instancias instaladas se pueden jugar igual.",
                        "Paraguacraft", ""));
            }
            List<NewsCard> finalCards = cards;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mNewsAdapter == null) return;
                mNewsAdapter.setCards(finalCards);
            });
        });
    }

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
        try {
            return o.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mVersionSpinner != null) mVersionSpinner.reloadProfiles();
        if (getView() != null) bindPlayLabel(getView());
        Wallpaper.apply(requireActivity());
        View hubs = requireActivity().findViewById(R.id.pg_hub_row);
        if (hubs != null) hubs.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPause() {
        View hubs = getActivity() != null ? getActivity().findViewById(R.id.pg_hub_row) : null;
        if (hubs != null) hubs.setVisibility(View.GONE);
        super.onPause();
    }

    private static final class NewsCard {
        final String title;
        final String body;
        final String meta;

        NewsCard(String title, String body, String author, String date) {
            this.title = title;
            this.body = body;
            String m = author == null ? "" : author;
            if (date != null && !date.isEmpty()) m = m.isEmpty() ? date : m + " · " + date;
            this.meta = m;
        }

        static List<NewsCard> loading(String text) {
            ArrayList<NewsCard> l = new ArrayList<>();
            l.add(new NewsCard("Paraguacraft", text, "", ""));
            return l;
        }
    }

    private static final class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.Holder> {
        private List<NewsCard> mCards = new ArrayList<>();

        void setCards(List<NewsCard> cards) {
            mCards = cards != null ? cards : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pg_news, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            NewsCard c = mCards.get(position);
            holder.title.setText(c.title);
            holder.body.setText(c.body);
            holder.meta.setText(c.meta);
        }

        @Override
        public int getItemCount() {
            return mCards.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView title, body, meta;
            Holder(View v) {
                super(v);
                title = v.findViewById(R.id.pg_news_title);
                body = v.findViewById(R.id.pg_news_body);
                meta = v.findViewById(R.id.pg_news_meta);
            }
        }
    }
}
