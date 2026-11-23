package org.telegram.ui.Components.blur3.drawable.color.impl;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;

public class BlurredBackgroundProviderImpl {
    public static BlurredBackgroundProvider mainTabs(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.74f : 0.76f;
                final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                int colorTarget = Theme.getColor(Theme.key_glass_targetMainTabs, r);
                if (isDark) {
                    colorTarget = guardDarkGlassTarget(r, colorBg, colorTarget, 0.055f);
                }
                return solveSrcColor(colorBg, colorTarget, alpha);
            })
            .setStrokeColorTop(0xE6FFFFFF, 0x28FFFFFF)
            .setStrokeColorBottom(0x33FFFFFF, 0x14FFFFFF)
            .setShadowColor((r, isDark) -> isDark ? (isAmoledSurfaceInverted(r) ? 0x24000000 : 0x04FFFFFF) : 0x20000000)
            .setShadowLayer(dpf2(2.667f), 0, dpf2(0.85f))
            .setStrokeWidth(dpf2(0.55f), dpf2(0.55f))
            .build();
    }

    public static BlurredBackgroundProvider topPanel(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.74f : 0.76f;
                final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                int colorTarget = Theme.getColor(Theme.key_glass_targetMainTopPanel, r);
                if (isDark) {
                    colorTarget = guardDarkGlassTarget(r, colorBg, colorTarget, 0.055f);
                }
                return solveSrcColor(colorBg, colorTarget, alpha);
            })
            .setStrokeColorTop(0xE6FFFFFF, 0x28FFFFFF)
            .setStrokeColorBottom(0x33FFFFFF, 0x14FFFFFF)
            .setShadowColor((r, isDark) -> isDark ? (isAmoledSurfaceInverted(r) ? 0x24000000 : 0x04FFFFFF) : 0x20000000)
            .setShadowLayer(dpf2(2.667f), 0, dpf2(0.85f))
            .setStrokeWidth(dpf2(0.55f), dpf2(0.55f))
            .build();
    }

    public static BlurredBackgroundProvider tabsPanelNoShadow(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) ->
                Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite, r), org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(0.88f)))
            .setStrokeColorTop(0, 0)
            .setStrokeColorBottom(0, 0)
            .setShadowColor(0, 0)
            .build();
    }

    public static BlurredBackgroundProvider emojiViewButton(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    return Theme.multAlpha(colorBg, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(alpha));
                })
                .setStrokeColorTop(0xFFFFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0xFFFFFFFF, 0x14FFFFFF)
                .setShadowColor(0x40000000, 0)
                .setShadowLayer(dpf2(11 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(0.5f), dpf2(0.5f))
                .build();
    }

    public static BlurredBackgroundProvider counterMini(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = 0.075f;
                    final int colorBg = Color.BLACK;
                    return Theme.multAlpha(colorBg, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(alpha));
                })
                .setStrokeColorTop(0x60FFFFFF, 0x50FFFFFF)
                .setStrokeColorBottom(0x24000000, 0x24000000)
                .setShadowColor(0, 0)
                .setShadowLayer(0, 0, 0)
                .setStrokeWidth(dpf2(0.43f), dpf2(0.43f))
                .build();
    }

    public static BlurredBackgroundProvider messageMenuReactionsBackground(Theme.ResourcesProvider resourcesProvider) {
        return messageMenuBackground(resourcesProvider);
    }

    public static BlurredBackgroundProvider messageMenuBackground(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) {
                        return Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, r);
                    }
                    return glassMultAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, r), isDark ? 0.85f : 0.825f);
                })
                .setStrokeColorTop(0x44FFFFFF, 0x20FFFFFF)
                .setStrokeColorBottom(0x22FFFFFF, 0x10FFFFFF)
                .setShadowColor(0x38000000, 0x30000000)
                .setShadowLayer(dpf2(3.5f), 0, 0)
                .setStrokeWidth(dpf2(2 / 3f), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider scrimMenuBackground(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) ->
                glassMultAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, r), isDark ? 0.85f : 0.825f))
            .setStrokeColorTop(0x44FFFFFF, 0x20FFFFFF)
            .setStrokeColorBottom(0x22FFFFFF, 0x10FFFFFF)
            .setShadowColor(0x26000000, 0x30000000)
            .setShadowLayer(dpf2(4f), 0, 0)
            .setStrokeWidth(dpf2(2 / 3f), dpf2(2 / 3f))
            .build();
    }

    public static BlurredBackgroundProvider attachMenuSearch(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    return Theme.multAlpha(colorBg, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(alpha));
                })
                .setStrokeColorTop(0x17000000, 0x17FFFFFF)
                .setStrokeColorBottom(0x17000000, 0x17FFFFFF)
                .setShadowColor(0x11000000, 0x04FFFFFF)
                .setShadowLayer(dpf2(2), 0, dpf2(1 / 3f))
                .setStrokeWidth(dpf2(0.4f), dpf2(0.4f))
                .build();
    }

    public static BlurredBackgroundProvider searchFloatingDate(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> 0x33000000)
                .setStrokeColorTop(0x17000000, 0x17FFFFFF)
                .setStrokeColorBottom(0x17000000, 0x17FFFFFF)
                .setShadowColor(0, 0)
                .setStrokeWidth(1, 1)
                .build();
    }

    public static BlurredBackgroundProvider bottomPanelChatActivity(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    if (!checkBlurEnabled(resourcesProvider)) {
                        return ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_chat_messagePanelBackground, r), 255);
                    }

                    final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.74f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_chat_messagePanelBackground, r);
                    return Theme.multAlpha(colorBg, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(alpha));
                })
                .setStrokeColorTop(0xFFFFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0xFFFFFFFF, 0x14FFFFFF)
                .setShadowColor(0x20000000, 0)
                //.setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(0.5f), dpf2(0.5f))
                .build();
    }

    public static BlurredBackgroundProvider topPanelChatActivity(Theme.ResourcesProvider resourcesProvider) {
        // compact mode's glass follows the design: no white outline, a soft shadow and a tight one as its outline
        return new BlurredBackgroundProviderBuilder(resourcesProvider) {
                @Override
                public int getStrokeColorTop() {
                    return compactGlassStroke(super.getStrokeColorTop(), resourcesProvider);
                }

                @Override
                public int getStrokeColorBottom() {
                    return compactGlassStroke(super.getStrokeColorBottom(), resourcesProvider);
                }

                @Override
                public int getShadowColor() {
                    return Theme.isCompactMode(resourcesProvider) ? Theme.getColor(Theme.key_chat_compact_glassShadow, resourcesProvider) : super.getShadowColor();
                }

                @Override
                public float getShadowRadius() {
                    return Theme.isCompactMode(resourcesProvider) ? dpf2(2.25f) : super.getShadowRadius();
                }

                @Override
                public float getShadowDy() {
                    return Theme.isCompactMode(resourcesProvider) ? dpf2(1) : super.getShadowDy();
                }

                @Override
                public int getOutlineShadowColor() {
                    return Theme.isCompactMode(resourcesProvider) ? Theme.getColor(Theme.key_chat_compact_glassHairline, resourcesProvider) : 0;
                }

                @Override
                public int getOutlineColor() {
                    return Theme.isCompactMode(resourcesProvider) ? Theme.getColor(Theme.key_chat_compact_glassOutline, resourcesProvider) : 0;
                }

                @Override
                public float getOutlineWidth() {
                    return dpf2(0.6f);
                }
            }
                .setBackgroundColor((r, isDark) -> {
                    if (!checkBlurEnabled(resourcesProvider)) {
                        return ColorUtils.setAlphaComponent(Theme.getColor(isDark ?
                            Theme.key_actionBarDefault : Theme.key_chat_topPanelBackground, r), 255);
                    }

                    final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.74f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_chat_topPanelBackground, r);
                    return Theme.multAlpha(colorBg, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(alpha));
                })
                .setStrokeColorTop(0xFFFFFFFF, 0x20FFFFFF)
                .setStrokeColorBottom(0xFFFFFFFF, 0x14FFFFFF)
                .setShadowColor(0x20000000, 0)
                //.setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(0.55f), dpf2(0.55f))
                .build();
    }

    // with the design's outline there is no white highlight on the glass
    private static int compactGlassStroke(int color, Theme.ResourcesProvider resourcesProvider) {
        if (!Theme.isCompactMode(resourcesProvider) || org.telegram.messenger.mirrly.MirrlyConfig.isLiquidGlassEverywhere()) return color;
        return Color.alpha(Theme.getColor(Theme.key_chat_compact_glassOutline, resourcesProvider)) != 0 ? 0 : color;
    }

    public static BlurredBackgroundProvider simpleActionBar(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.74f : 0.80f;
                final int colorBg = Theme.getColor(Theme.key_windowBackgroundGray, r);
                int colorTarget = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                if (isDark) {
                    colorTarget = guardDarkGlassTarget(r, colorBg, colorTarget, 0.055f);
                }
                return solveSrcColor(colorBg, colorTarget, alpha);
            })
            .setStrokeColorTop(0xFFFFFFFF, 0x20FFFFFF)
            .setStrokeColorBottom(0xFFFFFFFF, 0x14FFFFFF)
            .setShadowColor((r, isDark) -> isDark ? (isAmoledSurfaceInverted(r) ? 0x24000000 : 0) : 0x16333333)
            .setShadowLayer(dpf2(24 / 3f), 0, dpf2(7f / 3f))
            .setStrokeWidth(dpf2(0.55f), dpf2(0.55f))
            .build();
    }

    public static BlurredBackgroundProvider floatingButtonAccent(Theme.ResourcesProvider resourcesProvider, boolean isSubButton) {
        if (isSubButton) {
            return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    int colorTarget = Theme.getColor(Theme.key_glass_targetMainTopPanel, r);
                    if (isDark) {
                        colorTarget = guardDarkGlassTarget(r, colorBg, colorTarget, 0.055f);
                    }
                    if (!checkBlurEnabled(r)) {
                        return ColorUtils.setAlphaComponent(colorTarget, 255);
                    }
                    return solveSrcColor(colorBg, colorTarget, alpha);
                })
                .setStrokeColorTop(0xE6FFFFFF, 0x26FFFFFF)
                .setStrokeColorBottom(0x1E000000, 0x14FFFFFF)
                .setShadowColor(0x22000000, 0x30000000)
                .setShadowLayer(dpf2(3.5f), 0, dpf2(1f))
                .setStrokeWidth(dpf2(0.55f), dpf2(0.55f))
                .build();
        }
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, r);
                if (!checkBlurEnabled(r)) {
                    return ColorUtils.setAlphaComponent(accent, 255);
                }
                final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.84f : 0.78f;
                return Theme.multAlpha(accent, alpha);
            })
            .setStrokeColorTop(0x70FFFFFF, 0x38FFFFFF)
            .setStrokeColorBottom(0x24FFFFFF, 0x18FFFFFF)
            .setShadowColor(0x2E000000, 0x34000000)
            .setShadowLayer(dpf2(4f), 0, dpf2(1.5f))
            .setStrokeWidth(dpf2(0.65f), dpf2(0.65f))
            .build();
    }

    private static boolean isAmoledSurfaceInverted(Theme.ResourcesProvider r) {
        final int whiteColor = Theme.getColor(Theme.key_windowBackgroundWhite, r);
        final int grayColor = Theme.getColor(Theme.key_windowBackgroundGray, r);
        return AndroidUtilities.computePerceivedBrightness(whiteColor)
                <= AndroidUtilities.computePerceivedBrightness(grayColor) + 0.002f;
    }

    private static int guardDarkGlassTarget(Theme.ResourcesProvider r, int colorBg, int colorTarget, float minContrastLum) {
        final float bgLum = AndroidUtilities.computePerceivedBrightness(colorBg);
        final int whiteColor = Theme.getColor(Theme.key_windowBackgroundWhite, r);
        final int grayColor = Theme.getColor(Theme.key_windowBackgroundGray, r);
        final float refLum = Math.max(bgLum, Math.max(
                AndroidUtilities.computePerceivedBrightness(whiteColor),
                AndroidUtilities.computePerceivedBrightness(grayColor)));
        int best = colorTarget;
        float bestLum = AndroidUtilities.computePerceivedBrightness(best);
        final boolean inverted = isAmoledSurfaceInverted(r);

        if (bestLum <= bgLum + 0.002f || (inverted && bestLum < refLum + minContrastLum)) {
            final int[] fallbacks = {
                Theme.key_windowBackgroundUnchecked,
                Theme.key_dialogBackground,
                Theme.key_actionBarDefaultSubmenuBackground
            };
            for (int key : fallbacks) {
                final int candidate = Theme.getColor(key, r);
                final float candidateLum = AndroidUtilities.computePerceivedBrightness(candidate);
                if (candidateLum > bestLum && candidateLum > bgLum + 0.002f && candidateLum < 0.28f) {
                    best = candidate;
                    bestLum = candidateLum;
                }
            }
            if (bestLum < refLum + minContrastLum) {
                final int base = bestLum >= bgLum ? best : colorBg;
                final float baseLum = AndroidUtilities.computePerceivedBrightness(base);
                final float needed = MathUtils.clamp((refLum + minContrastLum - baseLum) / Math.max(0.1f, 1f - baseLum), 0.045f, 0.14f);
                best = ColorUtils.blendARGB(base, Color.WHITE, needed);
            }
        }
        return best;
    }

    /** A frosted sheet over the content, e.g. the list over the map: the content shows through its blur. */
    public static BlurredBackgroundProvider frostedSheet(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> glassMultAlpha(Theme.getColor(Theme.key_dialogBackground, r), isDark ? 0.82f : 0.78f))
            .setStrokeColorTop(0xFFFFFFFF, 0x20FFFFFF)
            .setStrokeColorBottom(0, 0)
            .setShadowColor(0x16333333, 0)
            .setShadowLayer(dpf2(24 / 3f), 0, -dpf2(2f / 3f))
            .setStrokeWidth(dpf2(0.55f), 0)
            .build();
    }

    public static BlurredBackgroundProvider topPanelChatActivityTags(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                if (!checkBlurEnabled(resourcesProvider)) {
                    return ColorUtils.setAlphaComponent(Theme.getColor(isDark ?
                            Theme.key_actionBarDefault : Theme.key_chat_topPanelBackground, r), 255);
                }

                final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.74f : 0.76f;
                final int colorBg = Theme.getColor(Theme.key_chat_topPanelBackground, r);
                return Theme.multAlpha(colorBg, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(alpha));
            })
            .setStrokeColorTop(0xE6FFFFFF, 0x24FFFFFF)
            .setStrokeColorBottom(0x33FFFFFF, 0x14FFFFFF)
            .setShadowColor(0, 0)
            .setShadowLayer(0, 0, 0)
            .setStrokeWidth(dpf2(0.5f), dpf2(0.5f))
            .build();
    }

    public static BlurredBackgroundProvider topPanelChatActivitySearchListBg(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = 0.7f;
                    final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    return Theme.multAlpha(colorBg, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(alpha));
                })
                .setStrokeColorTop(0, 0)
                .setStrokeColorBottom(0, 0)
                .setShadowColor(0, 0)
                .setShadowLayer(0, 0, 0)
                .setStrokeWidth(0, 0)
                .build();
    }

    public static BlurredBackgroundProvider bulletin(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f;
                final int colorBg = Theme.getColor(Theme.key_undo_background, r);
                return Theme.multAlpha(colorBg, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(alpha));
            })
            //.setStrokeColorTop(0xFFFFFFFF, 0x28FFFFFF)
            //.setStrokeColorBottom(0xFFFFFFFF, 0x14FFFFFF)
            //.setShadowColor(0x20000000, 0)
            //.setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
            .setStrokeWidth(dpf2(0.5f), dpf2(0.5f))
            .build();
    }

    /** A hint (HintView2): dark in both themes for its white text, an even 1px stroke along the edge and the arrow. */
    public static BlurredBackgroundProvider hint(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) -> {
                final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.82f : 0.76f;
                final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                final int colorTarget = 0xFF282828;
                return isDark ? glassMultAlpha(0xFF111111, 0.9f) : solveSrcColor(colorBg, colorTarget, alpha);
            })
            .setStrokeColorTop(0x33FFFFFF, 0x33FFFFFF)
            .setStrokeColorBottom(0x1AFFFFFF, 0x1AFFFFFF)
            .setShadowColor(0x30333333, 0x30000000)
            .setShadowLayer(dpf2(6f), 0, 0)
            .setStrokeWidth(1, 1)
            .build();
    }

    public static BlurredBackgroundProvider inputFieldDialogActivity(Theme.ResourcesProvider resourcesProvider) {
        return topPanel(resourcesProvider);
    }

    public static BlurredBackgroundProvider inputFieldShareAlert(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f;
                    final int colorBg = Theme.getColor(Theme.key_windowBackgroundWhite, r);
                    final int colorTarget = Theme.getColor(Theme.key_chat_messagePanelBackground, r);
                    return solveSrcColor(colorBg, colorTarget, alpha);
                })
                .setStrokeColorTop(0x28FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x14FFFFFF, 0x14FFFFFF)
                .setShadowColor(0x20000000, 0)
                .setShadowLayer(dpf2(10 / 3f), 0, dpf2(2 / 3f))
                .setStrokeWidth(dpf2(1), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider photoViewer(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final float alpha = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0.85f : 0.76f;
                    final int colorBg = 0xFF000000;
                    final int colorTarget = 0xFF1A1A1A;
                    return 0; // solveSrcColor(colorBg, colorTarget, alpha);
                })
                .setStrokeColorTop(0x28FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x14FFFFFF, 0x14FFFFFF)
                .setStrokeWidth(dpf2(2 / 3f), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider photoViewerMenu(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> 0x40000000)
                .setStrokeColorTop(0x28FFFFFF, 0x28FFFFFF)
                .setStrokeColorBottom(0x14FFFFFF, 0x14FFFFFF)
                .setStrokeWidth(dpf2(2 / 3f), dpf2(2 / 3f))
                .build();
    }

    public static BlurredBackgroundProvider premiumButton(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor((r, isDark) ->
                glassMultAlpha(Theme.getColor(Theme.key_dialogBackground, r), 0.78f))
            .setStrokeColorTop(0xFFFFFFFF, 0x20FFFFFF)
            .setStrokeColorBottom(0, 0x20FFFFFF)
            .setShadowColor(0x48000000, 0x04FFFFFF)
            .setShadowLayer(dpf2(2 / 3f), 0, 0)
            .setStrokeWidth(dpf2(0.67f), dpf2(0.67f))
            .build();
    }

    public static BlurredBackgroundProvider shadow(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setStrokeColorTop(0, 0x28FFFFFF)
            .setStrokeColorBottom(0, 0x14FFFFFF)
            .setShadowColor(0x30000000, 0x04FFFFFF)
            .setShadowLayer(dpf2(12 / 3f), 0, dpf2(1 / 3f))
            .setStrokeWidth(dpf2(0.4f), dpf2(0.4f))
            .build();
    }

    private static int glassMultAlpha(int color, float alpha) {
        return Theme.multAlpha(color, org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(alpha));
    }

    public static int solveSrcColor(int bgColor, int outColor, float alpha) {
        alpha = org.telegram.messenger.mirrly.MirrlyConfig.getEffectiveGlassAlpha(alpha);
        alpha = MathUtils.clamp(alpha, 0, 1);

        // Edge cases
        if (alpha <= 0f) {
            return Color.argb(0, 0, 0, 0);
        }
        if (alpha >= 1f) {
            return Color.argb(255, Color.red(outColor), Color.green(outColor), Color.blue(outColor));
        }

        final int bgR = Color.red(bgColor);
        final int bgG = Color.green(bgColor);
        final int bgB = Color.blue(bgColor);

        final int outR = Color.red(outColor);
        final int outG = Color.green(outColor);
        final int outB = Color.blue(outColor);

        final float invA = 1f - alpha;

        final int srcR = MathUtils.clamp(Math.round((outR - bgR * invA) / alpha), 0, 255);
        final int srcG = MathUtils.clamp(Math.round((outG - bgG * invA) / alpha), 0, 255);
        final int srcB = MathUtils.clamp(Math.round((outB - bgB * invA) / alpha), 0, 255);

        final int a8 = MathUtils.clamp(Math.round(alpha * 255f), 0, 255);

        return Color.argb(a8, srcR, srcG, srcB);
    }

    public static boolean checkBlurEnabled(Theme.ResourcesProvider resourcesProvider) {
        return checkBlurEnabled(UserConfig.selectedAccount, resourcesProvider);
    }

    public static boolean checkBlurEnabled(int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        final boolean isLight = !isDark;
        boolean blurEnabled = SharedConfig.chatBlurEnabled();
        if (blurEnabled && isLight) {
            if (MessagesController.getInstance(currentAccount).config.disableBlurInLightTheme.get()) {
                blurEnabled = false;
            }
        }
        if (blurEnabled && isDark) {
            if (MessagesController.getInstance(currentAccount).config.disableBlurInDarkTheme.get()) {
                blurEnabled = false;
            }
        }
        return blurEnabled;
    }
}
