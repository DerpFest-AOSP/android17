/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.notification.icon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Optional bundled launcher-style PNGs (from the Pink Bean theme pack) for status bar
 * notification icons, used when {@link android.provider.Settings.System#STATUSBAR_COLORED_ICONS}
 * and {@link android.provider.Settings.System#STATUSBAR_PINKBEAN_NOTIFICATION_ICONS} are enabled.
 * Packages without a bundled asset fall back to {@code PackageManager#getApplicationIcon}.
 *
 * <p>Loaded bitmaps are reframed for the circular mask: transparent margins are trimmed, then a
 * modest center crop can enlarge how much of the circle the artwork occupies (no view scaling).
 */
public final class PinkBeanNotificationIcons {
    private static final String TAG = "PinkBeanNotifIcons";
    private static final String ASSET_DIR = "pinkbean_icons";

    /**
     * When transparent padding was removed, keep only this center fraction of the square (e.g.
     * {@code 0.88f} crops ~6% from each edge) so the glyph uses more of the circular mask. Not
     * applied when the asset already runs to the edges (no trim), to avoid clipping full-bleed
     * icons.
     */
    private static final float INNER_CONTENT_FRACTION_AFTER_TRIM = 0.88f;

    /**
     * When no transparent border was detected, center crop is skipped by default ({@code 1f}) so
     * full-bleed icons are not clipped. Set to e.g. {@code 0.94f} if assets use an opaque colored
     * ring instead of alpha padding.
     */
    private static final float INNER_CONTENT_FRACTION_NO_TRIM = 1.0f;

    /** Pixels with alpha at or below this are treated as empty for border trim. */
    private static final int TRIM_ALPHA_THRESHOLD = 12;

    /**
     * If transparent trim would remove more than this fraction of width or height, it is skipped
     * (likely not a padded asset).
     */
    private static final float MAX_TRIM_FRACTION_PER_AXIS = 0.35f;

    private static final Paint sCropPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    private PinkBeanNotificationIcons() {}

    /**
     * @return A drawable for {@code packageName} if a matching PNG exists under {@code assets/},
     *         or null to use the normal colored-icon path.
     */
    public static @Nullable Drawable load(Context sysuiContext, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        final String path = ASSET_DIR + "/" + packageName + ".png";
        try (InputStream is = sysuiContext.getAssets().open(path)) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp == null) {
                return null;
            }
            bmp = reframeForCircularStatusIcon(bmp);
            // Match colored-icons path: launcher/adaptive icons read as circular; raw PNGs are square.
            RoundedBitmapDrawable drawable =
                    RoundedBitmapDrawableFactory.create(sysuiContext.getResources(), bmp);
            drawable.setCircular(true);
            drawable.setAntiAlias(true);
            return drawable;
        } catch (IOException e) {
            return null;
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "OOM loading " + path, e);
            return null;
        }
    }

    /**
     * Drops empty transparent margins, then crops a uniform ring so inner artwork uses more of the
     * circle. The returned bitmap may be a new allocation; the input is recycled when replaced.
     */
    private static Bitmap reframeForCircularStatusIcon(Bitmap bmp) {
        final int origW = bmp.getWidth();
        final int origH = bmp.getHeight();
        Bitmap trimmed = trimTransparentBorder(bmp);
        final boolean hadTransparentPadding =
                trimmed.getWidth() != origW || trimmed.getHeight() != origH;
        final float innerFraction =
                hadTransparentPadding
                        ? INNER_CONTENT_FRACTION_AFTER_TRIM
                        : INNER_CONTENT_FRACTION_NO_TRIM;
        return cropCenterSquare(trimmed, innerFraction);
    }

    /**
     * Removes rows/columns from the edges that are fully transparent (or nearly). Returns
     * {@code source} unchanged if there is nothing to trim or the trim looks unsafe.
     */
    private static Bitmap trimTransparentBorder(Bitmap source) {
        final int w = source.getWidth();
        final int h = source.getHeight();
        if (w <= 1 || h <= 1) {
            return source;
        }
        final int[] row = new int[Math.max(w, h)];

        int top = 0;
        topScan:
        for (; top < h; top++) {
            source.getPixels(row, 0, w, 0, top, w, 1);
            for (int x = 0; x < w; x++) {
                if (Color.alpha(row[x]) > TRIM_ALPHA_THRESHOLD) {
                    break topScan;
                }
            }
        }

        int bottom = h - 1;
        bottomScan:
        for (; bottom >= top; bottom--) {
            source.getPixels(row, 0, w, 0, bottom, w, 1);
            for (int x = 0; x < w; x++) {
                if (Color.alpha(row[x]) > TRIM_ALPHA_THRESHOLD) {
                    break bottomScan;
                }
            }
        }

        final int colH = bottom - top + 1;

        int left = 0;
        leftScan:
        for (; left < w; left++) {
            source.getPixels(row, 0, 1, left, top, 1, colH);
            for (int i = 0; i < colH; i++) {
                if (Color.alpha(row[i]) > TRIM_ALPHA_THRESHOLD) {
                    break leftScan;
                }
            }
        }

        int right = w - 1;
        rightScan:
        for (; right >= left; right--) {
            source.getPixels(row, 0, 1, right, top, 1, colH);
            for (int i = 0; i < colH; i++) {
                if (Color.alpha(row[i]) > TRIM_ALPHA_THRESHOLD) {
                    break rightScan;
                }
            }
        }

        final int newW = right - left + 1;
        final int newH = bottom - top + 1;
        if (newW <= 0 || newH <= 0 || (newW == w && newH == h)) {
            return source;
        }
        if (newW < w * (1f - MAX_TRIM_FRACTION_PER_AXIS)
                || newH < h * (1f - MAX_TRIM_FRACTION_PER_AXIS)) {
            return source;
        }

        Bitmap out = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawBitmap(source, new Rect(left, top, right + 1, bottom + 1), new Rect(0, 0, newW, newH),
                sCropPaint);
        if (!source.isRecycled()) {
            source.recycle();
        }
        return out;
    }

    /**
     * Crops to the center {@code side * visibleFraction} of the largest centered square in
     * {@code source}, then returns a new square bitmap. Recycles {@code source} when it produces
     * a new bitmap.
     */
    private static Bitmap cropCenterSquare(Bitmap source, float visibleFraction) {
        if (visibleFraction >= 1f || visibleFraction <= 0f) {
            return source;
        }
        final int w = source.getWidth();
        final int h = source.getHeight();
        final int side = Math.min(w, h);
        final int srcX = (w - side) / 2;
        final int srcY = (h - side) / 2;
        final int inset = Math.round(side * (1f - visibleFraction) * 0.5f);
        final int newSide = side - 2 * inset;
        if (newSide <= 0 || newSide >= side) {
            return source;
        }

        Bitmap out = Bitmap.createBitmap(newSide, newSide, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        final Rect src =
                new Rect(srcX + inset, srcY + inset, srcX + inset + newSide, srcY + inset + newSide);
        c.drawBitmap(source, src, new Rect(0, 0, newSide, newSide), sCropPaint);
        if (!source.isRecycled()) {
            source.recycle();
        }
        return out;
    }
}
