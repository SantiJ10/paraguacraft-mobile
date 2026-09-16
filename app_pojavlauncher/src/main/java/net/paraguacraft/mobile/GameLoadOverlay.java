package net.paraguacraft.mobile;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.R;

import java.lang.ref.WeakReference;

/** Overlay de carga con logo Paraguacraft mientras Java/Forge arrancan (en vez de pantalla negra). */
public final class GameLoadOverlay {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<View> sRoot = new WeakReference<>(null);
    private static WeakReference<TextView> sStatus = new WeakReference<>(null);
    private static int sFrames;
    private static boolean sHidden;
    private static final Logger.eventLogListener LOG = GameLoadOverlay::onLog;

    private GameLoadOverlay() {}

    public static void attach(Activity activity) {
        sHidden = false;
        sFrames = 0;
        View root = activity.findViewById(R.id.pg_load_overlay);
        TextView status = activity.findViewById(R.id.pg_load_status);
        sRoot = new WeakReference<>(root);
        sStatus = new WeakReference<>(status);
        if (root != null) {
            root.setVisibility(View.VISIBLE);
            root.setClickable(true);
        }
        setStatus("Iniciando Java…");
        Logger.addLogListener(LOG);
        MAIN.removeCallbacks(TIMEOUT);
        MAIN.postDelayed(TIMEOUT, 45_000);
    }

    public static void onSwapBuffer() {
        if (sHidden) return;
        sFrames++;
        if (sFrames == 1) setStatus("Abriendo el menú…");
        if (sFrames >= 3) hide();
    }

    public static void hide() {
        if (sHidden) return;
        sHidden = true;
        MAIN.removeCallbacks(TIMEOUT);
        Logger.removeLogListener(LOG);
        MAIN.post(() -> {
            View root = sRoot.get();
            if (root != null) root.setVisibility(View.GONE);
        });
    }

    private static final Runnable TIMEOUT = GameLoadOverlay::hide;

    private static void onLog(String text) {
        if (sHidden || text == null) return;
        String t = text.toLowerCase();
        if (t.contains("fml") && (t.contains("loading") || t.contains("mod"))) {
            setStatus("Cargando mods…");
        } else if (t.contains("fabric loader") || t.contains("loading minecraft")) {
            setStatus("Cargando mods…");
        } else if (t.contains("lwjgl") || t.contains("opengl")) {
            setStatus("Preparando gráficos…");
        } else if (t.contains("setting user") || t.contains("reloading resourcemanager")
                || t.contains("finished loading")) {
            setStatus("Casi listo…");
            MAIN.removeCallbacks(HIDE_SOON);
            MAIN.postDelayed(HIDE_SOON, 900);
        } else if (t.contains("mojang") || t.contains("minecraft")) {
            setStatus("Cargando Minecraft…");
        }
    }

    private static final Runnable HIDE_SOON = GameLoadOverlay::hide;

    private static void setStatus(String msg) {
        MAIN.post(() -> {
            TextView tv = sStatus.get();
            if (tv != null) tv.setText(msg);
        });
    }
}
