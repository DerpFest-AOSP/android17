package com.android.systemui.clocks;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.TextView;

import java.util.Calendar;

public class WordClockView extends TextView {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 1000);
        }
    };

    public WordClockView(Context context) {
        super(context);
        init();
    }

    public WordClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WordClockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        updateClock();
        handler.post(updateTimeRunnable);
    }

    private void updateClock() {
        Calendar cal = Calendar.getInstance();
        setText(
                WordClockFormatter.typographicHourText(getContext(), cal)
                        + "\n"
                        + WordClockFormatter.typographicMinuteText(getContext(), cal));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(updateTimeRunnable); // Stop updates when view is detached
    }
}
