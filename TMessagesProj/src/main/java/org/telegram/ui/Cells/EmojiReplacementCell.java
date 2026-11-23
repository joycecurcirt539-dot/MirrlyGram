/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.LayoutHelper;

import java.util.HashSet;

public class EmojiReplacementCell extends FrameLayout implements Theme.Colorable {

    private ImageView imageView;
    private String emoji;
    private final Theme.ResourcesProvider resourcesProvider;

    private AnimatedEmojiDrawable emojiDrawable;
    private int currentBgRes;

    public EmojiReplacementCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrame(42, 42, Gravity.CENTER_HORIZONTAL, 0, 5, 0, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(52) + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(54), MeasureSpec.EXACTLY));
    }

    public void setEmoji(String e, int side) {
        emoji = e;
        if (emoji != null && emoji.startsWith("animated_")) {
            try {
                long documentId = Long.parseLong(emoji.substring(9));
                if (emojiDrawable == null || emojiDrawable.getDocumentId() != documentId) {
                    emojiDrawable = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, documentId);
                    emojiDrawable.addView(this);
                }
            } catch (Exception ignore) {}
        } else {
            if (emojiDrawable != null) {
                emojiDrawable.removeView(this);
                emojiDrawable = null;
            }
        }
        if (emojiDrawable == null) {
            imageView.setImageDrawable(Emoji.getEmojiBigDrawable(e));
        } else {
            imageView.setImageDrawable(null);
        }

        int bgRes = 0;
        int padLeft = 0;
        int padRight = 0;
        if (side == -1) {
            bgRes = R.drawable.stickers_back_left;
            padLeft = AndroidUtilities.dp(7);
        } else if (side == 0) {
            bgRes = R.drawable.stickers_back_center;
        } else if (side == 1) {
            bgRes = R.drawable.stickers_back_right;
            padRight = AndroidUtilities.dp(7);
        } else if (side == 2) {
            bgRes = R.drawable.stickers_back_all;
            padLeft = AndroidUtilities.dp(3);
            padRight = AndroidUtilities.dp(3);
        }
        if (bgRes != 0) {
            if (bgRes != currentBgRes || getBackground() == null) {
                Drawable bg = ContextCompat.getDrawable(getContext(), bgRes);
                if (bg != null) {
                    bg = bg.mutate();
                }
                setBackground(bg);
                currentBgRes = bgRes;
            }
            setPadding(padLeft, 0, padRight, 0);
        }
        StickerCell.applyHintPanelBackground(this, resourcesProvider);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (emojiDrawable != null) {
            final int sz = AndroidUtilities.dp(38);
            emojiDrawable.setBounds((getWidth() - sz) / 2, (getHeight() - sz) / 2, (getWidth() + sz) / 2, (getHeight() + sz) / 2);
            emojiDrawable.draw(canvas);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (emojiDrawable != null) {
            emojiDrawable.removeView(this);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (emojiDrawable != null) {
            emojiDrawable.addView(this);
        }
        updateColors();
    }

    @Override
    public void updateColors() {
        StickerCell.applyHintPanelBackground(this, resourcesProvider);
    }

    @Override
    public void addColorKeys(HashSet<Integer> colors) {
        if (colors != null) {
            colors.add(Theme.key_chat_stickersHintPanel);
            colors.add(Theme.key_actionBarDefaultSubmenuBackground);
        }
    }

    public String getEmoji() {
        return emoji;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        imageView.invalidate();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
