package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.SharedConfig;

/**
 * Custom animated outline eye drawable matching Telegram's icon stroke style.
 * Displays an open eye when content is hidden (tap to show) and smoothly sweeps
 * a masked diagonal slash across the eye when content is visible (tap to hide).
 */
public class MirrlyPhoneEyeDrawable extends Drawable {

    private static final long ANIM_DURATION_MS = 260L;

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF layerBounds = new RectF();
    private final Path eyePath = new Path();
    private final Path fullSlashPath = new Path();
    private final Path partialSlashPath = new Path();
    private final PathMeasure slashMeasure = new PathMeasure();

    private final int sizeDp;
    private int color = Color.WHITE;
    private int alpha = 255;

    private boolean slashed;
    private float currentProgress;
    private float startProgress;
    private float targetProgress;
    private long animStartTime;
    private boolean animating;

    public MirrlyPhoneEyeDrawable(int sizeDp) {
        this.sizeDp = sizeDp;

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        pupilPaint.setStyle(Paint.Style.STROKE);
        pupilPaint.setStrokeCap(Paint.Cap.ROUND);
        pupilPaint.setStrokeJoin(Paint.Join.ROUND);

        clearPaint.setStyle(Paint.Style.STROKE);
        clearPaint.setStrokeCap(Paint.Cap.ROUND);
        clearPaint.setStrokeJoin(Paint.Join.ROUND);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void setColor(int color) {
        if (this.color != color) {
            this.color = color;
            invalidateSelf();
        }
    }

    public boolean isSlashed() {
        return slashed;
    }

    public void setSlashed(boolean slashed, boolean animated) {
        if (this.slashed == slashed && !animating) {
            float exact = slashed ? 1f : 0f;
            if (currentProgress != exact) {
                currentProgress = targetProgress = exact;
                invalidateSelf();
            }
            return;
        }
        this.slashed = slashed;
        float newTarget = slashed ? 1f : 0f;
        if (!animated || !SharedConfig.animationsEnabled()) {
            animating = false;
            currentProgress = startProgress = targetProgress = newTarget;
            invalidateSelf();
            return;
        }
        startProgress = currentProgress;
        targetProgress = newTarget;
        animStartTime = SystemClock.uptimeMillis();
        animating = true;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (animating) {
            long elapsed = SystemClock.uptimeMillis() - animStartTime;
            float raw = Math.min(1f, Math.max(0f, elapsed / (float) ANIM_DURATION_MS));
            float eased = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(raw);
            currentProgress = startProgress + (targetProgress - startProgress) * eased;
            if (raw >= 1f) {
                currentProgress = targetProgress;
                animating = false;
            } else {
                invalidateSelf();
            }
        }

        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }

        float size = Math.min(bounds.width(), bounds.height());
        if (size <= 0f) {
            return;
        }
        float scale = size / 24f;
        float offsetX = bounds.exactCenterX() - 12f * scale;
        float offsetY = bounds.exactCenterY() - 12f * scale;

        float strokeW = Math.max(dpf2(1.1f), 1.32f * scale);
        strokePaint.setColor(color);
        strokePaint.setAlpha(alpha);
        strokePaint.setStrokeWidth(strokeW);

        pupilPaint.setColor(color);
        pupilPaint.setAlpha(alpha);
        pupilPaint.setStrokeWidth(strokeW);

        clearPaint.setStrokeWidth(strokeW * 2.35f);

        float squint = 0.20f * currentProgress;
        float topCtrlY = 6.3f + squint * 1.8f;
        float botCtrlY = 17.7f - squint * 1.8f;
        float edgeCtrlOffset = 2.85f;

        eyePath.rewind();
        eyePath.moveTo(3.5f, 12f);
        eyePath.cubicTo(3.5f + edgeCtrlOffset, topCtrlY, 8.4f, topCtrlY, 12f, topCtrlY);
        eyePath.cubicTo(15.6f, topCtrlY, 20.5f - edgeCtrlOffset, topCtrlY, 20.5f, 12f);
        eyePath.cubicTo(20.5f - edgeCtrlOffset, botCtrlY, 15.6f, botCtrlY, 12f, botCtrlY);
        eyePath.cubicTo(8.4f, botCtrlY, 3.5f + edgeCtrlOffset, botCtrlY, 3.5f, 12f);
        eyePath.close();

        float pupilScale = 1f - 0.14f * currentProgress;
        float pupilR = 2.15f * pupilScale;

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        boolean needCutoutLayer = currentProgress > 0.01f;
        int saveCount = -1;
        if (needCutoutLayer) {
            layerBounds.set(-2f, -2f, 26f, 26f);
            saveCount = canvas.saveLayer(layerBounds, null);
        }

        canvas.drawPath(eyePath, strokePaint);
        canvas.drawCircle(12f, 12f, pupilR, pupilPaint);

        if (needCutoutLayer) {
            fullSlashPath.rewind();
            fullSlashPath.moveTo(5.1f, 5.1f);
            fullSlashPath.lineTo(18.9f, 18.9f);
            slashMeasure.setPath(fullSlashPath, false);
            float totalLen = slashMeasure.getLength();

            partialSlashPath.rewind();
            slashMeasure.getSegment(0f, totalLen * currentProgress, partialSlashPath, true);

            // Clear a symmetric halo gap around the slash before drawing the stroke.
            canvas.drawPath(partialSlashPath, clearPaint);
            canvas.drawPath(partialSlashPath, strokePaint);
            canvas.restoreToCount(saveCount);
        }

        canvas.restore();
    }

    @Override
    public int getIntrinsicWidth() {
        return dp(sizeDp);
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(sizeDp);
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha != alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        strokePaint.setColorFilter(colorFilter);
        pupilPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
