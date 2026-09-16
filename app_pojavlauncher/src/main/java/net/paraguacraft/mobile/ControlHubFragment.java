package net.paraguacraft.mobile;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.fragments.GamepadMapperFragment;
/** Hub de controles, misma estructura que Battly Mobile 2: clásicos/modernos, editor y mando. */
public class ControlHubFragment extends Fragment {
    public static final String TAG = "PG_CONTROL_HUB";
    private static final int ACCENT = 0xFF2ECC71;

    private LinearLayout mSchemeRow;
    private View mClassicCard;
    private View mModernCard;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView kicker = text("CONTROLES", ACCENT, 12, true);
        kicker.setLetterSpacing(0.08f);
        root.addView(kicker);
        TextView title = text("Elegí cómo se siente el toque", 0xFFFFFFFF, 26, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(6);
        root.addView(title, tp);
        TextView desc = text("Clásicos (compactos) o modernos (zonas más grandes). Después podés editar botones o mapear un mando.",
                0xFFC7D4DF, 14, false);
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp1.topMargin = dp(8);
        dp1.bottomMargin = dp(16);
        root.addView(desc, dp1);

        mSchemeRow = new LinearLayout(requireContext());
        mSchemeRow.setOrientation(LinearLayout.HORIZONTAL);
        mClassicCard = schemeCard("Controles clásicos",
                "Botones compactos y etiquetas conocidas.", R.drawable.pg_ctrl_classic, v -> {
                    ControlScheme.apply(requireContext(), ControlScheme.CLASSIC);
                    paintSelection();
                    android.widget.Toast.makeText(requireContext(), "Controles clásicos.", android.widget.Toast.LENGTH_SHORT).show();
                });
        mModernCard = schemeCard("Controles modernos",
                "Zonas táctiles más grandes y menos etiquetas.", R.drawable.pg_ctrl_modern, v -> {
                    ControlScheme.apply(requireContext(), ControlScheme.MODERN);
                    paintSelection();
                    android.widget.Toast.makeText(requireContext(), "Controles modernos.", android.widget.Toast.LENGTH_SHORT).show();
                });
        mSchemeRow.addView(mClassicCard);
        mSchemeRow.addView(mModernCard);
        root.addView(mSchemeRow);
        paintSelection();

        addAction(root, R.drawable.ic_menu_custom_controls, "Personalizar botones",
                "Mové, agrandá o cambiá cada control en el editor.",
                v -> {
                    ControlScheme.ensureLayoutFiles(requireContext());
                    startActivity(new Intent(requireContext(), CustomControlsActivity.class));
                });
        addAction(root, R.drawable.ic_menu_settings, "Mapear mando",
                "Asigná botones de un control Bluetooth o USB.",
                v -> Tools.swapFragment(requireActivity(), GamepadMapperFragment.class,
                        GamepadMapperFragment.TAG, null));
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        paintSelection();
    }

    private void paintSelection() {
        boolean modern = ControlScheme.isModern();
        styleCard(mClassicCard, "Controles clásicos", !modern);
        styleCard(mModernCard, "Controles modernos", modern);
    }

    private static void styleCard(View card, String title, boolean selected) {
        if (card == null) return;
        card.setBackgroundResource(selected ? R.drawable.pg_glass_card_selected : R.drawable.pg_glass_card);
        if (card instanceof LinearLayout && ((LinearLayout) card).getChildCount() > 0) {
            View first = ((LinearLayout) card).getChildAt(0);
            if (first instanceof TextView) {
                ((TextView) first).setText(selected ? title + " · seleccionado" : title);
            }
        }
    }

    private View schemeCard(String title, String desc, int previewRes, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(8));
        card.setLayoutParams(lp);
        TextView t = text(title, 0xFFFFFFFF, 16, true);
        ImageView preview = new ImageView(requireContext());
        preview.setImageResource(previewRes);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(110));
        pp.topMargin = dp(10);
        TextView s = text(desc, 0xFFB8C4BC, 12, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(6);
        card.addView(t);
        card.addView(preview, pp);
        card.addView(s, sp);
        card.setOnClickListener(click);
        return card;
    }

    private void addAction(LinearLayout parent, int icon, String title, String desc, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.pg_glass_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(78));
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);
        ImageView iv = new ImageView(requireContext());
        iv.setImageResource(icon);
        card.addView(iv, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMargins(dp(12), 0, 0, 0);
        texts.setLayoutParams(tp);
        texts.addView(text(title, 0xFFFFFFFF, 16, true));
        texts.addView(text(desc, 0xFFB8C4BC, 12, false));
        card.addView(texts);
        card.setOnClickListener(click);
        parent.addView(card);
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
