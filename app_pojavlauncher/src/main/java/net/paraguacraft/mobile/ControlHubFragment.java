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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.fragments.GamepadMapperFragment;

/** Hub de controles táctiles adaptativos: automático por versión, PvP 1.8.9, Survival 1.20+/26.3, tamaño y mando. */
public class ControlHubFragment extends Fragment {
    public static final String TAG = "PG_CONTROL_HUB";
    private static final int ACCENT = 0xFF2ECC71;

    private View mAutoCard;
    private View mPvpCard;
    private View mSurvivalCard;
    private View mModernCard;
    private View mClassicCard;
    private TextView mScaleValueText;

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

        TextView kicker = text("CONTROLES TÁCTILES", ACCENT, 12, true);
        kicker.setLetterSpacing(0.08f);
        root.addView(kicker);
        TextView title = text("Elegí cómo se siente el toque", 0xFFFFFFFF, 25, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(6);
        root.addView(title, tp);
        TextView desc = text("Adaptamos los botones según juegues PvP 1.8.9 o Survival moderno (1.20+ / 26.3), y podés ajustar el tamaño al instante.",
                0xFFC7D4DF, 13, false);
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp1.topMargin = dp(6);
        dp1.bottomMargin = dp(14);
        root.addView(desc, dp1);

        // Tarjeta destacada: Automático por versión
        mAutoCard = bannerSchemeCard(
                "Automático por versión (Recomendado)",
                "Usa el perfil PvP en 1.7 / 1.8.9 y cambia solo a Survival (con 2ª mano y Chat) en 1.12–26.3.",
                v -> selectScheme(ControlScheme.AUTO));
        root.addView(mAutoCard);

        // Fila 1: PvP 1.8.9 + Survival 1.20+/26.3
        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r1p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r1p.topMargin = dp(10);
        row1.setLayoutParams(r1p);
        mPvpCard = schemeCard("PvP / 1.8.9",
                "Atacar y Usar grandes, F5 en el pulgar, Soltar rápido y Correr fijo.",
                R.drawable.pg_ctrl_modern, v -> selectScheme(ControlScheme.PVP), true);
        mSurvivalCard = schemeCard("Survival 1.20+ / 26.3",
                "Segunda mano (F), Chat directo, Shift cómodo y vista limpia.",
                R.drawable.pg_ctrl_modern, v -> selectScheme(ControlScheme.SURVIVAL), false);
        row1.addView(mPvpCard);
        row1.addView(mSurvivalCard);
        root.addView(row1);

        // Fila 2: Modernos + Clásicos
        LinearLayout row2 = new LinearLayout(requireContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2p.topMargin = dp(10);
        row2.setLayoutParams(r2p);
        mModernCard = schemeCard("Modernos",
                "Zonas táctiles amplias y símbolos limpios.",
                R.drawable.pg_ctrl_modern, v -> selectScheme(ControlScheme.MODERN), true);
        mClassicCard = schemeCard("Clásicos",
                "Botones compactos y etiquetas tradicionales.",
                R.drawable.pg_ctrl_classic, v -> selectScheme(ControlScheme.CLASSIC), false);
        row2.addView(mModernCard);
        row2.addView(mClassicCard);
        root.addView(row2);

        // Ajustador rápido de tamaño de botones
        root.addView(buildScaleCard());

        addAction(root, R.drawable.ic_menu_custom_controls, "Personalizar botones en el editor",
                "Mové, agrandá o agregá teclas especiales a tu gusto.",
                v -> {
                    ControlScheme.ensureLayoutFiles(requireContext());
                    startActivity(new Intent(requireContext(), CustomControlsActivity.class));
                });
        addAction(root, R.drawable.ic_menu_settings, "Mapear mando Bluetooth / USB",
                "Asigná botones de un joystick PS, Xbox o genérico.",
                v -> Tools.swapFragment(requireActivity(), GamepadMapperFragment.class,
                        GamepadMapperFragment.TAG, null));
        addAction(root, R.drawable.ic_menu_custom_controls, "Restaurar botones de fábrica",
                "Vuelve el esquema actual al diseño limpio original de Paraguacraft.",
                v -> new AlertDialog.Builder(requireContext())
                        .setTitle("Restaurar controles")
                        .setMessage("¿Querés volver el esquema activo a su posición y botones originales?")
                        .setPositiveButton("Restaurar", (d, w) -> {
                            ControlScheme.resetActiveLayout(requireContext());
                            ControlScheme.resetScale(requireContext());
                            paintSelection();
                            Toast.makeText(requireContext(), "Controles restaurados.", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show());

        paintSelection();
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        paintSelection();
    }

    private void selectScheme(String scheme) {
        ControlScheme.apply(requireContext(), scheme);
        paintSelection();
        Toast.makeText(requireContext(), "Activo: " + ControlScheme.label(scheme), Toast.LENGTH_SHORT).show();
    }

    private View buildScaleCard() {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.pg_glass_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        card.setLayoutParams(lp);

        TextView head = text("Tamaño de botones táctiles", 0xFFFFFFFF, 15, true);
        card.addView(head);

        mScaleValueText = text("", 0xFFB8C4BC, 12, false);
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        svp.topMargin = dp(2);
        svp.bottomMargin = dp(10);
        card.addView(mScaleValueText, svp);

        LinearLayout btns = new LinearLayout(requireContext());
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.addView(pillButton("− 10 %", true, v -> {
            ControlScheme.adjustScale(requireContext(), -10);
            paintSelection();
        }));
        btns.addView(pillButton("Automático", true, v -> {
            ControlScheme.resetScale(requireContext());
            paintSelection();
        }));
        btns.addView(pillButton("+ 10 %", false, v -> {
            ControlScheme.adjustScale(requireContext(), 10);
            paintSelection();
        }));
        card.addView(btns);
        return card;
    }

    private View pillButton(String label, boolean marginEnd, View.OnClickListener click) {
        TextView b = text(label, 0xFFFFFFFF, 13, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(9), dp(10), dp(9));
        b.setBackgroundResource(R.drawable.pg_glass_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (marginEnd) lp.setMarginEnd(dp(8));
        b.setLayoutParams(lp);
        b.setOnClickListener(click);
        return b;
    }

    private void paintSelection() {
        String cur = ControlScheme.current();
        styleCard(mAutoCard, "Automático por versión (Recomendado)", ControlScheme.AUTO.equals(cur));
        styleCard(mPvpCard, "PvP / 1.8.9", ControlScheme.PVP.equals(cur));
        styleCard(mSurvivalCard, "Survival 1.20+ / 26.3", ControlScheme.SURVIVAL.equals(cur));
        styleCard(mModernCard, "Modernos", ControlScheme.MODERN.equals(cur));
        styleCard(mClassicCard, "Clásicos", ControlScheme.CLASSIC.equals(cur));

        if (mScaleValueText != null && isAdded()) {
            int scale = ControlScheme.buttonScale(requireContext(),
                    ControlScheme.resolveForVersion(cur, SkinHelper.instanceMcHint()));
            boolean custom = ControlScheme.hasCustomScale();
            mScaleValueText.setText("Escala actual: " + scale + "% · "
                    + (custom ? "Ajuste manual" : "Adaptado a tu pantalla"));
        }
    }

    private static void styleCard(View card, String title, boolean selected) {
        if (card == null) return;
        card.setBackgroundResource(selected ? R.drawable.pg_glass_card_selected : R.drawable.pg_glass_card);
        if (card instanceof LinearLayout && ((LinearLayout) card).getChildCount() > 0) {
            View first = ((LinearLayout) card).getChildAt(0);
            if (first instanceof TextView) {
                ((TextView) first).setText(selected ? title + " · activo" : title);
            }
        }
    }

    private View bannerSchemeCard(String title, String desc, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = text(title, 0xFFFFFFFF, 16, true);
        TextView s = text(desc, 0xFFB8C4BC, 12, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(4);
        card.addView(t);
        card.addView(s, sp);
        card.setOnClickListener(click);
        return card;
    }

    private View schemeCard(String title, String desc, int previewRes, View.OnClickListener click, boolean marginEnd) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (marginEnd) lp.setMarginEnd(dp(8));
        card.setLayoutParams(lp);
        TextView t = text(title, 0xFFFFFFFF, 15, true);
        ImageView preview = new ImageView(requireContext());
        preview.setImageResource(previewRes);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(82));
        pp.topMargin = dp(8);
        TextView s = text(desc, 0xFFB8C4BC, 11, false);
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
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
        texts.addView(text(title, 0xFFFFFFFF, 15, true));
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
