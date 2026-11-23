package org.telegram.ui.Components.glass;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.MainTabsLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class GlassTabView extends FrameLayout implements MainTabsLayout.Tab, FactorAnimator.Target, Theme.Colorable {
    private final TextView textView;
    private final RLottieImageView imageView;
    private BackupImageView backupImageView;
    private Theme.ResourcesProvider resourcesProvider;
    private final Paint paintCounterBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedTextView.AnimatedTextDrawable counter;

    private static final int ANIMATOR_ID_IS_SELECTED = 0;
    private static final int ANIMATOR_ID_COUNTER_VISIBLE = 1;
    private static final int ANIMATOR_ID_COUNTER_ERROR = 2;

    private final BoolAnimator isSelectedAnimator = new BoolAnimator(ANIMATOR_ID_IS_SELECTED, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 320);
    private final BoolAnimator isHasCounterAnimator = new BoolAnimator(ANIMATOR_ID_COUNTER_VISIBLE, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);
    private final BoolAnimator isHasCounterErrorAnimator = new BoolAnimator(ANIMATOR_ID_COUNTER_ERROR, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380);
    private int colorSelected;
    private int colorSelectedText;
    private int colorDefault;
    private boolean usePremiumCounter;

    private TabAnimation tabAnimation;
    private TLRPC.TL_attachMenuBot tabAnimationBot;
    private int staticIconRes;
    private boolean circleCompactMode;

    private final TextPaint defaultTextPaint;

    public GlassTabView(@NonNull Context context) {
        super(context);
        imageView = new RLottieImageView(context);
        addView(imageView, LayoutHelper.createFrame(44, 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, -6, 0, 0));

        imageView.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        textView.setSingleLine();
        textView.setLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER);

        defaultTextPaint = new TextPaint(textView.getPaint());
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 28.33f, 0, 0));

        counter = new AnimatedTextView.AnimatedTextDrawable();
        counter.setTypeface(AndroidUtilities.bold());
        counter.setCallback(this);
        counter.setGravity(Gravity.CENTER);
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(dp(10));
    }

    private boolean hasVisualWidth;
    private float visualWidth;
    public void setVisualWidth(float width) {
        hasVisualWidth = true;
        if (visualWidth != width) {
            visualWidth = width;
            checkVisualWidth();
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkVisualWidth();
    }

    private void checkVisualWidth() {
        if (hasVisualWidth) {
            final float offset = (visualWidth - getMeasuredWidth()) / 2f;
            imageView.setTranslationX(offset);
            textView.setTranslationX(offset);
            if (backupImageView != null) {
                backupImageView.setTranslationX(offset);
            }
        }
    }

    private static final RectF tmpRectF = new RectF();
    private final Paint glassLensFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassLensRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient glassLensFillGradient = new LinearGradient(0, 0, 0, 1, 0x18FFFFFF, 0x04FFFFFF, Shader.TileMode.CLAMP);
    private final LinearGradient glassLensRimDarkGradient = new LinearGradient(0, 0, 0, 1, 0x33FFFFFF, 0x16FFFFFF, Shader.TileMode.CLAMP);
    private final LinearGradient glassLensRimLightGradient = new LinearGradient(0, 0, 0, 1, 0xD9FFFFFF, 0x33FFFFFF, Shader.TileMode.CLAMP);
    private final Matrix glassLensMatrix = new Matrix();
    private final RectF glassLensRect = new RectF();

    private boolean hasGestureSelectedOverride;
    private float gestureSelectedOverride;
    private boolean skipDrawSelector;

    public void setGestureSelectedOverride(float gestureSelectedOverride, boolean allow) {
        this.gestureSelectedOverride = gestureSelectedOverride;
        this.hasGestureSelectedOverride = allow;
        invalidate();
    }

    public void setSkipDrawSelector(boolean skipDrawSelector) {
        if (this.skipDrawSelector != skipDrawSelector) {
            this.skipDrawSelector = skipDrawSelector;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final float viewWidth = hasVisualWidth ? visualWidth : getWidth();
        final float selectedFactor = hasGestureSelectedOverride ? gestureSelectedOverride : isSelectedAnimator.getFloatValue();
        if (selectedFactor > 0 && !skipDrawSelector && !circleCompactMode) {
            final float alpha = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(selectedFactor);

            paintCounterBackground.setColor(Theme.multAlpha(colorSelected, 0.09f * alpha));
            tmpRectF.set(0, 0, viewWidth, getHeight());
            final float r = Math.min(tmpRectF.width(), tmpRectF.height()) / 2f;
            final float s = lerp(0.6f, 1, selectedFactor) * MathUtils.clamp(attachScale, 0, 1);
            canvas.save();
            canvas.scale(s, s, tmpRectF.centerX(), tmpRectF.centerY());
            canvas.drawRoundRect(tmpRectF, r, r, paintCounterBackground);
            if (MirrlyConfig.isGlassLiquidEnabled() && tmpRectF.width() > 1f && tmpRectF.height() > 1f) {
                final int pillAlpha = Math.round(255f * alpha);
                final float h = Math.max(1f, tmpRectF.height());
                glassLensMatrix.reset();
                glassLensMatrix.setScale(1f, h);
                glassLensMatrix.postTranslate(0f, tmpRectF.top);
                glassLensFillGradient.setLocalMatrix(glassLensMatrix);
                glassLensFillPaint.setShader(glassLensFillGradient);
                glassLensFillPaint.setAlpha(pillAlpha);
                canvas.drawRoundRect(tmpRectF, r, r, glassLensFillPaint);

                final float rimScale = MirrlyConfig.getGlassRimScale();
                if (rimScale > 0.01f && s > 0.05f) {
                    final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
                    final LinearGradient rimShader = isDark ? glassLensRimDarkGradient : glassLensRimLightGradient;
                    rimShader.setLocalMatrix(glassLensMatrix);
                    final float strokeW = (dpf2(0.55f) * rimScale) / s;
                    final float inset = strokeW * 0.5f;
                    glassLensRimPaint.setStyle(Paint.Style.STROKE);
                    glassLensRimPaint.setStrokeWidth(strokeW);
                    glassLensRimPaint.setShader(rimShader);
                    glassLensRimPaint.setAlpha(pillAlpha);
                    glassLensRect.set(tmpRectF.left + inset, tmpRectF.top + inset, tmpRectF.right - inset, tmpRectF.bottom - inset);
                    canvas.drawRoundRect(glassLensRect, Math.max(0f, r - inset), Math.max(0f, r - inset), glassLensRimPaint);
                }
            }
            canvas.restore();
        }

        final float hasCounter = (usePremiumCounter ? 1f : isHasCounterAnimator.getFloatValue()) * attachScale;
        final boolean saveLayer = hasCounter > 0;
        if (saveLayer) {
            canvas.saveLayer(0, 0, viewWidth, getHeight(), null);
        }

        super.dispatchDraw(canvas);

        if (hasCounter > 0) {
            canvas.save();

            final float gap = dpf2(1.33f);
            final float height = dpf2(16);
            final float width = Math.max(height, counter.getCurrentWidth() + dp(8));
            float cx = viewWidth / 2f + dpf2(11);
            if (circleCompactMode && viewWidth > 0f) {
                cx = Math.min(cx, Math.max(viewWidth / 2f, viewWidth - width / 2f - dpf2(3)));
            }
            final float cy = circleCompactMode ? dpf2(11) : dpf2(10);
            final float rOuter = dpf2(9.333f);
            final float rInner = dpf2(8f);
            tmpRectF.set(
                    cx - width / 2f - gap,
                    cy - height / 2f - gap,
                    cx + width / 2f + gap,
                    cy + height / 2f + gap
            );

            canvas.scale(hasCounter, hasCounter, cx, cy);
            canvas.drawRoundRect(tmpRectF, rOuter, rOuter, Theme.PAINT_CLEAR);
            tmpRectF.inset(gap, gap);

            if (usePremiumCounter) {
                if (premiumStarDrawable == null) {
                    premiumStarDrawable = getContext().getResources().getDrawable(R.drawable.star).mutate();
                }

                PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, dp(96), dp(16), 0, 0);
                canvas.drawRoundRect(tmpRectF, rInner, rInner, PremiumGradient.getInstance().getMainGradientPaint());
                int x = (int)(cx - dpf2(7f));
                int y = (int)(cy - dpf2(7f));
                premiumStarDrawable.setBounds(x, y, x + dp(14), y + dp(14));
                premiumStarDrawable.draw(canvas);
            } else {
                paintCounterBackground.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_telegram_color), Theme.getColor(Theme.key_fill_RedNormal), isHasCounterErrorAnimator.getFloatValue()));
                canvas.drawRoundRect(tmpRectF, rInner, rInner, paintCounterBackground);
                counter.setBounds(tmpRectF);
                counter.draw(canvas);
            }
            canvas.restore();
        }

        if (saveLayer) {
            canvas.restore();
        }
    }

    private Drawable premiumStarDrawable;

    public void setCounter(String text, boolean isError, boolean animated) {
        counter.setText(text, animated);
        isHasCounterAnimator.setValue(!TextUtils.isEmpty(text), animated);
        isHasCounterErrorAnimator.setValue(isError, animated);
    }

    public void setPremiumBadge(boolean usePremiumBadge) {
        usePremiumCounter = usePremiumBadge;
    }

    public void setSelected(boolean selected, boolean animated) {
        isSelectedAnimator.setValue(selected, animated);
        checkPlayAnimation(animated);

        textView.setTypeface(selected ? AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD) : AndroidUtilities.bold());
    }

    public boolean isTabSelected() {
        return isSelectedAnimator.getValue();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_IS_SELECTED) {
            applyColors();
        }
        invalidate();
    }

    private boolean needUpdateBackupViewColor;
    private float adaptiveInvertFactor = 0f;
    private float smartAdaptiveWcagLuma = -1f;
    private int lastAppliedIconColor;
    private boolean hasAppliedIconColor;
    private PorterDuffColorFilter cachedIconColorFilter;

    public void applyAdaptiveGlassContrast(float invertFactor) {
        final float clamped = Math.max(0f, Math.min(1f, invertFactor));
        if (smartAdaptiveWcagLuma >= 0f || Math.abs(this.adaptiveInvertFactor - clamped) >= 0.008f) {
            this.smartAdaptiveWcagLuma = -1f;
            this.adaptiveInvertFactor = clamped;
            applyColors();
        }
    }

    public void updateSmartAdaptiveGlassContrast(
            @Nullable ViewGroup sampleTarget,
            int glassBgColor,
            int viewportW,
            int viewportH,
            float fallbackBgLuma,
            boolean resetToBase
    ) {
        if (resetToBase || getVisibility() != VISIBLE || getWidth() <= 0 || getHeight() <= 0) {
            if (smartAdaptiveWcagLuma >= 0f || adaptiveInvertFactor > 0.001f) {
                smartAdaptiveWcagLuma = -1f;
                adaptiveInvertFactor = 0f;
                applyColors();
            }
            return;
        }
        final float underLuma = org.telegram.utils.glass.GlassContrastHelper.estimateUnderlyingLuminanceInBand(
                this, sampleTarget, 0f, 0f, getWidth(), getHeight(),
                null, null, viewportW, viewportH, fallbackBgLuma, resourcesProvider);
        final float nextWcag = org.telegram.utils.glass.GlassContrastHelper.computeCompositeWcagLuminance(glassBgColor, underLuma);
        if (smartAdaptiveWcagLuma < 0f || Math.abs(smartAdaptiveWcagLuma - nextWcag) >= 0.015f) {
            smartAdaptiveWcagLuma = nextWcag;
            applyColors();
        }
    }

    private boolean hasAppliedTextColor;
    private int lastAppliedTextColor;
    private float lastAppliedHaloStrength = -1f;

    private void applyColors() {
        final float sel = isSelectedAnimator.getFloatValue();
        final int baseIcon = ColorUtils.blendARGB(colorDefault, colorSelected, sel);
        final int baseText = ColorUtils.blendARGB(colorDefault, colorSelectedText, sel);
        final int color;
        final int colorText;
        final float haloStrength;
        if (smartAdaptiveWcagLuma >= 0f) {
            final boolean needLight = org.telegram.utils.glass.GlassContrastHelper.resolveAdaptiveColorFromWcag(
                    baseText, smartAdaptiveWcagLuma, false, false) != baseText;
            color = org.telegram.utils.glass.GlassContrastHelper.resolveAdaptiveColorFromWcag(
                    baseIcon, smartAdaptiveWcagLuma, false, sel > 0.5f);
            colorText = org.telegram.utils.glass.GlassContrastHelper.resolveAdaptiveColorFromWcag(
                    baseText, smartAdaptiveWcagLuma, false, sel > 0.5f);
            haloStrength = needLight ? 0.72f : ((smartAdaptiveWcagLuma > 0.08f && smartAdaptiveWcagLuma < 0.35f) ? 0.58f : 0.25f);
        } else {
            color = adaptiveInvertFactor > 0.001f
                    ? org.telegram.utils.glass.GlassContrastHelper.blendAdaptiveIconColor(baseIcon, adaptiveInvertFactor)
                    : baseIcon;
            colorText = adaptiveInvertFactor > 0.001f
                    ? org.telegram.utils.glass.GlassContrastHelper.blendAdaptiveTextColor(baseText, adaptiveInvertFactor, false)
                    : baseText;
            haloStrength = adaptiveInvertFactor > 0.005f ? Math.max(0.45f, adaptiveInvertFactor * 0.78f) : 0f;
        }

        final boolean iconChanged = !hasAppliedIconColor || lastAppliedIconColor != color || cachedIconColorFilter == null;
        if (iconChanged) {
            hasAppliedIconColor = true;
            lastAppliedIconColor = color;
            cachedIconColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            if (backupImageView != null && needUpdateBackupViewColor) {
                backupImageView.setColorFilter(cachedIconColorFilter);
                backupImageView.invalidate();
            }
            imageView.setColorFilter(cachedIconColorFilter);
        }
        final boolean textChanged = !hasAppliedTextColor || lastAppliedTextColor != colorText;
        final boolean haloChanged = lastAppliedHaloStrength < 0f || Math.abs(lastAppliedHaloStrength - haloStrength) > 0.01f;
        if (textChanged) {
            hasAppliedTextColor = true;
            lastAppliedTextColor = colorText;
            textView.setTextColor(colorText);
        }
        if (textChanged || haloChanged) {
            lastAppliedHaloStrength = haloStrength;
            org.telegram.utils.glass.GlassContrastHelper.applyOpticalTextHalo(
                    textView.getPaint(),
                    colorText,
                    haloStrength
            );
            textView.invalidate();
        }
    }

    @Override
    public void updateColors() {
        // every theme frame: applyColors allocates a color filter, so only when a color changed
        if (colorDefault != Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider) ||
            colorSelected != Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider) ||
            colorSelectedText != Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider)) {
            updateColorsLottie();
        }
    }

    @Override
    public void addColorKeys(java.util.HashSet<Integer> colors) {
        colors.add(Theme.key_glass_tabUnselected);
        colors.add(Theme.key_glass_tabSelected);
        colors.add(Theme.key_glass_tabSelectedText);
    }

    public void updateColorsLottie() {
        colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        applyColors();
        invalidate();
    }


    private boolean lastIsSelected;
    private int lastIconAnimationRaw;
    private long lastBotIconId;

    private void checkPlayAnimation(boolean animated) {
        final boolean isSelected = isSelectedAnimator.getValue();

        if (tabAnimationBot !=  null) {
            boolean animatedIcon = true;
            TLRPC.TL_attachMenuBotIcon icon = MediaDataController.getAnimatedAttachMenuBotIcon(tabAnimationBot, isSelected);
            if (icon == null) {
                icon = MediaDataController.getStaticAttachMenuBotIcon(tabAnimationBot);
                animatedIcon = false;
            }
            if (icon != null && icon.icon != null) {
                TLRPC.Document iconDoc = icon.icon;
                if (lastBotIconId != icon.icon.id) {
                    String filter = "24_24" + (animatedIcon && !animated || true ? "_lastframe" : "");
                    backupImageView.setImage(
                        ImageLocation.getForDocument(iconDoc), filter,
                        ImageLocation.getForDocument(iconDoc), filter,
                        animatedIcon ? null : DocumentObject.getSvgThumb(iconDoc, Theme.key_windowBackgroundGray, 1f),
                        tabAnimationBot
                    );
                    lastBotIconId = iconDoc.id;
                }
            } else {
                backupImageView.clearImage();
            }
            applyColors();
            return;
        }

        if (staticIconRes != 0) {
            imageView.setImageResource(staticIconRes);
            applyColors();
            return;
        }

        if (tabAnimation == null) {
            return;
        }

        if (tabAnimation.iconStatic != -1) {
            imageView.setImageResource(tabAnimation.iconStatic);
            applyColors();
            return;
        }

        final int animationToSet = isSelected ?
            tabAnimation.iconToFilled : tabAnimation.iconToOutline;

        if (tabAnimation.endFrameMid != -1) {
            boolean update = lastIsSelected != isSelected;
            if (lastIconAnimationRaw != animationToSet) {
                lastIconAnimationRaw = animationToSet;
                imageView.setAnimation(animationToSet, 24, 24);
                update = true;
            }

            if (update) {
                final RLottieDrawable drawable = imageView.getAnimatedDrawable();
                if (drawable == null) {
                    return;
                }

                if (isSelected) {
                    drawable.setCustomEndFrame(tabAnimation.endFrameMid);
                    if (drawable.getCurrentFrame() >= tabAnimation.endFrameEnd - 2) {
                        drawable.setCurrentFrame(0, false);
                    }
                    if (drawable.getCurrentFrame() <= tabAnimation.endFrameMid) {
                        drawable.start();
                    } else {
                        drawable.setCurrentFrame(tabAnimation.endFrameMid);
                    }
                } else {
                    if (drawable.getCurrentFrame() >= tabAnimation.endFrameMid - 1) {
                        drawable.setCustomEndFrame(tabAnimation.endFrameEnd - 1);
                        drawable.start();
                    } else {
                        drawable.setCustomEndFrame(0);
                        drawable.setCurrentFrame(0);
                    }
                }
            }
            lastIsSelected = isSelected;
            return;
        }

        if (tabAnimation.iconToFilled != tabAnimation.iconToOutline) {
            if (lastIconAnimationRaw != animationToSet) {
                lastIconAnimationRaw = animationToSet;

                imageView.setAnimation(animationToSet, 24, 24);
                imageView.getAnimatedDrawable().setPlayInDirectionOfCustomEndFrame(false);
                if (animated) {
                    imageView.getAnimatedDrawable().setCurrentFrame(0);
                    imageView.playAnimation();
                } else {
                    imageView.getAnimatedDrawable().setProgress(0.99f);
                }
            }
            return;
        }

        if (imageView.getAnimatedDrawable() == null) {
            imageView.setAnimation(tabAnimation.iconToFilled, 24, 24);
        }

        final RLottieDrawable drawable = imageView.getAnimatedDrawable();
        if (drawable == null) {
            return;
        }

        if (lastIsSelected != isSelected) {
            lastIsSelected = isSelected;
            if (isSelected) {
                drawable.setPlayInDirectionOfCustomEndFrame(false);
                drawable.setCurrentFrame(0);
                drawable.setCustomEndFrame(drawable.getFramesCount());
            } else {
                drawable.setPlayInDirectionOfCustomEndFrame(true);
                drawable.setCurrentFrame(drawable.getFramesCount());
                drawable.setCustomEndFrame(0);
            }
            imageView.playAnimation();
        }
    }

    public static GlassTabView createMainTab(Context context, Theme.ResourcesProvider resourcesProvider, TabAnimation tabAnimation, @StringRes int stringRes) {
        GlassTabView tab = new GlassTabView(context);
        tab.resourcesProvider = resourcesProvider;
        tab.tabAnimation = tabAnimation;
        tab.textView.setText(LocaleController.getString(stringRes));
        tab.checkPlayAnimation(false);
        tab.imageView.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));
        tab.colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        tab.colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        tab.colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        tab.applyColors();
        return tab;
    }

    public static GlassTabView createAvatar(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, @StringRes int stringRes) {
        GlassTabView tab = new GlassTabView(context);
        tab.resourcesProvider = resourcesProvider;
        tab.textView.setText(LocaleController.getString(stringRes));
        tab.imageView.setVisibility(GONE);

        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        AvatarDrawable avatarDrawable = new AvatarDrawable(user);

        BackupImageView backupImageView = new BackupImageView(context);
        backupImageView.setForUserOrChat(user, avatarDrawable);
        backupImageView.setRoundRadius(dp(11));
        tab.backupImageView = backupImageView;
        tab.avatarDrawable = avatarDrawable;

        tab.addView(backupImageView, LayoutHelper.createFrame(22, 22, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 5, 0, 0));
        tab.colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        tab.colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        tab.colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        tab.applyColors();
        return tab;
    }

    public void updateUserAvatar(int currentAccount) {
        if (backupImageView == null) {
            return;
        }
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        if (avatarDrawable == null) {
            avatarDrawable = new AvatarDrawable();
        }
        avatarDrawable.setInfo(currentAccount, user);
        backupImageView.setForUserOrChat(user, avatarDrawable);
    }

    public static GlassTabView createAttachTab(Context context, Theme.ResourcesProvider resourcesProvider) {
        GlassTabView tab = new GlassTabView(context);
        tab.resourcesProvider = resourcesProvider;
        tab.selfMeasure = true;
        tab.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        tab.textView.setPadding(dp(8), 0, dp(8), 0);
        tab.checkPlayAnimation(false);
        tab.imageView.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));
        tab.colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        tab.colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        tab.colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        tab.applyColors();
        return tab;
    }

    public static GlassTabView createAttachBotTab(Context context, Theme.ResourcesProvider resourcesProvider) {
        GlassTabView tab = new GlassTabView(context);
        tab.resourcesProvider = resourcesProvider;
        tab.selfMeasure = true;
        tab.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        tab.textView.setPadding(dp(8), 0, dp(8), 0);
        tab.imageView.setVisibility(GONE);
        tab.checkPlayAnimation(false);
        tab.backupImageView = new BackupImageView(context);
        tab.addView(tab.backupImageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));
        tab.colorDefault = Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
        tab.colorSelected = Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
        tab.colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText, resourcesProvider);
        tab.applyColors();
        return tab;
    }

    public BackupImageView getBackupImageView() {
        return backupImageView;
    }

    private boolean selfMeasure;
    private int additionalWidth;

    public void setAdditionalWidth(int additionalWidth) {
        this.additionalWidth = additionalWidth;
        this.selfMeasure = true;
    }

    public float measureAttachTabWidth() {
        final float textWidth = measureTextWidth();
        final float padding = lerp(dpf2(16), dp(8), MathUtils.clamp((textWidth - dp(40)) / dp(16), 0, 1));
        return Math.min(dp(84), (int) (textWidth + padding * 2));
    }

    public float attachScale = 1;
    public void setAttachScale(float scale) {
        textView.setScaleX(scale);
        textView.setScaleY(scale);
        imageView.setScaleX(scale);
        imageView.setScaleY(scale);
        if (backupImageView != null) {
            backupImageView.setScaleX(scale);
            backupImageView.setScaleY(scale);
        }
        attachScale = scale;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (selfMeasure) {
            final int width = (int) (measureAttachTabWidth()) + additionalWidth;
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    public float measureTextWidth() {
        if (circleCompactMode) {
            return 0f;
        }
        return defaultTextPaint.measureText(textView.getText().toString());
    }

    private TextPaint scaledTextPaint;

    @Override
    public float measureTextWidth(float textSizeDp) {
        if (circleCompactMode) {
            return 0f;
        }
        if (scaledTextPaint == null) {
            scaledTextPaint = new TextPaint(defaultTextPaint);
        }
        scaledTextPaint.setTextSize(dp(textSizeDp));
        return scaledTextPaint.measureText(textView.getText().toString());
    }

    @Override
    public void setTextSizeDp(float textSizeDp) {
        final float px = dp(textSizeDp);
        if (textView.getTextSize() != px) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSizeDp);
            defaultTextPaint.setTextSize(px);
        }
    }

    private enum TabAnimationType {
        LOTTIE,
        STATIC
    }

    public enum TabAnimation {
        CONTACTS(R.raw.tab_contacts),
        CALLS(R.raw.tab_calls),
        CHATS(R.raw.tab_chats),
        SETTINGS(R.raw.tab_settings),

        CHECKLIST(R.raw.tab_checklist, R.raw.tab_checklist_reverse),
        COLORS(R.raw.tab_colors, R.raw.tab_colors_reverse),
        FILES(R.raw.tab_files, R.raw.tab_files_reverse),
        GALLERY(R.raw.tab_gallery, R.raw.tab_gallery_reverse),
        GIFT(R.raw.tab_gift, R.raw.tab_gift_reverse),
        LOCATION(R.raw.tab_location, R.raw.tab_location_reverse),
        STICKER(R.raw.tab_sticker, R.raw.tab_sticker_reverse),
        EMOJI(R.raw.tab_emoji, R.raw.tab_emoji_reverse),
        MODELS(R.raw.tab_models, R.raw.tab_models_reverse),
        MUSIC(R.raw.tab_music, R.raw.tab_music_reverse),
        POLL(R.raw.tab_poll, R.raw.tab_poll_reverse),
        SYMBOLS(R.raw.tab_symbols, R.raw.tab_symbols_reverse),
        REPLIES(R.raw.tab_reply, R.raw.tab_reply_reverse),
        WALLET(R.raw.tab_wallet, R.raw.tab_wallet_reverse),
        LINK(TabAnimationType.STATIC, R.drawable.tabs_link_24),
        ARTICLE(R.raw.tab_article, R.raw.tab_article_reverse),
        GRAM(R.raw.gram, R.raw.gram_reverse),

        BOOSTS(R.raw.boosts, 25, 49),
        MONETIZATION(R.raw.monetize, 19, 45),
        STORIES_SAVED(R.raw.msg_stories_saved, 20, 40),
        STORIES_ARCHIVE(R.raw.msg_stories_archive);

        public final @RawRes int iconToFilled;
        public final @RawRes int iconToOutline;
        public final @DrawableRes int iconStatic;
        public final int endFrameMid, endFrameEnd;

        TabAnimation(int iconRes, int endFrameMid, int endFrameEnd) {
            this.iconToFilled = iconRes;
            this.iconToOutline = iconRes;
            this.endFrameMid = endFrameMid;
            this.endFrameEnd = endFrameEnd;
            this.iconStatic = -1;
        }

        TabAnimation(TabAnimationType type, int icon) {
            if (type == TabAnimationType.LOTTIE) {
                this.iconToFilled = icon;
                this.iconToOutline = icon;
                this.iconStatic = -1;
            } else {
                this.iconStatic = icon;
                this.iconToFilled = -1;
                this.iconToOutline = -1;
            }
            this.endFrameMid = -1;
            this.endFrameEnd = -1;
        }

        TabAnimation(int iconRes) {
            this.iconToFilled = iconRes;
            this.iconToOutline = iconRes;
            this.endFrameMid = -1;
            this.endFrameEnd = -1;
            this.iconStatic = -1;
        }

        TabAnimation(int iconToFilled, int iconToOutline) {
            this.iconToFilled = iconToFilled;
            this.iconToOutline = iconToOutline;
            this.endFrameMid = -1;
            this.endFrameEnd = -1;
            this.iconStatic = -1;
        }
    }

    public void setTabAnimation(TabAnimation animation) {
        tabAnimation = animation;
        tabAnimationBot = null;
        staticIconRes = 0;
        lastIconAnimationRaw = 0;
        lastBotIconId = 0;
        imageView.clearAnimationDrawable();
        checkPlayAnimation(false);
    }

    public void setCircleCompactMode(boolean compact) {
        if (this.circleCompactMode == compact) {
            return;
        }
        this.circleCompactMode = compact;
        textView.setVisibility(compact ? GONE : VISIBLE);
        updateCompactIconLayout();
        invalidate();
    }

    public boolean isCircleCompactMode() {
        return circleCompactMode;
    }

    private void updateCompactIconLayout() {
        if (circleCompactMode) {
            imageView.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER));
            if (backupImageView != null && tabAnimationBot == null) {
                backupImageView.setRoundRadius(dp(13));
                backupImageView.setLayoutParams(LayoutHelper.createFrame(26, 26, Gravity.CENTER));
            }
        } else {
            imageView.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));
            if (backupImageView != null && tabAnimationBot == null) {
                backupImageView.setRoundRadius(dp(11));
                backupImageView.setLayoutParams(LayoutHelper.createFrame(22, 22, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 5, 0, 0));
            }
        }
        checkVisualWidth();
    }

    public void restoreMainTab(TabAnimation animation, CharSequence title) {
        tabAnimation = animation;
        tabAnimationBot = null;
        staticIconRes = 0;
        lastIconAnimationRaw = 0;
        lastBotIconId = 0;
        lastIsSelected = false;
        imageView.clearAnimationDrawable();
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setVisibility(VISIBLE);
        if (backupImageView != null) {
            backupImageView.setVisibility(GONE);
        }
        textView.setText(title);
        updateCompactIconLayout();
        if (animation != null && animation.iconStatic == -1 && animation.endFrameMid == -1 && animation.iconToFilled == animation.iconToOutline) {
            imageView.setAnimation(animation.iconToFilled, 24, 24);
        }
        checkPlayAnimation(false);
        applyColors();
        invalidate();
    }

    public void restoreUserAvatarTab(int currentAccount, CharSequence title) {
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        setCustomAvatar(user, currentAccount, title, R.drawable.msg_openprofile);
    }

    public void setCustomStaticIcon(@DrawableRes int iconRes, CharSequence title) {
        tabAnimation = null;
        tabAnimationBot = null;
        staticIconRes = iconRes;
        lastIconAnimationRaw = 0;
        lastBotIconId = 0;
        lastIsSelected = false;
        imageView.clearAnimationDrawable();
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageResource(iconRes);
        imageView.setVisibility(VISIBLE);
        if (backupImageView != null) {
            backupImageView.setVisibility(GONE);
        }
        textView.setText(title);
        updateCompactIconLayout();
        applyColors();
        invalidate();
    }

    public void setCustomAvatar(@Nullable TLObject object, int currentAccount, CharSequence title, @DrawableRes int fallbackIconRes) {
        if (object == null) {
            setCustomStaticIcon(fallbackIconRes, title);
            return;
        }
        tabAnimation = null;
        tabAnimationBot = null;
        staticIconRes = 0;
        lastIconAnimationRaw = 0;
        lastBotIconId = 0;
        lastIsSelected = false;
        imageView.clearAnimationDrawable();
        imageView.setVisibility(GONE);

        if (backupImageView == null) {
            backupImageView = new BackupImageView(getContext());
            addView(backupImageView, LayoutHelper.createFrame(22, 22, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 5, 0, 0));
        }
        if (avatarDrawable == null) {
            avatarDrawable = new AvatarDrawable();
        }
        avatarDrawable.setInfo(currentAccount, object);
        backupImageView.setSize(-1, -1);
        backupImageView.setForUserOrChat(object, avatarDrawable);
        backupImageView.setColorFilter(null);
        needUpdateBackupViewColor = false;
        backupImageView.setVisibility(VISIBLE);
        textView.setText(title);
        updateCompactIconLayout();
        applyColors();
        invalidate();
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }


    private AvatarDrawable avatarDrawable;

    public void setAttachBot(TLRPC.User user, TLRPC.TL_attachMenuBot bot, int currentAccount) {
        if (user == null || bot == null) {
            return;
        }
        tabAnimation = null;
        tabAnimationBot = bot;
        lastIconAnimationRaw = 0;
        lastBotIconId = 0;
        textView.setText(bot.short_name);

        backupImageView.setRoundRadius(0);
        backupImageView.setSize(dp(24), dp(24));
        backupImageView.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 4, 0, 0));
        needUpdateBackupViewColor = true;
        checkPlayAnimation(false);
        applyColors();
        invalidate();
    }

    public void setAttachBotUser(TLRPC.User user, int currentAccount) {
        if (user == null) {
            return;
        }
        tabAnimation = null;
        tabAnimationBot = null;
        lastIconAnimationRaw = 0;
        lastBotIconId = 0;

        textView.setText(ContactsController.formatName(user.first_name, user.last_name));
        if (avatarDrawable == null) {
            avatarDrawable = new AvatarDrawable();
        }
        avatarDrawable.setInfo(currentAccount, user);
        backupImageView.setForUserOrChat(user, avatarDrawable);
        backupImageView.setSize(-1, -1);
        backupImageView.setRoundRadius(dp(11.33f));
        backupImageView.setLayoutParams(LayoutHelper.createFrame(22, 22, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 5, 0, 0));
        backupImageView.setColorFilter(null);
        needUpdateBackupViewColor = false;
        invalidate();
    }

    public void onPreBind() {

    }
}