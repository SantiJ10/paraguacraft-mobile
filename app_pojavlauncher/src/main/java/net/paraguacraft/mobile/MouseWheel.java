package net.paraguacraft.mobile;

import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

/** Rueda del mouse en listas / diálogos (Android a veces solo scrollea táctil). */
public final class MouseWheel {
    private MouseWheel() {}

    public static void attach(View view) {
        if (view == null) return;
        view.setOnGenericMotionListener((v, event) -> dispatch(v, event));
    }

    public static boolean dispatch(View v, MotionEvent event) {
        if (event == null || v == null) return false;
        if (event.getAction() != MotionEvent.ACTION_SCROLL) return false;
        int src = event.getSource();
        if ((src & InputDevice.SOURCE_CLASS_POINTER) == 0
                && (src & InputDevice.SOURCE_MOUSE) == 0) {
            return false;
        }
        float y = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        float x = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
        if (y == 0f && x == 0f) return false;
        float d = v.getResources().getDisplayMetrics().density;
        int dy = Math.round(-y * 72f * d);
        int dx = Math.round(-x * 72f * d);
        if (v instanceof AbsListView) {
            ((AbsListView) v).smoothScrollBy(dy, 90);
            return true;
        }
        if (v instanceof RecyclerView) {
            ((RecyclerView) v).scrollBy(dx, dy);
            return true;
        }
        if (v instanceof NestedScrollView) {
            v.scrollBy(0, dy);
            return true;
        }
        if (v instanceof ScrollView) {
            v.scrollBy(0, dy);
            return true;
        }
        if (v instanceof HorizontalScrollView) {
            v.scrollBy(dx, 0);
            return true;
        }
        v.scrollBy(dx, dy);
        return true;
    }
}
