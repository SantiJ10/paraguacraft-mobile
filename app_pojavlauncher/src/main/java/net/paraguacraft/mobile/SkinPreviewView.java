package net.paraguacraft.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/** Dibuja un jugador 2.5D a partir de un PNG de skin 64×64 (classic/slim). */
public class SkinPreviewView extends View {
    private Bitmap mSkin;
    private boolean mBack;
    private boolean mSlim;
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    public SkinPreviewView(Context context) { super(context); }
    public SkinPreviewView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setSkin(Bitmap skin) {
        mSkin = skin;
        mSlim = skin != null && skin.getWidth() >= 64 && skin.getHeight() >= 64
                && (skin.getPixel(54, 20) >>> 24) == 0;
        invalidate();
    }

    public void setShowBack(boolean back) {
        mBack = back;
        invalidate();
    }

    public boolean isShowingBack() { return mBack; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (w <= 0) w = (int) (140 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(w, (int) (w * 2.15f));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (mSkin == null) return;
        int sw = mSkin.getWidth();
        int sh = mSkin.getHeight();
        float unit = Math.min(getWidth() / 16f, getHeight() / 34f);
        float x = (getWidth() - 8 * unit) / 2f;
        float y = unit;
        int armW = mSlim ? 3 : 4;
        if (!mBack) {
            blit(c, 8, 8, 8, 8, x, y, 8 * unit, 8 * unit, sw, sh);
            blit(c, 40, 8, 8, 8, x, y, 8 * unit, 8 * unit, sw, sh);
            y += 8 * unit;
            blit(c, 44, 20, armW, 12, x - armW * unit, y, armW * unit, 12 * unit, sw, sh);
            blit(c, 20, 20, 8, 12, x, y, 8 * unit, 12 * unit, sw, sh);
            if (sh >= 64) blit(c, 36, 52, armW, 12, x + 8 * unit, y, armW * unit, 12 * unit, sw, sh);
            else blit(c, 44, 20, armW, 12, x + 8 * unit, y, armW * unit, 12 * unit, sw, sh);
            y += 12 * unit;
            blit(c, 4, 20, 4, 12, x, y, 4 * unit, 12 * unit, sw, sh);
            if (sh >= 64) blit(c, 20, 52, 4, 12, x + 4 * unit, y, 4 * unit, 12 * unit, sw, sh);
            else blit(c, 4, 20, 4, 12, x + 4 * unit, y, 4 * unit, 12 * unit, sw, sh);
        } else {
            blit(c, 24, 8, 8, 8, x, y, 8 * unit, 8 * unit, sw, sh);
            blit(c, 56, 8, 8, 8, x, y, 8 * unit, 8 * unit, sw, sh);
            y += 8 * unit;
            blit(c, 52, 20, armW, 12, x - armW * unit, y, armW * unit, 12 * unit, sw, sh);
            blit(c, 32, 20, 8, 12, x, y, 8 * unit, 12 * unit, sw, sh);
            if (sh >= 64) blit(c, 44, 52, armW, 12, x + 8 * unit, y, armW * unit, 12 * unit, sw, sh);
            y += 12 * unit;
            blit(c, 12, 20, 4, 12, x, y, 4 * unit, 12 * unit, sw, sh);
            if (sh >= 64) blit(c, 28, 52, 4, 12, x + 4 * unit, y, 4 * unit, 12 * unit, sw, sh);
        }
    }

    private void blit(Canvas c, int sx, int sy, int sw, int sh, float dx, float dy, float dw, float dh, int texW, int texH) {
        if (mSkin == null) return;
        float scaleX = texW / 64f;
        float scaleY = texH / 64f;
        Rect src = new Rect(Math.round(sx * scaleX), Math.round(sy * scaleY),
                Math.round((sx + sw) * scaleX), Math.round((sy + sh) * scaleY));
        Rect dst = new Rect(Math.round(dx), Math.round(dy), Math.round(dx + dw), Math.round(dy + dh));
        c.drawBitmap(mSkin, src, dst, mPaint);
    }
}
