package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.mirrly.MirrlyConfig;

public class DialogsActivityTopBubblesFadeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private Shader shader;
    private int lastColor;
    private boolean lastLiquid;

    public DialogsActivityTopBubblesFadeView(Context context) {
        super(context);
    }

    public void setColor(int color) {
        final boolean liquid = MirrlyConfig.isGlassLiquidEnabled();
        if (lastColor != color || lastLiquid != liquid || shader == null) {
            lastColor = color;
            lastLiquid = liquid;

            final int alpha = Color.alpha(color);
            final int a0 = liquid ? 0x96 : 0xE8;
            final int a1 = liquid ? 0x6E : 0xC0;
            final int a2 = liquid ? 0x3C : 0x90;
            shader = new LinearGradient(0, 0, 0, 1, new int[]{
                    ColorUtils.setAlphaComponent(color, a0 * alpha / 255),
                    ColorUtils.setAlphaComponent(color, a1 * alpha / 255),
                    ColorUtils.setAlphaComponent(color, a2 * alpha / 255),
                    ColorUtils.setAlphaComponent(color, 0)
            }, null, Shader.TileMode.CLAMP);
            paint.setShader(shader);
            shader.setLocalMatrix(matrix);
            invalidate();
        }
    }

    private float fadeStart, fadeHeight;

    public void setPosition(float fadeStart, float fadeHeight) {
        if (this.fadeStart != fadeStart || this.fadeHeight != fadeHeight) {
            this.fadeStart = fadeStart;
            this.fadeHeight = fadeHeight;

            matrix.reset();
            matrix.setScale(1, fadeHeight);
            matrix.postTranslate(0, fadeStart);
            if (shader != null) {
                shader.setLocalMatrix(matrix);
            }
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (lastLiquid != MirrlyConfig.isGlassLiquidEnabled()) {
            setColor(lastColor);
        }
        canvas.drawRect(0, 0, getMeasuredWidth(), fadeStart + fadeHeight, paint);
    }
}
