package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyInterfaceConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedLinearLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.glass.GlassTabView;

import java.util.HashSet;
import java.util.Set;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.util.ClickHelper;

@SuppressLint("ViewConstructor")
public class MainTabsLayout extends AnimatedLinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    public MainTabsLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    private static final float[] PASS_TEXT_SIZES_DP = {12f, 12f, 10f};
    private static final int[] PASS_PADDINGS_DP = {16, 8, 4};

    private int maxWidthPx;
    private BlurredBackgroundDrawable blurredBackgroundDrawable;
    private boolean fullWidthCentering;
    private boolean collapsedCircleMode;
    private int horizontalAlignment = MirrlyInterfaceConfig.ALIGN_CENTER;
    private final FactorAnimator alignAnimator = new FactorAnimator(
        0,
        (id, factor, fraction, callee) -> updatePillAndEntries(),
        CubicBezierInterpolator.EASE_OUT_QUINT,
        320L,
        0.5f
    );
    @Nullable
    private Runnable onPillLayoutChangedListener;
    private final Path pillClipPath = new Path();
    private final RectF pillClipRectF = new RectF();
    private float lastPillClipLeft, lastPillClipTop, lastPillClipRight, lastPillClipBottom, lastPillClipRadius = -1f;
    private int lastPillBoundsLeft = Integer.MIN_VALUE, lastPillBoundsTop, lastPillBoundsRight, lastPillBoundsBottom, lastPillBoundsAlpha = -1;
    private int lastPillEntriesCount = -1;

    public void setMaxWidth(int maxWidthPx) {
        if (this.maxWidthPx != maxWidthPx) {
            this.maxWidthPx = maxWidthPx;
            requestLayout();
        }
    }

    public void setBlurredBackgroundDrawable(@Nullable BlurredBackgroundDrawable drawable) {
        this.blurredBackgroundDrawable = drawable;
        this.fullWidthCentering = drawable != null;
        requestLayout();
        invalidate();
    }

    public void setCollapsedCircleMode(boolean collapsed) {
        if (this.collapsedCircleMode != collapsed) {
            this.collapsedCircleMode = collapsed;
            requestLayout();
            invalidate();
        }
    }

    public void setOnPillLayoutChangedListener(@Nullable Runnable listener) {
        this.onPillLayoutChangedListener = listener;
    }

    public void setHorizontalAlignment(int alignment, boolean animated) {
        final int clamped = alignment == MirrlyInterfaceConfig.ALIGN_LEFT
                ? MirrlyInterfaceConfig.ALIGN_LEFT
                : (alignment == MirrlyInterfaceConfig.ALIGN_RIGHT
                        ? MirrlyInterfaceConfig.ALIGN_RIGHT
                        : MirrlyInterfaceConfig.ALIGN_CENTER);
        final float targetFactor = clamped == MirrlyInterfaceConfig.ALIGN_LEFT
                ? 0f
                : (clamped == MirrlyInterfaceConfig.ALIGN_RIGHT ? 1f : 0.5f);
        if (this.horizontalAlignment == clamped && !alignAnimator.isAnimating()
                && Math.abs(alignAnimator.getFactor() - targetFactor) < 0.001f) {
            return;
        }
        this.horizontalAlignment = clamped;
        if (animated && isAttachedToWindow() && getWidth() > 0) {
            alignAnimator.animateTo(targetFactor, this);
        } else {
            alignAnimator.forceFactor(targetFactor);
        }
        updatePillAndEntries();
    }

    public int getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public boolean isPointInsidePill(float localX, float localY) {
        if (blurredBackgroundDrawable != null) {
            final Rect padded = blurredBackgroundDrawable.getPaddedBounds();
            if (!padded.isEmpty()) {
                return localX >= padded.left - dp(4) && localX <= padded.right + dp(4)
                        && localY >= padded.top - dp(4) && localY <= padded.bottom + dp(4);
            }
            final Rect bounds = blurredBackgroundDrawable.getBounds();
            if (!bounds.isEmpty()) {
                return localX >= bounds.left && localX <= bounds.right
                        && localY >= bounds.top && localY <= bounds.bottom;
            }
            return false;
        }
        return localX >= 0 && localX <= getWidth() && localY >= 0 && localY <= getHeight();
    }

    public void getPillSampleBounds(@NonNull Rect out) {
        if (blurredBackgroundDrawable != null) {
            final Rect padded = blurredBackgroundDrawable.getPaddedBounds();
            if (!padded.isEmpty()) {
                out.set(padded);
                return;
            }
        }
        out.set(0, 0, getWidth(), getHeight());
    }

    public void pulseBounce() {
        if (blurredBackgroundDrawable != null) {
            final Rect bounds = blurredBackgroundDrawable.getBounds();
            if (!bounds.isEmpty()) {
                setPivotX(bounds.exactCenterX());
                setPivotY(bounds.exactCenterY());
            }
        }
        scaleX.cancel();
        scaleY.cancel();
        scaleX.setStartValue(0.91f);
        scaleY.setStartValue(0.91f);
        scaleX.animateToFinalPosition(1f);
        scaleY.animateToFinalPosition(1f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int rawWidth = MeasureSpec.getSize(widthMeasureSpec);
        int width = rawWidth;
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = height - getPaddingTop() - getPaddingBottom();

        if (maxWidthPx > 0 && width > maxWidthPx) {
            width = maxWidthPx;
        }

        final int maxTotalWidthForTabs = Math.max(0, width - getPaddingLeft() - getPaddingRight());

        int chosenPass = PASS_TEXT_SIZES_DP.length - 1;
        float lastMeasuredTextSize = -1;
        for (int pass = 0; pass < PASS_TEXT_SIZES_DP.length; pass++) {
            if (PASS_TEXT_SIZES_DP[pass] != lastMeasuredTextSize) {
                measureTabTexts(PASS_TEXT_SIZES_DP[pass]);
                lastMeasuredTextSize = PASS_TEXT_SIZES_DP[pass];
            }
            final int padding = dp(PASS_PADDINGS_DP[pass]);
            float total = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                if (!isViewVisible(getChildAt(a))) continue;
                final float withMargin = tabsTextWidth[a] + padding * 2;
                total += withMargin;
            }
            final boolean fits = total <= maxTotalWidthForTabs;
            if (fits || pass == PASS_TEXT_SIZES_DP.length - 1) {
                chosenPass = pass;
                break;
            }
        }

        final int minWidthDp;
        if (collapsedCircleMode && visibleChildCount == 1) {
            minWidthDp = 48;
        } else if (visibleChildCount <= 0) {
            minWidthDp = 0;
        } else if (visibleChildCount == 1) {
            minWidthDp = 80;
        } else if (visibleChildCount == 2) {
            minWidthDp = 168;
        } else if (visibleChildCount == 3) {
            minWidthDp = 248;
        } else {
            minWidthDp = 320;
        }
        final int minTotalWidthForTabs = Math.min(dp(minWidthDp), maxTotalWidthForTabs);

        applyPassTextSize(chosenPass);

        final int tabPadding = dp(PASS_PADDINGS_DP[chosenPass]);
        final int maxTabTextWidthIfEq = (maxTotalWidthForTabs / Math.max(1, visibleChildCount)) - tabPadding * 2;

        float totalWidth = 0;
        int totalWeight = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = tabsTextWidthWithMargin[a] = 0;
                tabsWeight[a] = 0;
                continue;
            }

            tabsTextWidthWithMargin[a] = tabsTextWidth[a] + tabPadding * 2;
            tabsWeight[a] = tabsTextWidthWithMargin[a] > (maxTabTextWidthIfEq + tabPadding * 2) ? 0 : 1;

            totalWidth += tabsTextWidthWithMargin[a];
            totalWeight += tabsWeight[a];
        }

        if (totalWeight == 0) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsWeight[a] = isViewVisible(getChildAt(a)) ? 1 : 0;
            }
            totalWeight = visibleChildCount;
        }

        if (totalWidth > maxTotalWidthForTabs && totalWidth > 0) {
            final float m = maxTotalWidthForTabs / totalWidth;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] *= m;
            }
        } else if (totalWidth < minTotalWidthForTabs && totalWeight > 0) {
            final float growW = minTotalWidthForTabs - totalWidth;
            final float growP = growW / totalWeight;

            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] += growP * tabsWeight[a];
            }
        }

        int l = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            if (!isViewVisible(getChildAt(a))) {
                continue;
            }

            tabsWidth[a] = Math.round(tabsTextWidthWithMargin[a]);
            tabsLeftPos[a] = l;
            l += tabsWidth[a];
        }
        final int measuredWidth = fullWidthCentering ? rawWidth : (l + getPaddingLeft() + getPaddingRight());
        setMeasuredDimension(measuredWidth, height);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            final int childW = Math.max(0, tabsWidth[a]);
            child.measure(
                MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, tabHeight), MeasureSpec.EXACTLY));
        }

        calculateTotalSizesAfterMeasure();
    }

    public interface Tab {
        float measureTextWidth();
        default float measureTextWidth(float textSizeDp) { return measureTextWidth(); }
        default void setTextSizeDp(float textSizeDp) {}
    }



    // fills tabsTextWidth[] and return visible child count;

    private float[] tabsTextWidth;
    private float[] tabsTextWidthWithMargin;
    private int[] tabsWeight;
    private int[] tabsWidth;

    private int[] tabsLeftPos;


    private int visibleChildCount;
    private int biggestTabTextWidth;

    private void measureTabTexts(float textSizeDp) {
        final int childCount = getChildCount();
        if (tabsTextWidth == null || tabsTextWidth.length < childCount) {
            tabsTextWidth = new float[childCount];
            tabsTextWidthWithMargin = new float[childCount];
            tabsWeight = new int[childCount];
            tabsLeftPos = new int[childCount];
            tabsWidth = new int[childCount];
        }

        float maxTabWidthF = 0;
        int index = 0;

        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = -1;
                continue;
            }

            final float tabWidth;
            if (child instanceof MainTabsLayout.Tab) {
                tabWidth = ((MainTabsLayout.Tab) child).measureTextWidth(textSizeDp);
            } else {
                tabWidth = 0;
            }

            tabsTextWidth[a] = tabWidth;
            maxTabWidthF = Math.max(maxTabWidthF, tabWidth);
            index++;
        }

        biggestTabTextWidth = (int) Math.ceil(maxTabWidthF);
        visibleChildCount = index;
    }

    private void applyPassTextSize(int pass) {
        final float textSizeDp = PASS_TEXT_SIZES_DP[pass];
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof MainTabsLayout.Tab) {
                ((MainTabsLayout.Tab) child).setTextSizeDp(textSizeDp);
            }
        }
    }

    @Override
    protected void setChildVisibilityFactor(View view, float factor) {
        final float s = lerp(0.7f, 1f, factor);
        view.setAlpha(factor);
        if (factor < 0.999f || !view.isPressed()) {
            view.setScaleX(s);
            view.setScaleY(s);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updatePillAndEntries();
    }

    @Override
    protected void onItemsChanged() {
        super.onItemsChanged();
        updatePillAndEntries();
    }

    private void updatePillAndEntries() {
        if (fullWidthCentering && blurredBackgroundDrawable != null) {
            final float contentW = getMetadata().getTotalWidth();
            final float visibility = MathUtils.clamp(getMetadata().getTotalVisibility(), 0f, 1f);
            final float pillTotalW = contentW > 0.5f ? (contentW + getPaddingLeft() + getPaddingRight()) : 0f;
            final float maxShift = Math.max(0f, getWidth() - pillTotalW);
            final float edgeMargin = Math.min(dp(12), maxShift * 0.5f);
            final float minLeft = edgeMargin;
            final float maxLeft = Math.max(edgeMargin, getWidth() - edgeMargin - pillTotalW);
            final float pillLeft = pillTotalW > 0f ? lerp(minLeft, maxLeft, alignAnimator.getFactor()) : 0f;
            final float pillRight = pillLeft + pillTotalW;
            final int bl = Math.round(pillLeft);
            final int bt = 0;
            final int br = Math.round(pillRight);
            final int bb = getHeight();
            final int ba = Math.round(255f * visibility);
            boolean pillChanged = lastPillBoundsLeft != bl || lastPillBoundsTop != bt
                    || lastPillBoundsRight != br || lastPillBoundsBottom != bb || lastPillBoundsAlpha != ba;
            lastPillBoundsLeft = bl;
            lastPillBoundsTop = bt;
            lastPillBoundsRight = br;
            lastPillBoundsBottom = bb;
            lastPillBoundsAlpha = ba;
            blurredBackgroundDrawable.setBounds(bl, bt, br, bb);
            blurredBackgroundDrawable.setAlpha(ba);

            final int count = getEntriesCount();
            if (lastPillEntriesCount != count) {
                lastPillEntriesCount = count;
                pillChanged = true;
            }
            for (int a = 0; a < count; a++) {
                final ListAnimator.Entry<Holder> entry = getEntry(a);
                final View view = entry.item.view;
                final RectF pos = entry.getRectF();
                final float tx = (pillLeft + getPaddingLeft() + pos.left) - view.getLeft();
                if (Math.abs(view.getTranslationX() - tx) > 0.05f) {
                    view.setTranslationX(tx);
                    pillChanged = true;
                }
                if (view instanceof GlassTabView) {
                    ((GlassTabView) view).setVisualWidth(pos.width());
                }
            }
            if (onPillLayoutChangedListener != null && pillChanged) {
                onPillLayoutChangedListener.run();
            }
            if (pillChanged || drawCustomSelector) {
                invalidate();
            }
        } else {
            checkVisualWidth();
        }
    }

    private void checkVisualWidth() {
        for (int a = 0, N = getEntriesCount(); a < N; a++) {
            final ListAnimator.Entry<Holder> entry = getEntry(a);
            final float width = entry.getRectF().width();
            ((GlassTabView) entry.item.view).setVisualWidth(width);
        }
    }








    public void setTabSelected(View tab, boolean animated) {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSelected(child == tab, animated);
            }
        }
    }

    private View findSelectedTab() {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }

            if (child instanceof GlassTabView) {
                if (((GlassTabView) child).isTabSelected()) {
                    return child;
                }
            }
        }
        return null;
    }

    private final Runnable restoreDrawSelector = () -> setSkipDrawSelector(false);

    private boolean drawCustomSelector;
    private void setSkipDrawSelector(boolean skipDrawSelector) {
        drawCustomSelector = skipDrawSelector;
        if (drawCustomSelector) {
            selectorPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider), 0.09f));
        }
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }

            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSkipDrawSelector(skipDrawSelector);
            }
        }
        invalidate();
    }







    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final boolean hasPillBg = blurredBackgroundDrawable != null;
        if (hasPillBg) {
            final float vis = getMetadata().getTotalVisibility();
            final float contentW = getMetadata().getTotalWidth();
            if (vis > 0.001f && contentW > 0.5f) {
                blurredBackgroundDrawable.draw(canvas);
            }
        }

        boolean clipped = false;
        if (hasPillBg) {
            final Rect padded = blurredBackgroundDrawable.getPaddedBounds();
            if (!padded.isEmpty()) {
                pillClipRectF.set(padded);
                final float r = Math.min(dp(DialogsActivity.MAIN_TABS_HEIGHT / 2f), Math.min(pillClipRectF.width(), pillClipRectF.height()) / 2f);
                if (lastPillClipRadius != r
                        || lastPillClipLeft != pillClipRectF.left || lastPillClipTop != pillClipRectF.top
                        || lastPillClipRight != pillClipRectF.right || lastPillClipBottom != pillClipRectF.bottom) {
                    lastPillClipLeft = pillClipRectF.left;
                    lastPillClipTop = pillClipRectF.top;
                    lastPillClipRight = pillClipRectF.right;
                    lastPillClipBottom = pillClipRectF.bottom;
                    lastPillClipRadius = r;
                    pillClipPath.rewind();
                    pillClipPath.addRoundRect(pillClipRectF, r, r, Path.Direction.CW);
                }
                canvas.save();
                canvas.clipPath(pillClipPath);
                clipped = true;
            }
        }

        if (drawCustomSelector && !collapsedCircleMode) {
            final float x = animatedLongSelectedViewCenterX + animatedLongSelectedViewOffsetX;
            final float sWidth = getInterpolatedWidthByX(x, this);
            final float sHeight = getHeight() - getPaddingTop() - getPaddingBottom();
            final float selL = x - sWidth / 2f;
            final float selT = (getHeight() - sHeight) / 2f;
            final float selR = x + sWidth / 2f;
            final float selB = (getHeight() + sHeight) / 2f;
            final float rad = sHeight / 2f;

            canvas.drawRoundRect(selL, selT, selR, selB, rad, rad, selectorPaint);
            if (MirrlyConfig.isGlassLiquidEnabled() && sWidth > 1f && sHeight > 1f) {
                glassLensMatrix.reset();
                glassLensMatrix.setScale(1f, sHeight);
                glassLensMatrix.postTranslate(0f, selT);
                glassLensFillGradient.setLocalMatrix(glassLensMatrix);
                glassLensFillPaint.setShader(glassLensFillGradient);
                glassLensFillPaint.setAlpha(255);
                glassLensRect.set(selL, selT, selR, selB);
                canvas.drawRoundRect(glassLensRect, rad, rad, glassLensFillPaint);

                final float rimScale = MirrlyConfig.getGlassRimScale();
                if (rimScale > 0.01f) {
                    final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
                    final LinearGradient rimShader = isDark ? glassLensRimDarkGradient : glassLensRimLightGradient;
                    rimShader.setLocalMatrix(glassLensMatrix);
                    final float strokeW = dpf2(0.55f) * rimScale;
                    final float inset = strokeW * 0.5f;
                    glassLensRimPaint.setStyle(Paint.Style.STROKE);
                    glassLensRimPaint.setStrokeWidth(strokeW);
                    glassLensRimPaint.setShader(rimShader);
                    glassLensRimPaint.setAlpha(255);
                    glassLensRect.set(selL + inset, selT + inset, selR - inset, selB - inset);
                    canvas.drawRoundRect(glassLensRect, Math.max(0f, rad - inset), Math.max(0f, rad - inset), glassLensRimPaint);
                }
            }
        }

        super.dispatchDraw(canvas);

        if (clipped) {
            canvas.restore();
        }
    }


    final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassLensFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassLensRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient glassLensFillGradient = new LinearGradient(0, 0, 0, 1, 0x18FFFFFF, 0x04FFFFFF, Shader.TileMode.CLAMP);
    private final LinearGradient glassLensRimDarkGradient = new LinearGradient(0, 0, 0, 1, 0x33FFFFFF, 0x16FFFFFF, Shader.TileMode.CLAMP);
    private final LinearGradient glassLensRimLightGradient = new LinearGradient(0, 0, 0, 1, 0xD9FFFFFF, 0x33FFFFFF, Shader.TileMode.CLAMP);
    private final Matrix glassLensMatrix = new Matrix();
    private final RectF glassLensRect = new RectF();
    final SpringAnimation scaleX = new SpringAnimation(this, DynamicAnimation.SCALE_X, 1f);
    final SpringAnimation scaleY = new SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f);

    final SpringAnimation selectedTabPositionOffsetX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionOffsetX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewOffsetX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewOffsetX = value;
            object.invalidate();
        }
    });
    final SpringAnimation selectedTabPositionX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewCenterX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewCenterX = value;
            object.invalidate();
        }
    });

    {
        selectedTabPositionOffsetX.setSpring(new SpringForce(1)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        scaleX.setSpring(new SpringForce(1f)
            .setStiffness(250)
            .setDampingRatio(0.25f));
        scaleY.setSpring(new SpringForce(1f)
            .setStiffness(250)
            .setDampingRatio(0.25f));
        selectedTabPositionX.setSpring(new SpringForce(1f)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
    }

    private float animatedLongSelectedViewCenterX;
    private float animatedLongSelectedViewOffsetX;

    private boolean isInLongPress;
    private float lastLongSelectedViewCenterX;
    private float lastLongSelectedViewWidth;
    private View lastLongSelectedView;




    public static View findChildUnder(ViewGroup parent, float x, float y) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);

            if (child.getVisibility() != View.VISIBLE) continue;
            if (parent instanceof MainTabsLayout && !((MainTabsLayout) parent).isViewVisible(child)) continue;

            final float left = child.getX();
            final float top = child.getY();
            if (x >= left && x <= left + child.getWidth()
                    && y >= top && y <= top + child.getHeight()) {
                return child;
            }
        }
        return null;
    }

    private void checkLongMove(float x_, float y, boolean start, boolean end) {
        final float x = clampXToChildrenCenters(x_, this);
        final View found = findNearestVisibleChildByX(x, this);
        if (start) {
            View selected = findSelectedTab();
            if (selected != null) {
                animatedLongSelectedViewCenterX = selected.getX() + selected.getWidth() / 2f;
                animatedLongSelectedViewOffsetX = animatedLongSelectedViewCenterX - x;
                selectedTabPositionOffsetX.animateToFinalPosition(0);
                if (selected != found && found != null) {
                    found.performClick();
                }
            }
            selectedTabPositionX.cancel();
        }

        if (!end) {
            animatedLongSelectedViewCenterX = x;
            invalidate();
        }

        if (found != null) {
            lastLongSelectedView = found;
            setTabSelected(found, true);

            if (end) {
                final float vw = found.getWidth();
                final float cx = found.getX() + vw / 2f;
                if (lastLongSelectedViewWidth != vw || lastLongSelectedViewCenterX != cx) {
                    selectedTabPositionX.animateToFinalPosition(cx);
                }
            }
        }
    }

    private final Set<View> tabsWithIgnoreClick = new HashSet<>();
    public void addTabToIgnoreClick(View v) {
        tabsWithIgnoreClick.add(v);
    }

    public void removeTabToIgnoreClick(View v) {
        tabsWithIgnoreClick.remove(v);
    }

    private final BoolAnimator animatorIsScaled = new BoolAnimator(0, (a, factor, c, g) -> {
        setScaleX(lerp(1, 1.019f, factor));
        setScaleY(lerp(1, 1.019f, factor));
    }, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    private final ClickHelper clickHelper = new ClickHelper(new ClickHelper.Delegate() {
        @Override
        public boolean needClickAt(View view, float x, float y) {
            lastLongSelectedView = null;
            final View found = findChildUnder(MainTabsLayout.this, x, y);
            return found != null && !tabsWithIgnoreClick.contains(found);
        }

        @Override
        public void onClickAt(View view, float x, float y) {
        }

        @Override
        public boolean needLongPress(float x, float y) {
            if (collapsedCircleMode || visibleChildCount <= 1) {
                return false;
            }
            final View found = findChildUnder(MainTabsLayout.this, x, y);
            return found != null && !tabsWithIgnoreClick.contains(found);
        }

        @Override
        public boolean needCancelTouchBySlopMove() {
            return false;
        }


        @Override
        public boolean onLongPressRequestedAt(View view, float x, float y) {
            checkPivot(view, x, y);
            isInLongPress = true;
            AndroidUtilities.cancelRunOnUIThread(restoreDrawSelector);
            setSkipDrawSelector(true);
            checkLongMove(x, y, true, false);
            invalidate();
            longTouchStart();
            return true;
        }

        @Override
        public void onLongPressMove(View view, MotionEvent e, float x, float y, float startX, float startY) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, false);
            invalidate();
        }

        @Override
        public long getLongPressDuration() {
            return ClickHelper.Delegate.super.getLongPressDuration() * 750 / 1000;
        }

        @Override
        public void onLongPressFinish(View view, float x, float y) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, true);
            isInLongPress = false;
            AndroidUtilities.runOnUIThread(restoreDrawSelector, 450);
            if (lastLongSelectedView != null) {
                lastLongSelectedView.performClick();
            }
            lastLongSelectedView = null;
            invalidate();
            longTouchEnd();
        }

        @Override
        public void onLongPressCancelled(View view, float x, float y) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, true);
            isInLongPress = false;
            AndroidUtilities.runOnUIThread(restoreDrawSelector, 450);
            lastLongSelectedView = null;
            invalidate();
            longTouchEnd();
        }

        private void longTouchStart() {
            animatorIsScaled.setValue(true, true);

            /*
            if (!scaleX.isRunning()) {
                scaleX.setStartVelocity(-0.45f);
                scaleY.setStartVelocity(-0.45f);
            }
            scaleX.animateToFinalPosition(1.012f);
            scaleY.animateToFinalPosition(1.012f);
            */
        }

        private void longTouchEnd() {
            animatorIsScaled.setValue(false, true);

            /*
            if (!scaleX.isRunning()) {
                scaleX.setStartVelocity(0.25f);
                scaleY.setStartVelocity(0.25f);
            }
            scaleX.animateToFinalPosition(1f);
            scaleY.animateToFinalPosition(1f);
            */
        }
    });

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        checkLayerType();
    }

    @Override
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        checkLayerType();
    }

    private void checkLayerType() {
        final int layerType = Math.abs(getScaleX() - 1f) < 0.0001f && Math.abs(getScaleY() - 1f) < 0.0001f ?
            View.LAYER_TYPE_NONE : View.LAYER_TYPE_HARDWARE;

        if (getLayerType() != layerType) {
            setLayerType(layerType, null);
            invalidate();
        }
    }


    private void checkPivot(View view, float x, float y) {
        float w = view.getWidth();
        float h = view.getHeight();

        if (w <= 0f || h <= 0f) {
            return;
        }

        float cx = w * 0.5f;
        float cy = h * 0.5f;

        float dx = x - cx;
        float dy = y - cy;

        float halfW = w * 0.5f;
        float halfH = h * 0.5f;

        float nx = dx / halfW;
        float ny = dy / halfH;

        float r = (float) Math.sqrt(nx * nx + ny * ny);

        float pivotX;
        float pivotY;

        if (r > 1e-4f) {
            float mappedR = 1.5f * r / (r + 0.5f);

            float scale = mappedR / r;
            pivotX = cx + dx * scale;
            pivotY = cy + dy * scale;
        } else {
            pivotX = cx;
            pivotY = cy;
        }

        pivotX = lerp(cx, pivotX, 1f);
        pivotY = lerp(cy, pivotY, 3f);

        view.setPivotX(pivotX);
        view.setPivotY(pivotY);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (fullWidthCentering && ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (getVisibility() != VISIBLE || !isPointInsidePill(ev.getX(), ev.getY())) {
                return false;
            }
        }
        clickHelper.onTouchEvent(this, ev);
        return super.dispatchTouchEvent(ev);
    }


    private static float clampXToChildrenCenters(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return x;
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        boolean found = false;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (parent instanceof MainTabsLayout && !((MainTabsLayout) parent).isViewVisible(view)) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;

            if (centerX < min) min = centerX;
            if (centerX > max) max = centerX;

            found = true;
        }

        if (!found) {
            return x;
        }

        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    @Nullable
    private static View findNearestVisibleChildByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return null;
        }

        View nearest = null;
        float nearestDistance = Float.MAX_VALUE;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (parent instanceof MainTabsLayout && !((MainTabsLayout) parent).isViewVisible(view)) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;
            float distance = Math.abs(centerX - x);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = view;
            }
        }

        return nearest;
    }

    private static float getInterpolatedWidthByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return 0f;
        }

        View left = null;
        View right = null;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (parent instanceof MainTabsLayout && !((MainTabsLayout) parent).isViewVisible(view)) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;

            if (centerX <= x && (left == null || centerX > getCenterX(left))) {
                left = view;
            }

            if (centerX >= x && (right == null || centerX < getCenterX(right))) {
                right = view;
            }
        }

        if (left == null && right == null) {
            return 0f;
        }

        if (left == null) {
            return right.getWidth();
        }

        if (right == null) {
            return left.getWidth();
        }

        float leftX = getCenterX(left);
        float rightX = getCenterX(right);

        if (left == right || leftX == rightX) {
            return left.getWidth();
        }

        float ratio = (x - leftX) / (rightX - leftX);
        return lerp(left.getWidth(), right.getWidth(), ratio);
    }

    @Override
    protected void onDetachedFromWindow() {
        alignAnimator.cancel();
        scaleX.cancel();
        scaleY.cancel();
        super.onDetachedFromWindow();
    }

    private static float getCenterX(View v) {
        return v.getX() + v.getWidth() * 0.5f;
    }
}
