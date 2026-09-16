package net.paraguacraft.mobile;

import android.app.Activity;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;

/** Selector de fondos, mismo flujo que Battly BackgroundSettingsFragment. */
public class BackgroundSettingsFragment extends Fragment {
    public static final String TAG = "PG_BACKGROUNDS";

    private ActivityResultLauncher<String> mImportPhoto;
    private LinearLayout mGrid;

    public BackgroundSettingsFragment() {
        super(R.layout.fragment_pg_background);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mImportPhoto = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPhotoPicked);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mGrid = view.findViewById(R.id.pg_background_grid);
        bind();
    }

    private void onPhotoPicked(Uri uri) {
        if (uri == null || !isAdded()) return;
        String type = null;
        try {
            type = requireContext().getContentResolver().getType(uri);
        } catch (Exception ignored) {}
        if (type == null || !type.startsWith("image/")) {
            Toast.makeText(requireContext(), "Tiene que ser una foto (JPG, PNG o WebP).", Toast.LENGTH_LONG).show();
            return;
        }
        if (Wallpaper.importPhoto(requireContext(), uri)) {
            Toast.makeText(requireContext(), "Fondo personalizado aplicado.", Toast.LENGTH_SHORT).show();
            bind();
        }
    }

    private void bind() {
        LinearLayout box = mGrid;
        if (box == null) return;
        box.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        TextView title = new TextView(requireContext());
        title.setText("Personalizá tu fondo");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);
        TextView sub = new TextView(requireContext());
        sub.setText("Elegí una imagen de Paraguacraft o importá una foto tuya.");
        sub.setTextColor(0xFFB8C4BC);
        sub.setTextSize(13);
        sub.setPadding(0, (int) (4 * d), 0, (int) (12 * d));
        box.addView(sub);

        int current = Wallpaper.index(requireContext());
        LinearLayout row = null;
        for (int i = 0; i < Wallpaper.DRAWABLES.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = (int) (8 * d);
                row.setLayoutParams(rlp);
                box.addView(row);
            }
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (8 * d));
            boolean selected = i == current;
            card.setBackgroundResource(selected ? R.drawable.pg_glass_card_selected : R.drawable.pg_glass_card);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMarginEnd((int) (6 * d));
            card.setLayoutParams(clp);
            ImageView img = new ImageView(requireContext());
            img.setImageResource(Wallpaper.DRAWABLES[i]);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (110 * d)));
            card.addView(img);
            TextView name = new TextView(requireContext());
            name.setText(selected ? Wallpaper.NAMES[i] + "  · activo" : Wallpaper.NAMES[i]);
            name.setTextColor(0xFFFFFFFF);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setPadding(0, (int) (8 * d), 0, 0);
            card.addView(name);
            final int idx = i;
            card.setOnClickListener(v -> {
                Activity a = getActivity();
                Wallpaper.set(a, idx);
                bind();
            });
            row.addView(card);
        }

        LinearLayout importCard = new LinearLayout(requireContext());
        importCard.setOrientation(LinearLayout.VERTICAL);
        importCard.setPadding((int) (12 * d), (int) (12 * d), (int) (12 * d), (int) (12 * d));
        boolean custom = Wallpaper.isCustom(requireContext());
        importCard.setBackgroundResource(custom ? R.drawable.pg_glass_card_selected : R.drawable.pg_glass_card);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = (int) (4 * d);
        importCard.setLayoutParams(ilp);
        if (custom && Wallpaper.customFile(requireContext()).isFile()) {
            ImageView preview = new ImageView(requireContext());
            preview.setImageURI(Uri.fromFile(Wallpaper.customFile(requireContext())));
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (120 * d)));
            importCard.addView(preview);
        }
        TextView importTitle = new TextView(requireContext());
        importTitle.setText(custom ? "Tu foto  · activa" : "Importar foto");
        importTitle.setTextColor(0xFFFFFFFF);
        importTitle.setTextSize(16);
        importTitle.setTypeface(Typeface.DEFAULT_BOLD);
        importTitle.setPadding(0, custom ? (int) (8 * d) : 0, 0, 0);
        TextView importSub = new TextView(requireContext());
        importSub.setText("Elegí una imagen de la galería. Solo fotos, no videos.");
        importSub.setTextColor(0xFFB8C4BC);
        importSub.setTextSize(12);
        importCard.addView(importTitle);
        importCard.addView(importSub);
        importCard.setOnClickListener(v -> mImportPhoto.launch("image/*"));
        box.addView(importCard);
    }
}
