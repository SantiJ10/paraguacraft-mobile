package net.paraguacraft.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ParaguaSkinsFragment extends Fragment {
    public static final String TAG = "PG_SKINS";
    private ActivityResultLauncher<Intent> mPickSkin;
    private ActivityResultLauncher<Intent> mPickCape;
    private SkinPreviewView mPreview;
    private TextView mCaption;
    private LinearLayout mStore;
    private LinearLayout mCapes;
    private int mPage = 1;
    private Bitmap mCurrentSkin;
    private int mGen;

    public ParaguaSkinsFragment() {
        super(R.layout.fragment_pg_optimized);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPickSkin = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
            importPng(result.getData().getData(), true);
        });
        mPickCape = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
            importPng(result.getData().getData(), false);
        });
    }

    @Override
    public void onDestroyView() {
        mGen++;
        mPreview = null;
        mCaption = null;
        mStore = null;
        mCapes = null;
        super.onDestroyView();
    }

    private boolean alive() {
        return isAdded() && getContext() != null && getView() != null;
    }

    private void onUi(Runnable r) {
        Tools.runOnUiThread(() -> {
            if (alive()) r.run();
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mGen++;
        LinearLayout list = view.findViewById(R.id.pg_optimized_list);
        list.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;

        TextView title = new TextView(requireContext());
        title.setText("Skins y capas");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        list.addView(title);

        TextView sub = new TextView(requireContext());
        MinecraftAccount acc = SkinHelper.activeAccount(requireContext());
        boolean premium = acc != null && !acc.isLocal() && !acc.isDemo();
        sub.setText((premium ? "Cuenta premium · Mojang + LocalSkin" : "Cuenta offline · LocalSkin / Ely.by")
                + " · " + SkinHelper.activeUsername(requireContext()));
        sub.setTextColor(0xFFB8C4BC);
        sub.setPadding(0, (int) (4 * d), 0, (int) (12 * d));
        list.addView(sub);

        LinearLayout hero = new LinearLayout(requireContext());
        hero.setOrientation(tablet ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackgroundColor(0xEE0C1C14);
        hero.setPadding((int) (16 * d), (int) (16 * d), (int) (16 * d), (int) (16 * d));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = (int) (12 * d);
        hero.setLayoutParams(hlp);

        mPreview = new SkinPreviewView(requireContext());
        int pw = (int) ((tablet ? 140 : 120) * d);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(pw, (int) (pw * 2.15f));
        mPreview.setLayoutParams(plp);
        mPreview.setOnClickListener(v -> {
            mPreview.setShowBack(!mPreview.isShowingBack());
            mCaption.setText(mPreview.isShowingBack() ? "Tocá para ver de frente" : "Tocá el modelo para girar");
        });
        hero.addView(mPreview);

        LinearLayout side = new LinearLayout(requireContext());
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(tablet ? (int) (20 * d) : 0, tablet ? 0 : (int) (12 * d), 0, 0);
        mCaption = new TextView(requireContext());
        mCaption.setText("Tu skin actual · tocá para girar");
        mCaption.setTextColor(0xFFC4A574);
        side.addView(mCaption);

        Button importSkin = action("Importar skin PNG");
        importSkin.setOnClickListener(v -> pick("image/png", true));
        side.addView(importSkin);
        Button importCape = action("Importar capa PNG");
        importCape.setOnClickListener(v -> pick("image/png", false));
        side.addView(importCape);
        hero.addView(side);
        list.addView(hero);

        EditText q = new EditText(requireContext());
        q.setHint("Buscar jugador o galería MineSkin");
        q.setHintTextColor(0x88FFFFFF);
        q.setTextColor(0xFFFFFFFF);
        q.setSingleLine(true);
        list.addView(q);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button search = action("Buscar tienda");
        Button random = action("Aleatorio");
        Button rec = action("Recomendadas");
        search.setOnClickListener(v -> loadStore(q.getText().toString().trim(), mPage = 1, false));
        random.setOnClickListener(v -> loadStore("", mPage = 1 + (int) (Math.random() * 40), true));
        rec.setOnClickListener(v -> showRecommended());
        row.addView(search);
        row.addView(random);
        row.addView(rec);
        list.addView(row);

        TextView storeTitle = label("Tienda (la misma galería que en PC)");
        list.addView(storeTitle);
        mStore = new LinearLayout(requireContext());
        mStore.setOrientation(LinearLayout.VERTICAL);
        list.addView(mStore);

        LinearLayout nav = new LinearLayout(requireContext());
        Button prev = action("← Anterior");
        Button next = action("Siguiente →");
        prev.setOnClickListener(v -> loadStore(q.getText().toString().trim(), Math.max(1, --mPage), false));
        next.setOnClickListener(v -> loadStore(q.getText().toString().trim(), ++mPage, false));
        nav.addView(prev);
        nav.addView(next);
        list.addView(nav);

        TextView capeTitle = label("Capas");
        list.addView(capeTitle);
        TextView capeHelp = new TextView(requireContext());
        capeHelp.setText(premium
                ? "Inventario premium de Mojang + PNG descargadas. La capa se aplica a tu nick."
                : "Importá un PNG de capa (64×32). Se guarda en LocalSkin/capes de la instancia.");
        capeHelp.setTextColor(0xFFAAAAAA);
        capeHelp.setTextSize(12);
        list.addView(capeHelp);
        mCapes = new LinearLayout(requireContext());
        mCapes.setOrientation(LinearLayout.VERTICAL);
        list.addView(mCapes);

        refreshPreview();
        loadStore("", 1, false);
        if (premium) loadPremiumCapes();
    }

    private TextView label(String s) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextColor(0xFFC4A574);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, (int) (16 * getResources().getDisplayMetrics().density), 0, (int) (8 * getResources().getDisplayMetrics().density));
        return t;
    }

    private Button action(String text) {
        Button b = new Button(requireContext());
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
        lp.bottomMargin = (int) (6 * getResources().getDisplayMetrics().density);
        b.setLayoutParams(lp);
        return b;
    }

    private View capeThumb(String label, String url, View.OnClickListener click) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (88 * d), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (10 * d));
        card.setLayoutParams(lp);
        ImageView img = new ImageView(requireContext());
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setBackgroundColor(0xFF121A16);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams((int) (48 * d), (int) (76 * d));
        img.setLayoutParams(ip);
        TextView t = new TextView(requireContext());
        t.setText(label);
        t.setTextColor(0xFFDDDDDD);
        t.setTextSize(11);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        card.addView(img);
        card.addView(t);
        card.setOnClickListener(click);
        if (url != null && !url.isEmpty()) {
            final int gen = mGen;
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    File cache = new File(Tools.DIR_CACHE, "pg-cape-" + Integer.toHexString(url.hashCode()) + ".png");
                    if (!cache.isFile()) DownloadUtils.downloadFile(url.replace("http://", "https://"), cache);
                    Bitmap raw = SkinHelper.decodePng(cache);
                    Bitmap front = SkinHelper.cropCapeFront(raw);
                    onUi(() -> {
                        if (gen != mGen || front == null) return;
                        img.setImageBitmap(front);
                    });
                } catch (Exception ignored) {}
            });
        }
        return card;
    }

    private void pick(String type, boolean skin) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(type);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if (skin) mPickSkin.launch(i);
        else mPickCape.launch(i);
    }

    private void importPng(Uri uri, boolean skin) {
        if (uri == null) return;
        Context ctx = getContext();
        if (ctx == null) return;
        PojavApplication.sExecutorService.execute(() -> {
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("No se pudo leer el PNG");
                File dest = skin ? SkinHelper.globalSkin() : SkinHelper.globalCape();
                dest.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                if (skin) SkinHelper.injectSkin(ctx, dest);
                else SkinHelper.injectCape(ctx, dest);
                onUi(() -> {
                    refreshPreview();
                    toast(skin ? "Skin aplicada" : "Capa aplicada");
                });
            } catch (Exception e) {
                onUi(() -> toast(e.getMessage()));
            }
        });
    }

    private void toast(String msg) {
        Context ctx = getContext();
        if (ctx == null || msg == null) return;
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }

    private void refreshPreview() {
        if (mPreview == null) return;
        Context ctx = getContext();
        MinecraftAccount acc = ctx != null ? SkinHelper.activeAccount(ctx) : null;
        boolean premium = acc != null && !acc.isLocal() && !acc.isDemo();
        if (premium) {
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    File cache = new File(Tools.DIR_CACHE, "pg-skin-" + acc.username + ".png");
                    DownloadUtils.downloadFile("https://minotar.net/skin/" + acc.username, cache);
                    Bitmap b = SkinHelper.decodePng(cache);
                    onUi(() -> {
                        mCurrentSkin = b;
                        if (mPreview != null) mPreview.setSkin(b);
                    });
                } catch (Exception ignored) {}
            });
            return;
        }
        String nick = SkinHelper.activeUsername(ctx);
        Bitmap bmp = SkinHelper.decodePng(SkinHelper.userSkinFile(nick));
        if (bmp == null) bmp = SkinHelper.decodePng(SkinHelper.globalSkin());
        mCurrentSkin = bmp;
        mPreview.setSkin(bmp);
    }

    private void showRecommended() {
        mStore.removeAllViews();
        String[] recs = {"Steve", "Alex", "jeb_", "Notch", "Dinnerbone", "Technoblade", "Dream", "San_Jlf"};
        HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String name : recs) {
            row.addView(catalogCard(name, "https://minotar.net/skin/" + name, "https://minotar.net/body/" + name + "/120"));
        }
        hsv.addView(row);
        mStore.addView(hsv);
    }

    private void loadStore(String query, int page, boolean random) {
        if (!alive() || mStore == null) return;
        final int gen = mGen;
        mStore.removeAllViews();
        TextView loading = new TextView(requireContext());
        loading.setText("Cargando galería…");
        loading.setTextColor(0xFFAAAAAA);
        mStore.addView(loading);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                if (!query.isEmpty() && !query.contains(" ")) {
                    try {
                        JSONObject player = SkinHelper.lookupPlayer(query);
                        onUi(() -> {
                            if (mStore == null || gen != mGen) return;
                            mStore.removeAllViews();
                            mStore.addView(catalogCard(
                                    player.optString("username", query),
                                    player.optString("skinUrl"),
                                    player.optString("previewUrl")));
                            if (player.has("capeUrl")) {
                                String capeUrl = player.optString("capeUrl");
                                mStore.addView(capeThumb("Capa de " + player.optString("username"), capeUrl,
                                        v -> applyCapeUrl(capeUrl)));
                            }
                        });
                        return;
                    } catch (Exception ignored) {}
                }
                JSONObject json = SkinHelper.fetchMineskinPage(page);
                JSONArray skins = json.optJSONArray("skins");
                onUi(() -> {
                    if (mStore == null || gen != mGen) return;
                    mStore.removeAllViews();
                    if (skins == null || skins.length() == 0) {
                        TextView empty = new TextView(requireContext());
                        empty.setText("No hay resultados. Probá un nick de Minecraft.");
                        empty.setTextColor(0xFFAAAAAA);
                        mStore.addView(empty);
                        return;
                    }
                    int max = Math.min(skins.length(), 12);
                    boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
                    LinearLayout grid = new LinearLayout(requireContext());
                    grid.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout row = null;
                    for (int i = 0; i < max; i++) {
                        JSONObject s = skins.optJSONObject(i);
                        if (s == null) continue;
                        String url = s.optString("url");
                        String name = s.optString("name", "Skin " + (i + 1));
                        if (name.isEmpty()) name = s.optString("uuid", "Skin").substring(0, Math.min(8, s.optString("uuid").length()));
                        View card = catalogCard(name, url, null);
                        if (!tablet) {
                            grid.addView(card);
                            continue;
                        }
                        if (row == null) {
                            row = new LinearLayout(requireContext());
                            row.setOrientation(LinearLayout.HORIZONTAL);
                        }
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                        card.setLayoutParams(lp);
                        row.addView(card);
                        if (row.getChildCount() == 2) {
                            grid.addView(row);
                            row = null;
                        }
                    }
                    if (row != null) grid.addView(row);
                    mStore.addView(grid);
                });
            } catch (Exception e) {
                onUi(() -> {
                    if (mStore == null || gen != mGen) return;
                    mStore.removeAllViews();
                    TextView err = new TextView(requireContext());
                    err.setText("Sin conexión o MineSkin no responde.\nPodés importar un PNG igual.");
                    err.setTextColor(0xFFCC8888);
                    mStore.addView(err);
                });
            }
        });
    }

    private View catalogCard(String name, String skinUrl, String previewUrl) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding((int) (8 * d), (int) (8 * d), (int) (8 * d), (int) (8 * d));
        card.setBackgroundColor(0x22000000);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = (int) (8 * d);
        card.setLayoutParams(clp);

        ImageView img = new ImageView(requireContext());
        int sz = (int) (72 * d);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, (int) (sz * 1.6f));
        ilp.setMarginEnd((int) (12 * d));
        img.setLayoutParams(ilp);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(img);

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(requireContext());
        t.setText(name);
        t.setTextColor(0xFFFFFFFF);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        Button apply = action("Ver y aplicar");
        apply.setOnClickListener(v -> applySkinUrl(skinUrl, name));
        col.addView(t);
        col.addView(apply);
        card.addView(col);

        if (previewUrl != null && !previewUrl.isEmpty()) {
            loadPreviewInto(img, previewUrl);
        } else if (skinUrl != null && !skinUrl.isEmpty()) {
            final int gen = mGen;
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    File cache = new File(Tools.DIR_CACHE, "pg-cat-" + Integer.toHexString(skinUrl.hashCode()) + ".png");
                    if (!cache.isFile() || cache.length() < 64) {
                        DownloadUtils.downloadFile(skinUrl.replace("http://", "https://"), cache);
                    }
                    Bitmap skin = BitmapFactory.decodeFile(cache.getAbsolutePath());
                    Tools.runOnUiThread(() -> {
                        if (gen != mGen || !card.isAttachedToWindow()) return;
                        Context ctx = card.getContext();
                        if (ctx == null) return;
                        SkinPreviewView mini = new SkinPreviewView(ctx);
                        mini.setSkin(skin);
                        mini.setLayoutParams(ilp);
                        int idx = card.indexOfChild(img);
                        card.removeView(img);
                        card.addView(mini, Math.max(0, idx));
                    });
                } catch (Exception ignored) {}
            });
        }
        return card;
    }

    private void loadPreviewInto(ImageView img, String url) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File cache = new File(Tools.DIR_CACHE, "pg-prev-" + Integer.toHexString(url.hashCode()) + ".png");
                if (!cache.isFile() || cache.length() < 64) {
                    DownloadUtils.downloadFile(url, cache);
                }
                Bitmap b = BitmapFactory.decodeFile(cache.getAbsolutePath());
                Tools.runOnUiThread(() -> {
                    if (img.isAttachedToWindow()) img.setImageBitmap(b);
                });
            } catch (Exception ignored) {}
        });
    }

    private void applySkinUrl(String url, String name) {
        if (url == null || url.isEmpty()) return;
        Context ctx = getContext();
        if (ctx == null) return;
        Toast.makeText(ctx, "Aplicando " + name + "…", Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                SkinHelper.applyUrl(ctx.getApplicationContext(), url);
                onUi(() -> {
                    refreshPreview();
                    toast("Skin aplicada. Entrá de nuevo al mundo para verla.");
                });
            } catch (Exception e) {
                onUi(() -> toast(e.getMessage()));
            }
        });
    }

    private void applyCapeUrl(String url) {
        Context ctx = getContext();
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                SkinHelper.applyCapeUrl(app, url);
                onUi(() -> toast("Capa aplicada"));
            } catch (Exception e) {
                onUi(() -> toast(e.getMessage()));
            }
        });
    }

    private void loadPremiumCapes() {
        Context ctx = getContext();
        if (ctx == null) return;
        MinecraftAccount acc = SkinHelper.activeAccount(ctx);
        final int gen = mGen;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                JSONArray capes = SkinHelper.premiumCapes(acc);
                onUi(() -> {
                    if (mCapes == null || gen != mGen) return;
                    mCapes.removeAllViews();
                    if (capes.length() == 0) {
                        TextView t = new TextView(requireContext());
                        t.setText("No hay capas en el inventario de Mojang. Podés importar un PNG.");
                        t.setTextColor(0xFFAAAAAA);
                        mCapes.addView(t);
                        return;
                    }
                    HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
                    LinearLayout row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    hsv.addView(row);
                    mCapes.addView(hsv);
                    for (int i = 0; i < capes.length(); i++) {
                        JSONObject c = capes.optJSONObject(i);
                        if (c == null) continue;
                        String alias = c.optString("alias", "Capa");
                        String state = c.optString("state", "");
                        String id = c.optString("id");
                        String url = c.optString("url");
                        String label = (state.equals("ACTIVE") ? "● " : "") + alias;
                        row.addView(capeThumb(label, url, v -> {
                            Context now = getContext();
                            if (now == null) return;
                            Context app = now.getApplicationContext();
                            PojavApplication.sExecutorService.execute(() -> {
                                try {
                                    SkinHelper.equipPremiumCape(acc, id);
                                    if (!url.isEmpty()) SkinHelper.applyCapeUrl(app, url);
                                    onUi(() -> toast("Capa " + alias + " activa"));
                                } catch (Exception e) {
                                    onUi(() -> toast(e.getMessage()));
                                }
                            });
                        }));
                    }
                });
            } catch (Exception e) {
                onUi(() -> {
                    if (mCapes == null || gen != mGen) return;
                    TextView t = new TextView(requireContext());
                    t.setText("No se pudo leer el inventario de capas. Importá un PNG.");
                    t.setTextColor(0xFFAAAAAA);
                    mCapes.addView(t);
                });
            }
        });
    }
}
