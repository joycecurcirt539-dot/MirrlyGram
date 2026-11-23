/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import org.telegram.messenger.FileLog;
import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;

public class ChatUnreadCell extends FrameLayout implements Theme.Colorable {

    private TextView textView;
    private ImageView imageView;
    private FrameLayout backgroundLayout;
    private Theme.ResourcesProvider resourcesProvider;

    public ChatUnreadCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        backgroundLayout = new FrameLayout(context);
        addView(backgroundLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 27, Gravity.LEFT | Gravity.TOP, 0, 7, 0, 0));

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.ic_ab_new);
        imageView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        backgroundLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        textView = new TextView(context);
        textView.setPadding(0, 0, 0, AndroidUtilities.dp(1));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTypeface(AndroidUtilities.bold());
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));

        applyThemeColors(true);
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public ImageView getImageView() {
        return imageView;
    }

    public TextView getTextView() {
        return textView;
    }

    public FrameLayout getBackgroundLayout() {
        return backgroundLayout;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateColors();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40), MeasureSpec.EXACTLY));
    }

    private int getColor(int key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    private boolean isDarkTheme() {
        if (resourcesProvider != null) {
            return resourcesProvider.isDark();
        }
        return Theme.isCurrentThemeDark();
    }

    private int resolveUnreadBgColor() {
        final int bgColor = getColor(Theme.key_chat_unreadMessagesStartBackground);
        if (isDarkTheme() && AndroidUtilities.computePerceivedBrightness(bgColor) > 0.85f) {
            final int fallback = getColor(Theme.key_actionBarDefaultSubmenuBackground);
            final float lum = AndroidUtilities.computePerceivedBrightness(fallback);
            return (lum > 0.02f && lum < 0.5f) ? fallback : 0xFF212121;
        }
        return bgColor;
    }

    private boolean initialized;
    private boolean appliedCompactMode;
    private int themedBackgroundColor;
    private int themedTextColor;
    private int themedArrowColor;
    private ColorFilter appliedBackgroundFilter;

    @Override
    public void updateColors() {
        try {
            if (applyThemeColors(false)) {
                invalidate();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean applyThemeColors(boolean force) {
        final boolean compact = Theme.isCompactMode(resourcesProvider);
        final boolean modeChanged = force || !initialized || compact != appliedCompactMode;
        boolean changed = false;

        if (modeChanged) {
            initialized = true;
            appliedCompactMode = compact;
            changed = true;
            ThemeBind.clear(backgroundLayout);
            appliedBackgroundFilter = null;
            if (compact) {
                final int compactBg = getColor(Theme.key_chat_compact_actionBackground);
                themedBackgroundColor = compactBg;
                ThemeBind.fillBackground(
                        backgroundLayout,
                        Theme.createRoundRectDrawable(0, compactBg),
                        ThemeBind.of(Theme.key_chat_compact_actionBackground),
                        resourcesProvider
                );
                if (textView != null) {
                    themedTextColor = getColor(Theme.key_chat_compact_actionText);
                    ThemeBind.textColor(textView, Theme.key_chat_compact_actionText, resourcesProvider);
                }
                if (imageView != null) {
                    imageView.setVisibility(GONE);
                }
            } else {
                Drawable bg = ContextCompat.getDrawable(getContext(), R.drawable.newmsg_divider);
                if (bg != null) {
                    bg = bg.mutate();
                    backgroundLayout.setBackground(bg);
                } else {
                    backgroundLayout.setBackgroundResource(R.drawable.newmsg_divider);
                    bg = backgroundLayout.getBackground();
                }
                final int rawBgColor = getColor(Theme.key_chat_unreadMessagesStartBackground);
                final int bgColor = resolveUnreadBgColor();
                themedBackgroundColor = bgColor;
                if (bg != null) {
                    if (bgColor == rawBgColor) {
                        ThemeBind.drawableColorFilter(backgroundLayout, bg, Theme.key_chat_unreadMessagesStartBackground, resourcesProvider, PorterDuff.Mode.MULTIPLY);
                    } else {
                        bg.setColorFilter(new PorterDuffColorFilter(bgColor, PorterDuff.Mode.MULTIPLY));
                    }
                    appliedBackgroundFilter = bg.getColorFilter();
                }
                if (textView != null) {
                    themedTextColor = getColor(Theme.key_chat_unreadMessagesStartText);
                    ThemeBind.textColor(textView, Theme.key_chat_unreadMessagesStartText, resourcesProvider);
                }
                if (imageView != null) {
                    imageView.setVisibility(VISIBLE);
                    themedArrowColor = getColor(Theme.key_chat_unreadMessagesStartArrowIcon);
                    ThemeBind.colorFilter(imageView, Theme.key_chat_unreadMessagesStartArrowIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
                }
            }
            return true;
        }

        if (compact) {
            final Drawable bg = backgroundLayout != null ? backgroundLayout.getBackground() : null;
            if (bg != null && bg.getColorFilter() != null) {
                bg.setColorFilter(null);
                changed = true;
            }
            final int compactBg = getColor(Theme.key_chat_compact_actionBackground);
            if (bg instanceof ShapeDrawable && compactBg != themedBackgroundColor) {
                ((ShapeDrawable) bg).getPaint().setColor(compactBg);
                bg.invalidateSelf();
                themedBackgroundColor = compactBg;
                changed = true;
            }
            if (textView != null) {
                final int compactText = getColor(Theme.key_chat_compact_actionText);
                if (compactText != themedTextColor || textView.getCurrentTextColor() != compactText) {
                    themedTextColor = compactText;
                    ThemeBind.textColor(textView, Theme.key_chat_compact_actionText, resourcesProvider);
                    changed = true;
                }
            }
            if (imageView != null && imageView.getVisibility() != GONE) {
                imageView.setVisibility(GONE);
                changed = true;
            }
        } else {
            final Drawable bg = backgroundLayout != null ? backgroundLayout.getBackground() : null;
            final int rawBgColor = getColor(Theme.key_chat_unreadMessagesStartBackground);
            final int bgColor = resolveUnreadBgColor();
            if (bg != null && (bgColor != themedBackgroundColor || bg.getColorFilter() == null || (appliedBackgroundFilter != null && bg.getColorFilter() != appliedBackgroundFilter))) {
                themedBackgroundColor = bgColor;
                if (bgColor == rawBgColor) {
                    ThemeBind.drawableColorFilter(backgroundLayout, bg, Theme.key_chat_unreadMessagesStartBackground, resourcesProvider, PorterDuff.Mode.MULTIPLY);
                } else {
                    ThemeBind.clear(backgroundLayout);
                    bg.setColorFilter(new PorterDuffColorFilter(bgColor, PorterDuff.Mode.MULTIPLY));
                }
                appliedBackgroundFilter = bg.getColorFilter();
                backgroundLayout.invalidate();
                changed = true;
            }
            if (textView != null) {
                final int textColor = getColor(Theme.key_chat_unreadMessagesStartText);
                if (textColor != themedTextColor || textView.getCurrentTextColor() != textColor) {
                    themedTextColor = textColor;
                    ThemeBind.textColor(textView, Theme.key_chat_unreadMessagesStartText, resourcesProvider);
                    changed = true;
                }
            }
            if (imageView != null) {
                if (imageView.getVisibility() != VISIBLE) {
                    imageView.setVisibility(VISIBLE);
                    changed = true;
                }
                final int arrowColor = getColor(Theme.key_chat_unreadMessagesStartArrowIcon);
                if (arrowColor != themedArrowColor || imageView.getColorFilter() == null) {
                    themedArrowColor = arrowColor;
                    ThemeBind.colorFilter(imageView, Theme.key_chat_unreadMessagesStartArrowIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
