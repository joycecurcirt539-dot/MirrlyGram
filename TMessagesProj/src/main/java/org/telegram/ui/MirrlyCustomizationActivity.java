package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyMonetThemeHelper;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.AppIconsSelectorCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.GlassCaptureSourceCanvas;
import org.telegram.utils.glass.GlassEngine;
import org.telegram.utils.glass.positions.GlassPositionsArray;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceRenderNode;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Mirrlygram Customization settings fragment built 1:1 on native Telegram UI components:
 * live ThemePreviewMessagesCell, Monet theme card selector, palette styles/seeds, bubble corner radius, and app icons.
 */
public class MirrlyCustomizationActivity extends MirrlyBaseSettingsFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_CHAT_PREVIEW = 1;
    private static final int ID_MONET_CARDS = 2;
    private static final int ID_TOGGLE_FOLLOW_SYSTEM = 3;

    private static final int ID_MONET_WALLPAPER_PREVIEW = 9;
    private static final int ID_PALETTE_STYLE_SLIDER = 10;
    private static final int ID_SEED_COLOR_BAR = 11;
    private static final int ID_TOGGLE_OUT_GRADIENT = 12;
    private static final int ID_TOGGLE_THEMED_WALLPAPER = 13;
    private static final int ID_TOGGLE_GLASS_TINT = 14;
    private static final int ID_ACTION_REFRESH_PALETTE = 15;

    private static final int ID_BUBBLE_RADIUS_SLIDER = 20;
    private static final int ID_APP_ICONS_SELECTOR = 21;

    private static final int ID_GLASS_PREVIEW = 30;
    private static final int ID_TOGGLE_GLASS_BLUR = 31;
    private static final int ID_TOGGLE_GLASS_LIQUID = 32;
    private static final int ID_GLASS_SLIDER_BLUR = 33;
    private static final int ID_GLASS_SLIDER_OPACITY = 34;
    private static final int ID_GLASS_SLIDER_SATURATION = 35;
    private static final int ID_GLASS_SLIDER_REFRACT = 36;
    private static final int ID_GLASS_SLIDER_THICKNESS = 37;
    private static final int ID_GLASS_SLIDER_RIM = 38;
    private static final int ID_ACTION_RESET_GLASS = 39;
    private static final int ID_TOGGLE_DIALOGS_GLASS_HEADER = 40;
    private static final int ID_TOGGLE_LIQUID_GLASS_EVERYWHERE = 41;

    private boolean isApplyingLocally = false;
    private int initialBubbleRadius = 17;

    @Override
    protected boolean shouldForceActionBarTitle() {
        return true;
    }

    @Override
    public boolean onFragmentCreate() {
        initialBubbleRadius = SharedConfig.bubbleRadius;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.wallpapersNeedReload);
        return super.onFragmentCreate();
    }

    @Override
    public void onPause() {
        super.onPause();
        MirrlyConfig.saveGlassSettings();
    }

    @Override
    public void onFragmentDestroy() {
        MirrlyConfig.saveGlassSettings();
        if (SharedConfig.bubbleRadius != initialBubbleRadius) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, 0);
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.wallpapersNeedReload);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            onThemeUpdatedSmoothly();
        } else if (id == NotificationCenter.didSetNewWallpapper || id == NotificationCenter.wallpapersNeedReload) {
            invalidateChatPreviewOnly();
        }
    }

    private void invalidateChatPreviewOnly() {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof ThemePreviewMessagesCell) {
                child.invalidate();
            }
        }
    }

    @Override
    protected CharSequence getScreenTitle() {
        return LocaleController.getString(R.string.MirrlyCustomizationTitle);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(ChatPreviewFactory.of(ID_CHAT_PREVIEW, this));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyMonetModeHeader)));
        items.add(MonetThemeCardsCell.Factory.of(ID_MONET_CARDS, this));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_TOGGLE_FOLLOW_SYSTEM,
                R.drawable.msg_theme,
                LocaleController.getString(R.string.MirrlyMonetFollowSystem),
                MirrlyConfig.getMonetMode() == MirrlyConfig.MONET_MODE_AUTO
        ));
        items.add(UItem.asShadow(LocaleController.getString(
                MirrlyMonetThemeHelper.isDeviceMonetSupported()
                        ? R.string.MirrlyMonetHint
                        : R.string.MirrlyMonetFallbackHint
        )));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyMonetOptionsHeader)));
        items.add(PaletteStyleCell.Factory.of(ID_PALETTE_STYLE_SLIDER, this));
        items.add(SeedColorBarCell.Factory.of(ID_SEED_COLOR_BAR, this));
        items.add(MonetWallpaperPreviewCell.Factory.of(ID_MONET_WALLPAPER_PREVIEW, this));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_TOGGLE_OUT_GRADIENT,
                R.drawable.msg_palette,
                LocaleController.getString(R.string.MirrlyMonetOutGradient),
                MirrlyConfig.isMonetOutGradient()
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_TOGGLE_THEMED_WALLPAPER,
                R.drawable.msg_gallery,
                LocaleController.getString(R.string.MirrlyMonetThemedWallpaper),
                MirrlyConfig.isMonetThemedWallpaper()
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_TOGGLE_GLASS_TINT,
                R.drawable.msg_blur_radial,
                LocaleController.getString(R.string.MirrlyMonetGlassTint),
                MirrlyConfig.isMonetGlassTint()
        ));
        items.add(UItem.asButton(
                ID_ACTION_REFRESH_PALETTE,
                R.drawable.msg_retry,
                LocaleController.getString(R.string.MirrlyMonetRefresh)
        ).accent());
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyGlassHeader)));
        items.add(GlassPreviewCell.Factory.of(ID_GLASS_PREVIEW, this));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_TOGGLE_GLASS_BLUR,
                R.drawable.msg_blur_radial,
                LocaleController.getString(R.string.MirrlyGlassBlurToggle),
                MirrlyConfig.isGlassBlurEnabled()
        ));
        if (MirrlyConfig.isGlassLiquidSupported()) {
            items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                    ID_TOGGLE_GLASS_LIQUID,
                    R.drawable.msg_blur_radial,
                    LocaleController.getString(R.string.MirrlyGlassLiquidToggle),
                    MirrlyConfig.isGlassLiquidEnabled()
            ));
            items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                    ID_TOGGLE_LIQUID_GLASS_EVERYWHERE,
                    R.drawable.msg_blur_radial,
                    LocaleController.getString(R.string.MirrlyGlassEverywhereToggle),
                    MirrlyConfig.isLiquidGlassEverywhere()
            ));
        }
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_TOGGLE_DIALOGS_GLASS_HEADER,
                R.drawable.msg_customize,
                LocaleController.getString(R.string.MirrlyGlassDialogsHeader),
                MirrlyConfig.isDialogsGlassHeader()
        ));
        items.add(GlassSliderCell.Factory.of(ID_GLASS_SLIDER_BLUR, this));
        items.add(GlassSliderCell.Factory.of(ID_GLASS_SLIDER_OPACITY, this));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items.add(GlassSliderCell.Factory.of(ID_GLASS_SLIDER_SATURATION, this));
        }
        if (MirrlyConfig.isGlassLiquidSupported()) {
            items.add(GlassSliderCell.Factory.of(ID_GLASS_SLIDER_REFRACT, this));
            items.add(GlassSliderCell.Factory.of(ID_GLASS_SLIDER_THICKNESS, this));
        }
        items.add(GlassSliderCell.Factory.of(ID_GLASS_SLIDER_RIM, this));
        items.add(UItem.asButton(
                ID_ACTION_RESET_GLASS,
                R.drawable.msg_reset,
                LocaleController.getString(R.string.MirrlyGlassReset)
        ).accent());
        items.add(UItem.asShadow(LocaleController.getString(
                MirrlyConfig.isGlassLiquidSupported()
                        ? R.string.MirrlyGlassHint
                        : R.string.MirrlyGlassLegacyHint
        )));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyCustomizationBubbleRadius)));
        items.add(BubbleRadiusCell.Factory.of(ID_BUBBLE_RADIUS_SLIDER, this));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyCustomizationAppIconHeader)));
        items.add(AppIconsCellFactory.of(ID_APP_ICONS_SELECTOR, this));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyCustomizationAppIconHint)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item == null) {
            return;
        }
        switch (item.id) {
            case ID_TOGGLE_FOLLOW_SYSTEM: {
                boolean next = MirrlyConfig.getMonetMode() != MirrlyConfig.MONET_MODE_AUTO;
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                performTactileTap(view);
                if (next) {
                    applyMonetChange(MirrlyConfig.MONET_MODE_AUTO, true, view);
                } else {
                    int fixedMode = MirrlyMonetThemeHelper.resolveEffectiveDark(getContext(), MirrlyConfig.MONET_MODE_AUTO)
                            ? MirrlyConfig.MONET_MODE_DARK
                            : MirrlyConfig.MONET_MODE_LIGHT;
                    applyMonetChange(fixedMode, false, null);
                }
                syncItemsWithoutNotify();
                refreshVisibleMonetControls();
                break;
            }

            case ID_TOGGLE_OUT_GRADIENT: {
                boolean next = !MirrlyConfig.isMonetOutGradient();
                MirrlyConfig.setMonetOutGradient(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                performTactileTap(view);
                syncItemsWithoutNotify();
                regenerateActiveMonetSafely();
                refreshVisibleMonetControls();
                break;
            }

            case ID_TOGGLE_THEMED_WALLPAPER: {
                boolean next = !MirrlyConfig.isMonetThemedWallpaper();
                MirrlyConfig.setMonetThemedWallpaper(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                performTactileTap(view);
                syncItemsWithoutNotify();
                regenerateActiveMonetSafely();
                refreshVisibleMonetControls();
                break;
            }

            case ID_TOGGLE_GLASS_TINT: {
                boolean next = !MirrlyConfig.isMonetGlassTint();
                MirrlyConfig.setMonetGlassTint(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                performTactileTap(view);
                syncItemsWithoutNotify();
                regenerateActiveMonetSafely();
                refreshVisibleMonetControls();
                break;
            }

            case ID_ACTION_REFRESH_PALETTE: {
                performTactileTap(view);
                if (MirrlyConfig.getMonetCustomSeed() != 0) {
                    MirrlyConfig.setMonetCustomSeed(0);
                }
                if (MirrlyConfig.getMonetMode() == MirrlyConfig.MONET_MODE_OFF) {
                    int mode = Theme.isCurrentThemeNight()
                            ? MirrlyConfig.MONET_MODE_DARK
                            : MirrlyConfig.MONET_MODE_LIGHT;
                    applyMonetChange(mode, true, view);
                } else {
                    regenerateActiveMonetSafely();
                }
                syncItemsWithoutNotify();
                refreshVisibleMonetControls();
                BulletinFactory.of(this)
                        .createSuccessBulletin(LocaleController.getString(R.string.MirrlyMonetRefreshed))
                        .show();
                break;
            }

            case ID_TOGGLE_GLASS_BLUR: {
                boolean next = !MirrlyConfig.isGlassBlurEnabled();
                MirrlyConfig.setGlassBlurEnabled(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                performTactileTap(view);
                syncItemsWithoutNotify();
                refreshVisibleGlassControls(true, false);
                break;
            }

            case ID_TOGGLE_GLASS_LIQUID: {
                boolean next = !MirrlyConfig.isGlassLiquidEnabled();
                MirrlyConfig.setGlassLiquidEnabled(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                performTactileTap(view);
                syncItemsWithoutNotify();
                syncGlassBlurRowDirectly();
                refreshVisibleGlassControls(true, false);
                break;
            }

            case ID_TOGGLE_LIQUID_GLASS_EVERYWHERE: {
                boolean next = !MirrlyConfig.isLiquidGlassEverywhere();
                MirrlyConfig.setLiquidGlassEverywhere(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                performTactileTap(view);
                syncItemsWithoutNotify();
                syncSwitchRowDirectly(ID_TOGGLE_GLASS_BLUR, MirrlyConfig.isGlassBlurEnabled());
                syncSwitchRowDirectly(ID_TOGGLE_GLASS_LIQUID, MirrlyConfig.isGlassLiquidEnabled());
                refreshVisibleGlassControls(true, false);
                break;
            }

            case ID_TOGGLE_DIALOGS_GLASS_HEADER: {
                boolean next = !MirrlyConfig.isDialogsGlassHeader();
                MirrlyConfig.setDialogsGlassHeader(next);
                if (view instanceof TextCell) {
                    ((TextCell) view).setChecked(next);
                }
                performTactileTap(view);
                syncItemsWithoutNotify();
                break;
            }

            case ID_ACTION_RESET_GLASS: {
                MirrlyConfig.resetGlassDefaults();
                performTactileTap(view);
                syncItemsWithoutNotify();
                refreshVisibleGlassControls(true, true);
                BulletinFactory.of(this)
                        .createSuccessBulletin(LocaleController.getString(R.string.MirrlyGlassResetDone))
                        .show();
                break;
            }
        }
    }

    private void onCardSelected(int cardIndex, View anchorView) {
        if (cardIndex == 0) {
            if (MirrlyConfig.getMonetMode() == MirrlyConfig.MONET_MODE_OFF) {
                return;
            }
            applyMonetChange(MirrlyConfig.MONET_MODE_OFF, true, anchorView);
        } else if (cardIndex == 1) {
            if (MirrlyConfig.getMonetMode() == MirrlyConfig.MONET_MODE_LIGHT) {
                return;
            }
            applyMonetChange(MirrlyConfig.MONET_MODE_LIGHT, true, anchorView);
        } else if (cardIndex == 2) {
            if (MirrlyConfig.getMonetMode() == MirrlyConfig.MONET_MODE_DARK && !MirrlyConfig.isMonetAmoled()) {
                return;
            }
            MirrlyConfig.setMonetAmoled(false);
            applyMonetChange(MirrlyConfig.MONET_MODE_DARK, true, anchorView);
        } else if (cardIndex == 3) {
            if (MirrlyConfig.getMonetMode() == MirrlyConfig.MONET_MODE_DARK && MirrlyConfig.isMonetAmoled()) {
                return;
            }
            MirrlyConfig.setMonetAmoled(true);
            applyMonetChange(MirrlyConfig.MONET_MODE_DARK, true, anchorView);
        }
        syncItemsWithoutNotify();
        syncFollowSystemRowDirectly();
        refreshVisibleMonetControls();
    }

    private void onPaletteStyleChanged(int styleIndex) {
        if (MirrlyConfig.getMonetStyle() == styleIndex) {
            return;
        }
        MirrlyConfig.setMonetStyle(styleIndex);
        syncItemsWithoutNotify();
        regenerateActiveMonetSafely();
        refreshVisibleMonetControls();
    }

    private void onSeedColorSelected(int seedColor) {
        if (MirrlyConfig.getMonetCustomSeed() == seedColor) {
            return;
        }
        MirrlyConfig.setMonetCustomSeed(seedColor);
        syncItemsWithoutNotify();
        regenerateActiveMonetSafely();
        refreshVisibleMonetControls();
    }

    private void onBubbleRadiusChanged(int newRadius, boolean commitSync) {
        int oldRadius = SharedConfig.bubbleRadius;
        int clamped = Math.max(0, Math.min(17, newRadius));
        boolean crossedThreshold = (oldRadius >= 10) != (clamped >= 10);
        if (oldRadius != clamped) {
            SharedConfig.bubbleRadius = clamped;
            relayoutChatPreviewBubbles(crossedThreshold);
            refreshWallpaperPreviewOnly();
        }
        if (commitSync) {
            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putInt("bubbleRadius", clamped);
            editor.apply();
            relayoutChatPreviewBubbles(true);
            syncItemsWithoutNotify();
        }
    }

    private void onGlassSliderChanged(int sliderId, int rawValue, boolean commitSync) {
        switch (sliderId) {
            case ID_GLASS_SLIDER_BLUR:
                MirrlyConfig.setGlassBlurScale(rawValue / 100f, commitSync);
                break;
            case ID_GLASS_SLIDER_OPACITY:
                MirrlyConfig.setGlassOpacityScale(rawValue / 100f, commitSync);
                break;
            case ID_GLASS_SLIDER_SATURATION:
                MirrlyConfig.setGlassSaturationScale(rawValue / 100f, commitSync);
                break;
            case ID_GLASS_SLIDER_REFRACT:
                MirrlyConfig.setGlassRefractScale(rawValue / 100f, commitSync);
                break;
            case ID_GLASS_SLIDER_THICKNESS:
                MirrlyConfig.setGlassThicknessDp(rawValue, commitSync);
                break;
            case ID_GLASS_SLIDER_RIM:
                MirrlyConfig.setGlassRimScale(rawValue / 100f, commitSync);
                break;
        }
        refreshGlassPreviewAndActionBar();
        if (commitSync) {
            syncItemsWithoutNotify();
        }
    }

    private void applyMonetChange(int mode, boolean animate, @Nullable View revealAnchor) {
        isApplyingLocally = true;
        try {
            MirrlyMonetThemeHelper.applyMonetMode(getContext(), mode, animate, revealAnchor);
        } finally {
            isApplyingLocally = false;
        }
    }

    private void regenerateActiveMonetSafely() {
        if (MirrlyConfig.getMonetMode() == MirrlyConfig.MONET_MODE_OFF) {
            return;
        }
        isApplyingLocally = true;
        try {
            MirrlyMonetThemeHelper.regenerateActiveMonet(getContext());
        } finally {
            isApplyingLocally = false;
        }
    }

    private void syncItemsWithoutNotify() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.updateWithoutNotify();
        }
    }

    private void syncFollowSystemRowDirectly() {
        if (listView == null) {
            return;
        }
        boolean autoChecked = MirrlyConfig.getMonetMode() == MirrlyConfig.MONET_MODE_AUTO;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MirrlyUiKit.IconSwitchCell) {
                MirrlyUiKit.IconSwitchCell cell = (MirrlyUiKit.IconSwitchCell) child;
                if (cell.boundItemId == ID_TOGGLE_FOLLOW_SYSTEM) {
                    cell.setChecked(autoChecked);
                    break;
                }
            }
        }
    }

    private void refreshVisibleMonetControls() {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonetThemeCardsCell) {
                ((MonetThemeCardsCell) child).refreshCardsState();
            } else if (child instanceof SeedColorBarCell) {
                ((SeedColorBarCell) child).refreshSelection();
            } else if (child instanceof PaletteStyleCell) {
                ((PaletteStyleCell) child).syncFromConfig();
            } else if (child instanceof MonetWallpaperPreviewCell) {
                ((MonetWallpaperPreviewCell) child).refreshFromConfig();
            } else if (child instanceof GlassPreviewCell) {
                ((GlassPreviewCell) child).onGlassOrThemeChanged();
            }
        }
    }

    private void refreshWallpaperPreviewOnly() {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonetWallpaperPreviewCell) {
                ((MonetWallpaperPreviewCell) child).refreshFromConfig();
            }
        }
    }

    private void syncGlassBlurRowDirectly() {
        syncSwitchRowDirectly(ID_TOGGLE_GLASS_BLUR, MirrlyConfig.isGlassBlurEnabled());
    }

    private void syncSwitchRowDirectly(int itemId, boolean checked) {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MirrlyUiKit.IconSwitchCell) {
                MirrlyUiKit.IconSwitchCell cell = (MirrlyUiKit.IconSwitchCell) child;
                if (cell.boundItemId == itemId) {
                    cell.setChecked(checked);
                    break;
                }
            }
        }
    }

    private void refreshGlassPreviewAndActionBar() {
        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof GlassPreviewCell) {
                    ((GlassPreviewCell) child).onGlassOrThemeChanged();
                }
            }
        }
        if (simpleActionBar != null) {
            simpleActionBar.updateButtonsGlassColors();
            simpleActionBar.invalidate();
        }
        if (actionBar != null) {
            actionBar.invalidate();
        }
        if (contentView != null) {
            contentView.invalidate();
        }
    }

    private void refreshVisibleGlassControls(boolean updateEnabled, boolean syncValues) {
        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof GlassPreviewCell) {
                    ((GlassPreviewCell) child).onGlassOrThemeChanged();
                } else if (child instanceof MonetThemeCardsCell) {
                    ((MonetThemeCardsCell) child).updateColors();
                } else if (child instanceof GlassSliderCell) {
                    GlassSliderCell slider = (GlassSliderCell) child;
                    if (updateEnabled) {
                        slider.updateEnabledState(true);
                    }
                    if (syncValues) {
                        slider.syncFromConfig(true);
                    }
                }
            }
        }
        if (simpleActionBar != null) {
            simpleActionBar.updateButtonsGlassColors();
            simpleActionBar.invalidate();
        }
        if (actionBar != null) {
            actionBar.invalidate();
        }
        if (contentView != null) {
            contentView.invalidate();
        }
    }

    private void onThemeUpdatedSmoothly() {
        syncItemsWithoutNotify();
        if (contentView != null) {
            ThemeBind.backgroundColor(contentView, Theme.key_windowBackgroundGray, getResourceProvider());
            contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider()));
        }
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof Theme.Colorable) {
                ((Theme.Colorable) child).updateColors();
            } else if (child instanceof ThemePreviewMessagesCell) {
                ThemePreviewMessagesCell preview = (ThemePreviewMessagesCell) child;
                preview.customAnimation = true;
                preview.invalidate();
                ChatMessageCell[] cells = preview.getCells();
                if (cells != null) {
                    for (ChatMessageCell cell : cells) {
                        if (cell != null) {
                            cell.invalidate();
                        }
                    }
                }
            } else if (child instanceof AppIconsSelectorCell) {
                AppIconsSelectorCell icons = (AppIconsSelectorCell) child;
                for (int j = 0; j < icons.getChildCount(); j++) {
                    View iconChild = icons.getChildAt(j);
                    if (iconChild instanceof Theme.Colorable) {
                        ((Theme.Colorable) iconChild).updateColors();
                    }
                }
            }
        }
        if (!isApplyingLocally) {
            syncFollowSystemRowDirectly();
        }
        listView.invalidate();
    }

    private void relayoutChatPreviewBubbles(boolean fullRelayout) {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof ThemePreviewMessagesCell) {
                ThemePreviewMessagesCell preview = (ThemePreviewMessagesCell) child;
                ChatMessageCell[] cells = preview.getCells();
                if (cells != null) {
                    for (ChatMessageCell cell : cells) {
                        if (cell != null) {
                            if (fullRelayout && cell.getMessageObject() != null) {
                                cell.getMessageObject().resetLayout();
                                cell.requestLayout();
                            }
                            cell.invalidate();
                        }
                    }
                }
                preview.invalidate();
            }
        }
    }

    private void performTactileTap(@Nullable View view) {
        if (view != null) {
            AndroidUtilities.vibrateCursor(view);
        }
    }

    /**
     * Wraps Telegram's native {@link ThemePreviewMessagesCell} inside the rounded section card.
     */
    public static class ChatPreviewFactory extends UItem.UItemFactory<ThemePreviewMessagesCell> {
        static {
            setup(new ChatPreviewFactory());
        }

        @Override
        public ThemePreviewMessagesCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            BaseFragment host = listView instanceof MirrlyUiKit.HeroCardView.HostProvider
                    ? ((MirrlyUiKit.HeroCardView.HostProvider) listView).getMirrlyHeroHost()
                    : null;
            ThemePreviewMessagesCell cell = new ThemePreviewMessagesCell(
                    context,
                    host != null ? host.getParentLayout() : null,
                    1,
                    0,
                    resourcesProvider
            ) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    return false;
                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    return false;
                }
            };
            cell.customAnimation = true;
            cell.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
                }
            });
            cell.setClipToOutline(true);
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            view.invalidate();
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return true;
        }

        public static UItem of(int id, MirrlyCustomizationActivity host) {
            UItem item = UItem.ofFactory(ChatPreviewFactory.class);
            item.id = id;
            item.object = new WeakReference<>(host);
            return item;
        }
    }

    /**
     * Horizontal 4-card Monet theme switcher matching Telegram's ThemeSmallPreviewView proportions
     * and reveal-from-card transitions.
     */
    private static class MonetThemeCardsCell extends LinearLayout implements Theme.Colorable {

        private static final int CARD_COUNT = 4;
        private final Theme.ResourcesProvider resourcesProvider;
        private final MiniThemeCardView[] cardViews = new MiniThemeCardView[CARD_COUNT];
        private WeakReference<MirrlyCustomizationActivity> hostRef;

        public MonetThemeCardsCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(12), dp(12), dp(12), dp(8));

            int[] labelRes = new int[]{
                    R.string.MirrlyMonetCardOff,
                    R.string.MirrlyMonetCardLight,
                    R.string.MirrlyMonetCardDark,
                    R.string.MirrlyMonetCardAmoled
            };

            for (int i = 0; i < CARD_COUNT; i++) {
                final int index = i;
                MiniThemeCardView card = new MiniThemeCardView(context, index, LocaleController.getString(labelRes[i]), resourcesProvider);
                ScaleStateListAnimator.apply(card, 0.04f, 1.4f);
                MirrlyUiKit.attachDisallowInterceptTouch(card);
                card.setOnClickListener(v -> {
                    AndroidUtilities.vibrateCursor(v);
                    MirrlyCustomizationActivity host = hostRef != null ? hostRef.get() : null;
                    if (host != null) {
                        host.onCardSelected(index, v);
                    }
                });
                cardViews[i] = card;
                LayoutParams lp = new LayoutParams(0, dp(104), 1f);
                if (i > 0) {
                    if (LocaleController.isRTL) {
                        lp.rightMargin = dp(8);
                    } else {
                        lp.leftMargin = dp(8);
                    }
                }
                addView(card, lp);
            }
            refreshCardsState();
        }

        public void setHost(MirrlyCustomizationActivity host) {
            this.hostRef = new WeakReference<>(host);
        }

        public void refreshCardsState() {
            int mode = MirrlyConfig.getMonetMode();
            boolean amoled = MirrlyConfig.isMonetAmoled();
            int activeCard;
            if (mode == MirrlyConfig.MONET_MODE_OFF) {
                activeCard = 0;
            } else if (mode == MirrlyConfig.MONET_MODE_LIGHT) {
                activeCard = 1;
            } else if (mode == MirrlyConfig.MONET_MODE_DARK) {
                activeCard = amoled ? 3 : 2;
            } else {
                boolean darkNow = MirrlyMonetThemeHelper.resolveEffectiveDark(getContext(), mode);
                activeCard = darkNow ? (amoled ? 3 : 2) : 1;
            }

            Context ctx = getContext();
            boolean outGradient = MirrlyConfig.isMonetOutGradient();
            boolean themedWallpaper = MirrlyConfig.isMonetThemedWallpaper();
            boolean glassTint = MirrlyConfig.isMonetGlassTint();
            int style = MirrlyConfig.getMonetStyle();
            int seed = MirrlyConfig.getMonetCustomSeed();

            MirrlyMonetThemeHelper.PreviewColors lightPreview = MirrlyMonetThemeHelper.getPreviewColors(
                    ctx, style, seed, false, false, outGradient, themedWallpaper, glassTint);
            MirrlyMonetThemeHelper.PreviewColors darkPreview = MirrlyMonetThemeHelper.getPreviewColors(
                    ctx, style, seed, true, false, outGradient, themedWallpaper, glassTint);
            MirrlyMonetThemeHelper.PreviewColors amoledPreview = MirrlyMonetThemeHelper.getPreviewColors(
                    ctx, style, seed, true, true, outGradient, themedWallpaper, glassTint);

            MirrlyMonetThemeHelper.PreviewColors offPreview = createDefaultCardPreview();

            cardViews[0].updateCard(offPreview, activeCard == 0, true);
            cardViews[1].updateCard(lightPreview, activeCard == 1, true);
            cardViews[2].updateCard(darkPreview, activeCard == 2, true);
            cardViews[3].updateCard(amoledPreview, activeCard == 3, true);
        }

        private MirrlyMonetThemeHelper.PreviewColors createDefaultCardPreview() {
            boolean night = Theme.isCurrentThemeNight();
            if (night) {
                return new MirrlyMonetThemeHelper.PreviewColors(
                        0xFF5A9DF8, 0xFF3E6BA3, 0xFF4C8CD9,
                        0xFF1D2733, 0xFF151E27,
                        0xFF222E3C, 0xFF2B5278, 0xFF2B5278,
                        0xFF151E27, 0xFF1D2C3D, 0xFF192431, 0xFF24374C, 0,
                        0xFFFFFFFF, 0xFF8D9BAE, 0xFFFFFFFF,
                        0xFF1D2733
                );
            }
            return new MirrlyMonetThemeHelper.PreviewColors(
                    0xFF3390EC, 0xFF5CAEFF, 0xFF4CA0F5,
                    0xFFFFFFFF, 0xFFF1F3F5,
                    0xFFFFFFFF, 0xFFD9F3C9, 0xFFEFFFDE,
                    0xFFDBDDAA, 0xFF9AC58E, 0xFFB8CE9D, 0xFF85B890, 0,
                    0xFF111418, 0xFF707579, 0xFF111418,
                    0xFFFFFFFF
            );
        }

        @Override
        public void updateColors() {
            if (cardViews[0] == null) {
                return;
            }
            refreshCardsState();
            for (MiniThemeCardView card : cardViews) {
                if (card != null) {
                    card.updateColors();
                }
            }
        }

        public static class Factory extends UItem.UItemFactory<MonetThemeCardsCell> {
            static {
                setup(new Factory());
            }

            @Override
            public MonetThemeCardsCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new MonetThemeCardsCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                MonetThemeCardsCell cell = (MonetThemeCardsCell) view;
                if (item.object instanceof WeakReference) {
                    Object ref = ((WeakReference<?>) item.object).get();
                    if (ref instanceof MirrlyCustomizationActivity) {
                        cell.setHost((MirrlyCustomizationActivity) ref);
                    }
                }
                cell.refreshCardsState();
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return true;
            }

            public static UItem of(int id, MirrlyCustomizationActivity host) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = new WeakReference<>(host);
                return item;
            }
        }
    }

    private static class MiniThemeCardView extends View implements Theme.Colorable {

        private final int cardIndex;
        private final String title;
        private final Theme.ResourcesProvider resourcesProvider;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint checkFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint checkTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        private final RectF cardRect = new RectF();
        private final RectF bubbleRect = new RectF();
        private final Path clipPath = new Path();
        private final AnimatedFloat selectionAnim = new AnimatedFloat(this, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);

        private MirrlyMonetThemeHelper.PreviewColors colors;
        private boolean selected;

        public MiniThemeCardView(Context context, int cardIndex, String title, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.cardIndex = cardIndex;
            this.title = title;
            this.resourcesProvider = resourcesProvider;
            barRimPaint.setStyle(Paint.Style.STROKE);
            barRimPaint.setStrokeWidth(dpf2(0.5f));
            outlinePaint.setStyle(Paint.Style.STROKE);
            checkTickPaint.setStyle(Paint.Style.STROKE);
            checkTickPaint.setStrokeCap(Paint.Cap.ROUND);
            checkTickPaint.setStrokeJoin(Paint.Join.ROUND);
            checkTickPaint.setStrokeWidth(dp(1.7f));
            textPaint.setTextSize(dp(12));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        public void updateCard(MirrlyMonetThemeHelper.PreviewColors colors, boolean selected, boolean animated) {
            this.colors = colors;
            this.selected = selected;
            selectionAnim.set(selected ? 1f : 0f, !animated);
            invalidate();
        }

        @Override
        public void updateColors() {
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (colors == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }

            float t = selectionAnim.set(selected ? 1f : 0f);
            float w = getWidth();
            float previewBottom = getHeight() - dp(24);
            float pad = dp(2);
            cardRect.set(pad, pad, w - pad, previewBottom);

            float corner = dp(10);
            clipPath.rewind();
            clipPath.addRoundRect(cardRect, corner, corner, Path.Direction.CW);

            canvas.save();
            canvas.clipPath(clipPath);
            if (colors.wallpaperTop != colors.wallpaperBottom) {
                bgPaint.setShader(new LinearGradient(
                        cardRect.left, cardRect.top,
                        cardRect.right, cardRect.bottom,
                        colors.wallpaperTop, colors.wallpaperBottom,
                        Shader.TileMode.CLAMP
                ));
            } else {
                bgPaint.setShader(null);
                bgPaint.setColor(colors.wallpaperTop);
            }
            canvas.drawRect(cardRect, bgPaint);
            bgPaint.setShader(null);

            boolean glassBars = MirrlyConfig.isGlassBlurEnabled();
            int barColor = colors.tabBarTint;
            int barRimColor = 0;
            if (glassBars) {
                float barAlpha = Utilities.clamp(MirrlyConfig.getEffectiveGlassAlpha(0.80f), 0.92f, 0.35f);
                barColor = ColorUtils.setAlphaComponent(colors.tabBarTint, Math.round(barAlpha * 255f));
                boolean darkBar = AndroidUtilities.computePerceivedBrightness(colors.tabBarTint) < 0.5f;
                float rimScale = Utilities.clamp(MirrlyConfig.getGlassRimScale(), 1.5f, 0.4f);
                barRimColor = ColorUtils.setAlphaComponent(
                        darkBar ? Color.WHITE : Color.BLACK,
                        Math.min(255, Math.round((darkBar ? 0x2C : 0x18) * rimScale))
                );
                barRimPaint.setColor(barRimColor);
            }

            // Top mini app bar
            barPaint.setColor(barColor);
            float topBarBottom = cardRect.top + dp(12);
            canvas.drawRect(cardRect.left, cardRect.top, cardRect.right, topBarBottom, barPaint);
            if (glassBars && Color.alpha(barRimColor) > 0) {
                float rimY = topBarBottom - dpf2(0.25f);
                canvas.drawLine(cardRect.left, rimY, cardRect.right, rimY, barRimPaint);
            }

            // Incoming mini bubble
            float inW = Math.min(dp(38), cardRect.width() * 0.56f);
            float bH = dp(13);
            bubbleRect.set(cardRect.left + dp(6), cardRect.top + dp(19), cardRect.left + dp(6) + inW, cardRect.top + dp(19) + bH);
            bubblePaint.setShader(null);
            bubblePaint.setColor(colors.incomingBubble);
            canvas.drawRoundRect(bubbleRect, dp(5.5f), dp(5.5f), bubblePaint);

            // Outgoing mini bubble
            float outW = Math.min(dp(42), cardRect.width() * 0.62f);
            float outTop = bubbleRect.bottom + dp(5);
            bubbleRect.set(cardRect.right - dp(6) - outW, outTop, cardRect.right - dp(6), outTop + bH);
            if (colors.outgoingBubbleTop != colors.outgoingBubbleBottom) {
                bubblePaint.setShader(new LinearGradient(
                        bubbleRect.left, bubbleRect.top,
                        bubbleRect.left, bubbleRect.bottom,
                        colors.outgoingBubbleTop, colors.outgoingBubbleBottom,
                        Shader.TileMode.CLAMP
                ));
            } else {
                bubblePaint.setShader(null);
                bubblePaint.setColor(colors.outgoingBubbleBottom);
            }
            canvas.drawRoundRect(bubbleRect, dp(5.5f), dp(5.5f), bubblePaint);
            bubblePaint.setShader(null);

            // Bottom mini input bar
            barPaint.setColor(barColor);
            float bottomBarTop = cardRect.bottom - dp(10);
            canvas.drawRect(cardRect.left, bottomBarTop, cardRect.right, cardRect.bottom, barPaint);
            if (glassBars && Color.alpha(barRimColor) > 0) {
                float rimY = bottomBarTop + dpf2(0.25f);
                canvas.drawLine(cardRect.left, rimY, cardRect.right, rimY, barRimPaint);
            }
            canvas.restore();

            int unselectedStroke = ColorUtils.setAlphaComponent(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), 0x38);
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider);
            outlinePaint.setColor(ColorUtils.blendARGB(unselectedStroke, accent, t));
            outlinePaint.setStrokeWidth(AndroidUtilities.lerp(dp(1), dp(2.2f), t));
            canvas.drawRoundRect(cardRect, corner, corner, outlinePaint);

            // Animated checkmark badge in bottom-right corner
            if (t > 0.01f) {
                float cx = cardRect.right - dp(12);
                float cy = cardRect.bottom - dp(14);
                float cr = dp(8) * t;
                checkFillPaint.setColor(accent);
                canvas.drawCircle(cx, cy, cr, checkFillPaint);

                int checkFg = ColorUtils.calculateLuminance(accent) > 0.45 ? 0xFF101318 : Color.WHITE;
                checkTickPaint.setColor(ColorUtils.setAlphaComponent(checkFg, (int) (255 * t)));
                canvas.drawLine(cx - dp(3) * t, cy, cx - dp(0.6f) * t, cy + dp(2.4f) * t, checkTickPaint);
                canvas.drawLine(cx - dp(0.6f) * t, cy + dp(2.4f) * t, cx + dp(3.4f) * t, cy - dp(2.2f) * t, checkTickPaint);
            }

            int unselectedText = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            textPaint.setColor(ColorUtils.blendARGB(unselectedText, accent, t));
            textPaint.setTypeface(t > 0.5f ? AndroidUtilities.bold() : Typeface.DEFAULT);
            CharSequence label = TextUtils.ellipsize(title, textPaint, w - dp(4), TextUtils.TruncateAt.END);
            canvas.drawText(label, 0, label.length(), w / 2f, getHeight() - dp(5), textPaint);
        }
    }

    /**
     * 4-step Monet palette style selector wrapped in a stable UItemFactory so scrolling never resets it.
     */
    private static class PaletteStyleCell extends FrameLayout implements Theme.Colorable {

        private final SlideChooseView slideView;
        private WeakReference<MirrlyCustomizationActivity> hostRef;

        public PaletteStyleCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            slideView = new SlideChooseView(context, resourcesProvider);
            slideView.setCallback(new SlideChooseView.Callback() {
                @Override
                public void onOptionSelected(int index) {
                    MirrlyCustomizationActivity host = hostRef != null ? hostRef.get() : null;
                    if (host != null) {
                        host.onPaletteStyleChanged(index);
                    }
                }
            });
            addView(slideView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            syncFromConfig();
        }

        public void setHost(MirrlyCustomizationActivity host) {
            this.hostRef = new WeakReference<>(host);
        }

        public void syncFromConfig() {
            String[] labels = new String[]{
                    LocaleController.getString(R.string.MirrlyMonetStyleTonal),
                    LocaleController.getString(R.string.MirrlyMonetStyleVibrant),
                    LocaleController.getString(R.string.MirrlyMonetStylePastel),
                    LocaleController.getString(R.string.MirrlyMonetStyleMono)
            };
            slideView.setOptions(MirrlyConfig.getMonetStyle(), labels);
        }

        @Override
        public void updateColors() {
            if (slideView != null) {
                slideView.invalidate();
            }
        }

        public static class Factory extends UItem.UItemFactory<PaletteStyleCell> {
            static {
                setup(new Factory());
            }

            @Override
            public PaletteStyleCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new PaletteStyleCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                PaletteStyleCell cell = (PaletteStyleCell) view;
                if (item.object instanceof WeakReference) {
                    Object ref = ((WeakReference<?>) item.object).get();
                    if (ref instanceof MirrlyCustomizationActivity) {
                        cell.setHost((MirrlyCustomizationActivity) ref);
                    }
                }
                cell.syncFromConfig();
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return true;
            }

            public static UItem of(int id, MirrlyCustomizationActivity host) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = new WeakReference<>(host);
                return item;
            }
        }
    }

    /**
     * Compact row of circular Monet seed swatches (first circle = live wallpaper colors; others = overrides).
     */
    private static class SeedColorBarCell extends LinearLayout implements Theme.Colorable {

        private final SeedSwatchView[] swatchViews = new SeedSwatchView[MirrlyMonetThemeHelper.SEED_PRESETS.length];
        private final Theme.ResourcesProvider resourcesProvider;
        private WeakReference<MirrlyCustomizationActivity> hostRef;

        public SeedColorBarCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setPadding(dp(14), dp(4), dp(14), dp(10));

            for (int i = 0; i < MirrlyMonetThemeHelper.SEED_PRESETS.length; i++) {
                final int seed = MirrlyMonetThemeHelper.SEED_PRESETS[i];
                SeedSwatchView swatch = new SeedSwatchView(context, seed, i == 0, resourcesProvider);
                ScaleStateListAnimator.apply(swatch, 0.1f, 1.5f);
                MirrlyUiKit.attachDisallowInterceptTouch(swatch);
                swatch.setOnClickListener(v -> {
                    AndroidUtilities.vibrateCursor(v);
                    MirrlyCustomizationActivity host = hostRef != null ? hostRef.get() : null;
                    if (host != null) {
                        host.onSeedColorSelected(seed);
                    }
                });
                swatchViews[i] = swatch;
                LayoutParams lp = new LayoutParams(0, dp(42), 1f);
                addView(swatch, lp);
            }
            refreshSelection();
        }

        public void setHost(MirrlyCustomizationActivity host) {
            this.hostRef = new WeakReference<>(host);
        }

        public void refreshSelection() {
            int currentSeed = MirrlyConfig.getMonetCustomSeed();
            MirrlyMonetThemeHelper.Palette sysPalette = MirrlyMonetThemeHelper.extractPalette(
                    getContext(), MirrlyConfig.getMonetStyle(), 0);
            for (int i = 0; i < swatchViews.length; i++) {
                if (swatchViews[i] != null) {
                    if (i == 0) {
                        swatchViews[i].setSystemColors(sysPalette.accent1_500, sysPalette.accent2_200, sysPalette.accent3_500);
                    }
                    swatchViews[i].setSelected(MirrlyMonetThemeHelper.SEED_PRESETS[i] == currentSeed, true);
                }
            }
        }

        @Override
        public void updateColors() {
            if (swatchViews[0] == null) {
                return;
            }
            refreshSelection();
            for (SeedSwatchView swatch : swatchViews) {
                if (swatch != null) {
                    swatch.invalidate();
                }
            }
        }

        public static class Factory extends UItem.UItemFactory<SeedColorBarCell> {
            static {
                setup(new Factory());
            }

            @Override
            public SeedColorBarCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new SeedColorBarCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                SeedColorBarCell cell = (SeedColorBarCell) view;
                if (item.object instanceof WeakReference) {
                    Object ref = ((WeakReference<?>) item.object).get();
                    if (ref instanceof MirrlyCustomizationActivity) {
                        cell.setHost((MirrlyCustomizationActivity) ref);
                    }
                }
                cell.refreshSelection();
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return true;
            }

            public static UItem of(int id, MirrlyCustomizationActivity host) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = new WeakReference<>(host);
                return item;
            }
        }
    }

    private static class SeedSwatchView extends View {

        private final int seedColor;
        private final boolean isAutoWallpaper;
        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();
        private final AnimatedFloat selectAnim = new AnimatedFloat(this, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);

        private int sys1 = 0xFF4277E8;
        private int sys2 = 0xFFBDC7DC;
        private int sys3 = 0xFF7D5FA0;
        private boolean selected;

        public SeedSwatchView(Context context, int seedColor, boolean isAutoWallpaper, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.seedColor = seedColor;
            this.isAutoWallpaper = isAutoWallpaper;
            this.resourcesProvider = resourcesProvider;
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2));
        }

        public void setSystemColors(int c1, int c2, int c3) {
            this.sys1 = c1;
            this.sys2 = c2;
            this.sys3 = c3;
            invalidate();
        }

        public void setSelected(boolean selected, boolean animated) {
            this.selected = selected;
            selectAnim.set(selected ? 1f : 0f, !animated);
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float t = selectAnim.set(selected ? 1f : 0f);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float outerR = dp(15);
            float innerR = AndroidUtilities.lerp(outerR, dp(11), t);

            if (isAutoWallpaper) {
                arcRect.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR);
                fillPaint.setColor(sys1);
                canvas.drawArc(arcRect, -90f, 120f, true, fillPaint);
                fillPaint.setColor(sys2);
                canvas.drawArc(arcRect, 30f, 120f, true, fillPaint);
                fillPaint.setColor(sys3);
                canvas.drawArc(arcRect, 150f, 120f, true, fillPaint);
            } else {
                fillPaint.setColor(seedColor);
                canvas.drawCircle(cx, cy, innerR, fillPaint);
            }

            if (t > 0.01f) {
                int ringColor = isAutoWallpaper ? Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider) : seedColor;
                ringPaint.setColor(ColorUtils.setAlphaComponent(ringColor, (int) (255 * t)));
                canvas.drawCircle(cx, cy, outerR - dp(1), ringPaint);
            }
        }
    }

    /**
     * Native SeekBarView-based bubble corner radius slider reading SharedConfig directly
     * so scrolling up/down never reverts the thumb, with smooth animatedThumbX via separatorsCount.
     */
    private static class BubbleRadiusCell extends FrameLayout implements Theme.Colorable {

        private static final int MIN_RADIUS = 0;
        private static final int MAX_RADIUS = 17;

        private final Theme.ResourcesProvider resourcesProvider;
        private final SeekBarView sizeBar;
        private final AnimatedTextView valueText;
        private int lastReportedRadius = -1;
        private int themedOuterColor;
        private WeakReference<MirrlyCustomizationActivity> hostRef;

        public BubbleRadiusCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            valueText = new AnimatedTextView(context, false, true, true);
            valueText.setAnimationProperties(.3f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
            valueText.setTextSize(dp(15));
            valueText.setTypeface(AndroidUtilities.bold());
            valueText.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
            valueText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider));
            valueText.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(valueText, LayoutHelper.createFrame(54, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 18, 0, 18, 0));

            sizeBar = new SeekBarView(context, resourcesProvider) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN && getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return super.onTouchEvent(event);
                }
            };
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(MAX_RADIUS - MIN_RADIUS + 1);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    int val = Math.round(MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * Utilities.clamp01(progress));
                    if (val != lastReportedRadius) {
                        AndroidUtilities.vibrateCursor(sizeBar);
                        lastReportedRadius = val;
                        valueText.setText(val + " dp", true);
                    }
                    MirrlyCustomizationActivity host = hostRef != null ? hostRef.get() : null;
                    if (host != null) {
                        host.onBubbleRadiusChanged(val, stop);
                    }
                }

                @Override
                public CharSequence getContentDescription() {
                    return String.valueOf(SharedConfig.bubbleRadius);
                }

                @Override
                public int getStepsCount() {
                    return MAX_RADIUS - MIN_RADIUS;
                }
            });
            addView(sizeBar, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, 38,
                    Gravity.CENTER_VERTICAL,
                    LocaleController.isRTL ? 74 : 8, 0,
                    LocaleController.isRTL ? 8 : 74, 0
            ));
            syncFromConfig(false);
        }

        public void setHost(MirrlyCustomizationActivity host) {
            this.hostRef = new WeakReference<>(host);
        }

        public void syncFromConfig(boolean animated) {
            if (sizeBar == null || sizeBar.isDragging()) {
                return;
            }
            int current = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, SharedConfig.bubbleRadius));
            lastReportedRadius = current;
            float progress = (current - MIN_RADIUS) / (float) (MAX_RADIUS - MIN_RADIUS);
            sizeBar.setProgress(progress, animated);
            valueText.setText(current + " dp", animated);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(54), MeasureSpec.EXACTLY)
            );
            if (sizeBar != null && !sizeBar.isDragging()) {
                int current = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, SharedConfig.bubbleRadius));
                float progress = (current - MIN_RADIUS) / (float) (MAX_RADIUS - MIN_RADIUS);
                sizeBar.setProgress(progress, false);
            }
        }

        @Override
        public void updateColors() {
            try {
                if (sizeBar == null || valueText == null) {
                    return;
                }
                int outer = Theme.getColor(Theme.key_player_progress, resourcesProvider);
                if (outer != themedOuterColor) {
                    themedOuterColor = outer;
                    sizeBar.setOuterColor(outer);
                }
                valueText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider), false);
                sizeBar.invalidate();
                invalidate();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_player_progress);
            colors.add(Theme.key_windowBackgroundWhiteValueText);
        }

        public static class Factory extends UItem.UItemFactory<BubbleRadiusCell> {
            static {
                setup(new Factory());
            }

            @Override
            public BubbleRadiusCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new BubbleRadiusCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                BubbleRadiusCell cell = (BubbleRadiusCell) view;
                if (item.object instanceof WeakReference) {
                    Object ref = ((WeakReference<?>) item.object).get();
                    if (ref instanceof MirrlyCustomizationActivity) {
                        cell.setHost((MirrlyCustomizationActivity) ref);
                    }
                }
                cell.syncFromConfig(false);
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return true;
            }

            public static UItem of(int id, MirrlyCustomizationActivity host) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = new WeakReference<>(host);
                return item;
            }
        }
    }

    /**
     * Dual live Monet chat wallpaper preview (Light vs Dark/AMOLED) rendered via MotionBackgroundDrawable
     * + Telegram's vector doodle pattern + bubble corner & gradient reflection.
     */
    private static class MonetWallpaperPreviewCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final TextView captionView;
        private final WallpaperMiniCardView lightCard;
        private final WallpaperMiniCardView darkCard;
        private WeakReference<MirrlyCustomizationActivity> hostRef;

        public MonetWallpaperPreviewCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(VERTICAL);
            setPadding(dp(12), dp(8), dp(12), dp(10));

            captionView = new TextView(context);
            captionView.setText(LocaleController.getString(R.string.MirrlyMonetWallpaperPreview));
            captionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            captionView.setTypeface(AndroidUtilities.bold());
            captionView.setSingleLine(true);
            captionView.setEllipsize(TextUtils.TruncateAt.END);
            captionView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            captionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            LayoutParams capLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            capLp.leftMargin = dp(4);
            capLp.rightMargin = dp(4);
            capLp.bottomMargin = dp(8);
            addView(captionView, capLp);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            lightCard = new WallpaperMiniCardView(context, false, resourcesProvider);
            darkCard = new WallpaperMiniCardView(context, true, resourcesProvider);

            ScaleStateListAnimator.apply(lightCard, 0.03f, 1.3f);
            ScaleStateListAnimator.apply(darkCard, 0.03f, 1.3f);
            MirrlyUiKit.attachDisallowInterceptTouch(lightCard);
            MirrlyUiKit.attachDisallowInterceptTouch(darkCard);

            lightCard.setOnClickListener(v -> {
                AndroidUtilities.vibrateCursor(v);
                MirrlyCustomizationActivity host = hostRef != null ? hostRef.get() : null;
                if (host != null) {
                    host.onCardSelected(1, v);
                }
            });
            darkCard.setOnClickListener(v -> {
                AndroidUtilities.vibrateCursor(v);
                MirrlyCustomizationActivity host = hostRef != null ? hostRef.get() : null;
                if (host != null) {
                    host.onCardSelected(MirrlyConfig.isMonetAmoled() ? 3 : 2, v);
                }
            });

            LayoutParams leftLp = new LayoutParams(0, dp(138), 1f);
            LayoutParams rightLp = new LayoutParams(0, dp(138), 1f);
            if (LocaleController.isRTL) {
                rightLp.rightMargin = dp(10);
                row.addView(darkCard, rightLp);
                row.addView(lightCard, leftLp);
            } else {
                leftLp.rightMargin = dp(10);
                row.addView(lightCard, leftLp);
                row.addView(darkCard, rightLp);
            }
            addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 138));
            refreshFromConfig();
        }

        public void setHost(MirrlyCustomizationActivity host) {
            this.hostRef = new WeakReference<>(host);
        }

        public void refreshFromConfig() {
            Context ctx = getContext();
            int mode = MirrlyConfig.getMonetMode();
            boolean amoled = MirrlyConfig.isMonetAmoled();
            boolean outGradient = MirrlyConfig.isMonetOutGradient();
            boolean themedWallpaper = MirrlyConfig.isMonetThemedWallpaper();
            boolean glassTint = MirrlyConfig.isMonetGlassTint();
            int style = MirrlyConfig.getMonetStyle();
            int seed = MirrlyConfig.getMonetCustomSeed();

            boolean darkActive;
            boolean anyMonetActive = mode != MirrlyConfig.MONET_MODE_OFF;
            if (mode == MirrlyConfig.MONET_MODE_DARK) {
                darkActive = true;
            } else if (mode == MirrlyConfig.MONET_MODE_LIGHT) {
                darkActive = false;
            } else {
                darkActive = MirrlyMonetThemeHelper.resolveEffectiveDark(ctx, mode);
            }

            MirrlyMonetThemeHelper.PreviewColors lightPreview = MirrlyMonetThemeHelper.getPreviewColors(
                    ctx, style, seed, false, false, outGradient, themedWallpaper, glassTint);
            MirrlyMonetThemeHelper.PreviewColors darkPreview = MirrlyMonetThemeHelper.getPreviewColors(
                    ctx, style, seed, true, amoled, outGradient, themedWallpaper, glassTint);

            String lightLabel = LocaleController.getString(R.string.MirrlyMonetCardLight);
            String darkLabel = LocaleController.getString(
                    amoled ? R.string.MirrlyMonetCardAmoled : R.string.MirrlyMonetCardDark);

            lightCard.updateState(lightPreview, lightLabel, themedWallpaper, outGradient, anyMonetActive && !darkActive);
            darkCard.updateState(darkPreview, darkLabel, themedWallpaper, outGradient, anyMonetActive && darkActive);
        }

        @Override
        public void updateColors() {
            captionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            refreshFromConfig();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteGrayText);
            colors.add(Theme.key_windowBackgroundWhiteValueText);
        }

        public static class Factory extends UItem.UItemFactory<MonetWallpaperPreviewCell> {
            static {
                setup(new Factory());
            }

            @Override
            public MonetWallpaperPreviewCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new MonetWallpaperPreviewCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                MonetWallpaperPreviewCell cell = (MonetWallpaperPreviewCell) view;
                if (item.object instanceof WeakReference) {
                    Object ref = ((WeakReference<?>) item.object).get();
                    if (ref instanceof MirrlyCustomizationActivity) {
                        cell.setHost((MirrlyCustomizationActivity) ref);
                    }
                }
                cell.refreshFromConfig();
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return true;
            }

            public static UItem of(int id, MirrlyCustomizationActivity host) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = new WeakReference<>(host);
                return item;
            }
        }
    }

    /**
     * Single mini chat wallpaper card inside {@link MonetWallpaperPreviewCell}.
     */
    private static class WallpaperMiniCardView extends View {

        private static Bitmap cachedPreviewPattern;

        private final Theme.ResourcesProvider resourcesProvider;
        private final boolean darkCard;
        private final Paint flatBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF bubbleRect = new RectF();
        private final RectF pillRect = new RectF();

        private MotionBackgroundDrawable motionDrawable;
        private MirrlyMonetThemeHelper.PreviewColors colors;
        private String title = "";
        private boolean themedWallpaper;
        private boolean outGradient;
        private boolean selected;
        private final AnimatedFloat selectedProgress = new AnimatedFloat(this, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);

        private int gradW, gradH, gradTop, gradBottom;
        private LinearGradient outGradientShader;

        public WallpaperMiniCardView(@NonNull Context context, boolean darkCard, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.darkCard = darkCard;
            this.resourcesProvider = resourcesProvider;
            strokePaint.setStyle(Paint.Style.STROKE);
            labelPaint.setTextSize(dp(11.5f));
            labelPaint.setTypeface(AndroidUtilities.bold());
            ensurePatternLoadedAsync();
        }

        private void ensurePatternLoadedAsync() {
            if (cachedPreviewPattern != null && !cachedPreviewPattern.isRecycled()) {
                return;
            }
            Utilities.themeQueue.postRunnable(() -> {
                Bitmap bmp = Theme.getOrCreateMonetPatternBitmap(420, 560);
                if (bmp != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        cachedPreviewPattern = bmp;
                        if (motionDrawable != null && themedWallpaper && colors != null) {
                            motionDrawable.setPatternBitmap(colors.wallpaperPatternIntensity, bmp, true);
                            motionDrawable.setPatternColorFilter(motionDrawable.getPatternColor());
                            invalidate();
                        }
                    });
                }
            });
        }

        public void updateState(
                MirrlyMonetThemeHelper.PreviewColors nextColors,
                String nextTitle,
                boolean nextThemedWallpaper,
                boolean nextOutGradient,
                boolean isSelected
        ) {
            this.colors = nextColors;
            this.title = nextTitle != null ? nextTitle : "";
            this.themedWallpaper = nextThemedWallpaper;
            this.outGradient = nextOutGradient;
            this.selected = isSelected;
            this.selectedProgress.set(isSelected ? 1f : 0f, true);
            this.outGradientShader = null;

            if (nextThemedWallpaper && nextColors != null) {
                if (motionDrawable == null) {
                    motionDrawable = new MotionBackgroundDrawable(
                            nextColors.wallpaperTop,
                            nextColors.wallpaperBottom,
                            nextColors.wallpaper3,
                            nextColors.wallpaper4,
                            true
                    );
                    motionDrawable.setRoundRadius(dp(14));
                } else {
                    motionDrawable.setColors(
                            nextColors.wallpaperTop,
                            nextColors.wallpaperBottom,
                            nextColors.wallpaper3,
                            nextColors.wallpaper4
                    );
                }
                if (cachedPreviewPattern != null && !cachedPreviewPattern.isRecycled()) {
                    motionDrawable.setPatternBitmap(nextColors.wallpaperPatternIntensity, cachedPreviewPattern, true);
                    motionDrawable.setPatternColorFilter(motionDrawable.getPatternColor());
                } else {
                    ensurePatternLoadedAsync();
                }
                if (getWidth() > 0 && getHeight() > 0) {
                    motionDrawable.setBounds(0, 0, getWidth(), getHeight());
                }
            }
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (motionDrawable != null && w > 0 && h > 0) {
                motionDrawable.setBounds(0, 0, w, h);
            }
            outGradientShader = null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (colors == null) {
                return;
            }
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float radius = dp(14);
            cardRect.set(dp(1), dp(1), w - dp(1), h - dp(1));

            if (themedWallpaper && motionDrawable != null) {
                motionDrawable.setBounds(0, 0, (int) w, (int) h);
                motionDrawable.draw(canvas);
            } else {
                flatBgPaint.setColor(colors.wallpaperTop);
                canvas.drawRoundRect(cardRect, radius, radius, flatBgPaint);
            }

            // Scaled bubble corners matching SharedConfig.bubbleRadius live
            float bubbleCorner = Utilities.clamp(dp(Math.max(2f, SharedConfig.bubbleRadius * 0.65f)), dp(12), dp(2));

            // Incoming bubble
            float inTop = h - dp(66);
            float inRight = w * 0.66f;
            bubbleRect.set(dp(12), inTop, inRight, inTop + dp(24));
            bubblePaint.setShader(null);
            bubblePaint.setColor(colors.incomingBubble);
            canvas.drawRoundRect(bubbleRect, bubbleCorner, bubbleCorner, bubblePaint);
            linePaint.setColor(ColorUtils.setAlphaComponent(colors.textPrimary, darkCard ? 0x45 : 0x35));
            canvas.drawRoundRect(bubbleRect.left + dp(8), bubbleRect.top + dp(9), bubbleRect.right - dp(12), bubbleRect.top + dp(14), dp(2.5f), dp(2.5f), linePaint);

            // Outgoing bubble
            float outTop = h - dp(36);
            float outLeft = w * 0.30f;
            bubbleRect.set(outLeft, outTop, w - dp(12), outTop + dp(24));
            if (outGradient && colors.outgoingBubbleTop != colors.outgoingBubbleBottom) {
                if (outGradientShader == null || gradW != (int) w || gradH != (int) h
                        || gradTop != colors.outgoingBubbleTop || gradBottom != colors.outgoingBubbleBottom) {
                    gradW = (int) w;
                    gradH = (int) h;
                    gradTop = colors.outgoingBubbleTop;
                    gradBottom = colors.outgoingBubbleBottom;
                    outGradientShader = new LinearGradient(0, bubbleRect.top, 0, bubbleRect.bottom,
                            gradTop, gradBottom, Shader.TileMode.CLAMP);
                }
                bubblePaint.setShader(outGradientShader);
            } else {
                bubblePaint.setShader(null);
                bubblePaint.setColor(colors.outgoingBubbleBottom);
            }
            canvas.drawRoundRect(bubbleRect, bubbleCorner, bubbleCorner, bubblePaint);
            bubblePaint.setShader(null);
            linePaint.setColor(ColorUtils.setAlphaComponent(colors.textOnOutgoing, 0x55));
            canvas.drawRoundRect(bubbleRect.left + dp(10), bubbleRect.top + dp(9), bubbleRect.right - dp(10), bubbleRect.top + dp(14), dp(2.5f), dp(2.5f), linePaint);

            // Top label pill
            float maxTextW = Math.max(0f, w - dp(38));
            CharSequence pillLabel = TextUtils.ellipsize(title, labelPaint, maxTextW, TextUtils.TruncateAt.END);
            float pillTextW = labelPaint.measureText(pillLabel, 0, pillLabel.length());
            float pillW = Math.min(w - dp(20), pillTextW + dp(18));
            float pillLeft = LocaleController.isRTL ? (w - dp(10) - pillW) : dp(10);
            pillRect.set(pillLeft, dp(10), pillLeft + pillW, dp(31));
            int pillBg = darkCard ? 0x8814181F : 0xCCFFFFFF;
            int pillFg = darkCard ? 0xFFF2F5FA : 0xFF151922;
            pillPaint.setColor(pillBg);
            canvas.drawRoundRect(pillRect, dp(10.5f), dp(10.5f), pillPaint);
            labelPaint.setColor(pillFg);
            canvas.drawText(pillLabel, 0, pillLabel.length(), pillRect.left + (pillW - pillTextW) / 2f, pillRect.top + dp(14.5f), labelPaint);

            // Selection ring
            float t = selectedProgress.set(selected ? 1f : 0f);
            int unselectedStroke = ColorUtils.setAlphaComponent(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), 0x35);
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider);
            strokePaint.setColor(ColorUtils.blendARGB(unselectedStroke, accent, t));
            strokePaint.setStrokeWidth(AndroidUtilities.lerp(dp(1), dp(2.3f), t));
            canvas.drawRoundRect(cardRect, radius, radius, strokePaint);
        }
    }

    /**
     * Live interactive Glass preview powered by Telegram's real GlassCaptureSourceCanvas + BlurredBackgroundDrawable
     * + LiquidGlassEffect (API 33+) over a touch-draggable & theme-adaptive scene.
     */
    private static class GlassPreviewCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final FrameLayout previewContainer;
        private final GlassBackdropSceneView sceneView;
        private final FrameLayout pillView;
        private final ImageView pillSearchIcon;
        private final TextView pillTitleView;
        private final ImageView pillGlassIcon;
        private final ImageView chipView;
        private final GlassSourceColor staticColorSource = new GlassSourceColor();
        private final BlurredBackgroundDrawable pillDrawable;
        private final BlurredBackgroundDrawable chipDrawable;

        @Nullable
        private final Api31GlassPipeline glassPipeline;

        private boolean sceneDirty = true;
        private int lastGlassGeneration = -1;
        private final Paint cardStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public GlassPreviewCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setPadding(dp(12), dp(8), dp(12), dp(10));

            staticColorSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));

            BlurredBackgroundDrawableViewFactory factory;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                glassPipeline = new Api31GlassPipeline(staticColorSource);
                factory = glassPipeline.factory;
            } else {
                glassPipeline = null;
                factory = new BlurredBackgroundDrawableViewFactory(staticColorSource);
            }

            previewContainer = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int w = MeasureSpec.getSize(widthMeasureSpec);
                    int h = MeasureSpec.getSize(heightMeasureSpec);
                    setMeasuredDimension(w, h);
                    if (w > 0 && h > 0) {
                        sceneView.measure(
                                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
                        );
                        int gap = dp(10);
                        int chipSize = dp(48);
                        int pillW = Math.min(dp(248), Math.max(dp(156), w - chipSize - gap - dp(16)));
                        int pillH = dp(54);
                        pillView.measure(
                                MeasureSpec.makeMeasureSpec(pillW, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(pillH, MeasureSpec.EXACTLY)
                        );
                        chipView.measure(
                                MeasureSpec.makeMeasureSpec(chipSize, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(chipSize, MeasureSpec.EXACTLY)
                        );
                    }
                }

                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    int w = right - left;
                    int h = bottom - top;
                    if (w <= 0 || h <= 0) {
                        return;
                    }
                    sceneView.layout(0, 0, w, h);
                    positionOverlayViews(w, h);
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    if (glassPipeline != null && getWidth() > 0 && getHeight() > 0) {
                        GlassEngine.getCurrentGlassSettings();
                        int curGen = GlassEngine.getGlassSettingsGenerationId();
                        if (sceneDirty || lastGlassGeneration != curGen) {
                            lastGlassGeneration = curGen;
                            glassPipeline.updateCaptureAndDrawables(this, sceneView, pillDrawable, chipDrawable);
                            sceneDirty = false;
                        }
                    }
                    super.dispatchDraw(canvas);
                    cardStrokePaint.setStyle(Paint.Style.STROKE);
                    cardStrokePaint.setStrokeWidth(dp(1));
                    cardStrokePaint.setColor(ColorUtils.setAlphaComponent(
                            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), 0x32));
                    float inset = dp(0.5f);
                    canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                            dp(16), dp(16), cardStrokePaint);
                }
            };
            previewContainer.setWillNotDraw(false);
            previewContainer.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
                }
            });
            previewContainer.setClipToOutline(true);

            sceneView = new GlassBackdropSceneView(context, resourcesProvider, () -> {
                sceneDirty = true;
                previewContainer.invalidate();
            });
            previewContainer.addView(sceneView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            pillView = new FrameLayout(context);
            pillDrawable = factory.create(pillView, BlurredBackgroundProviderImpl.topPanel(resourcesProvider));
            pillDrawable.setRadius(dp(26), dp(26), dp(26), dp(26), false);
            pillView.setBackground(pillDrawable);
            pillView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    pillDrawable.getOutline(outline);
                }
            });
            pillView.setClipToOutline(true);

            LinearLayout pillContent = new LinearLayout(context);
            pillContent.setOrientation(LinearLayout.HORIZONTAL);
            pillContent.setGravity(Gravity.CENTER);
            pillContent.setPadding(dp(12), 0, dp(12), 0);

            int iconTint = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            pillSearchIcon = new ImageView(context);
            pillSearchIcon.setImageResource(R.drawable.msg_search);
            pillSearchIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            pillSearchIcon.setColorFilter(new PorterDuffColorFilter(iconTint, PorterDuff.Mode.MULTIPLY));

            pillTitleView = new TextView(context);
            pillTitleView.setText(LocaleController.getString(R.string.MirrlyGlassPreviewTitle));
            pillTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            pillTitleView.setTypeface(AndroidUtilities.bold());
            pillTitleView.setSingleLine(true);
            pillTitleView.setGravity(Gravity.CENTER);
            pillTitleView.setEllipsize(TextUtils.TruncateAt.END);
            pillTitleView.setTextColor(iconTint);
            pillTitleView.setPadding(dp(6), 0, dp(6), 0);

            pillGlassIcon = new ImageView(context);
            pillGlassIcon.setImageResource(R.drawable.msg_blur_radial);
            pillGlassIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            pillGlassIcon.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider), PorterDuff.Mode.MULTIPLY));

            pillContent.addView(pillSearchIcon, LayoutHelper.createLinear(18, 18));
            pillContent.addView(pillTitleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            pillContent.addView(pillGlassIcon, LayoutHelper.createLinear(18, 18));
            pillView.addView(pillContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            chipView = new ImageView(context);
            chipDrawable = factory.create(chipView, BlurredBackgroundProviderImpl.topPanel(resourcesProvider));
            chipDrawable.setRadius(dp(24), dp(24), dp(24), dp(24), false);
            chipView.setBackground(chipDrawable);
            chipView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    chipDrawable.getOutline(outline);
                }
            });
            chipView.setClipToOutline(true);
            chipView.setImageResource(R.drawable.msg_palette);
            chipView.setScaleType(ImageView.ScaleType.CENTER);
            chipView.setColorFilter(new PorterDuffColorFilter(iconTint, PorterDuff.Mode.MULTIPLY));

            previewContainer.addView(pillView, LayoutHelper.createFrame(214, 54, Gravity.CENTER));
            previewContainer.addView(chipView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

            addView(previewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 168));
        }

        private void positionOverlayViews(int w, int h) {
            if (w <= 0 || h <= 0) {
                return;
            }
            int gap = dp(10);
            int pillW = pillView.getMeasuredWidth() > 0
                    ? pillView.getMeasuredWidth()
                    : Math.min(dp(248), Math.max(dp(156), w - dp(48) - gap - dp(16)));
            int pillH = pillView.getMeasuredHeight() > 0 ? pillView.getMeasuredHeight() : dp(54);
            int chipSize = chipView.getMeasuredWidth() > 0 ? chipView.getMeasuredWidth() : dp(48);
            int totalW = pillW + gap + chipSize;
            int startX = (w - totalW) / 2;

            int pillLeft = LocaleController.isRTL ? (startX + chipSize + gap) : startX;
            int pillTop = (h - pillH) / 2 - dp(6);
            int chipLeft = LocaleController.isRTL ? startX : (startX + pillW + gap);
            int chipTop = (h - chipSize) / 2 - dp(6);

            pillView.layout(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH);
            chipView.layout(chipLeft, chipTop, chipLeft + chipSize, chipTop + chipSize);

            pillDrawable.setBounds(0, 0, pillW, pillH);
            pillDrawable.setSourceOffset(pillLeft, pillTop);
            chipDrawable.setBounds(0, 0, chipSize, chipSize);
            chipDrawable.setSourceOffset(chipLeft, chipTop);

            if (glassPipeline != null) {
                glassPipeline.updatePositions(w, h, pillLeft, pillTop, pillLeft + pillW, pillTop + pillH,
                        chipLeft, chipTop, chipLeft + chipSize, chipTop + chipSize);
                sceneDirty = true;
            }
        }

        public void onGlassOrThemeChanged() {
            staticColorSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            sceneView.refreshThemeColors();
            pillDrawable.updateColors();
            chipDrawable.updateColors();
            int iconTint = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            pillSearchIcon.setColorFilter(new PorterDuffColorFilter(iconTint, PorterDuff.Mode.MULTIPLY));
            pillTitleView.setTextColor(iconTint);
            pillGlassIcon.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            chipView.setColorFilter(new PorterDuffColorFilter(iconTint, PorterDuff.Mode.MULTIPLY));
            sceneDirty = true;
            pillView.invalidate();
            chipView.invalidate();
            previewContainer.invalidate();
        }

        @Override
        public void updateColors() {
            onGlassOrThemeChanged();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhite);
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteValueText);
            colors.add(Theme.key_windowBackgroundWhiteGrayText);
        }

        @RequiresApi(api = Build.VERSION_CODES.S)
        private static final class Api31GlassPipeline {
            final GlassCaptureSourceCanvas captureCanvas = new GlassCaptureSourceCanvas();
            final GlassSourceRenderNode glassSource;
            final GlassSourceRenderNode frostedSource;
            final BlurredBackgroundDrawableViewFactory factory;
            final GlassPositionsArray positions = new GlassPositionsArray(2);

            Api31GlassPipeline(GlassSourceColor fallbackColor) {
                glassSource = new GlassSourceRenderNode(fallbackColor);
                glassSource.setUnderSource(fallbackColor);
                glassSource.setGlassCaptureSourceCanvas(captureCanvas, GlassCaptureSourceCanvas.SourceIndex.GLASS);

                frostedSource = new GlassSourceRenderNode(fallbackColor);
                frostedSource.setUnderSource(fallbackColor);
                frostedSource.setGlassCaptureSourceCanvas(captureCanvas, GlassCaptureSourceCanvas.SourceIndex.FROSTED_GLASS);

                factory = new BlurredBackgroundDrawableViewFactory(glassSource, frostedSource, fallbackColor);
                factory.setLiquidGlassEffectAllowed(true);
            }

            void updatePositions(int w, int h, int l1, int t1, int r1, int b1, int l2, int t2, int r2, int b2) {
                int pad = dp(28);
                positions.clear();
                positions.add(Math.max(0, l1 - pad), Math.max(0, t1 - pad), Math.min(w, r1 + pad), Math.min(h, b1 + pad));
                positions.add(Math.max(0, l2 - pad), Math.max(0, t2 - pad), Math.min(w, r2 + pad), Math.min(h, b2 + pad));
                captureCanvas.setPositions(positions);
            }

            void updateCaptureAndDrawables(
                    View root,
                    View sceneView,
                    BlurredBackgroundDrawable pillDrawable,
                    BlurredBackgroundDrawable chipDrawable
            ) {
                captureCanvas.setRootView(root);
                captureCanvas.setAllowDownsample(false);
                captureCanvas.setOffset(0, 0);
                captureCanvas.setCapture((canvas, pos) -> sceneView.draw(canvas));
                if (GlassEngine.getCurrentGlassSettings().isBlurEnabled) {
                    captureCanvas.invalidate();
                }
                org.telegram.utils.glass.GlassSettings currentSettings = GlassEngine.getCurrentGlassSettings();
                pillDrawable.setGlassSettings(currentSettings);
                chipDrawable.setGlassSettings(currentSettings);
                pillDrawable.invalidateDisplayList();
                pillDrawable.updateDisplayList();
                chipDrawable.invalidateDisplayList();
                chipDrawable.updateDisplayList();
            }
        }

        public static class Factory extends UItem.UItemFactory<GlassPreviewCell> {
            static {
                setup(new Factory());
            }

            @Override
            public GlassPreviewCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new GlassPreviewCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((GlassPreviewCell) view).onGlassOrThemeChanged();
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return true;
            }

            public static UItem of(int id, MirrlyCustomizationActivity host) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = new WeakReference<>(host);
                return item;
            }
        }
    }

    /**
     * Draggable colorful backdrop rendered beneath {@link GlassPreviewCell} so blur radius, opacity,
     * color saturation, optical refraction, lens thickness, and rim highlights are clearly testable.
     */
    private static class GlassBackdropSceneView extends View {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Runnable onSceneDirty;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hintBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hintRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint hintTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmpRect = new RectF();

        private LinearGradient bgGradient;
        private int gradW, gradH, colorA, colorB, accent1, accent2, accent3, surfaceBg;
        private float panX, panY;
        private float downX, downY, startPanX, startPanY;
        private boolean hasInteracted;
        private final AnimatedFloat hintAlpha = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);

        public GlassBackdropSceneView(
                @NonNull Context context,
                Theme.ResourcesProvider resourcesProvider,
                @NonNull Runnable onSceneDirty
        ) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            this.onSceneDirty = onSceneDirty;
            hintRimPaint.setStyle(Paint.Style.STROKE);
            hintRimPaint.setStrokeWidth(dpf2(0.5f));
            hintTextPaint.setTextSize(dp(11.5f));
            hintTextPaint.setTypeface(AndroidUtilities.bold());
            hintTextPaint.setTextAlign(Paint.Align.CENTER);
            refreshThemeColors();
        }

        public void refreshThemeColors() {
            boolean night = Theme.isCurrentThemeNight();
            MirrlyMonetThemeHelper.PreviewColors monet = MirrlyMonetThemeHelper.getPreviewColors(
                    getContext(), night, MirrlyConfig.isMonetAmoled(), true, true);
            colorA = monet.wallpaperTop;
            colorB = monet.wallpaperBottom;
            accent1 = monet.accentPrimary;
            accent2 = monet.accentSecondary;
            accent3 = monet.accentTertiary;
            surfaceBg = monet.surfaceBackground;
            bgGradient = null;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            bgGradient = null;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float limitX = Math.max(dp(72), getWidth() * 0.35f);
            float limitY = Math.max(dp(40), getHeight() * 0.25f);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    startPanX = panX;
                    startPanY = panY;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (!hasInteracted) {
                        hasInteracted = true;
                        hintAlpha.set(0f, false);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    panX = Utilities.clamp(startPanX + dx, limitX, -limitX);
                    panY = Utilities.clamp(startPanY + dy, limitY, -limitY);
                    invalidate();
                    onSceneDirty.run();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            if (bgGradient == null || gradW != (int) w || gradH != (int) h) {
                gradW = (int) w;
                gradH = (int) h;
                bgGradient = new LinearGradient(0, 0, w, h, colorA, colorB, Shader.TileMode.CLAMP);
            }
            bgPaint.setShader(bgGradient);
            canvas.drawRect(0, 0, w, h, bgPaint);

            canvas.save();
            canvas.translate(panX, panY);

            // Vivid orbs for blur + saturation visualization
            shapePaint.setColor(ColorUtils.setAlphaComponent(accent1, 0xCC));
            canvas.drawCircle(w * 0.24f, h * 0.42f, dp(34), shapePaint);

            shapePaint.setColor(ColorUtils.setAlphaComponent(accent3, 0xCC));
            canvas.drawCircle(w * 0.62f, h * 0.34f, dp(28), shapePaint);

            shapePaint.setColor(0xD9F06292);
            canvas.drawCircle(w * 0.80f, h * 0.56f, dp(24), shapePaint);

            shapePaint.setColor(0xD92CC98F);
            canvas.drawCircle(w * 0.42f, h * 0.64f, dp(22), shapePaint);

            // Crisp diagonal contrast stripes passing under glass perimeter to expose lens refraction
            stripePaint.setStrokeWidth(dp(4));
            stripePaint.setColor(ColorUtils.setAlphaComponent(accent2, 0xA8));
            for (int i = -3; i <= 5; i++) {
                float sx = i * dp(44);
                canvas.drawLine(sx, -h * 0.4f, sx + h * 0.8f, h * 1.4f, stripePaint);
            }

            // Sample miniature bubbles directly crossing the glass pill
            float bubbleR = dp(10);
            shapePaint.setColor(ColorUtils.blendARGB(surfaceBg, Color.WHITE, Theme.isCurrentThemeNight() ? 0.14f : 0.75f));
            tmpRect.set(dp(18), dp(22), w * 0.54f, dp(52));
            canvas.drawRoundRect(tmpRect, bubbleR, bubbleR, shapePaint);

            shapePaint.setColor(accent1);
            tmpRect.set(w * 0.46f, h - dp(68), w - dp(18), h - dp(36));
            canvas.drawRoundRect(tmpRect, bubbleR, bubbleR, shapePaint);

            canvas.restore();

            float ha = hintAlpha.set(hasInteracted ? 0f : 1f);
            if (ha > 0.02f) {
                String hint = LocaleController.getString(R.string.MirrlyGlassPreviewHint);
                float tw = hintTextPaint.measureText(hint) + dp(20);
                float left = (w - tw) / 2f;
                float top = h - dp(26);
                hintBgPaint.setColor(ColorUtils.setAlphaComponent(0xFF0E1117, (int) (0x88 * ha)));
                tmpRect.set(left, top, left + tw, top + dp(19));
                canvas.drawRoundRect(tmpRect, dp(9.5f), dp(9.5f), hintBgPaint);
                float rimInset = dpf2(0.25f);
                tmpRect.inset(rimInset, rimInset);
                hintRimPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (0x2E * ha)));
                canvas.drawRoundRect(tmpRect, dp(9.5f) - rimInset, dp(9.5f) - rimInset, hintRimPaint);
                hintTextPaint.setColor(ColorUtils.setAlphaComponent(0xFFF5F7FB, (int) (255 * ha)));
                canvas.drawText(hint, w / 2f, top + dp(13.5f), hintTextPaint);
            }
        }
    }

    /**
     * Two-row native Telegram slider cell supporting global Glass effects with smooth animated value readout
     * and zero clipping for RU / EN / FA RTL labels.
     */
    private static class GlassSliderCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final TextView titleView;
        private final AnimatedTextView valueText;
        private final SeekBarView sizeBar;
        private WeakReference<MirrlyCustomizationActivity> hostRef;

        private int boundSliderId = -1;
        private int minValue = 0;
        private int maxValue = 200;
        private int stepValue = 5;
        private boolean isDpUnit = false;
        private boolean sliderEnabled = true;
        private int lastReportedValue = Integer.MIN_VALUE;
        private int themedOuterColor;

        public GlassSliderCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addView(titleView, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                    LocaleController.isRTL ? 82 : 18, 10,
                    LocaleController.isRTL ? 18 : 82, 0
            ));

            valueText = new AnimatedTextView(context, false, true, true);
            valueText.setAnimationProperties(.3f, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
            valueText.setTextSize(dp(14));
            valueText.setTypeface(AndroidUtilities.bold());
            valueText.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
            valueText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider));
            valueText.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(valueText, LayoutHelper.createFrame(
                    58, 22,
                    (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP,
                    18, 10, 18, 0
            ));

            sizeBar = new SeekBarView(context, resourcesProvider) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (!sliderEnabled) {
                        return false;
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN && getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return super.onTouchEvent(event);
                }
            };
            sizeBar.setReportChanges(true);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    if (!sliderEnabled) {
                        return;
                    }
                    int raw = minValue + Math.round(((maxValue - minValue) * Utilities.clamp01(progress)) / (float) stepValue) * stepValue;
                    raw = Math.max(minValue, Math.min(maxValue, raw));
                    if (raw != lastReportedValue) {
                        AndroidUtilities.vibrateCursor(sizeBar);
                        lastReportedValue = raw;
                        valueText.setText(formatValue(raw), true);
                    }
                    MirrlyCustomizationActivity host = hostRef != null ? hostRef.get() : null;
                    if (host != null) {
                        host.onGlassSliderChanged(boundSliderId, raw, stop);
                    }
                }

                @Override
                public CharSequence getContentDescription() {
                    return titleView.getText();
                }

                @Override
                public int getStepsCount() {
                    return Math.max(1, (maxValue - minValue) / stepValue);
                }
            });
            addView(sizeBar, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, 34,
                    Gravity.BOTTOM,
                    8, 0, 8, 2
            ));
        }

        public void setHost(MirrlyCustomizationActivity host) {
            this.hostRef = new WeakReference<>(host);
        }

        public void bindSlider(int sliderId, MirrlyCustomizationActivity host) {
            setHost(host);
            if (this.boundSliderId == sliderId) {
                updateEnabledState(false);
                syncFromConfig(false);
                return;
            }
            this.boundSliderId = sliderId;
            switch (sliderId) {
                case ID_GLASS_SLIDER_BLUR:
                    titleView.setText(LocaleController.getString(R.string.MirrlyGlassParamBlur));
                    minValue = 25;
                    maxValue = 200;
                    stepValue = 5;
                    isDpUnit = false;
                    break;
                case ID_GLASS_SLIDER_OPACITY:
                    titleView.setText(LocaleController.getString(R.string.MirrlyGlassParamOpacity));
                    minValue = 30;
                    maxValue = 140;
                    stepValue = 5;
                    isDpUnit = false;
                    break;
                case ID_GLASS_SLIDER_SATURATION:
                    titleView.setText(LocaleController.getString(R.string.MirrlyGlassParamSaturation));
                    minValue = 50;
                    maxValue = 150;
                    stepValue = 5;
                    isDpUnit = false;
                    break;
                case ID_GLASS_SLIDER_REFRACT:
                    titleView.setText(LocaleController.getString(R.string.MirrlyGlassParamRefraction));
                    minValue = 0;
                    maxValue = 200;
                    stepValue = 5;
                    isDpUnit = false;
                    break;
                case ID_GLASS_SLIDER_THICKNESS:
                    titleView.setText(LocaleController.getString(R.string.MirrlyGlassParamThickness));
                    minValue = 4;
                    maxValue = 20;
                    stepValue = 1;
                    isDpUnit = true;
                    sizeBar.setSeparatorsCount(maxValue - minValue + 1);
                    break;
                case ID_GLASS_SLIDER_RIM:
                    titleView.setText(LocaleController.getString(R.string.MirrlyGlassParamRim));
                    minValue = 0;
                    maxValue = 200;
                    stepValue = 5;
                    isDpUnit = false;
                    break;
            }
            if (!isDpUnit) {
                sizeBar.setSeparatorsCount(0);
            }
            updateEnabledState(false);
            syncFromConfig(false);
        }

        private int readCurrentValueFromConfig() {
            switch (boundSliderId) {
                case ID_GLASS_SLIDER_BLUR:
                    return Math.round(MirrlyConfig.getGlassBlurScale() * 100f);
                case ID_GLASS_SLIDER_OPACITY:
                    return Math.round(MirrlyConfig.getGlassOpacityScale() * 100f);
                case ID_GLASS_SLIDER_SATURATION:
                    return Math.round(MirrlyConfig.getGlassSaturationScale() * 100f);
                case ID_GLASS_SLIDER_REFRACT:
                    return Math.round(MirrlyConfig.getGlassRefractScale() * 100f);
                case ID_GLASS_SLIDER_THICKNESS:
                    return MirrlyConfig.getGlassThicknessDp();
                case ID_GLASS_SLIDER_RIM:
                    return Math.round(MirrlyConfig.getGlassRimScale() * 100f);
                default:
                    return 100;
            }
        }

        private boolean computeEnabled() {
            if (boundSliderId == ID_GLASS_SLIDER_RIM) {
                return true;
            }
            if (!MirrlyConfig.isGlassBlurEnabled()) {
                return false;
            }
            if (boundSliderId == ID_GLASS_SLIDER_REFRACT || boundSliderId == ID_GLASS_SLIDER_THICKNESS) {
                return MirrlyConfig.isGlassLiquidEnabled();
            }
            return true;
        }

        public void updateEnabledState(boolean animated) {
            boolean nextEnabled = computeEnabled();
            sliderEnabled = nextEnabled;
            float targetAlpha = nextEnabled ? 1.0f : 0.42f;
            if (animated) {
                animate().alpha(targetAlpha).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            } else {
                animate().cancel();
                setAlpha(targetAlpha);
            }
        }

        public void syncFromConfig(boolean animated) {
            if (sizeBar == null || sizeBar.isDragging() || boundSliderId < 0) {
                return;
            }
            int current = Math.max(minValue, Math.min(maxValue, readCurrentValueFromConfig()));
            lastReportedValue = current;
            float progress = (current - minValue) / (float) Math.max(1, maxValue - minValue);
            sizeBar.setProgress(progress, animated);
            valueText.setText(formatValue(current), animated);
        }

        private String formatValue(int val) {
            return isDpUnit ? (val + " dp") : (val + "%");
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(64), MeasureSpec.EXACTLY)
            );
            if (sizeBar != null && !sizeBar.isDragging() && boundSliderId >= 0) {
                int current = Math.max(minValue, Math.min(maxValue, readCurrentValueFromConfig()));
                float progress = (current - minValue) / (float) Math.max(1, maxValue - minValue);
                sizeBar.setProgress(progress, false);
            }
        }

        @Override
        public void updateColors() {
            int outer = Theme.getColor(Theme.key_player_progress, resourcesProvider);
            if (outer != themedOuterColor) {
                themedOuterColor = outer;
                sizeBar.setOuterColor(outer);
            }
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            valueText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider), false);
            sizeBar.invalidate();
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_player_progress);
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteValueText);
        }

        public static class Factory extends UItem.UItemFactory<GlassSliderCell> {
            static {
                setup(new Factory());
            }

            @Override
            public GlassSliderCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new GlassSliderCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                GlassSliderCell cell = (GlassSliderCell) view;
                MirrlyCustomizationActivity host = null;
                if (item.object instanceof WeakReference) {
                    Object ref = ((WeakReference<?>) item.object).get();
                    if (ref instanceof MirrlyCustomizationActivity) {
                        host = (MirrlyCustomizationActivity) ref;
                    }
                }
                cell.bindSlider(item.id, host);
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id;
            }

            public static UItem of(int id, MirrlyCustomizationActivity host) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = new WeakReference<>(host);
                return item;
            }
        }
    }

    /**
     * Wraps Telegram's native {@link AppIconsSelectorCell} in a stable UItemFactory.
     */
    public static class AppIconsCellFactory extends UItem.UItemFactory<AppIconsSelectorCell> {
        static {
            setup(new AppIconsCellFactory());
        }

        @Override
        public AppIconsSelectorCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            BaseFragment fragment = null;
            if (listView instanceof MirrlyUiKit.HeroCardView.HostProvider) {
                fragment = ((MirrlyUiKit.HeroCardView.HostProvider) listView).getMirrlyHeroHost();
            }
            AppIconsSelectorCell cell = new AppIconsSelectorCell(context, fragment, currentAccount);
            cell.setLayoutParams(new RecyclerListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return true;
        }

        public static UItem of(int id, MirrlyCustomizationActivity host) {
            UItem item = UItem.ofFactory(AppIconsCellFactory.class);
            item.id = id;
            item.object = new WeakReference<>(host);
            return item;
        }
    }
}
