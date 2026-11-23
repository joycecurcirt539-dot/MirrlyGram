package org.telegram.utils.glass;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.utils.glass.source.GlassSource;
import org.telegram.utils.glass.source.GlassSourceBitmap;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceWrapped;

import java.lang.ref.WeakReference;

/**
 * Zero-allocation zoned and per-column luminance estimator, WCAG 2.1 multi-layer contrast solver,
 * hysteresis controller, and horizontal adaptive gradient generator for Liquid Glass overlays.
 */
public final class GlassContrastHelper {

    public static final int ADAPTIVE_DARK_TEXT = 0xFF0E1218;
    public static final int ADAPTIVE_DARK_SUBTITLE = 0xDE0E1218;
    public static final int ADAPTIVE_DARK_HINT = 0xB00E1218;
    public static final int ADAPTIVE_LIGHT_TEXT = 0xFFFFFFFF;
    public static final int ADAPTIVE_LIGHT_SUBTITLE = 0xEBFFFFFF;
    public static final int ADAPTIVE_LIGHT_HINT = 0xC4FFFFFF;

    // Equal-contrast WCAG 2.1 crossover between #FFFFFF (L=1.0) and #0E1218 (L=0.0046) is L ≈ 0.179.
    private static final float WCAG_CROSSOVER = 0.179f;
    private static final float WCAG_HYST_LOW = 0.148f;
    private static final float WCAG_HYST_HIGH = 0.214f;

