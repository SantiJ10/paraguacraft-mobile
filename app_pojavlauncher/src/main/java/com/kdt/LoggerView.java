package com.kdt;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.R;

/**
 * A class able to display logs to the user.
 * It has support for the Logger class
 */
public class LoggerView extends ConstraintLayout {
    /** Texto retenido en pantalla; el log completo sigue yendo a latestlog.txt. */
    private static final int MAX_CHARS = 64 * 1024;
    /** Los mods escupen cientos de lineas por segundo: refrescar por linea
     *  disparaba un layout por cada una y congelaba el juego. */
    private static final long FLUSH_MS = 100;

    private Logger.eventLogListener mLogListener;
    private ToggleButton mLogToggle;
    private DefocusableScrollView mScrollView;
    private TextView mLogTextView;

    private final StringBuilder mPending = new StringBuilder();
    private boolean mFlushScheduled = false;

    private final Runnable mFlushPending = () -> {
        String chunk;
        synchronized (mPending) {
            mFlushScheduled = false;
            if (mPending.length() == 0) return;
            chunk = mPending.toString();
            mPending.setLength(0);
        }
        mLogTextView.append(chunk);
        trimLog();
        if (mScrollView.isKeepFocusing()) mScrollView.fullScroll(View.FOCUS_DOWN);
    };

    /** Descarta el principio del log para no crecer sin tope. */
    private void trimLog() {
        CharSequence text = mLogTextView.getText();
        if (text.length() <= MAX_CHARS) return;
        int cut = text.length() - MAX_CHARS;
        int newline = TextUtils.indexOf(text, '\n', cut);
        int from = newline >= 0 ? newline + 1 : cut;
        mLogTextView.setText(text.subSequence(from, text.length()));
    }

    private void clearPending() {
        synchronized (mPending) {
            mPending.setLength(0);
            mFlushScheduled = false;
        }
        removeCallbacks(mFlushPending);
    }


    public LoggerView(@NonNull Context context) {
        this(context, null);
    }

    public LoggerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        // Triggers the log view shown state by default when viewing it
        mLogToggle.setChecked(visibility == VISIBLE);
    }

    /**
     * Inflate the layout, and add component behaviors
     */
    private void init(){
        inflate(getContext(), R.layout.view_logger, this);
        mLogTextView = findViewById(R.id.content_log_view);
        mLogTextView.setTypeface(Typeface.MONOSPACE);
        //TODO clamp the max text so it doesn't go oob
        mLogTextView.setMaxLines(Integer.MAX_VALUE);
        mLogTextView.setEllipsize(null);
        mLogTextView.setVisibility(GONE);

        // Toggle log visibility
        mLogToggle = findViewById(R.id.content_log_toggle_log);
        mLogToggle.setOnCheckedChangeListener(
                (compoundButton, isChecked) -> {
                    mLogTextView.setVisibility(isChecked ? VISIBLE : GONE);
                    if(isChecked) {
                        Logger.addLogListener(mLogListener);
                    }else{
                        clearPending();
                        mLogTextView.setText("");
                        Logger.removeLogListener(mLogListener);
                    }
                });
        mLogToggle.setChecked(false);

        // Remove the loggerView from the user View
        ImageButton cancelButton = findViewById(R.id.log_view_cancel);
        cancelButton.setOnClickListener(view -> LoggerView.this.setVisibility(GONE));

        // Set the scroll view
        mScrollView = findViewById(R.id.content_log_scroll);
        mScrollView.setKeepFocusing(true);

        //Set up the autoscroll switch
        ToggleButton autoscrollToggle = findViewById(R.id.content_log_toggle_autoscroll);
        autoscrollToggle.setOnCheckedChangeListener(
                (compoundButton, isChecked) -> {
                    if(isChecked) mScrollView.fullScroll(View.FOCUS_DOWN);
                    mScrollView.setKeepFocusing(isChecked);
                }
        );
        autoscrollToggle.setChecked(true);

        // Listen to logs
        // Las lineas se acumulan y se vuelcan en lote: una sola pasada de layout
        // cada FLUSH_MS en vez de una por linea.
        mLogListener = text -> {
            if(mLogTextView.getVisibility() != VISIBLE) return;
            boolean schedule;
            synchronized (mPending) {
                mPending.append(text).append('\n');
                schedule = !mFlushScheduled;
                mFlushScheduled = true;
            }
            if (schedule) postDelayed(mFlushPending, FLUSH_MS);
        };
    }

}
