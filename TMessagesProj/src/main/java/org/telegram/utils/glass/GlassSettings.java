package org.telegram.utils.glass;

import java.util.Objects;

public class GlassSettings {

    public final boolean isBlurEnabled;
    public final boolean isLiquidGlassEnabled;
    public final boolean isLiquidGlassEverywhere;
    public final float blurScale;
    public final float opacityScale;
    public final float saturationScale;
    public final float refractScale;
    public final int thicknessDp;
    public final float rimScale;

    public GlassSettings(boolean isBlurEnabled, boolean isLiquidGlassEnabled) {
        this(isBlurEnabled, isLiquidGlassEnabled, true, 1.0f, 1.0f, 1.0f, 1.0f, 11, 1.0f);
    }

    public GlassSettings(
            boolean isBlurEnabled,
            boolean isLiquidGlassEnabled,
            float blurScale,
            float opacityScale,
            float saturationScale,
            float refractScale,
            int thicknessDp,
            float rimScale
    ) {
        this(isBlurEnabled, isLiquidGlassEnabled, true, blurScale, opacityScale, saturationScale, refractScale, thicknessDp, rimScale);
    }

    public GlassSettings(
            boolean isBlurEnabled,
            boolean isLiquidGlassEnabled,
            boolean isLiquidGlassEverywhere,
            float blurScale,
            float opacityScale,
            float saturationScale,
            float refractScale,
            int thicknessDp,
            float rimScale
    ) {
        this.isBlurEnabled = isBlurEnabled;
        this.isLiquidGlassEnabled = isLiquidGlassEnabled;
        this.isLiquidGlassEverywhere = isLiquidGlassEverywhere;
        this.blurScale = blurScale;
        this.opacityScale = opacityScale;
        this.saturationScale = saturationScale;
        this.refractScale = refractScale;
        this.thicknessDp = thicknessDp;
        this.rimScale = rimScale;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GlassSettings)) return false;
        GlassSettings settings = (GlassSettings) o;
        return isBlurEnabled == settings.isBlurEnabled
                && isLiquidGlassEnabled == settings.isLiquidGlassEnabled
                && isLiquidGlassEverywhere == settings.isLiquidGlassEverywhere
                && Float.compare(settings.blurScale, blurScale) == 0
                && Float.compare(settings.opacityScale, opacityScale) == 0
                && Float.compare(settings.saturationScale, saturationScale) == 0
                && Float.compare(settings.refractScale, refractScale) == 0
                && thicknessDp == settings.thicknessDp
                && Float.compare(settings.rimScale, rimScale) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                isBlurEnabled,
                isLiquidGlassEnabled,
                isLiquidGlassEverywhere,
                blurScale,
                opacityScale,
                saturationScale,
                refractScale,
                thicknessDp,
                rimScale
        );
    }
}