    private static final float[] SRGB_TO_LINEAR_LUT = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            final float c = i / 255f;
            SRGB_TO_LINEAR_LUT[i] = c <= 0.04045f
                    ? (c / 12.92f)
                    : (float) Math.pow((c + 0.055) / 1.055, 2.4);
        }
    }

    /**
     * Reusable container populated by {@link #computeHorizontalAdaptiveBand} with zero draw-loop allocations.
     */
    public static final class HorizontalAdaptiveBand {
        public static final int SAMPLE_COUNT = 5;
        public final float[] positions = new float[SAMPLE_COUNT];
        public final int[] colors = new int[SAMPLE_COUNT];
        public final float[] columnEffSrgb = new float[SAMPLE_COUNT];
        public boolean useGradient;
        public int solidColor;
        public float haloStrength;
        public float avgInvertFactor;
        public int gradientWidth;

        public HorizontalAdaptiveBand() {
            for (int i = 0; i < SAMPLE_COUNT; i++) {
                positions[i] = i / (float) (SAMPLE_COUNT - 1);
            }
        }
    }

    private GlassContrastHelper() {
    }

    public static float computeLuminance(int color) {
        return (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f;
    }

    public static float computeRelativeLuminance(int color) {
        final float r = SRGB_TO_LINEAR_LUT[Color.red(color)];
        final float g = SRGB_TO_LINEAR_LUT[Color.green(color)];
        final float b = SRGB_TO_LINEAR_LUT[Color.blue(color)];
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    public static float srgbScalarToWcag(float srgb) {
        final float c = MathUtils.clamp(srgb, 0f, 1f);
        return c <= 0.04045f
                ? (c / 12.92f)
                : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    public static float computeContrastRatio(int fgColor, float bgWcagLuma) {
        final float bg = MathUtils.clamp(bgWcagLuma, 0f, 1f);
        final float fgAlpha = Color.alpha(fgColor) / 255f;
        float fgLuma = computeRelativeLuminance(fgColor);
        if (fgAlpha < 0.999f) {
            fgLuma = fgLuma * fgAlpha + bg * (1f - fgAlpha);
        }
        final float hi = Math.max(fgLuma, bg);
        final float lo = Math.min(fgLuma, bg);
        return (hi + 0.05f) / (lo + 0.05f);
    }

    public static boolean isDarkInkColor(int color) {
        return computeRelativeLuminance(color) < WCAG_CROSSOVER;
    }

    public static float computeColorContrastLuminance(int color, float backdropLuma) {
        final float alpha = Color.alpha(color) / 255f;
        final float fgLuma = computeLuminance(color);
        return fgLuma * alpha + MathUtils.clamp(backdropLuma, 0f, 1f) * (1f - alpha);
    }

    private static float getRelOffsetX(@Nullable View view, @Nullable View ancestor) {
        float x = 0f;
        View current = view;
        while (current != null && current != ancestor) {
            x += current.getX();
            final ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        if (current == ancestor) {
            return x;
        }
        return getWindowX(view) - getWindowX(ancestor);
    }

    private static float getRelOffsetY(@Nullable View view, @Nullable View ancestor) {
        float y = 0f;
        View current = view;
        while (current != null && current != ancestor) {
            y += current.getY();
            final ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        if (current == ancestor) {
            return y;
        }
        return getWindowY(view) - getWindowY(ancestor);
    }

    private static float getWindowX(@Nullable View view) {
        float x = 0f;
        View current = view;
        while (current != null) {
            x += current.getX();
            final ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return x;
    }

    private static float getWindowY(@Nullable View view) {
        float y = 0f;
        View current = view;
        while (current != null) {
            y += current.getY();
            final ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return y;
    }

    public static float estimateUnderlyingLuminance(
            @Nullable View glassView,
            @Nullable ViewGroup listViewOrContainer,
            float fallbackBgLuma
    ) {
        if (glassView == null) {
            return MathUtils.clamp(fallbackBgLuma, 0f, 1f);
        }
        return estimateUnderlyingLuminanceInBand(
                glassView,
                listViewOrContainer,
                0f,
                0f,
                glassView.getWidth(),
                glassView.getHeight(),
                fallbackBgLuma,
                null
        );
    }

    public static float estimateUnderlyingLuminanceInBand(
            @Nullable View glassView,
            @Nullable ViewGroup listViewOrContainer,
            float localLeft,
            float localTop,
            float localRight,
            float localBottom,
            float fallbackBgLuma,
            @Nullable Theme.ResourcesProvider resourcesProvider
    ) {
        return estimateUnderlyingLuminanceInBand(
                glassView,
                listViewOrContainer,
                localLeft,
                localTop,
                localRight,
                localBottom,
                null,
                null,
                0,
                0,
                fallbackBgLuma,
                resourcesProvider
        );
    }

    public static float estimateUnderlyingLuminanceInBand(
            @Nullable View glassView,
            @Nullable ViewGroup listViewOrContainer,
            float localLeft,
            float localTop,
            float localRight,
            float localBottom,
            @Nullable GlassSource backdropSource,
            @Nullable Drawable backdropDrawable,
            int viewportW,
            int viewportH,
            float fallbackBgLuma,
            @Nullable Theme.ResourcesProvider resourcesProvider
    ) {
        final float clampedFallback = MathUtils.clamp(fallbackBgLuma, 0f, 1f);
        if (glassView == null) {
            return clampedFallback;
        }
        if (localRight <= localLeft || localBottom <= localTop) {
            return clampedFallback;
        }

        final float offsetX = listViewOrContainer != null ? getRelOffsetX(glassView, listViewOrContainer) : getWindowX(glassView);
        final float offsetY = listViewOrContainer != null ? getRelOffsetY(glassView, listViewOrContainer) : getWindowY(glassView);
        final float zoneLeft = offsetX + localLeft;
        final float zoneTop = offsetY + localTop;
        final float zoneRight = offsetX + localRight;
        final float zoneBottom = offsetY + localBottom;

        float regionBackdrop = clampedFallback;
        if (backdropSource != null || backdropDrawable != null) {
            final int refW = viewportW > 0 ? viewportW : (listViewOrContainer != null && listViewOrContainer.getWidth() > 0 ? listViewOrContainer.getWidth() : 1080);
            final int refH = viewportH > 0 ? viewportH : (listViewOrContainer != null && listViewOrContainer.getHeight() > 0 ? listViewOrContainer.getHeight() : 1920);
            final float rx0 = MathUtils.clamp(zoneLeft / Math.max(1f, refW), 0f, 1f);
            final float ry0 = MathUtils.clamp(zoneTop / Math.max(1f, refH), 0f, 1f);
            final float rx1 = MathUtils.clamp(zoneRight / Math.max(1f, refW), 0f, 1f);
            final float ry1 = MathUtils.clamp(zoneBottom / Math.max(1f, refH), 0f, 1f);
            regionBackdrop = estimateBackdropLuminanceInRegion(backdropSource, backdropDrawable, rx0, ry0, rx1, ry1, clampedFallback);
        }

        if (listViewOrContainer == null || listViewOrContainer.getChildCount() == 0) {
            return regionBackdrop;
        }

        return estimateUnderlyingLuminanceInContainerRect(
                glassView,
                listViewOrContainer,
                zoneLeft,
                zoneTop,
                zoneRight,
                zoneBottom,
                regionBackdrop,
                resourcesProvider
        );
    }

    public static float estimateUnderlyingLuminanceInContainerRect(
            @Nullable View excludeView,
            @Nullable ViewGroup container,
            float zoneLeft,
            float zoneTop,
            float zoneRight,
            float zoneBottom,
            float fallbackBgLuma,
            @Nullable Theme.ResourcesProvider resourcesProvider
    ) {
        final float clampedFallback = MathUtils.clamp(fallbackBgLuma, 0f, 1f);
        if (container == null || zoneRight <= zoneLeft || zoneBottom <= zoneTop || container.getChildCount() == 0) {
            return clampedFallback;
        }

        final float zoneArea = Math.max(1f, (zoneRight - zoneLeft) * (zoneBottom - zoneTop));
        final RecyclerListView parentList = container instanceof RecyclerListView ? (RecyclerListView) container : null;
        final boolean hasSectionDecoration = parentList != null && parentList.hasSections();
        final float effectiveBackdrop = hasSectionDecoration
                ? computeLuminance(parentList.getSectionsBackdropColor())
                : clampedFallback;

        float weightedLumaSum = 0f;
        float totalCoverage = 0f;

        final int childCount = container.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = container.getChildAt(i);
            if (child == null || child == excludeView || child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0.05f) {
                continue;
            }
            if (child.getWidth() <= 0 || child.getHeight() <= 0) {
                continue;
            }
            final float cLeft = child.getX();
            final float cTop = child.getY();
            final float cRight = cLeft + child.getWidth();
            final float cBottom = cTop + child.getHeight();

            final float overlapLeft = Math.max(zoneLeft, cLeft);
            final float overlapTop = Math.max(zoneTop, cTop);
            final float overlapRight = Math.min(zoneRight, cRight);
            final float overlapBottom = Math.min(zoneBottom, cBottom);
            if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) {
                continue;
            }

            final float overlapArea = ((overlapRight - overlapLeft) * (overlapBottom - overlapTop)) * child.getAlpha();
            final float childWeight = MathUtils.clamp(overlapArea / zoneArea, 0f, 1f);
            if (childWeight <= 0.005f) {
                continue;
            }

            float sectionBase = effectiveBackdrop;
            if (hasSectionDecoration && parentList.isVisualSectionChild(child)) {
                final int secPad = parentList.getSectionHorizontalPadding();
                final float secLeft = secPad;
                final float secRight = Math.max(secLeft, parentList.getWidth() - secPad);
                final float secOverlapW = Math.max(0f, Math.min(overlapRight, secRight) - Math.max(overlapLeft, secLeft));
                final float ovW = Math.max(1f, overlapRight - overlapLeft);
                final float secCov = MathUtils.clamp(secOverlapW / ovW, 0f, 1f);
                final float cardLuma = computeColorContrastLuminance(parentList.getSectionSurfaceColor(), effectiveBackdrop);
                sectionBase = cardLuma * secCov + effectiveBackdrop * (1f - secCov);
            }

            final float childLuma = estimateChildLuminanceInLocalRect(
                    child,
                    overlapLeft - cLeft,
                    overlapTop - cTop,
                    overlapRight - cLeft,
                    overlapBottom - cTop,
                    sectionBase,
                    resourcesProvider,
                    0
            );
            weightedLumaSum += childLuma * childWeight;
            totalCoverage += childWeight;
        }

        if (totalCoverage <= 0.02f) {
            return effectiveBackdrop;
        }
        final float normCoverage = MathUtils.clamp(totalCoverage, 0f, 1f);
        final float measuredLuma = weightedLumaSum / totalCoverage;
        return MathUtils.clamp(measuredLuma * normCoverage + effectiveBackdrop * (1f - normCoverage), 0f, 1f);
    }

    private static float estimateChildLuminanceInLocalRect(
            View child,
            float localLeft,
            float localTop,
            float localRight,
            float localBottom,
            float fallbackBgLuma,
            @Nullable Theme.ResourcesProvider rp,
            int depth
    ) {
        if (child instanceof ChatMessageCell) {
            return estimateChatMessageCellLuminance(
                    (ChatMessageCell) child,
                    localLeft,
                    localTop,
                    localRight,
                    localBottom,
                    fallbackBgLuma,
                    rp
            );
        }

        if (child instanceof ChatActionCell) {
            final ChatActionCell actionCell = (ChatActionCell) child;
            final float bl = actionCell.getBoundsLeft();
            final float br = actionCell.getBoundsRight();
            final float bandW = Math.max(1f, localRight - localLeft);
            final float pillOverlapW = Math.max(0f, Math.min(localRight, br) - Math.max(localLeft, bl));
            final float cov = MathUtils.clamp(pillOverlapW / bandW, 0f, 1f);
            if (cov > 0.05f) {
                final int serviceBg = Theme.getColor(Theme.key_chat_serviceBackground, rp);
                final float pillLuma = computeColorContrastLuminance(serviceBg, fallbackBgLuma);
                return pillLuma * cov + fallbackBgLuma * (1f - cov);
            }
            return fallbackBgLuma;
        }

        float surfaceLuma = fallbackBgLuma;
        final Drawable bg = child.getBackground();
        if (bg != null) {
            final float bgLuma = extractDrawableLuminance(bg, localLeft, localTop, localRight, localBottom, child.getWidth(), child.getHeight(), fallbackBgLuma);
            if (bgLuma >= 0f) {
                surfaceLuma = bgLuma;
            }
        }

        if (child instanceof BackupImageView) {
            final ImageReceiver receiver = ((BackupImageView) child).getImageReceiver();
            if (receiver != null) {
                return sampleImageReceiverOrFallback(receiver, localLeft, localTop, localRight, localBottom, surfaceLuma);
            }
        } else if (child instanceof ImageView) {
            final Drawable d = ((ImageView) child).getDrawable();
            if (d instanceof BitmapDrawable) {
                final Bitmap bmp = ((BitmapDrawable) d).getBitmap();
                if (isReadableBitmap(bmp)) {
                    final float rx0 = MathUtils.clamp(localLeft / Math.max(1f, child.getWidth()), 0f, 1f);
                    final float rx1 = MathUtils.clamp(localRight / Math.max(1f, child.getWidth()), 0f, 1f);
                    final float ry0 = MathUtils.clamp(localTop / Math.max(1f, child.getHeight()), 0f, 1f);
                    final float ry1 = MathUtils.clamp(localBottom / Math.max(1f, child.getHeight()), 0f, 1f);
                    return sampleBitmapRegionLuminance(bmp, rx0, ry0, rx1, ry1, surfaceLuma);
                }
            }
        }

        if (child instanceof ViewGroup && depth < 2) {
            final ViewGroup group = (ViewGroup) child;
            final float bandArea = Math.max(1f, (localRight - localLeft) * (localBottom - localTop));
            float subSum = 0f;
            float subWeight = 0f;
            for (int i = 0, n = group.getChildCount(); i < n; i++) {
                final View sub = group.getChildAt(i);
                if (sub == null || sub.getVisibility() != View.VISIBLE || sub.getAlpha() <= 0.05f || sub.getWidth() <= 0 || sub.getHeight() <= 0) {
                    continue;
                }
                final float sl = sub.getX();
                final float st = sub.getY();
                final float sr = sl + sub.getWidth();
                final float sb = st + sub.getHeight();
                final float ol = Math.max(localLeft, sl);
                final float ot = Math.max(localTop, st);
                final float or = Math.min(localRight, sr);
                final float ob = Math.min(localBottom, sb);
                if (or <= ol || ob <= ot) {
                    continue;
                }
                final float areaFrac = MathUtils.clamp(((or - ol) * (ob - ot)) / bandArea, 0f, 1f) * sub.getAlpha();
                if (areaFrac <= 0.05f) {
                    continue;
                }
                final Drawable subBg = sub.getBackground();
                if (sub instanceof BackupImageView || sub instanceof ImageView
                        || (subBg != null && extractDrawableLuminance(subBg, 0f, 0f, sub.getWidth(), sub.getHeight(), sub.getWidth(), sub.getHeight(), -1f) >= 0f)
                        || (depth == 0 && sub instanceof ViewGroup)) {
                    final float subLuma = estimateChildLuminanceInLocalRect(sub, ol - sl, ot - st, or - sl, ob - st, surfaceLuma, rp, depth + 1);
                    subSum += subLuma * areaFrac;
                    subWeight += areaFrac;
                }
            }
            if (subWeight > 0.05f) {
                final float cov = MathUtils.clamp(subWeight, 0f, 1f);
                return (subSum / subWeight) * cov + surfaceLuma * (1f - cov);
            }
        }

        return surfaceLuma;
    }

    private static float estimateChatMessageCellLuminance(
            ChatMessageCell cell,
            float localLeft,
            float localTop,
            float localRight,
            float localBottom,
            float fallbackBgLuma,
            @Nullable Theme.ResourcesProvider rp
    ) {
        final float zoneArea = Math.max(1f, (localRight - localLeft) * (localBottom - localTop));
        final MessageObject msg = cell.getMessageObject();
        final boolean isOut = msg != null && msg.isOutOwner();

        float surfaceBaseLuma = fallbackBgLuma;

        // 1. Incoming group avatar circle on the left side of the message
        final ImageReceiver avatar = cell.getAvatarImage();
        if (avatar != null && avatar.getVisible() && avatar.getImageWidth() > 0 && avatar.getImageHeight() > 0) {
            final float al = avatar.getImageX();
            final float at = avatar.getImageY();
            final float ar = avatar.getImageX2();
            final float ab = avatar.getImageY2();
            final float ol = Math.max(localLeft, al);
            final float ot = Math.max(localTop, at);
            final float or = Math.min(localRight, ar);
            final float ob = Math.min(localBottom, ab);
            if (or > ol && ob > ot) {
                final float avCov = MathUtils.clamp((((or - ol) * (ob - ot)) / zoneArea) * 0.8f, 0f, 1f);
                final Bitmap bmp = resolveReadableBitmap(avatar);
                if (bmp != null) {
                    final float rx0 = MathUtils.clamp((ol - al) / Math.max(1f, ar - al), 0f, 1f);
                    final float ry0 = MathUtils.clamp((ot - at) / Math.max(1f, ab - at), 0f, 1f);
                    final float rx1 = MathUtils.clamp((or - al) / Math.max(1f, ar - al), 0f, 1f);
                    final float ry1 = MathUtils.clamp((ob - at) / Math.max(1f, ab - at), 0f, 1f);
                    final float aLuma = sampleBitmapRegionLuminance(bmp, rx0, ry0, rx1, ry1, surfaceBaseLuma);
                    surfaceBaseLuma = aLuma * avCov + surfaceBaseLuma * (1f - avCov);
                }
            }
        }

        // 2. Message bubble layer
        if (cell.currentBackgroundDrawable != null && (msg == null || !msg.shouldDrawWithoutBackground())) {
            final int bLeft = cell.getBackgroundDrawableLeft();
            final int bTop = cell.getBackgroundDrawableTop();
            final int bRight = cell.getBackgroundDrawableRight();
            final int bBottom = cell.getBackgroundDrawableBottom();
            if (bRight > bLeft && bBottom > bTop) {
                final float ol = Math.max(localLeft, bLeft);
                final float ot = Math.max(localTop, bTop);
                final float or = Math.min(localRight, bRight);
                final float ob = Math.min(localBottom, bBottom);
                if (or > ol && ob > ot) {
                    final float bubbleCoverage = MathUtils.clamp(((or - ol) * (ob - ot)) / zoneArea, 0f, 1f);
                    final int primary = Theme.getColor(isOut ? Theme.key_chat_outBubble : Theme.key_chat_inBubble, rp);
                    float bubbleLuma = computeColorContrastLuminance(primary, fallbackBgLuma);
                    if (isOut) {
                        final int grad1 = Theme.getColor(Theme.key_chat_outBubbleGradient1, rp);
                        if (Color.alpha(grad1) > 12) {
                            final float g1Luma = computeColorContrastLuminance(grad1, fallbackBgLuma);
                            bubbleLuma = (bubbleLuma + g1Luma) * 0.5f;
                        }
                    }
                    surfaceBaseLuma = bubbleLuma * bubbleCoverage + surfaceBaseLuma * (1f - bubbleCoverage);
                }
            }
        }

        // 3. Primary photo / video / round video / sticker image receiver
        final ImageReceiver photo = cell.getPhotoImage();
        if (photo != null && (photo.getVisible() || (msg != null && msg.isRoundVideo()))) {
            final float imgLeft = photo.getImageX();
            final float imgTop = photo.getImageY();
            final float imgRight = photo.getImageX2();
            final float imgBottom = photo.getImageY2();
            if (imgRight > imgLeft && imgBottom > imgTop) {
                final float ol = Math.max(localLeft, imgLeft);
                final float ot = Math.max(localTop, imgTop);
                final float or = Math.min(localRight, imgRight);
                final float ob = Math.min(localBottom, imgBottom);
                if (or > ol && ob > ot) {
                    float imgCov = MathUtils.clamp(((or - ol) * (ob - ot)) / zoneArea, 0f, 1f);
                    if (msg != null && msg.isRoundVideo()) {
                        final float cx = (imgLeft + imgRight) * 0.5f;
                        final float cy = (imgTop + imgBottom) * 0.5f;
                        final float radius = Math.min(imgRight - imgLeft, imgBottom - imgTop) * 0.5f;
                        final float nearX = MathUtils.clamp(cx, ol, or);
                        final float nearY = MathUtils.clamp(cy, ot, ob);
                        final float dist2 = (nearX - cx) * (nearX - cx) + (nearY - cy) * (nearY - cy);
                        if (dist2 > radius * radius) {
                            imgCov = 0f;
                        } else {
                            final float midX = (ol + or) * 0.5f;
                            final float midY = (ot + ob) * 0.5f;
                            final float centerDist = (float) Math.hypot(midX - cx, midY - cy);
                            final float circleWeight = MathUtils.clamp(1.15f - centerDist / Math.max(1f, radius), 0.25f, 1f);
                            imgCov *= circleWeight;
                        }
                    }
                    if (imgCov > 0.01f) {
                        final float rx0 = MathUtils.clamp((ol - imgLeft) / Math.max(1f, imgRight - imgLeft), 0f, 1f);
                        final float ry0 = MathUtils.clamp((ot - imgTop) / Math.max(1f, imgBottom - imgTop), 0f, 1f);
                        final float rx1 = MathUtils.clamp((or - imgLeft) / Math.max(1f, imgRight - imgLeft), 0f, 1f);
                        final float ry1 = MathUtils.clamp((ob - imgTop) / Math.max(1f, imgBottom - imgTop), 0f, 1f);

                        final Bitmap bmp = resolveReadableBitmap(photo);
                        if (bmp != null) {
                            final float bmpLuma = sampleBitmapRegionLuminance(bmp, rx0, ry0, rx1, ry1, surfaceBaseLuma);
                            surfaceBaseLuma = bmpLuma * imgCov + surfaceBaseLuma * (1f - imgCov);
                        } else if (msg != null && (msg.isVideo() || msg.isRoundVideo())) {
                            final float darkMediaFloor = Math.min(surfaceBaseLuma, 0.20f);
                            surfaceBaseLuma = darkMediaFloor * imgCov + surfaceBaseLuma * (1f - imgCov);
                        }
                    }
                }
            }
        }

        // 4. Reply thumbnail preview
        final ImageReceiver replyImg = cell.replyImageReceiver;
        if (replyImg != null && replyImg.getVisible() && replyImg.hasImageSet() && replyImg.getImageWidth() > 0 && replyImg.getImageHeight() > 0) {
            final float rl = replyImg.getImageX();
            final float rt = replyImg.getImageY();
            final float rr = replyImg.getImageX2();
            final float rb = replyImg.getImageY2();
            final float ol = Math.max(localLeft, rl);
            final float ot = Math.max(localTop, rt);
            final float or = Math.min(localRight, rr);
            final float ob = Math.min(localBottom, rb);
            if (or > ol && ob > ot) {
                final float rCov = MathUtils.clamp(((or - ol) * (ob - ot)) / zoneArea, 0f, 1f);
                final Bitmap bmp = resolveReadableBitmap(replyImg);
                if (bmp != null) {
                    final float rx0 = MathUtils.clamp((ol - rl) / Math.max(1f, rr - rl), 0f, 1f);
                    final float ry0 = MathUtils.clamp((ot - rt) / Math.max(1f, rb - rt), 0f, 1f);
                    final float rx1 = MathUtils.clamp((or - rl) / Math.max(1f, rr - rl), 0f, 1f);
                    final float ry1 = MathUtils.clamp((ob - rt) / Math.max(1f, rb - rt), 0f, 1f);
                    final float rLuma = sampleBitmapRegionLuminance(bmp, rx0, ry0, rx1, ry1, surfaceBaseLuma);
                    surfaceBaseLuma = rLuma * rCov + surfaceBaseLuma * (1f - rCov);
                }
            }
        }

        return surfaceBaseLuma;
    }

    @Nullable
    private static Bitmap resolveReadableBitmap(@Nullable ImageReceiver receiver) {
        if (receiver == null) {
            return null;
        }
        final Bitmap bmp = receiver.getBitmap();
        if (isReadableBitmap(bmp)) {
            return bmp;
        }
        final Bitmap thumb = receiver.getThumbBitmap();
        if (isReadableBitmap(thumb)) {
            return thumb;
        }
        return null;
    }

    private static boolean isReadableBitmap(@Nullable Bitmap bmp) {
        if (bmp == null || bmp.isRecycled()) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || bmp.getConfig() != Bitmap.Config.HARDWARE;
    }

    private static float sampleImageReceiverOrFallback(
            ImageReceiver receiver,
            float localLeft,
            float localTop,
            float localRight,
            float localBottom,
            float fallback
    ) {
        final float imgLeft = receiver.getImageX();
        final float imgTop = receiver.getImageY();
        final float imgRight = receiver.getImageX2();
        final float imgBottom = receiver.getImageY2();
        if (imgRight <= imgLeft || imgBottom <= imgTop) {
            return fallback;
        }
        final float ol = Math.max(localLeft, imgLeft);
        final float ot = Math.max(localTop, imgTop);
        final float or = Math.min(localRight, imgRight);
        final float ob = Math.min(localBottom, imgBottom);
        if (or <= ol || ob <= ot) {
            return fallback;
        }
        final Bitmap bmp = resolveReadableBitmap(receiver);
        if (bmp == null) {
            return fallback;
        }
        final float zoneArea = Math.max(1f, (localRight - localLeft) * (localBottom - localTop));
        final float cov = MathUtils.clamp(((or - ol) * (ob - ot)) / zoneArea, 0f, 1f);
        final float rx0 = MathUtils.clamp((ol - imgLeft) / Math.max(1f, imgRight - imgLeft), 0f, 1f);
        final float ry0 = MathUtils.clamp((ot - imgTop) / Math.max(1f, imgBottom - imgTop), 0f, 1f);
        final float rx1 = MathUtils.clamp((or - imgLeft) / Math.max(1f, imgRight - imgLeft), 0f, 1f);
        final float ry1 = MathUtils.clamp((ob - imgTop) / Math.max(1f, imgBottom - imgTop), 0f, 1f);
        final float bmpLuma = sampleBitmapRegionLuminance(bmp, rx0, ry0, rx1, ry1, fallback);
        return bmpLuma * cov + fallback * (1f - cov);
    }

    private static float extractDrawableLuminance(
            Drawable bg,
            float localLeft,
            float localTop,
            float localRight,
            float localBottom,
            int viewW,
            int viewH,
            float backdropLuma
    ) {
        if (bg instanceof ChatBackgroundDrawable) {
            final Drawable inner = ((ChatBackgroundDrawable) bg).getDrawable(false);
            if (inner != null && inner != bg) {
                return extractDrawableLuminance(inner, localLeft, localTop, localRight, localBottom, viewW, viewH, backdropLuma);
            }
        }
        if (bg instanceof MotionBackgroundDrawable) {
            final MotionBackgroundDrawable motion = (MotionBackgroundDrawable) bg;
            if (motion.getIntensity() < 0) {
                return computeColorContrastLuminance(Color.BLACK, backdropLuma);
            }
            final Bitmap bmp = motion.getBitmap();
            if (isReadableBitmap(bmp) && viewW > 0 && viewH > 0) {
                return sampleBitmapRegionLuminance(
                        bmp,
                        MathUtils.clamp(localLeft / viewW, 0f, 1f),
                        MathUtils.clamp(localTop / viewH, 0f, 1f),
                        MathUtils.clamp(localRight / viewW, 0f, 1f),
                        MathUtils.clamp(localBottom / viewH, 0f, 1f),
                        backdropLuma
                );
            }
        }
        if (bg instanceof ColorDrawable) {
            final int color = ((ColorDrawable) bg).getColor();
            if (Color.alpha(color) <= 4) {
                return -1f;
            }
            return computeColorContrastLuminance(color, backdropLuma);
        }
        if (bg instanceof BitmapDrawable) {
            final Bitmap bmp = ((BitmapDrawable) bg).getBitmap();
            if (isReadableBitmap(bmp) && viewW > 0 && viewH > 0) {
                return sampleBitmapRegionLuminance(
                        bmp,
                        MathUtils.clamp(localLeft / viewW, 0f, 1f),
                        MathUtils.clamp(localTop / viewH, 0f, 1f),
                        MathUtils.clamp(localRight / viewW, 0f, 1f),
                        MathUtils.clamp(localBottom / viewH, 0f, 1f),
                        backdropLuma
                );
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && bg instanceof GradientDrawable) {
            final int[] colors = ((GradientDrawable) bg).getColors();
            if (colors != null && colors.length > 0) {
                float sum = 0f;
                int count = 0;
                for (int c : colors) {
                    if (Color.alpha(c) > 4) {
                        sum += computeColorContrastLuminance(c, backdropLuma);
                        count++;
                    }
                }
                if (count > 0) {
                    return sum / count;
                }
            }
        }
        return -1f;
    }

    private static final int BITMAP_GRID_DIM = 4;
    private static final int BITMAP_GRID_LEN = BITMAP_GRID_DIM * BITMAP_GRID_DIM;
    private static final int BITMAP_GRID_CACHE_CAP = 16;

    private static final class CachedBitmapGrid {
        @NonNull WeakReference<Bitmap> bitmapRef = new WeakReference<>(null);
        int generationId;
        int width;
        int height;
        final int[] pixels = new int[BITMAP_GRID_LEN];
        long lastUsedTick;
    }

    private static final Object BITMAP_GRID_LOCK = new Object();
    private static final CachedBitmapGrid[] BITMAP_GRID_CACHE = new CachedBitmapGrid[BITMAP_GRID_CACHE_CAP];
    private static long bitmapGridTick;
    private static final ThreadLocal<int[]> BITMAP_GRID_SCRATCH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[BITMAP_GRID_LEN];
        }
    };

    private static boolean getOrSampleBitmapGrid(Bitmap bmp, int bw, int bh, int[] outPixels) {
        if (!isReadableBitmap(bmp)) {
            return false;
        }
        final int genId = bmp.getGenerationId();
        synchronized (BITMAP_GRID_LOCK) {
            for (int i = 0; i < BITMAP_GRID_CACHE_CAP; i++) {
                final CachedBitmapGrid entry = BITMAP_GRID_CACHE[i];
                if (entry != null
                        && entry.bitmapRef.get() == bmp
                        && entry.generationId == genId
                        && entry.width == bw
                        && entry.height == bh) {
                    entry.lastUsedTick = ++bitmapGridTick;
                    System.arraycopy(entry.pixels, 0, outPixels, 0, BITMAP_GRID_LEN);
                    return true;
                }
            }
        }
        try {
            for (int gy = 0; gy < BITMAP_GRID_DIM; gy++) {
                final int py = MathUtils.clamp(Math.round(((gy + 0.5f) / BITMAP_GRID_DIM) * (bh - 1)), 0, bh - 1);
                final int rowOffset = gy * BITMAP_GRID_DIM;
                for (int gx = 0; gx < BITMAP_GRID_DIM; gx++) {
                    final int px = MathUtils.clamp(Math.round(((gx + 0.5f) / BITMAP_GRID_DIM) * (bw - 1)), 0, bw - 1);
                    outPixels[rowOffset + gx] = bmp.getPixel(px, py);
                }
            }
        } catch (Throwable ignore) {
            return false;
        }
        synchronized (BITMAP_GRID_LOCK) {
            int targetIdx = -1;
            long oldestTick = Long.MAX_VALUE;
            for (int i = 0; i < BITMAP_GRID_CACHE_CAP; i++) {
                final CachedBitmapGrid entry = BITMAP_GRID_CACHE[i];
                if (entry == null) {
                    targetIdx = i;
                    break;
                }
                final Bitmap existing = entry.bitmapRef.get();
                if (existing == null || existing == bmp) {
                    targetIdx = i;
                    break;
                }
                if (entry.lastUsedTick < oldestTick) {
                    oldestTick = entry.lastUsedTick;
                    targetIdx = i;
                }
            }
            CachedBitmapGrid slot = BITMAP_GRID_CACHE[targetIdx];
            if (slot == null) {
                slot = new CachedBitmapGrid();
                BITMAP_GRID_CACHE[targetIdx] = slot;
            }
            slot.bitmapRef = new WeakReference<>(bmp);
            slot.generationId = genId;
            slot.width = bw;
            slot.height = bh;
            slot.lastUsedTick = ++bitmapGridTick;
            System.arraycopy(outPixels, 0, slot.pixels, 0, BITMAP_GRID_LEN);
        }
        return true;
    }

    /**
     * Samples up to 6 points inside the intersected region from a cached 4x4 grid and composites transparent pixels over {@code backdrop}.
     */
    private static float sampleBitmapRegionLuminance(
            Bitmap bmp,
            float relX0,
            float relY0,
            float relX1,
            float relY1,
            float backdrop
    ) {
        try {
            final int bw = bmp.getWidth();
            final int bh = bmp.getHeight();
            if (bw <= 0 || bh <= 0) {
                return backdrop;
            }
            final int[] grid = BITMAP_GRID_SCRATCH.get();
            if (grid == null || !getOrSampleBitmapGrid(bmp, bw, bh, grid)) {
                return backdrop;
            }
            final float xSpan = Math.max(0.02f, relX1 - relX0);
            final float ySpan = Math.max(0.02f, relY1 - relY0);
            final int gx0 = MathUtils.clamp((int) ((relX0 + xSpan * 0.20f) * BITMAP_GRID_DIM), 0, BITMAP_GRID_DIM - 1);
            final int gx1 = MathUtils.clamp((int) ((relX0 + xSpan * 0.50f) * BITMAP_GRID_DIM), 0, BITMAP_GRID_DIM - 1);
            final int gx2 = MathUtils.clamp((int) ((relX0 + xSpan * 0.80f) * BITMAP_GRID_DIM), 0, BITMAP_GRID_DIM - 1);
            final int row0 = MathUtils.clamp((int) ((relY0 + ySpan * 0.30f) * BITMAP_GRID_DIM), 0, BITMAP_GRID_DIM - 1) * BITMAP_GRID_DIM;
            final int row1 = MathUtils.clamp((int) ((relY0 + ySpan * 0.70f) * BITMAP_GRID_DIM), 0, BITMAP_GRID_DIM - 1) * BITMAP_GRID_DIM;

            float sum = 0f;
            sum += computeColorContrastLuminance(grid[row0 + gx0], backdrop);
            sum += computeColorContrastLuminance(grid[row0 + gx1], backdrop);
            sum += computeColorContrastLuminance(grid[row0 + gx2], backdrop);
            sum += computeColorContrastLuminance(grid[row1 + gx0], backdrop);
            sum += computeColorContrastLuminance(grid[row1 + gx1], backdrop);
            sum += computeColorContrastLuminance(grid[row1 + gx2], backdrop);
            return sum / 6f;
        } catch (Throwable ignore) {
            return backdrop;
        }
    }

    public static float estimateBackdropLuminanceInRegion(
            @Nullable GlassSource backdropSource,
            @Nullable Drawable wallpaperDrawable,
            float relX0,
            float relY0,
            float relX1,
            float relY1,
            float fallbackBgLuma
    ) {
        final float clampedFallback = MathUtils.clamp(fallbackBgLuma, 0f, 1f);
        GlassSource source = backdropSource;
        int depth = 0;
        while (source instanceof GlassSourceWrapped && depth < 4) {
            source = ((GlassSourceWrapped) source).getSource();
            depth++;
        }
        if (source instanceof GlassSourceColor) {
            final int color = ((GlassSourceColor) source).getColor();
            if (Color.alpha(color) > 8) {
                return computeColorContrastLuminance(color, clampedFallback);
            }
        } else if (source instanceof GlassSourceBitmap) {
            final Bitmap bmp = ((GlassSourceBitmap) source).getBitmap();
            if (isReadableBitmap(bmp)) {
                return sampleBitmapRegionLuminance(bmp, relX0, relY0, relX1, relY1, clampedFallback);
            }
        }
        final Drawable wp = wallpaperDrawable != null ? wallpaperDrawable : Theme.getCachedWallpaperNonBlocking();
        if (wp != null) {
            final float wpLuma = extractDrawableLuminance(wp, relX0 * 100f, relY0 * 100f, relX1 * 100f, relY1 * 100f, 100, 100, clampedFallback);
            if (wpLuma >= 0f) {
                return wpLuma;
            }
        }
        return clampedFallback;
    }

    public static float resolveThemeBackdropLuminance(@Nullable Theme.ResourcesProvider rp, int fallbackBgColor) {
        final float fallbackLuma = computeLuminance(fallbackBgColor);
        final Drawable wp = Theme.getCachedWallpaperNonBlocking();
        if (wp != null) {
            final float extracted = extractDrawableLuminance(wp, 0f, 0f, 1f, 1f, 1, 1, fallbackLuma);
            if (extracted >= 0f) {
                return extracted;
            }
        }
        return fallbackLuma;
    }

    /**
     * Computes actual glass surface luminance matching the pure refractive composite in liquid_glass_shader.agsl.
     */
    public static float computeEffectiveGlassLuminance(int glassBgColor, float underlyingLuma, boolean liquidShaderActive) {
        final float bgLuma = MathUtils.clamp(underlyingLuma, 0f, 1f);
        final float fgA = MathUtils.clamp((Color.alpha(glassBgColor)) / 255f, 0f, 1f);
        if (fgA <= 0.001f) {
            return bgLuma;
        }
        final float fgLuma = computeLuminance(glassBgColor);
        return MathUtils.clamp(fgLuma * fgA + bgLuma * (1f - fgA), 0f, 1f);
    }

    /**
     * Composites premultiplied shader foreground over an underlying grayscale luminance in sRGB channel space,
     * then converts the exact resulting surface RGB to WCAG 2.1 relative luminance.
     */
    public static float computeCompositeWcagLuminance(int glassBgColor, float underlyingSrgbLuma) {
        final float bg = MathUtils.clamp(underlyingSrgbLuma, 0f, 1f) * 255f;
        final float fgA = MathUtils.clamp(Color.alpha(glassBgColor) / 255f, 0f, 1f);
        final float invA = 1f - fgA;
        final int outR = MathUtils.clamp(Math.round(Color.red(glassBgColor) * fgA + bg * invA), 0, 255);
        final int outG = MathUtils.clamp(Math.round(Color.green(glassBgColor) * fgA + bg * invA), 0, 255);
        final int outB = MathUtils.clamp(Math.round(Color.blue(glassBgColor) * fgA + bg * invA), 0, 255);
        return 0.2126f * SRGB_TO_LINEAR_LUT[outR]
                + 0.7152f * SRGB_TO_LINEAR_LUT[outG]
                + 0.0722f * SRGB_TO_LINEAR_LUT[outB];
    }

    public static float updateContrastInvertFactor(float currentFactor, float effectiveGlassLuma, boolean themeExpectsDarkText) {
        return updateContrastInvertFactor(currentFactor, effectiveGlassLuma, themeExpectsDarkText, 0.24f);
    }

    public static float updateContrastInvertFactor(float currentFactor, float effectiveGlassLuma, boolean themeExpectsDarkText, float dampingStep) {
        final float wcagL = srgbScalarToWcag(effectiveGlassLuma);
        final float target;
        if (themeExpectsDarkText) {
            if (wcagL < WCAG_HYST_LOW) {
                target = 1f;
            } else if (wcagL > WCAG_HYST_HIGH) {
                target = 0f;
            } else {
                target = currentFactor >= 0.5f ? 1f : 0f;
            }
        } else {
            if (wcagL > WCAG_HYST_HIGH) {
                target = 1f;
            } else if (wcagL < WCAG_HYST_LOW) {
                target = 0f;
            } else {
                target = currentFactor >= 0.5f ? 1f : 0f;
            }
        }
        float next = currentFactor + (target - currentFactor) * MathUtils.clamp(dampingStep, 0.05f, 1f);
        if (Math.abs(target - next) < 0.008f) {
            next = target;
        }
        return MathUtils.clamp(next, 0f, 1f);
    }

    /**
     * Computes an ink color that is guaranteed WCAG-legible over a glass surface with {@code effSrgbLuma},
     * never leaving dark-on-dark or white-on-white regardless of glass tint opacity or mixed layers.
     */
    public static int resolveAdaptiveColorForLuma(int baseColor, float effSrgbLuma, boolean isSubtitle, boolean isAccent) {
        return resolveAdaptiveColorFromWcag(baseColor, srgbScalarToWcag(effSrgbLuma), isSubtitle, isAccent);
    }

    public static int resolveAdaptiveColorFromWcag(int baseColor, float effWcagLuma, boolean isSubtitle, boolean isAccent) {
        final float bgL = MathUtils.clamp(effWcagLuma, 0f, 1f);
        final int alpha = Color.alpha(baseColor);
        if (alpha == 0) {
            return baseColor;
        }
        final boolean needLightInk = bgL < WCAG_CROSSOVER;
        final float currentRatio = computeContrastRatio(baseColor, bgL);
        final float minOkRatio = isAccent ? 3.8f : (isSubtitle ? 4.3f : 4.8f);
        final boolean baseIsDark = computeRelativeLuminance(baseColor) < WCAG_CROSSOVER;

        if (currentRatio >= minOkRatio && (needLightInk != baseIsDark)) {
            return baseColor;
        }

        final int lightTarget = isSubtitle ? ADAPTIVE_LIGHT_SUBTITLE : ADAPTIVE_LIGHT_TEXT;
        final int darkTarget = isSubtitle ? ADAPTIVE_DARK_SUBTITLE : ADAPTIVE_DARK_TEXT;

        if (isAccent) {
            final int chroma = Math.max(Math.abs(Color.red(baseColor) - Color.green(baseColor)),
                    Math.max(Math.abs(Color.green(baseColor) - Color.blue(baseColor)), Math.abs(Color.red(baseColor) - Color.blue(baseColor))));
            if (chroma > 28) {
                final int boosted = needLightInk
                        ? ColorUtils.blendARGB(baseColor, Color.WHITE, 0.72f)
                        : ColorUtils.blendARGB(baseColor, 0xFF0A2444, 0.68f);
                return computeContrastRatio(boosted, bgL) >= 3.8f ? boosted : (needLightInk ? Color.WHITE : 0xFF0A2444);
            }
        }

        final int idealInk = needLightInk ? lightTarget : darkTarget;
        if (currentRatio >= minOkRatio) {
            final float nudge = MathUtils.clamp((5.8f - currentRatio) / 3.0f, 0.15f, 0.65f);
            return ColorUtils.blendARGB(baseColor, idealInk, nudge);
        }
        return ColorUtils.setAlphaComponent(idealInk, Math.max(alpha, Color.alpha(idealInk)));
    }

    /**
     * Resolves an adaptive icon or button tint directly over its own bounding rectangle.
     */
    public static int estimateIconAdaptiveColor(
            @Nullable View iconView,
            @Nullable ViewGroup listViewOrContainer,
            int glassBgColor,
            int baseIconColor,
            @Nullable GlassSource backdropSource,
            @Nullable Drawable backdropDrawable,
            int viewportW,
            int viewportH,
            float fallbackBgLuma,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            boolean isAccent,
            boolean liquidShaderActive
    ) {
        if (iconView == null || iconView.getWidth() <= 0 || iconView.getHeight() <= 0) {
            return baseIconColor;
        }
        final float under = estimateUnderlyingLuminanceInBand(
                iconView,
                listViewOrContainer,
                0f, 0f, iconView.getWidth(), iconView.getHeight(),
                backdropSource, backdropDrawable,
                viewportW, viewportH,
                fallbackBgLuma,
                resourcesProvider
        );
        final float wcag = computeCompositeWcagLuminance(glassBgColor, under);
        return resolveAdaptiveColorFromWcag(baseIconColor, wcag, false, isAccent);
    }

    /**
     * Evaluates N horizontal columns across a text element so letters crossing dark/light boundaries
     * dynamically receive per-column contrasting colors via a horizontal {@link android.graphics.LinearGradient}.
     * Performs zero allocations.
     */
    public static void computeHorizontalAdaptiveBand(
            @Nullable View hostView,
            @Nullable ViewGroup listViewOrContainer,
            float localLeft,
            float localTop,
            float localRight,
            float localBottom,
            int glassBgColor,
            int baseColor,
            @Nullable GlassSource backdropSource,
            @Nullable Drawable backdropDrawable,
            int viewportW,
            int viewportH,
            float fallbackBgLuma,
            @Nullable Theme.ResourcesProvider rp,
            boolean isSubtitle,
            boolean isAccent,
            boolean liquidShaderActive,
            @NonNull HorizontalAdaptiveBand outBand
    ) {
        final float spanW = localRight - localLeft;
        final float spanH = localBottom - localTop;
        if (hostView == null || spanW <= 1f || spanH <= 1f) {
            outBand.useGradient = false;
            outBand.solidColor = baseColor;
            outBand.haloStrength = 0.25f;
            outBand.avgInvertFactor = 0f;
            outBand.gradientWidth = 0;
            return;
        }

        final int n = HorizontalAdaptiveBand.SAMPLE_COUNT;
        final float colHalf = Math.max(AndroidUtilities.dpf2(6f), spanW / (n * 1.4f));
        float minWcag = 1f;
        float maxWcag = 0f;
        int darkCount = 0;
        int lightCount = 0;
        float sumWcag = 0f;

        for (int i = 0; i < n; i++) {
            final float frac = i / (float) (n - 1);
            final float cx = localLeft + spanW * frac;
            final float sLeft = Math.max(localLeft, cx - colHalf);
            final float sRight = Math.min(localRight, cx + colHalf);
            final float under = estimateUnderlyingLuminanceInBand(
                    hostView,
                    listViewOrContainer,
                    sLeft, localTop, Math.max(sLeft + 1f, sRight), localBottom,
                    backdropSource, backdropDrawable,
                    viewportW, viewportH,
                    fallbackBgLuma,
                    rp
            );
            final float effSrgb = computeEffectiveGlassLuminance(glassBgColor, under, liquidShaderActive);
            final float wcag = computeCompositeWcagLuminance(glassBgColor, under);
            outBand.columnEffSrgb[i] = effSrgb;
            sumWcag += wcag;
            if (wcag < minWcag) minWcag = wcag;
            if (wcag > maxWcag) maxWcag = wcag;

            final int colColor = resolveAdaptiveColorFromWcag(baseColor, wcag, isSubtitle, isAccent);
            outBand.colors[i] = colColor;
            if (computeRelativeLuminance(colColor) < WCAG_CROSSOVER) {
                darkCount++;
            } else {
                lightCount++;
            }
        }

        final float avgWcag = sumWcag / n;
        final int uniformColor = resolveAdaptiveColorFromWcag(baseColor, avgWcag, isSubtitle, isAccent);
        final boolean baseIsDark = computeRelativeLuminance(baseColor) < WCAG_CROSSOVER;
        final boolean avgIsDark = computeRelativeLuminance(uniformColor) < WCAG_CROSSOVER;
        outBand.avgInvertFactor = (baseIsDark != avgIsDark) ? 1f : (uniformColor != baseColor ? 0.6f : 0f);

        final boolean mixedZones = darkCount > 0 && lightCount > 0
                && spanW >= AndroidUtilities.dp(22)
                && (maxWcag - minWcag) >= 0.08f;

        outBand.useGradient = mixedZones;
        outBand.solidColor = uniformColor;
        outBand.gradientWidth = Math.max(1, Math.round(spanW));
        outBand.haloStrength = mixedZones
                ? 0.68f
                : ((avgWcag > 0.08f && avgWcag < 0.35f) ? 0.62f : (outBand.avgInvertFactor > 0.01f ? 0.55f : 0.25f));
    }

    private static float crispContrastCurve(float t) {
        final float clamped = MathUtils.clamp(t, 0f, 1f);
        // Compress mid-gray crossing so text switches cleanly without lingering at unreadable 50% gray.
        return clamped * clamped * (3f - 2f * clamped);
    }

    public static int blendAdaptiveTextColor(int baseTextColor, float invertFactor, boolean isSubtitle) {
        if (invertFactor <= 0.001f) {
            return baseTextColor;
        }
        final boolean baseIsDark = computeRelativeLuminance(baseTextColor) < WCAG_CROSSOVER;
        final int oppositeColor = baseIsDark
                ? (isSubtitle ? ADAPTIVE_LIGHT_SUBTITLE : ADAPTIVE_LIGHT_TEXT)
                : (isSubtitle ? ADAPTIVE_DARK_SUBTITLE : ADAPTIVE_DARK_TEXT);
        return ColorUtils.blendARGB(baseTextColor, oppositeColor, crispContrastCurve(invertFactor));
    }

    public static int blendAdaptiveHintColor(int baseHintColor, float invertFactor) {
        if (invertFactor <= 0.001f) {
            return baseHintColor;
        }
        final boolean baseIsDark = computeRelativeLuminance(baseHintColor) < WCAG_CROSSOVER;
        final int oppositeColor = baseIsDark ? ADAPTIVE_LIGHT_HINT : ADAPTIVE_DARK_HINT;
        return ColorUtils.blendARGB(baseHintColor, oppositeColor, crispContrastCurve(invertFactor));
    }

    public static int blendAdaptiveIconColor(int baseIconColor, float invertFactor) {
        if (invertFactor <= 0.001f) {
            return baseIconColor;
        }
        final boolean baseIsDark = computeRelativeLuminance(baseIconColor) < WCAG_CROSSOVER;
        final int oppositeColor = baseIsDark ? ADAPTIVE_LIGHT_TEXT : ADAPTIVE_DARK_TEXT;
        return ColorUtils.blendARGB(baseIconColor, oppositeColor, crispContrastCurve(invertFactor));
    }

    public static int blendAdaptiveAccentColor(int baseAccentColor, float invertFactor) {
        if (invertFactor <= 0.001f) {
            return baseAccentColor;
        }
        final boolean baseIsDark = computeRelativeLuminance(baseAccentColor) < WCAG_CROSSOVER;
        final int boostedTarget = baseIsDark
                ? ColorUtils.blendARGB(baseAccentColor, Color.WHITE, 0.78f)
                : ColorUtils.blendARGB(baseAccentColor, 0xFF0A2444, 0.70f);
        return ColorUtils.blendARGB(baseAccentColor, boostedTarget, MathUtils.clamp(invertFactor, 0f, 1f));
    }

    public static void applyOpticalTextHalo(@Nullable TextPaint paint, int textColor, boolean enabled) {
        applyOpticalTextHalo(paint, textColor, enabled ? 0.55f : 0f);
    }

    public static void applyOpticalTextHalo(@Nullable TextPaint paint, int textColor, float strength) {
        if (paint == null) {
            return;
        }
        final int alpha = Color.alpha(textColor);
        final float clamped = MathUtils.clamp(strength, 0f, 1f);
        if (clamped < 0.45f || alpha == 0) {
            paint.clearShadowLayer();
            return;
        }
        final boolean isDarkText = computeRelativeLuminance(textColor) < WCAG_CROSSOVER;
        final int maxHaloAlpha = isDarkText ? 0x70 : 0x7C;
        final int haloAlpha = Math.round(maxHaloAlpha * clamped * (alpha / 255f));
        final int haloColor = ColorUtils.setAlphaComponent(isDarkText ? Color.WHITE : Color.BLACK, haloAlpha);
        final float radius = AndroidUtilities.dpf2(1.1f + 1.1f * clamped);
        final float dy = AndroidUtilities.dpf2(0.35f + 0.25f * clamped);
        paint.setShadowLayer(radius, 0f, dy, haloColor);
    }
}
