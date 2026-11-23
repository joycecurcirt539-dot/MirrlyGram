package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyInterfaceConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;
import org.telegram.utils.glass.GlassEngine;
import org.telegram.utils.glass.GlassSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import me.vkryl.core.reference.ReferenceList;

/**
 * Modular MirrlyGram interface customization screen featuring 4 liquid-glass tabs
 * (Home, Bottom Bar, More Menu, Message Menu) with interactive live previews,
 * custom action routing, chat picking, and reorderable menu controls.
 */
public class MirrlyInterfaceSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final int TAB_HOME = 0;
    public static final int TAB_BOTTOM_BAR = 1;
    public static final int TAB_MORE_MENU = 2;
    public static final int TAB_MESSAGE_MENU = 3;
    private static final int TAB_COUNT = 4;

    private static final int ID_HOME_PREVIEW = 100;
    private static final int ID_HOME_HIDE_STORIES = 101;
    private static final int ID_HOME_HIDE_ARCHIVE = 102;
    private static final int ID_HOME_TOP_THEME = 103;
    private static final int ID_HOME_SETTINGS_CIRCLE_MODE = 104;
    private static final int ID_HOME_RESET = 105;
    private static final int ID_HOME_HIDE_FAB_STORIES = 106;
    private static final int ID_HOME_HIDE_FAB_NEW_CHAT = 107;
    private static final int ID_HOME_TOP_PROXY = 108;
    private static final int ID_HOME_TOP_DONATE = 109;
    private static final int ID_HOME_TOP_MORE = 110;

    private static final int ID_BAR_PREVIEW = 200;
    private static final int ID_BAR_DYNAMIC_STATUS = 201;
    private static final int ID_BAR_COLLAPSE_MODE = 202;
    private static final int ID_BAR_ALIGN_MODE = 203;
    private static final int ID_BAR_SLOT_BASE = 210;
    private static final int ID_BAR_RESET = 220;

    private static final int ID_DRAWER_PREVIEW = 300;
    private static final int ID_DRAWER_ITEM_BASE = 310;
    private static final int ID_DRAWER_RESET = 390;

    private static final int ID_MSG_PREVIEW = 400;
    private static final int ID_MSG_ITEM_BASE = 410;
    private static final int ID_MSG_RESET = 490;

    private static final int ID_SHEET_MODE_DEFAULT = 1001;
    private static final int ID_SHEET_MODE_HIDDEN = 1002;
    private static final int ID_SHEET_MODE_CUSTOM = 1003;
    private static final int ID_SHEET_PICK_CHAT = 1010;
    private static final int ID_SHEET_ACTION_BASE = 1100;

    private final int initialTab;
    private FrameLayout rootLayout;
    private ViewPagerFixed pager;
    private IconTabSwitcher iconTabsView;
    private FrameLayout tabsContainer;
    private View listTopOverlay;
    private final ReferenceList<TabListView> listViews = new ReferenceList<>();

    private int insetTop;
    private int insetBottom;
    private boolean hasResumedOnce = false;
    private int shakeShiftDp = 6;
    private int lastSubtitleTab = -1;

    public MirrlyInterfaceSettingsActivity() {
        this(TAB_HOME);
    }

    public MirrlyInterfaceSettingsActivity(int initialTab) {
        super();
        if (initialTab >= TAB_HOME && initialTab < TAB_COUNT) {
            this.initialTab = initialTab;
        } else {
            this.initialTab = TAB_HOME;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        MirrlyConfig.load();
        MirrlyInterfaceConfig.load();
        NotificationCenter global = NotificationCenter.getGlobalInstance();
        global.addObserver(this, NotificationCenter.mirrlyInterfaceSettingsChanged);
        global.addObserver(this, NotificationCenter.mirrlyBottomBarConfigChanged);
        global.addObserver(this, NotificationCenter.mirrlyMessageMenuConfigChanged);
        global.addObserver(this, NotificationCenter.didSetNewTheme);
        global.addObserver(this, NotificationCenter.mirrlyGlassSettingsDidChange);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter global = NotificationCenter.getGlobalInstance();
        global.removeObserver(this, NotificationCenter.mirrlyInterfaceSettingsChanged);
        global.removeObserver(this, NotificationCenter.mirrlyBottomBarConfigChanged);
        global.removeObserver(this, NotificationCenter.mirrlyMessageMenuConfigChanged);
        global.removeObserver(this, NotificationCenter.didSetNewTheme);
        global.removeObserver(this, NotificationCenter.mirrlyGlassSettingsDidChange);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!hasResumedOnce) {
            hasResumedOnce = true;
            return;
        }
        for (TabListView lv : listViews) {
            if (lv.adapter != null) {
                lv.adapter.update(true);
            }
        }
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    protected boolean useFadeDrawableForActionBar() {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean hasOwnBackground() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.MirrlyInterfaceTitle));
        lastSubtitleTab = initialTab;
        simpleActionBar.setSubtitle(getTabTitle(initialTab));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        rootLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
                );
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                if (!isPaused && ((pager != null && (pager.checkTabsAnimationInProgress() || pager.isManualScrolling() || isPagerTranslating()))
                        || hasAnimatingPreviewUnderTabs())) {
                    glassEngine.invalidate();
                }
            }
        };
        ThemeBind.backgroundColor(rootLayout, Theme.key_windowBackgroundGray, getResourceProvider());

        pager = new ViewPagerFixed(context) {
            @Override
            protected float getAvailableTranslationX() {
                int w = getMeasuredWidth();
                return w > 0 ? w : super.getAvailableTranslationX();
            }

            @Override
            protected void onTabPageSelected(int position, boolean forward) {
                syncActiveTabUi(position, true);
            }

            @Override
            protected void onItemSelected(View currentPage, View oldPage, int position, int oldPosition) {
                syncActiveTabUi(position, true);
            }

            @Override
            protected void onTabScrollEnd(int position) {
                syncActiveTabUi(position, true);
                if (iconTabsView != null) {
                    iconTabsView.onPagerPositionChanged(position, position);
                }
            }

            @Override
            public void onTabAnimationUpdate(boolean manual) {
                if (iconTabsView != null) {
                    iconTabsView.onPagerPositionChanged(getPositionAnimated(), getCurrentPosition());
                }
                if (!isPaused && GlassEngine.getCurrentGlassSettings().isBlurEnabled) {
                    glassEngine.invalidate();
                }
            }
        };
        pager.setAdapter(new PageAdapter());

        tabsContainer = new FrameLayout(context);
        iconTabsView = new IconTabSwitcher(
                context,
                resourceProvider,
                new int[]{
                        R.drawable.mirrly_tab_home,
                        R.drawable.mirrly_tab_bottom_bar,
                        R.drawable.mirrly_tab_more_menu,
                        R.drawable.mirrly_tab_msg_menu
                },
                new CharSequence[]{
                        getTabTitle(TAB_HOME),
                        getTabTitle(TAB_BOTTOM_BAR),
                        getTabTitle(TAB_MORE_MENU),
                        getTabTitle(TAB_MESSAGE_MENU)
                },
                index -> {
                    if (pager != null && pager.getCurrentPosition() != index) {
                        pager.scrollToPosition(index);
                    }
                }
        );
        if (initialTab != TAB_HOME) {
            pager.setPosition(initialTab);
            iconTabsView.setPositionImmediate(initialTab);
        } else {
            iconTabsView.setPositionImmediate(TAB_HOME);
        }
        rootLayout.addView(pager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        listTopOverlay = new View(context);
        rootLayout.addView(listTopOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP));
        ViewGroup.LayoutParams overlayLp = listTopOverlay.getLayoutParams();
        overlayLp.height = insetTop + ActionBar.getCurrentActionBarHeight() + dp(44);

        navigationBarFadeView = new View(context);
        navigationBarFadeView.setVisibility(insetBottom >= dp(40) ? View.VISIBLE : View.GONE);
        rootLayout.addView(navigationBarFadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, Math.max(0, insetBottom), Gravity.BOTTOM));

        tabsContainer.setTranslationY(BaseFragmentSimpleGlass.getPillGlassTop(insetTop) - dp(9.66f));
        tabsContainer.addView(iconTabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36 + 6 + 6));
        tabsContainer.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabsContainer.setBackground(getBaseSimpleGlass().factory
                .create(tabsContainer)
                .setColorProvider(BlurredBackgroundProviderImpl.simpleActionBar(resourceProvider))
                .setPadding(dp(9.66f))
                .setRadius(dp(18))
        );

        getBaseSimpleGlass().setViews(rootLayout, pager, simpleActionBar, resourceProvider);
        getBaseSimpleGlass().setCapture((canvas, position) -> {
            for (int a = 0, n = pager.getChildCount(); a < n; a++) {
                final View child = pager.getChildAt(a);
                if (child instanceof RecyclerListView) {
                    final RecyclerListView rv = (RecyclerListView) child;
                    Blur3Utils.captureRelativeParent(rv, canvas, position, rv, rootLayout);
                }
            }
        });
        getBaseSimpleGlass().setClipView(null);
        rootLayout.addView(tabsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, -4, 0, -4, 0));

        Drawable topFadeDrawable = getBaseSimpleGlass().createActionBarFadeDrawable(listTopOverlay);
        if (topFadeDrawable instanceof org.telegram.utils.glass.fade.GlassFadeBlurredDrawable) {
            ((org.telegram.utils.glass.fade.GlassFadeBlurredDrawable) topFadeDrawable).setOverlayAlpha(105);
        }
        listTopOverlay.setBackground(topFadeDrawable);
        navigationBarFadeView.setBackground(getBaseSimpleGlass().createNavigationBarFadeDrawable(navigationBarFadeView));
        simpleActionBar.setBackground(null);

        return fragmentView = rootLayout;
    }

    private boolean isPagerTranslating() {
        if (pager == null) {
            return false;
        }
        View[] pages = pager.getViewPages();
        if (pages == null) {
            return false;
        }
        if (pages.length > 1 && pages[1] != null && pages[1].getVisibility() == View.VISIBLE) {
            return true;
        }
        return pages.length > 0 && pages[0] != null && pages[0].getTranslationX() != 0f;
    }

    private boolean hasAnimatingPreviewUnderTabs() {
        if (pager == null || !GlassEngine.getCurrentGlassSettings().isBlurEnabled) {
            return false;
        }
        for (int a = 0, n = pager.getChildCount(); a < n; a++) {
            View page = pager.getChildAt(a);
            if (!(page instanceof TabListView) || page.getVisibility() != View.VISIBLE) {
                continue;
            }
            TabListView lv = (TabListView) page;
            for (int i = 0, count = lv.getChildCount(); i < count; i++) {
                View child = lv.getChildAt(i);
                if (child instanceof MirrlyInterfacePreviews.HomeHeaderPreviewCell
                        && ((MirrlyInterfacePreviews.HomeHeaderPreviewCell) child).isAnimating()) {
                    return true;
                }
                if (child instanceof MirrlyInterfacePreviews.BottomBarPreviewCell
                        && ((MirrlyInterfacePreviews.BottomBarPreviewCell) child).isAnimating()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (event != null && event.getY() <= ActionBar.getCurrentActionBarHeight() + dp(48)) {
            return true;
        }
        return pager == null || pager.getCurrentPosition() == 0;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        this.insetTop = top;
        this.insetBottom = bottom;

        if (tabsContainer != null) {
            tabsContainer.setTranslationY(BaseFragmentSimpleGlass.getPillGlassTop(insetTop) - dp(9.66f));
        }
        if (listTopOverlay != null) {
            ViewGroup.LayoutParams lp = listTopOverlay.getLayoutParams();
            lp.height = insetTop + ActionBar.getCurrentActionBarHeight() + dp(44);
            listTopOverlay.requestLayout();
        }
        if (navigationBarFadeView != null) {
            ViewGroup.LayoutParams navLp = navigationBarFadeView.getLayoutParams();
            int nextVisibility = bottom >= dp(40) ? View.VISIBLE : View.GONE;
            if (navigationBarFadeView.getVisibility() != nextVisibility) {
                navigationBarFadeView.setVisibility(nextVisibility);
            }
            if (navLp != null && navLp.height != bottom) {
                navLp.height = bottom;
                navigationBarFadeView.requestLayout();
            }
        }
        for (TabListView lv : listViews) {
            lv.checkInsets();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme || id == NotificationCenter.mirrlyGlassSettingsDidChange) {
            if (rootLayout != null) {
                ThemeBind.backgroundColor(rootLayout, Theme.key_windowBackgroundGray, getResourceProvider());
                rootLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider()));
            }
            if (simpleActionBar != null) {
                simpleActionBar.updateButtonsGlassColors();
                simpleActionBar.updateColors();
                simpleActionBar.invalidate();
            }
            if (tabsContainer != null) {
                tabsContainer.invalidate();
            }
            if (iconTabsView != null) {
                iconTabsView.updateColors();
            }
            for (TabListView lv : listViews) {
                if (lv.adapter != null) {
                    lv.adapter.updateWithoutNotify();
                }
                for (int i = 0; i < lv.getChildCount(); i++) {
                    View child = lv.getChildAt(i);
                    if (child instanceof Theme.Colorable) {
                        ((Theme.Colorable) child).updateColors();
                    }
                }
                lv.invalidate();
            }
        } else if (id == NotificationCenter.mirrlyInterfaceSettingsChanged
                || id == NotificationCenter.mirrlyBottomBarConfigChanged
                || id == NotificationCenter.mirrlyMessageMenuConfigChanged) {
            for (TabListView lv : listViews) {
                if (lv.adapter != null) {
                    lv.adapter.update(true);
                }
            }
        }
    }

    private void syncActiveTabUi(int position, boolean animated) {
        int clamped = Math.max(TAB_HOME, Math.min(TAB_COUNT - 1, position));
        if (lastSubtitleTab != clamped) {
            lastSubtitleTab = clamped;
            if (simpleActionBar != null) {
                CharSequence subtitle = getTabTitle(clamped);
                if (animated) {
                    simpleActionBar.setSubtitleAnimated(subtitle);
                } else {
                    simpleActionBar.setSubtitle(subtitle);
                }
            }
        }
        if (iconTabsView != null) {
            iconTabsView.setTargetPosition(clamped);
        }
    }

    private String getTabTitle(int position) {
        switch (position) {
            case TAB_BOTTOM_BAR:
                return LocaleController.getString(R.string.MirrlyInterfaceTabBottomBar);
            case TAB_MORE_MENU:
                return LocaleController.getString(R.string.MirrlyInterfaceTabMoreMenu);
            case TAB_MESSAGE_MENU:
                return LocaleController.getString(R.string.MirrlyInterfaceTabMsgMenu);
            case TAB_HOME:
            default:
                return LocaleController.getString(R.string.MirrlyInterfaceTabHome);
        }
    }

    // -------------------------------------------------------------------------
    // ViewPager & TabListView
    // -------------------------------------------------------------------------

    private class PageAdapter extends ViewPagerFixed.Adapter {
        @Override
        public int getItemCount() {
            return TAB_COUNT;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @Override
        public View createView(int viewType) {
            TabListView listView = new TabListView(getContext(), viewType);
            listViews.add(listView);
            return listView;
        }

        @Override
        public void bindView(View view, int position, int viewType) {
            ((TabListView) view).checkInsets();
        }

        @Override
        public String getItemTitle(int position) {
            return getTabTitle(position);
        }
    }

    private class TabListView extends UniversalRecyclerView {

        private final RecyclerItemsEnterAnimator enterAnimator;
        private boolean enterAnimated = false;

        TabListView(Context context, int tabType) {
            super(
                    context,
                    currentAccount,
                    classGuid,
                    false,
                    (items, adapter) -> fillItemsForTab(tabType, items),
                    null,
                    null,
                    resourceProvider
            );
            setCaptureSectionsDecoratorAllowed(true);
            adapter.setApplyBackground(false);
            setSections();
            setSectionsDrawBackground(true);
            setClipToPadding(false);
            if (getItemAnimator() instanceof DefaultItemAnimator) {
                DefaultItemAnimator anim = (DefaultItemAnimator) getItemAnimator();
                anim.setDurations(350);
                anim.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                anim.setDelayAnimations(false);
                anim.setSupportsChangeAnimations(false);
            }
            glassEngine.addScrolledView(this);

            enterAnimator = new RecyclerItemsEnterAnimator(this, true);

            setOnItemClickListener((view, position, x, y) -> {
                UItem item = adapter.getItem(position);
                if (item != null) {
                    onTabItemClick(this, item, view);
                }
            });
            checkInsets();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!enterAnimated && SharedConfig.animationsEnabled()) {
                enterAnimated = true;
                enterAnimator.showItemsAnimated(0);
            }
        }

        void checkInsets() {
            BaseFragmentSimpleGlass.setListViewParams(
                    this,
                    insetTop,
                    insetBottom,
                    ActionBar.getCurrentActionBarHeight() + dp(42),
                    0
            );
        }
    }

    // -------------------------------------------------------------------------
    // Tab Data Population
    // -------------------------------------------------------------------------

    private void fillItemsForTab(int tabType, ArrayList<UItem> items) {
        MirrlyInterfaceConfig.load();
        switch (tabType) {
            case TAB_BOTTOM_BAR:
                fillBottomBarTab(items);
                break;
            case TAB_MORE_MENU:
                fillMoreMenuTab(items);
                break;
            case TAB_MESSAGE_MENU:
                fillMessageMenuTab(items);
                break;
            case TAB_HOME:
            default:
                fillHomeTab(items);
                break;
        }
    }

    private void fillHomeTab(ArrayList<UItem> items) {
        boolean showCallsTab = UserConfig.getInstance(currentAccount).showCallsTab;
        boolean hideStories = MirrlyInterfaceConfig.isHideTopStories();
        boolean hideArchive = MirrlyInterfaceConfig.isHideArchiveFromChats();
        boolean showThemeBtn = MirrlyInterfaceConfig.isShowTopThemeToggle();
        boolean showProxyBtn = MirrlyInterfaceConfig.isShowTopProxyBtn();
        boolean showDonateBtn = MirrlyInterfaceConfig.isShowTopDonateBtn();
        boolean showMoreBtn = MirrlyInterfaceConfig.isShowTopMoreBtn();
        boolean showSettingsCircle = MirrlyInterfaceConfig.shouldShowTopSettingsCircle(showCallsTab);
        boolean hideFabStories = MirrlyInterfaceConfig.isHideFabStories();
        boolean hideFabNewChat = MirrlyInterfaceConfig.isHideFabNewChat();

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfacePreviewHeader)));
        items.add(MirrlyInterfacePreviews.HomeHeaderPreviewCell.Factory.of(
                ID_HOME_PREVIEW,
                new MirrlyInterfacePreviews.HomeSpec(
                        hideStories,
                        hideArchive,
                        showThemeBtn,
                        showProxyBtn,
                        showDonateBtn,
                        showMoreBtn,
                        showSettingsCircle,
                        hideFabStories,
                        hideFabNewChat
                ),
                () -> BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.MirrlyInterfacePreviewThemePulse))
                        .show()
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfaceHomeHeader)));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_HOME_HIDE_STORIES,
                R.drawable.msg_stories_myhide,
                LocaleController.getString(R.string.MirrlyInterfaceHideStories),
                hideStories
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_HOME_HIDE_ARCHIVE,
                R.drawable.msg_archive_hide,
                LocaleController.getString(R.string.MirrlyInterfaceHideArchive),
                hideArchive
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_HOME_TOP_THEME,
                R.drawable.msg_theme,
                LocaleController.getString(R.string.MirrlyInterfaceTopThemeBtn),
                showThemeBtn
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_HOME_TOP_PROXY,
                R.drawable.mirrly_about_proxy,
                LocaleController.getString(R.string.MirrlyInterfaceTopProxyBtn),
                showProxyBtn
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_HOME_TOP_DONATE,
                R.drawable.mirrly_about_support,
                LocaleController.getString(R.string.MirrlyInterfaceTopDonateBtn),
                showDonateBtn
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_HOME_TOP_MORE,
                R.drawable.ic_ab_other,
                LocaleController.getString(R.string.MirrlyInterfaceTopMoreBtn),
                showMoreBtn
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_HOME_HIDE_FAB_STORIES,
                R.drawable.outline_fab_story_24,
                LocaleController.getString(R.string.MirrlyInterfaceHideFabStories),
                hideFabStories
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_HOME_HIDE_FAB_NEW_CHAT,
                R.drawable.msg_message,
                LocaleController.getString(R.string.MirrlyInterfaceHideFabNewChat),
                hideFabNewChat
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyInterfaceHomeHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfaceTopSettingsTitle)));
        items.add(TripleSegmentCardCell.Factory.of(
                ID_HOME_SETTINGS_CIRCLE_MODE,
                new TripleSegmentSpec(
                        LocaleController.getString(R.string.MirrlyInterfaceModeAuto),
                        LocaleController.getString(R.string.MirrlyInterfaceModeAlways),
                        LocaleController.getString(R.string.MirrlyInterfaceModeOff),
                        MirrlyInterfaceConfig.getTopSettingsCircleMode(),
                        MirrlyInterfaceConfig::setTopSettingsCircleMode
                )
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyInterfaceTopSettingsDesc)));

        items.add(UItem.asButton(
                ID_HOME_RESET,
                R.drawable.msg_reset,
                LocaleController.getString(R.string.MirrlyInterfaceHomeReset)
        ).accent());
        items.add(UItem.asShadow(null));
    }

    private void fillBottomBarTab(ArrayList<UItem> items) {
        MirrlyInterfacePreviews.BottomBarSpec spec = MirrlyInterfacePreviews.BottomBarSpec.capture(currentAccount);

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfacePreviewHeader)));
        items.add(MirrlyInterfacePreviews.BottomBarPreviewCell.Factory.of(ID_BAR_PREVIEW, spec));
        items.add(UItem.asShadow(ID_BAR_DYNAMIC_STATUS, buildBottomBarStatusSummary(spec)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfaceBarCollapseHeader)));
        items.add(TripleSegmentCardCell.Factory.of(
                ID_BAR_COLLAPSE_MODE,
                new TripleSegmentSpec(
                        LocaleController.getString(R.string.MirrlyInterfaceCollapseOff),
                        LocaleController.getString(R.string.MirrlyInterfaceCollapseAuto),
                        LocaleController.getString(R.string.MirrlyInterfaceCollapseAlways),
                        spec.collapseMode,
                        MirrlyInterfaceConfig::setBottomBarCollapseMode
                )
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfaceBarAlignHeader)));
        items.add(TripleSegmentCardCell.Factory.of(
                ID_BAR_ALIGN_MODE,
                new TripleSegmentSpec(
                        LocaleController.getString(R.string.MirrlyInterfaceBarAlignLeft),
                        LocaleController.getString(R.string.MirrlyInterfaceBarAlignCenter),
                        LocaleController.getString(R.string.MirrlyInterfaceBarAlignRight),
                        spec.alignment,
                        true,
                        MirrlyInterfaceConfig::setBottomBarAlignment
                )
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyInterfaceBarAlignHint)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfaceBarSlotsHeader)));
        for (int slot = 0; slot < MirrlyInterfaceConfig.SLOT_COUNT; slot++) {
            int mode = spec.slotModes[slot];
            String slotTitle = getSlotTitle(slot);
            int iconRes;
            int topColor;
            int bottomColor;
            String subtitle;
            String badgeValue;

            if (mode == MirrlyInterfaceConfig.SLOT_MODE_HIDDEN) {
                iconRes = resolveDefaultSlotIcon(slot, spec.showCallsTab);
                topColor = IconBackgroundColors.GRAY.top;
                bottomColor = IconBackgroundColors.GRAY.bottom;
                subtitle = LocaleController.getString(R.string.MirrlyInterfaceSlotModeHiddenFull);
                badgeValue = LocaleController.getString(R.string.MirrlyInterfaceSlotModeHidden);
            } else if (mode == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM) {
                String action = spec.slotActions[slot];
                iconRes = MirrlyInterfacePreviews.resolveQuickActionIcon(action);
                topColor = IconBackgroundColors.PURPLE.top;
                bottomColor = IconBackgroundColors.PURPLE.bottom;
                if (MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT.equals(action)) {
                    String chatTitle = MirrlyInterfacePreviews.resolveQuickActionShort(
                            currentAccount, action, spec.slotChatIds[slot], spec.slotChatTitles[slot]);
                    subtitle = LocaleController.formatString(R.string.MirrlyInterfaceQuickCustomChatCurrent, chatTitle);
                } else {
                    subtitle = MirrlyInterfacePreviews.resolveQuickActionName(action);
                }
                badgeValue = LocaleController.getString(R.string.MirrlyInterfaceSlotModeCustom);
            } else {
                iconRes = resolveDefaultSlotIcon(slot, spec.showCallsTab);
                IconBackgroundColors bg = resolveSlotBadgeColor(slot);
                topColor = bg.top;
                bottomColor = bg.bottom;
                subtitle = LocaleController.getString(R.string.MirrlyInterfaceSlotModeDefaultFull);
                badgeValue = LocaleController.getString(R.string.MirrlyInterfaceSlotModeDefault);
            }

            items.add(SettingsActivity.SettingCell.Factory.of(
                    ID_BAR_SLOT_BASE + slot,
                    topColor,
                    bottomColor,
                    iconRes,
                    slotTitle,
                    subtitle,
                    badgeValue
            ));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asButton(
                ID_BAR_RESET,
                R.drawable.msg_reset,
                LocaleController.getString(R.string.MirrlyInterfaceBarReset)
        ).accent());
        items.add(UItem.asShadow(null));
    }

    private void fillMoreMenuTab(ArrayList<UItem> items) {
        MirrlyInterfacePreviews.DrawerSpec spec = MirrlyInterfacePreviews.DrawerSpec.capture();
        ArrayList<String> order = MirrlyInterfaceConfig.getDrawerMenuOrder();

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfacePreviewHeader)));
        items.add(MirrlyInterfacePreviews.DrawerMenuPreviewCell.Factory.of(ID_DRAWER_PREVIEW, spec));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfaceDrawerHeader)));
        for (int i = 0; i < order.size(); i++) {
            String key = order.get(i);
            boolean visible = MirrlyInterfaceConfig.isDrawerItemVisible(key);
            boolean autoEnabled = MirrlyInterfaceConfig.isDrawerItemAutoEnabled(key);
            int stableId = ID_DRAWER_ITEM_BASE + getStableIndex(MirrlyInterfaceConfig.DEFAULT_DRAWER_ORDER, key, i);
            items.add(ReorderableMenuItemCell.Factory.of(
                    stableId,
                    new ReorderableItemSpec(
                            key,
                            false,
                            MirrlyInterfacePreviews.resolveDrawerItemIcon(key),
                            MirrlyInterfacePreviews.resolveDrawerItemTitle(key),
                            visible,
                            autoEnabled,
                            false,
                            i > 0,
                            i < order.size() - 1
                    ),
                    this::handleReorderAction,
                    this::handleToggleAction
            ));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyInterfaceDrawerHint)));

        items.add(UItem.asButton(
                ID_DRAWER_RESET,
                R.drawable.msg_reset,
                LocaleController.getString(R.string.MirrlyInterfaceDrawerReset)
        ).accent());
        items.add(UItem.asShadow(null));
    }

    private void fillMessageMenuTab(ArrayList<UItem> items) {
        MirrlyInterfacePreviews.MsgMenuSpec spec = MirrlyInterfacePreviews.MsgMenuSpec.capture();
        ArrayList<String> order = MirrlyInterfaceConfig.getMessageMenuOrder();

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfacePreviewHeader)));
        items.add(MirrlyInterfacePreviews.MessageMenuPreviewCell.Factory.of(ID_MSG_PREVIEW, spec));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfaceMsgMenuHeader)));
        for (int i = 0; i < order.size(); i++) {
            String key = order.get(i);
            boolean visible = MirrlyInterfaceConfig.isMessageMenuItemVisible(key);
            boolean destructive = MirrlyInterfacePreviews.isMsgMenuItemDestructive(key);
            int stableId = ID_MSG_ITEM_BASE + getStableIndex(MirrlyInterfaceConfig.DEFAULT_MSG_MENU_ORDER, key, i);
            items.add(ReorderableMenuItemCell.Factory.of(
                    stableId,
                    new ReorderableItemSpec(
                            key,
                            true,
                            MirrlyInterfacePreviews.resolveMsgMenuItemIcon(key),
                            MirrlyInterfacePreviews.resolveMsgMenuItemTitle(key),
                            visible,
                            false,
                            destructive,
                            i > 0,
                            i < order.size() - 1
                    ),
                    this::handleReorderAction,
                    this::handleToggleAction
            ));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyInterfaceMsgMenuHint)));

        items.add(UItem.asButton(
                ID_MSG_RESET,
                R.drawable.msg_reset,
                LocaleController.getString(R.string.MirrlyInterfaceMsgMenuReset)
        ).accent());
        items.add(UItem.asShadow(null));
    }

    // -------------------------------------------------------------------------
    // Click & Action Handlers
    // -------------------------------------------------------------------------

    private void onTabItemClick(TabListView lv, UItem item, View view) {
        switch (item.id) {
            case ID_HOME_HIDE_STORIES: {
                boolean next = !MirrlyInterfaceConfig.isHideTopStories();
                MirrlyInterfaceConfig.setHideTopStories(next);
                if (view instanceof MirrlyUiKit.IconSwitchCell) {
                    ((MirrlyUiKit.IconSwitchCell) view).setChecked(next);
                }
                AndroidUtilities.vibrateCursor(view);
                break;
            }
            case ID_HOME_HIDE_ARCHIVE: {
                boolean next = !MirrlyInterfaceConfig.isHideArchiveFromChats();
                MirrlyInterfaceConfig.setHideArchiveFromChats(next);
                if (view instanceof MirrlyUiKit.IconSwitchCell) {
                    ((MirrlyUiKit.IconSwitchCell) view).setChecked(next);
                }
                AndroidUtilities.vibrateCursor(view);
                break;
            }
            case ID_HOME_TOP_THEME: {
                boolean next = !MirrlyInterfaceConfig.isShowTopThemeToggle();
                MirrlyInterfaceConfig.setShowTopThemeToggle(next);
                if (view instanceof MirrlyUiKit.IconSwitchCell) {
                    ((MirrlyUiKit.IconSwitchCell) view).setChecked(next);
                }
                AndroidUtilities.vibrateCursor(view);
                break;
            }
            case ID_HOME_TOP_PROXY: {
                boolean next = !MirrlyInterfaceConfig.isShowTopProxyBtn();
                MirrlyInterfaceConfig.setShowTopProxyBtn(next);
                if (view instanceof MirrlyUiKit.IconSwitchCell) {
                    ((MirrlyUiKit.IconSwitchCell) view).setChecked(next);
                }
                AndroidUtilities.vibrateCursor(view);
                break;
            }
            case ID_HOME_TOP_DONATE: {
                boolean next = !MirrlyInterfaceConfig.isShowTopDonateBtn();
                MirrlyInterfaceConfig.setShowTopDonateBtn(next);
                if (view instanceof MirrlyUiKit.IconSwitchCell) {
                    ((MirrlyUiKit.IconSwitchCell) view).setChecked(next);
                }
                AndroidUtilities.vibrateCursor(view);
                break;
            }
            case ID_HOME_TOP_MORE: {
                boolean next = !MirrlyInterfaceConfig.isShowTopMoreBtn();
                MirrlyInterfaceConfig.setShowTopMoreBtn(next);
                if (view instanceof MirrlyUiKit.IconSwitchCell) {
                    ((MirrlyUiKit.IconSwitchCell) view).setChecked(next);
                }
                AndroidUtilities.vibrateCursor(view);
                break;
            }
            case ID_HOME_HIDE_FAB_STORIES: {
                boolean next = !MirrlyInterfaceConfig.isHideFabStories();
                MirrlyInterfaceConfig.setHideFabStories(next);
                if (view instanceof MirrlyUiKit.IconSwitchCell) {
                    ((MirrlyUiKit.IconSwitchCell) view).setChecked(next);
                }
                AndroidUtilities.vibrateCursor(view);
                break;
            }
            case ID_HOME_HIDE_FAB_NEW_CHAT: {
                boolean next = !MirrlyInterfaceConfig.isHideFabNewChat();
                MirrlyInterfaceConfig.setHideFabNewChat(next);
                if (view instanceof MirrlyUiKit.IconSwitchCell) {
                    ((MirrlyUiKit.IconSwitchCell) view).setChecked(next);
                }
                AndroidUtilities.vibrateCursor(view);
                break;
            }
            case ID_HOME_RESET:
                MirrlyInterfaceConfig.resetHomeSettingsDefaults();
                AndroidUtilities.vibrateCursor(view);
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.MirrlyInterfaceHomeResetDone))
                        .show();
                break;

            case ID_BAR_RESET:
                MirrlyInterfaceConfig.resetBottomBarDefaults();
                AndroidUtilities.vibrateCursor(view);
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.MirrlyInterfaceBarResetDone))
                        .show();
                break;

            case ID_DRAWER_RESET:
                MirrlyInterfaceConfig.resetDrawerMenuDefaults();
                AndroidUtilities.vibrateCursor(view);
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.MirrlyInterfaceDrawerResetDone))
                        .show();
                break;

            case ID_MSG_RESET:
                MirrlyInterfaceConfig.resetMessageMenuDefaults();
                AndroidUtilities.vibrateCursor(view);
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.done, LocaleController.getString(R.string.MirrlyInterfaceMsgMenuResetDone))
                        .show();
                break;

            default:
                if (item.id >= ID_BAR_SLOT_BASE && item.id < ID_BAR_SLOT_BASE + MirrlyInterfaceConfig.SLOT_COUNT) {
                    int slot = item.id - ID_BAR_SLOT_BASE;
                    AndroidUtilities.vibrateCursor(view);
                    showSlotEditorSheet(slot);
                } else if (view instanceof ReorderableMenuItemCell) {
                    ((ReorderableMenuItemCell) view).performRowToggle();
                }
                break;
        }
    }

    private void handleReorderAction(ReorderableItemSpec spec, int delta, View anchor) {
        if (anchor != null) {
            AndroidUtilities.vibrateCursor(anchor);
        }
        if (spec.isMessageMenu) {
            MirrlyInterfaceConfig.moveMessageMenuItem(spec.key, delta);
        } else {
            MirrlyInterfaceConfig.moveDrawerItem(spec.key, delta);
        }
    }

    private void handleToggleAction(ReorderableItemSpec spec, boolean next, ReorderableMenuItemCell cell) {
        boolean applied;
        if (spec.isMessageMenu) {
            applied = MirrlyInterfaceConfig.setMessageMenuItemVisible(spec.key, next);
            if (!applied) {
                shakeShiftDp = -shakeShiftDp;
                AndroidUtilities.shakeViewSpring(cell, shakeShiftDp);
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                cell.setChecked(true, true);
                BulletinFactory.of(this)
                        .createErrorBulletin(LocaleController.getString(R.string.MirrlyInterfaceMsgMenuEmptyGuard))
                        .show();
                return;
            }
            AndroidUtilities.vibrateCursor(cell);
        } else {
            applied = MirrlyInterfaceConfig.setDrawerItemVisible(spec.key, next);
            if (!applied) {
                shakeShiftDp = -shakeShiftDp;
                AndroidUtilities.shakeViewSpring(cell, shakeShiftDp);
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                cell.setChecked(true, true);
                BulletinFactory.of(this)
                        .createErrorBulletin(LocaleController.getString(R.string.MirrlyInterfaceDrawerEmptyGuard))
                        .show();
                return;
            }
            AndroidUtilities.vibrateCursor(cell);
            if (!next && MirrlyInterfaceConfig.DRAWER_ARCHIVE.equals(spec.key)
                    && MirrlyInterfaceConfig.isHideArchiveFromChats()) {
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.MirrlyInterfaceDrawerArchiveWarn))
                        .show();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Bottom Bar Slot Editor BottomSheet & Specific Chat Picker
    // -------------------------------------------------------------------------

    private void showSlotEditorSheet(int slot) {
        if (getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();
        String slotName = getSlotTitle(slot);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.formatString(R.string.MirrlyInterfaceSlotSheetTitle, slotName));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        titleView.setPadding(dp(22), dp(18), dp(22), dp(8));
        container.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final BottomSheet[] sheetHolder = new BottomSheet[1];
        final UniversalRecyclerView[] sheetRvHolder = new UniversalRecyclerView[1];

        UniversalRecyclerView sheetRv = new UniversalRecyclerView(
                context,
                currentAccount,
                classGuid,
                true,
                (items, adapter) -> fillSlotEditorItems(slot, items),
                (item, view, position, x, y) -> {
                    if (item == null) {
                        return;
                    }
                    if (item.id == ID_SHEET_MODE_DEFAULT) {
                        AndroidUtilities.vibrateCursor(view);
                        MirrlyInterfaceConfig.setSlotMode(slot, MirrlyInterfaceConfig.SLOT_MODE_DEFAULT);
                        if (sheetHolder[0] != null) {
                            sheetHolder[0].dismiss();
                        }
                    } else if (item.id == ID_SHEET_MODE_HIDDEN) {
                        AndroidUtilities.vibrateCursor(view);
                        MirrlyInterfaceConfig.setSlotMode(slot, MirrlyInterfaceConfig.SLOT_MODE_HIDDEN);
                        if (sheetHolder[0] != null) {
                            sheetHolder[0].dismiss();
                        }
                    } else if (item.id == ID_SHEET_MODE_CUSTOM) {
                        AndroidUtilities.vibrateCursor(view);
                        MirrlyInterfaceConfig.setSlotMode(slot, MirrlyInterfaceConfig.SLOT_MODE_CUSTOM);
                        if (sheetRvHolder[0] != null && sheetRvHolder[0].adapter != null) {
                            sheetRvHolder[0].adapter.update(true);
                        }
                    } else if (item.id == ID_SHEET_PICK_CHAT) {
                        AndroidUtilities.vibrateCursor(view);
                        if (sheetHolder[0] != null) {
                            sheetHolder[0].dismiss();
                        }
                        launchCustomChatPicker(slot);
                    } else if (item.id >= ID_SHEET_ACTION_BASE && item.object instanceof String) {
                        AndroidUtilities.vibrateCursor(view);
                        String actionKey = (String) item.object;
                        MirrlyInterfaceConfig.setSlotCustomAction(slot, actionKey);
                        if (sheetHolder[0] != null) {
                            sheetHolder[0].dismiss();
                        }
                    }
                },
                null,
                resourceProvider
        );
        sheetRvHolder[0] = sheetRv;
        sheetRv.setCaptureSectionsDecoratorAllowed(true);
        sheetRv.adapter.setApplyBackground(false);
        sheetRv.setSections();
        sheetRv.setSectionsDrawBackground(true);
        if (sheetRv.getItemAnimator() instanceof DefaultItemAnimator) {
            DefaultItemAnimator anim = (DefaultItemAnimator) sheetRv.getItemAnimator();
            anim.setDurations(280);
            anim.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            anim.setDelayAnimations(false);
            anim.setSupportsChangeAnimations(false);
        }

        int sheetListH = Math.min(dp(500), Math.round(AndroidUtilities.displaySize.y * 0.68f));
        container.addView(sheetRv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, sheetListH));

        BottomSheet sheet = MirrlyUiKit.createFrostedBottomSheet(context, false, resourceProvider, container);
        sheetHolder[0] = sheet;
        showDialog(sheet);
    }

    private void fillSlotEditorItems(int slot, ArrayList<UItem> items) {
        int currentMode = MirrlyInterfaceConfig.getSlotMode(slot);
        String currentAction = MirrlyInterfaceConfig.getSlotCustomAction(slot);
        boolean isCustomMode = currentMode == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM;
        long customChatId = MirrlyInterfaceConfig.getSlotCustomChatId(slot);
        String customChatTitle = MirrlyInterfaceConfig.getSlotCustomChatTitle(slot);

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfaceSlotModeHeader)));
        items.add(SheetModeCell.Factory.of(
                ID_SHEET_MODE_DEFAULT,
                LocaleController.getString(R.string.MirrlyInterfaceSlotModeDefaultFull),
                currentMode == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT
        ));
        items.add(SheetModeCell.Factory.of(
                ID_SHEET_MODE_HIDDEN,
                LocaleController.getString(R.string.MirrlyInterfaceSlotModeHiddenFull),
                currentMode == MirrlyInterfaceConfig.SLOT_MODE_HIDDEN
        ));
        items.add(SheetModeCell.Factory.of(
                ID_SHEET_MODE_CUSTOM,
                LocaleController.getString(R.string.MirrlyInterfaceSlotModeCustomFull),
                isCustomMode
        ));
        items.add(UItem.asShadow(null));

        // Specific chat picker row
        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyInterfaceQuickActionsHeader)));
        String customChatSub;
        if (customChatId != 0L) {
            String resolvedTitle = MirrlyInterfacePreviews.resolveQuickActionShort(
                    currentAccount, MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT, customChatId, customChatTitle);
            customChatSub = LocaleController.formatString(R.string.MirrlyInterfaceQuickCustomChatCurrent, resolvedTitle);
        } else {
            customChatSub = LocaleController.getString(R.string.MirrlyInterfaceQuickCustomChatPickSub);
        }
        items.add(QuickActionCell.Factory.of(
                ID_SHEET_PICK_CHAT,
                MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT,
                R.drawable.msg_discuss,
                LocaleController.getString(R.string.MirrlyInterfaceQuickCustomChatPick),
                customChatSub,
                isCustomMode && MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT.equals(currentAction)
        ));
        items.add(UItem.asShadow(null));

        // Category 1: MirrlyGram
        String[] mirrlyActions = new String[]{
                MirrlyInterfaceConfig.QUICK_NOTES,
                MirrlyInterfaceConfig.QUICK_DELETED,
                MirrlyInterfaceConfig.QUICK_MIRRLY_SETTINGS,
                MirrlyInterfaceConfig.QUICK_CUSTOMIZATION,
                MirrlyInterfaceConfig.QUICK_NETWORK,
                MirrlyInterfaceConfig.QUICK_INTERFACE_SETTINGS
        };
        addQuickActionCategory(items, R.string.MirrlyInterfaceQuickCatMirrly, mirrlyActions, 0, isCustomMode, currentAction);

        // Category 2: Telegram Screens
        String[] telegramActions = new String[]{
                MirrlyInterfaceConfig.QUICK_SAVED,
                MirrlyInterfaceConfig.QUICK_ARCHIVE,
                MirrlyInterfaceConfig.QUICK_CONTACTS,
                MirrlyInterfaceConfig.QUICK_CALLS,
                MirrlyInterfaceConfig.QUICK_SETTINGS,
                MirrlyInterfaceConfig.QUICK_PROFILE,
                MirrlyInterfaceConfig.QUICK_FOLDERS,
                MirrlyInterfaceConfig.QUICK_DEVICES,
                MirrlyInterfaceConfig.QUICK_APPEARANCE,
                MirrlyInterfaceConfig.QUICK_PROXY_LIST,
                MirrlyInterfaceConfig.QUICK_WALLET
        };
        addQuickActionCategory(items, R.string.MirrlyInterfaceQuickCatTelegram, telegramActions, 20, isCustomMode, currentAction);

        // Category 3: Instant Actions
        String[] instantActions = new String[]{
                MirrlyInterfaceConfig.QUICK_SEARCH,
                MirrlyInterfaceConfig.QUICK_NEW_CHAT,
                MirrlyInterfaceConfig.QUICK_NEW_GROUP,
                MirrlyInterfaceConfig.QUICK_NEW_CHANNEL,
                MirrlyInterfaceConfig.QUICK_THEME_TOGGLE
        };
        addQuickActionCategory(items, R.string.MirrlyInterfaceQuickCatActions, instantActions, 50, isCustomMode, currentAction);
    }

    private void addQuickActionCategory(
            ArrayList<UItem> items,
            int headerRes,
            String[] actions,
            int idOffset,
            boolean isCustomMode,
            String currentAction
    ) {
        items.add(UItem.asHeader(LocaleController.getString(headerRes)));
        for (int i = 0; i < actions.length; i++) {
            String action = actions[i];
            items.add(QuickActionCell.Factory.of(
                    ID_SHEET_ACTION_BASE + idOffset + i,
                    action,
                    MirrlyInterfacePreviews.resolveQuickActionIcon(action),
                    MirrlyInterfacePreviews.resolveQuickActionName(action),
                    null,
                    isCustomMode && action.equals(currentAction)
            ));
        }
        items.add(UItem.asShadow(null));
    }

    private void launchCustomChatPicker(int slot) {
        if (getParentActivity() == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_DEFAULT);
        args.putBoolean("allowUsers", true);
        args.putBoolean("allowGroups", true);
        args.putBoolean("allowMegagroups", true);
        args.putBoolean("allowLegacyGroups", true);
        args.putBoolean("allowChannels", true);
        args.putBoolean("allowBots", true);
        args.putBoolean("checkCanWrite", false);
        args.putBoolean("canSelectTopics", false);
        args.putBoolean("resetDelegate", true);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((dialogsActivity, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids == null || dids.isEmpty()) {
                return true;
            }
            long dialogId = dids.get(0).dialogId;
            if (DialogObject.isFolderDialogId(dialogId)) {
                return false;
            }
            String chatTitle = MirrlyInterfacePreviews.resolveDialogTitle(currentAccount, dialogId);
            if (TextUtils.isEmpty(chatTitle)) {
                chatTitle = LocaleController.getString(R.string.MirrlyQuickShortCustomChat);
            }
            MirrlyInterfaceConfig.setSlotCustomChat(slot, dialogId, chatTitle);
            dialogsActivity.finishFragment();
            return true;
        });
        presentFragment(picker);
    }

    // -------------------------------------------------------------------------
    // Status & Naming Helpers
    // -------------------------------------------------------------------------

    private String buildBottomBarStatusSummary(MirrlyInterfacePreviews.BottomBarSpec spec) {
        int visible = spec.getVisibleCount();
        StringBuilder sb = new StringBuilder();
        if (visible == 0) {
            sb.append(LocaleController.getString(R.string.MirrlyInterfaceBarStatusAllHidden));
        } else {
            int hidden = MirrlyInterfaceConfig.SLOT_COUNT - visible;
            int custom = 0;
            for (int mode : spec.slotModes) {
                if (mode == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM) {
                    custom++;
                }
            }
            sb.append(LocaleController.formatString(R.string.MirrlyInterfaceBarStatusVisibleCount, visible));
            if (hidden > 0) {
                sb.append(' ').append(LocaleController.getString(R.string.MirrlyInterfaceBarStatusShrunk));
            }
            if (custom > 0) {
                sb.append(' ').append(LocaleController.formatString(R.string.MirrlyInterfaceBarStatusFullWidth, custom));
            }
            if (spec.collapseMode == MirrlyInterfaceConfig.COLLAPSE_ALWAYS) {
                sb.append('\n').append(LocaleController.getString(R.string.MirrlyInterfaceBarStatusCircleAlways));
            } else if (spec.collapseMode == MirrlyInterfaceConfig.COLLAPSE_AUTO) {
                sb.append('\n').append(LocaleController.getString(R.string.MirrlyInterfaceBarStatusCircleAuto));
            }
        }
        if (spec.showTopSettingsFallback) {
            sb.append('\n').append(LocaleController.getString(R.string.MirrlyInterfaceBarFallbackSettings));
        }
        if (spec.showMoreProfileFallback) {
            sb.append('\n').append(LocaleController.getString(R.string.MirrlyInterfaceBarFallbackProfile));
        }
        return sb.toString();
    }

    private static String getSlotTitle(int slot) {
        switch (slot) {
            case MirrlyInterfaceConfig.SLOT_CHATS:
                return LocaleController.getString(R.string.MirrlyInterfaceSlotChats);
            case MirrlyInterfaceConfig.SLOT_CONTACTS:
                return LocaleController.getString(R.string.MirrlyInterfaceSlotContacts);
            case MirrlyInterfaceConfig.SLOT_SETTINGS_OR_CALLS:
                return LocaleController.getString(R.string.MirrlyInterfaceSlotSettingsOrCalls);
            case MirrlyInterfaceConfig.SLOT_PROFILE:
            default:
                return LocaleController.getString(R.string.MirrlyInterfaceSlotProfile);
        }
    }

    @DrawableRes
    private static int resolveDefaultSlotIcon(int slot, boolean showCallsTab) {
        switch (slot) {
            case MirrlyInterfaceConfig.SLOT_CHATS:
                return R.drawable.filled_chatlist2;
            case MirrlyInterfaceConfig.SLOT_CONTACTS:
                return R.drawable.msg_contacts;
            case MirrlyInterfaceConfig.SLOT_SETTINGS_OR_CALLS:
                return showCallsTab ? R.drawable.msg_calls : R.drawable.msg_settings;
            case MirrlyInterfaceConfig.SLOT_PROFILE:
            default:
                return R.drawable.msg_openprofile;
        }
    }

    private static IconBackgroundColors resolveSlotBadgeColor(int slot) {
        switch (slot) {
            case MirrlyInterfaceConfig.SLOT_CHATS:
                return IconBackgroundColors.BLUE;
            case MirrlyInterfaceConfig.SLOT_CONTACTS:
                return IconBackgroundColors.GREEN;
            case MirrlyInterfaceConfig.SLOT_SETTINGS_OR_CALLS:
                return IconBackgroundColors.CYAN;
            case MirrlyInterfaceConfig.SLOT_PROFILE:
            default:
                return IconBackgroundColors.ORANGE;
        }
    }

    private static int getStableIndex(List<String> defaults, String key, int fallback) {
        int idx = defaults.indexOf(key);
        return idx >= 0 ? idx : fallback;
    }

    // -------------------------------------------------------------------------
    // Top Liquid-Glass 4-Icon Tab Switcher (Synced to ViewPagerFixed)
    // -------------------------------------------------------------------------

    private static class IconTabSwitcher extends FrameLayout implements Theme.Colorable {

        private static final float[] GLASS_PILL_RIM_POSITIONS = new float[]{0f, 0.52f, 1f};

        private final Theme.ResourcesProvider resourcesProvider;
        private final FrameLayout[] slots;
        private final ImageView[] iconViews;
        private final int[] lastIconColors;

        private final Paint pillFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glassPillFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glassPillRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF pillRect = new RectF();
        private final RectF glassPillRect = new RectF();
        private final Matrix glassPillMatrix = new Matrix();
        private final int[] glassPillRimColors = new int[3];
        @Nullable
        private LinearGradient glassPillFillShader;
        @Nullable
        private LinearGradient glassPillRimShader;
        private boolean glassPillShaderIsDark;
        private float glassPillShaderRimScale = -1f;

        private int activeColor;
        private int inactiveColor;
        private int selectedPillColor;
        private boolean isDarkTheme;
        private float visualPos = 0f;
        private int targetPos = 0;

        IconTabSwitcher(
                @NonNull Context context,
                @Nullable Theme.ResourcesProvider resourcesProvider,
                @DrawableRes int[] iconResIds,
                CharSequence[] descriptions,
                Utilities.Callback<Integer> onTabSelected
        ) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            int count = iconResIds.length;
            this.slots = new FrameLayout[count];
            this.iconViews = new ImageView[count];
            this.lastIconColors = new int[count];

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < count; i++) {
                lastIconColors[i] = Integer.MIN_VALUE;

                FrameLayout slot = new FrameLayout(context);
                slot.setClickable(true);
                slot.setFocusable(true);
                if (descriptions != null && i < descriptions.length) {
                    slot.setContentDescription(descriptions[i]);
                }
                ScaleStateListAnimator.apply(slot, 0.08f, 1.3f);
                MirrlyUiKit.attachDisallowInterceptTouch(slot);

                ImageView iv = new ImageView(context);
                iv.setImageResource(iconResIds[i]);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                slot.addView(iv, LayoutHelper.createFrame(22, 22, Gravity.CENTER));

                final int tabIndex = i;
                slot.setOnClickListener(v -> {
                    AndroidUtilities.vibrateCursor(v);
                    if (onTabSelected != null) {
                        onTabSelected.run(tabIndex);
                    }
                });

                slots[i] = slot;
                iconViews[i] = iv;
                row.addView(slot, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
            }

            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 9, 0, 9, 0));
            updateColors();
        }

        void setPositionImmediate(int position) {
            int count = iconViews.length;
            int clamped = Math.max(0, Math.min(count - 1, position));
            targetPos = clamped;
            visualPos = clamped;
            updateSelectedAccessibility();
            refreshIconStates();
            invalidate();
        }

        void setTargetPosition(int position) {
            int count = iconViews.length;
            int clamped = Math.max(0, Math.min(count - 1, position));
            if (targetPos != clamped) {
                targetPos = clamped;
                updateSelectedAccessibility();
            }
        }

        void onPagerPositionChanged(float animatedPosition, int currentTarget) {
            int count = iconViews.length;
            float clampedAnim = MathUtils.clamp(animatedPosition, 0f, Math.max(0, count - 1));
            setTargetPosition(currentTarget);
            if (Math.abs(visualPos - clampedAnim) > 0.0005f) {
                visualPos = clampedAnim;
                refreshIconStates();
                invalidate();
            }
        }

        private void updateSelectedAccessibility() {
            for (int i = 0; i < slots.length; i++) {
                slots[i].setSelected(i == targetPos);
            }
        }

        private void refreshIconStates() {
            int count = iconViews.length;
            float clamped = MathUtils.clamp(visualPos, 0f, Math.max(0, count - 1));
            for (int i = 0; i < count; i++) {
                float closeness = Math.max(0f, 1f - Math.abs(i - clamped));
                float scale = 1f + 0.08f * closeness;
                iconViews[i].setScaleX(scale);
                iconViews[i].setScaleY(scale);
                int color = ColorUtils.blendARGB(inactiveColor, activeColor, closeness);
                if (lastIconColors[i] != color) {
                    lastIconColors[i] = color;
                    iconViews[i].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                }
            }
        }

        @Override
        public void updateColors() {
            isDarkTheme = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            activeColor = Theme.getColor(Theme.key_profile_tabSelectedText, resourcesProvider);
            inactiveColor = Theme.getColor(Theme.key_profile_tabText, resourcesProvider);
            selectedPillColor = Theme.getColor(Theme.key_profile_tabSelectedLine, resourcesProvider);
            glassPillFillShader = null;
            glassPillRimShader = null;
            for (int i = 0; i < lastIconColors.length; i++) {
                lastIconColors[i] = Integer.MIN_VALUE;
            }
            refreshIconStates();
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_profile_tabSelectedText);
            colors.add(Theme.key_profile_tabText);
            colors.add(Theme.key_profile_tabSelectedLine);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            return true;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            int count = iconViews.length;
            if (w <= 0 || h <= 0 || count <= 0) {
                return;
            }

            float trackLeft = dp(9);
            float trackRight = w - dp(9);
            float trackW = Math.max(0f, trackRight - trackLeft);
            float slotW = trackW / count;
            float clampedPos = MathUtils.clamp(visualPos, 0f, count - 1);

            float left = trackLeft + slotW * clampedPos + dp(2);
            float right = trackLeft + slotW * (clampedPos + 1f) - dp(2);
            float pillH = dp(30);
            float top = (h - pillH) * 0.5f;
            float bottom = top + pillH;
            float radius = pillH * 0.5f;

            pillRect.set(left, top, right, bottom);
            pillFillPaint.setColor(ColorUtils.setAlphaComponent(selectedPillColor, isDarkTheme ? 40 : 31));
            canvas.drawRoundRect(pillRect, radius, radius, pillFillPaint);
            drawActiveTabGlassLens(canvas, left, top, right, bottom, radius);
        }

        private void drawActiveTabGlassLens(Canvas canvas, float left, float top, float right, float bottom, float radius) {
            GlassSettings gs = GlassEngine.getCurrentGlassSettings();
            if (gs == null || !gs.isLiquidGlassEnabled || gs.rimScale <= 0.001f) {
                return;
            }
            float h = bottom - top;
            if (h <= 1f || right <= left) {
                return;
            }
            float rimScale = gs.rimScale;
            if (glassPillFillShader == null || glassPillRimShader == null
                    || glassPillShaderIsDark != isDarkTheme
                    || Math.abs(glassPillShaderRimScale - rimScale) > 0.001f) {
                glassPillShaderIsDark = isDarkTheme;
                glassPillShaderRimScale = rimScale;

                int fillTop = isDarkTheme ? 0x18FFFFFF : 0x26FFFFFF;
                int fillBottom = isDarkTheme ? 0x04FFFFFF : 0x06FFFFFF;
                glassPillFillShader = new LinearGradient(0f, 0f, 0f, 1f, fillTop, fillBottom, Shader.TileMode.CLAMP);
                glassPillFillPaint.setStyle(Paint.Style.FILL);
                glassPillFillPaint.setShader(glassPillFillShader);

                glassPillRimColors[0] = Theme.multAlpha(isDarkTheme ? 0x33FFFFFF : 0xD9FFFFFF, rimScale);
                glassPillRimColors[1] = Theme.multAlpha(isDarkTheme ? 0x0CFFFFFF : 0x26FFFFFF, rimScale);
                glassPillRimColors[2] = Theme.multAlpha(isDarkTheme ? 0x16FFFFFF : 0x33FFFFFF, rimScale);
                glassPillRimShader = new LinearGradient(0f, 0f, 0f, 1f, glassPillRimColors, GLASS_PILL_RIM_POSITIONS, Shader.TileMode.CLAMP);
                glassPillRimPaint.setStyle(Paint.Style.STROKE);
                glassPillRimPaint.setShader(glassPillRimShader);
            }

            glassPillMatrix.reset();
            glassPillMatrix.setScale(1f, h);
            glassPillMatrix.postTranslate(0f, top);
            glassPillFillShader.setLocalMatrix(glassPillMatrix);
            glassPillRimShader.setLocalMatrix(glassPillMatrix);

            glassPillFillPaint.setAlpha(255);
            glassPillRect.set(left, top, right, bottom);
            canvas.drawRoundRect(glassPillRect, radius, radius, glassPillFillPaint);

            float strokeW = dpf2(0.55f) * rimScale;
            glassPillRimPaint.setStrokeWidth(strokeW);
            glassPillRimPaint.setAlpha(255);
            float halfStroke = strokeW * 0.5f;
            glassPillRect.inset(halfStroke, halfStroke);
            float insetRadius = Math.max(0f, radius - halfStroke);
            canvas.drawRoundRect(glassPillRect, insetRadius, insetRadius, glassPillRimPaint);
        }
    }

    // -------------------------------------------------------------------------
    // Custom 3-Option Segmented Pill Card (Theme.Colorable + RTL)
    // -------------------------------------------------------------------------

    private static final class TripleSegmentSpec {
        final CharSequence label0;
        final CharSequence label1;
        final CharSequence label2;
        final int selectedIndex;
        final boolean spatialLtrOrder;
        final Utilities.Callback<Integer> onSelected;

        TripleSegmentSpec(
                CharSequence label0,
                CharSequence label1,
                CharSequence label2,
                int selectedIndex,
                Utilities.Callback<Integer> onSelected
        ) {
            this(label0, label1, label2, selectedIndex, false, onSelected);
        }

        TripleSegmentSpec(
                CharSequence label0,
                CharSequence label1,
                CharSequence label2,
                int selectedIndex,
                boolean spatialLtrOrder,
                Utilities.Callback<Integer> onSelected
        ) {
            this.label0 = label0;
            this.label1 = label1;
            this.label2 = label2;
            this.selectedIndex = Math.max(0, Math.min(2, selectedIndex));
            this.spatialLtrOrder = spatialLtrOrder;
            this.onSelected = onSelected;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TripleSegmentSpec)) return false;
            TripleSegmentSpec that = (TripleSegmentSpec) o;
            return selectedIndex == that.selectedIndex
                    && spatialLtrOrder == that.spatialLtrOrder
                    && TextUtils.equals(label0, that.label0)
                    && TextUtils.equals(label1, that.label1)
                    && TextUtils.equals(label2, that.label2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label0, label1, label2, selectedIndex, spatialLtrOrder);
        }
    }

    private static class TripleSegmentCardCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trackBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbSheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF trackRect = new RectF();
        private final RectF thumbRect = new RectF();
        private final Path clipThumb = new Path();
        private final CellFlickerDrawable thumbFlicker;

        private final AnimatedFloat thumbVisualPos = new AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final TextView[] labels = new TextView[3];
        private final LinearLayout row;

        private int selectedIndex = 0;
        private boolean spatialLtrOrder = false;
        @Nullable
        private Utilities.Callback<Integer> onSelected;
        private int lastTrackTop = Integer.MIN_VALUE;
        private int lastTrackBottom = Integer.MIN_VALUE;
        private int lastThumbTop = Integer.MIN_VALUE;
        private int lastThumbBottom = Integer.MIN_VALUE;
        private int lastGlassGen = -1;
        private boolean cachedIsDark;
        private int cachedAccent;
        private int cachedDivider;

        TripleSegmentCardCell(@NonNull Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            setPadding(dp(14), dp(10), dp(14), dp(10));

            trackBorderPaint.setStyle(Paint.Style.STROKE);
            thumbBorderPaint.setStyle(Paint.Style.STROKE);

            thumbFlicker = new CellFlickerDrawable(30, 74, 160);
            thumbFlicker.drawFrame = false;
            thumbFlicker.repeatProgress = 4.5f;
            thumbFlicker.animationSpeedScale = 0.75f;

            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(3), dp(3), dp(3), dp(3));

            for (int v = 0; v < 3; v++) {
                TextView tv = new TextView(context);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
                tv.setTypeface(AndroidUtilities.bold());
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(dp(6), 0, dp(6), 0);
                ScaleStateListAnimator.apply(tv, 0.03f, 1.0f);
                MirrlyUiKit.attachDisallowInterceptTouch(tv);
                final int visualIdx = v;
                tv.setOnClickListener(view -> {
                    int logicalIdx = toLogicalIndex(visualIdx);
                    if (selectedIndex == logicalIdx) {
                        return;
                    }
                    selectedIndex = logicalIdx;
                    thumbVisualPos.set(toVisualIndex(logicalIdx), true);
                    AndroidUtilities.vibrateCursor(view);
                    invalidate();
                    if (onSelected != null) {
                        onSelected.run(logicalIdx);
                    }
                });
                labels[v] = tv;
                row.addView(tv, LayoutHelper.createLinear(0, 40, 1f));
            }

            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 46));
            updateColors();
        }

        private int toVisualIndex(int logicalIndex) {
            return (LocaleController.isRTL && !spatialLtrOrder) ? (2 - logicalIndex) : logicalIndex;
        }

        private int toLogicalIndex(int visualIndex) {
            return (LocaleController.isRTL && !spatialLtrOrder) ? (2 - visualIndex) : visualIndex;
        }

        void bind(TripleSegmentSpec spec) {
            boolean orderChanged = this.spatialLtrOrder != spec.spatialLtrOrder;
            this.spatialLtrOrder = spec.spatialLtrOrder;
            this.onSelected = spec.onSelected;
            labels[toVisualIndex(0)].setText(spec.label0);
            labels[toVisualIndex(1)].setText(spec.label1);
            labels[toVisualIndex(2)].setText(spec.label2);
            if (this.selectedIndex != spec.selectedIndex || orderChanged) {
                this.selectedIndex = spec.selectedIndex;
                thumbVisualPos.set(toVisualIndex(this.selectedIndex), !orderChanged && isAttachedToWindow());
            } else {
                thumbVisualPos.set(toVisualIndex(this.selectedIndex), false);
            }
            updateColors();
            invalidate();
        }

        @Override
        public void updateColors() {
            boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
            int cardWhite = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            int grayBg = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            int divider = Theme.getColor(Theme.key_divider, resourcesProvider);

            cachedIsDark = isDark;
            cachedAccent = accent;
            cachedDivider = divider;
            lastTrackTop = Integer.MIN_VALUE;
            lastThumbTop = Integer.MIN_VALUE;

            float alphaScale = Utilities.clamp(MirrlyConfig.getEffectiveGlassAlpha(1f), 1.3f, 0.4f);
            trackPaint.setColor(ColorUtils.blendARGB(cardWhite, grayBg, isDark ? 0.58f : 0.85f));
            thumbPaint.setColor(ColorUtils.blendARGB(cardWhite, accent, (isDark ? 0.28f : 0.14f) * alphaScale));
            thumbFlicker.setColors(isDark ? Color.WHITE : accent, isDark ? 18 : 14, isDark ? 42 : 30);
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhite);
            colors.add(Theme.key_windowBackgroundGray);
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
            colors.add(Theme.key_windowBackgroundWhiteGrayText);
            colors.add(Theme.key_divider);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            thumbFlicker.setParentWidth(Math.max(1, w / 3));
        }

        private void ensureGlassGradients(int trackTop, int trackBottom, int thumbTop, int thumbBottom) {
            int gen = GlassEngine.getGlassSettingsGenerationId();
            float rimScale = Utilities.clamp(MirrlyConfig.getGlassRimScale(), 1.6f, 0.25f);
            if (trackTop != lastTrackTop || trackBottom != lastTrackBottom || gen != lastGlassGen) {
                lastTrackTop = trackTop;
                lastTrackBottom = trackBottom;
                int baseTrackAlpha = cachedIsDark ? 0x7A : 0x55;
                int topTrackRim = ColorUtils.blendARGB(cachedDivider, Color.WHITE, cachedIsDark ? 0.28f : 0.45f);
                trackBorderPaint.setStrokeWidth(dpf2(0.75f) * Utilities.clamp(rimScale, 1.35f, 0.5f));
                trackBorderPaint.setShader(new LinearGradient(
                        0f, trackTop, 0f, trackBottom,
                        ColorUtils.setAlphaComponent(topTrackRim, Math.min(255, Math.round(baseTrackAlpha * 1.15f * rimScale))),
                        ColorUtils.setAlphaComponent(cachedDivider, Math.min(255, Math.round(baseTrackAlpha * 0.75f * rimScale))),
                        Shader.TileMode.CLAMP
                ));
            }
            if (thumbTop != lastThumbTop || thumbBottom != lastThumbBottom || gen != lastGlassGen) {
                lastThumbTop = thumbTop;
                lastThumbBottom = thumbBottom;
                lastGlassGen = gen;
                int topSpecular = ColorUtils.blendARGB(cachedAccent, Color.WHITE, cachedIsDark ? 0.55f : 0.38f);
                int topAlpha = Math.min(255, Math.round((cachedIsDark ? 0xA8 : 0x7A) * rimScale));
                int midAlpha = Math.min(255, Math.round((cachedIsDark ? 0x55 : 0x3D) * rimScale));
                int bottomAlpha = Math.min(255, Math.round((cachedIsDark ? 0x74 : 0x52) * rimScale));
                thumbBorderPaint.setStrokeWidth(dpf2(0.85f) * Utilities.clamp(rimScale, 1.45f, 0.45f));
                thumbBorderPaint.setShader(new LinearGradient(
                        0f, thumbTop, 0f, thumbBottom,
                        new int[]{
                                ColorUtils.setAlphaComponent(topSpecular, topAlpha),
                                ColorUtils.setAlphaComponent(cachedAccent, midAlpha),
                                ColorUtils.setAlphaComponent(cachedAccent, bottomAlpha)
                        },
                        new float[]{0f, 0.55f, 1f},
                        Shader.TileMode.CLAMP
                ));
                int sheenAlpha = Math.min(255, Math.round((cachedIsDark ? 0x24 : 0x3C) * Utilities.clamp(rimScale, 1.35f, 0f)));
                thumbSheenPaint.setShader(new LinearGradient(
                        0f, thumbTop, 0f, thumbTop + Math.max(1f, (thumbBottom - thumbTop) * 0.68f),
                        ColorUtils.setAlphaComponent(Color.WHITE, sheenAlpha),
                        0x00FFFFFF,
                        Shader.TileMode.CLAMP
                ));
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float left = getPaddingLeft();
            float top = getPaddingTop();
            float right = getWidth() - getPaddingRight();
            float bottom = getHeight() - getPaddingBottom();

            trackRect.set(left, top, right, bottom);
            float trackRadius = dp(13);

            float visualPos = thumbVisualPos.set(toVisualIndex(selectedIndex));
            float inset = dp(3);
            float segW = (trackRect.width() - inset * 2f) / 3f;
            float thumbLeft = trackRect.left + inset + segW * visualPos;
            thumbRect.set(thumbLeft, trackRect.top + inset, thumbLeft + segW, trackRect.bottom - inset);

            ensureGlassGradients(
                    Math.round(trackRect.top),
                    Math.round(trackRect.bottom),
                    Math.round(thumbRect.top),
                    Math.round(thumbRect.bottom)
            );

            canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint);
            canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackBorderPaint);

            float thumbRadius = dp(10);
            canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbPaint);
            canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbSheenPaint);
            if (SharedConfig.animationsEnabled() && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES)) {
                canvas.save();
                clipThumb.rewind();
                clipThumb.addRoundRect(thumbRect, thumbRadius, thumbRadius, Path.Direction.CW);
                canvas.clipPath(clipThumb);
                thumbFlicker.draw(canvas, thumbRect, thumbRadius, this);
                canvas.restore();
            }
            canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbBorderPaint);

            int activeText = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            int inactiveText = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            for (int v = 0; v < 3; v++) {
                float dist = Math.abs(v - visualPos);
                float frac = Math.max(0f, 1f - dist);
                labels[v].setTextColor(ColorUtils.blendARGB(inactiveText, activeText, frac));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
            );
        }

        static class Factory extends UItem.UItemFactory<TripleSegmentCardCell> {
            static {
                setup(new Factory());
            }

            @Override
            public TripleSegmentCardCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new TripleSegmentCardCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof TripleSegmentSpec) {
                    ((TripleSegmentCardCell) view).bind((TripleSegmentSpec) item.object);
                }
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                if (a == null || b == null || a.id != b.id || a.viewType != b.viewType) {
                    return false;
                }
                return Objects.equals(a.object, b.object);
            }

            static UItem of(int id, TripleSegmentSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Reorderable + Toggleable Menu Item Row (Tab 2 & Tab 3)
    // -------------------------------------------------------------------------

    private interface ReorderCallback {
        void onReorder(ReorderableItemSpec spec, int delta, View anchor);
    }

    private interface ToggleCallback {
        void onToggle(ReorderableItemSpec spec, boolean nextChecked, ReorderableMenuItemCell cell);
    }

    private static final class ReorderableItemSpec {
        final String key;
        final boolean isMessageMenu;
        @DrawableRes final int iconRes;
        final CharSequence title;
        final boolean checked;
        final boolean autoEnabled;
        final boolean destructive;
        final boolean canMoveUp;
        final boolean canMoveDown;

        ReorderableItemSpec(
                String key,
                boolean isMessageMenu,
                @DrawableRes int iconRes,
                CharSequence title,
                boolean checked,
                boolean autoEnabled,
                boolean destructive,
                boolean canMoveUp,
                boolean canMoveDown
        ) {
            this.key = key;
            this.isMessageMenu = isMessageMenu;
            this.iconRes = iconRes;
            this.title = title;
            this.checked = checked;
            this.autoEnabled = autoEnabled;
            this.destructive = destructive;
            this.canMoveUp = canMoveUp;
            this.canMoveDown = canMoveDown;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ReorderableItemSpec)) return false;
            ReorderableItemSpec that = (ReorderableItemSpec) o;
            return isMessageMenu == that.isMessageMenu
                    && iconRes == that.iconRes
                    && checked == that.checked
                    && autoEnabled == that.autoEnabled
                    && destructive == that.destructive
                    && canMoveUp == that.canMoveUp
                    && canMoveDown == that.canMoveDown
                    && Objects.equals(key, that.key)
                    && TextUtils.equals(title, that.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, isMessageMenu, iconRes, title, checked, autoEnabled, destructive, canMoveUp, canMoveDown);
        }
    }

    private static class ReorderableMenuItemCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final ImageView iconView;
        private final SimpleTextView titleView;
        private final TextView autoBadgeView;
        private final ImageView upBtn;
        private final ImageView downBtn;
        private final Switch switchView;

        @Nullable private ReorderableItemSpec boundSpec;
        @Nullable private String boundKey;
        @Nullable private ReorderCallback reorderCallback;
        @Nullable private ToggleCallback toggleCallback;
        private boolean needDivider;

        ReorderableMenuItemCell(@NonNull Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView(iconView, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 20, 0, 20, 0));

            titleView = new SimpleTextView(context);
            titleView.setTextSize(16);
            titleView.setEllipsizeByGradient(true);
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(titleView);

            autoBadgeView = new TextView(context);
            autoBadgeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            autoBadgeView.setTypeface(AndroidUtilities.bold());
            autoBadgeView.setText(LocaleController.getString(R.string.MirrlyInterfaceDrawerAutoBadge));
            autoBadgeView.setPadding(dp(7), dp(2), dp(7), dp(2));
            autoBadgeView.setVisibility(GONE);
            addView(autoBadgeView);

            upBtn = new ImageView(context);
            upBtn.setImageResource(R.drawable.msg_go_up);
            upBtn.setScaleType(ImageView.ScaleType.CENTER);
            upBtn.setContentDescription(LocaleController.getString(R.string.MirrlyInterfaceDrawerMoveUp));
            ScaleStateListAnimator.apply(upBtn, 0.12f, 1.5f);
            upBtn.setOnClickListener(v -> {
                if (boundSpec != null && boundSpec.canMoveUp && reorderCallback != null) {
                    reorderCallback.onReorder(boundSpec, -1, v);
                }
            });
            addView(upBtn);

            downBtn = new ImageView(context);
            downBtn.setImageResource(R.drawable.msg_go_down);
            downBtn.setScaleType(ImageView.ScaleType.CENTER);
            downBtn.setContentDescription(LocaleController.getString(R.string.MirrlyInterfaceDrawerMoveDown));
            ScaleStateListAnimator.apply(downBtn, 0.12f, 1.5f);
            downBtn.setOnClickListener(v -> {
                if (boundSpec != null && boundSpec.canMoveDown && reorderCallback != null) {
                    reorderCallback.onReorder(boundSpec, 1, v);
                }
            });
            addView(downBtn);

            switchView = new Switch(context, resourcesProvider);
            switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
            switchView.setClickable(false);
            switchView.setFocusable(false);
            addView(switchView);

            updateColors();
        }

        void bind(
                ReorderableItemSpec spec,
                boolean divider,
                @Nullable ReorderCallback reorderCb,
                @Nullable ToggleCallback toggleCb
        ) {
            boolean sameKey = TextUtils.equals(boundKey, spec.key);
            this.boundSpec = spec;
            this.boundKey = spec.key;
            this.needDivider = divider;
            this.reorderCallback = reorderCb;
            this.toggleCallback = toggleCb;

            iconView.setImageResource(spec.iconRes);
            titleView.setText(spec.title);
            autoBadgeView.setVisibility(spec.autoEnabled ? VISIBLE : GONE);

            upBtn.setEnabled(spec.canMoveUp);
            upBtn.setAlpha(spec.canMoveUp ? 1f : 0.24f);

            downBtn.setEnabled(spec.canMoveDown);
            downBtn.setAlpha(spec.canMoveDown ? 1f : 0.24f);

            switchView.setChecked(spec.checked, sameKey && isAttachedToWindow());

            float contentAlpha = spec.checked ? 1f : 0.52f;
            iconView.setAlpha(contentAlpha);
            titleView.setAlpha(contentAlpha);

            updateColors();
            requestLayout();
            invalidate();
        }

        void setChecked(boolean checked, boolean animated) {
            switchView.setChecked(checked, animated);
            float contentAlpha = checked ? 1f : 0.52f;
            iconView.setAlpha(contentAlpha);
            titleView.setAlpha(contentAlpha);
        }

        void performRowToggle() {
            if (boundSpec == null || toggleCallback == null) {
                return;
            }
            boolean next = !switchView.isChecked();
            switchView.setChecked(next, true);
            toggleCallback.onToggle(boundSpec, next, this);
        }

        @Override
        public void updateColors() {
            boolean destructive = boundSpec != null && boundSpec.destructive;
            int iconColor = Theme.getColor(destructive ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider);
            int textColor = Theme.getColor(destructive ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            int selector = Theme.getColor(Theme.key_listSelector, resourcesProvider);
            boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();

            iconView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
            titleView.setTextColor(textColor);
            upBtn.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
            downBtn.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
            upBtn.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(32), Color.TRANSPARENT, selector));
            downBtn.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(32), Color.TRANSPARENT, selector));

            autoBadgeView.setTextColor(accent);
            autoBadgeView.setBackground(Theme.createRoundRectDrawable(dp(8), ColorUtils.setAlphaComponent(accent, isDark ? 0x33 : 0x1E)));
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteGrayIcon);
            colors.add(Theme.key_windowBackgroundWhiteBlackText);
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
            colors.add(Theme.key_text_RedRegular);
            colors.add(Theme.key_listSelector);
            colors.add(Theme.key_switchTrack);
            colors.add(Theme.key_switchTrackChecked);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = dp(52);

            iconView.measure(MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY));
            upBtn.measure(MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY));
            downBtn.measure(MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(32), MeasureSpec.EXACTLY));
            switchView.measure(MeasureSpec.makeMeasureSpec(dp(37), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(20), MeasureSpec.EXACTLY));

            int badgeW = 0;
            if (autoBadgeView.getVisibility() == VISIBLE) {
                autoBadgeView.measure(MeasureSpec.makeMeasureSpec(dp(90), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(22), MeasureSpec.AT_MOST));
                badgeW = autoBadgeView.getMeasuredWidth() + dp(8);
            }

            // Reserve space for left icon (62dp) + trailing controls (18dp + 37dp switch + 10dp + 32dp down + 2dp + 32dp up + 8dp)
            int reserved = dp(62) + dp(18 + 37 + 10 + 32 + 2 + 32 + 10) + badgeW;
            int maxTitleW = Math.max(0, width - reserved);
            titleView.measure(MeasureSpec.makeMeasureSpec(maxTitleW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(22), MeasureSpec.EXACTLY));

            setMeasuredDimension(width, height + (needDivider ? 1 : 0));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int w = right - left;
            int h = dp(52);
            boolean rtl = LocaleController.isRTL;

            int iconTop = (h - iconView.getMeasuredHeight()) / 2;
            int iconLeft = rtl ? (w - dp(20) - iconView.getMeasuredWidth()) : dp(20);
            iconView.layout(iconLeft, iconTop, iconLeft + iconView.getMeasuredWidth(), iconTop + iconView.getMeasuredHeight());

            int swTop = (h - switchView.getMeasuredHeight()) / 2;
            int swLeft = rtl ? dp(18) : (w - dp(18) - switchView.getMeasuredWidth());
            switchView.layout(swLeft, swTop, swLeft + switchView.getMeasuredWidth(), swTop + switchView.getMeasuredHeight());

            int btnTop = (h - downBtn.getMeasuredHeight()) / 2;
            int downLeft = rtl ? (swLeft + switchView.getMeasuredWidth() + dp(10)) : (swLeft - dp(10) - downBtn.getMeasuredWidth());
            downBtn.layout(downLeft, btnTop, downLeft + downBtn.getMeasuredWidth(), btnTop + downBtn.getMeasuredHeight());

            int upLeft = rtl ? (downLeft + downBtn.getMeasuredWidth() + dp(2)) : (downLeft - dp(2) - upBtn.getMeasuredWidth());
            upBtn.layout(upLeft, btnTop, upLeft + upBtn.getMeasuredWidth(), btnTop + upBtn.getMeasuredHeight());

            int titleTop = (h - titleView.getMeasuredHeight()) / 2;
            int titleW = titleView.getMeasuredWidth();
            int titleLeft = rtl ? (iconLeft - dp(16) - titleW) : dp(62);
            titleView.layout(titleLeft, titleTop, titleLeft + titleW, titleTop + titleView.getMeasuredHeight());

            if (autoBadgeView.getVisibility() == VISIBLE) {
                int bw = autoBadgeView.getMeasuredWidth();
                int bh = autoBadgeView.getMeasuredHeight();
                int bTop = (h - bh) / 2;
                int bLeft = rtl ? (titleLeft - dp(8) - bw) : (titleLeft + titleW + dp(8));
                autoBadgeView.layout(bLeft, bTop, bLeft + bw, bTop + bh);
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                Paint dividerPaint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : null;
                if (dividerPaint == null) {
                    dividerPaint = Theme.dividerPaint;
                }
                int lineLeft = LocaleController.isRTL ? 0 : dp(62);
                int lineRight = getMeasuredWidth() - (LocaleController.isRTL ? dp(62) : 0);
                canvas.drawLine(lineLeft, getMeasuredHeight() - 1, lineRight, getMeasuredHeight() - 1, dividerPaint);
            }
        }

        static class Factory extends UItem.UItemFactory<ReorderableMenuItemCell> {
            static {
                setup(new Factory());
            }

            @Override
            public ReorderableMenuItemCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new ReorderableMenuItemCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof ReorderableItemSpec) {
                    Object[] callbacks = item.object2 instanceof Object[] ? (Object[]) item.object2 : null;
                    ReorderCallback reorderCb = callbacks != null && callbacks.length > 0 && callbacks[0] instanceof ReorderCallback
                            ? (ReorderCallback) callbacks[0]
                            : null;
                    ToggleCallback toggleCb = callbacks != null && callbacks.length > 1 && callbacks[1] instanceof ToggleCallback
                            ? (ToggleCallback) callbacks[1]
                            : null;
                    ((ReorderableMenuItemCell) view).bind((ReorderableItemSpec) item.object, divider, reorderCb, toggleCb);
                }
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null
                        && a.id == b.id
                        && a.viewType == b.viewType
                        && Objects.equals(a.object, b.object);
            }

            static UItem of(int id, ReorderableItemSpec spec, ReorderCallback reorderCb, ToggleCallback toggleCb) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                item.object2 = new Object[]{reorderCb, toggleCb};
                return item;
            }
        }
    }

    // -------------------------------------------------------------------------
    // BottomSheet Custom Cells (Smooth Radio + Categorized Quick Action Rows)
    // -------------------------------------------------------------------------

    private static class SheetModeCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final RadioButton radioButton;
        private final TextView titleView;
        private int boundItemId = Integer.MIN_VALUE;
        private boolean needDivider;

        SheetModeCell(@NonNull Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            radioButton = new RadioButton(context);
            radioButton.setSize(dp(20));
            addView(radioButton, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 20, 0, 20, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(titleView, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL,
                    LocaleController.isRTL ? 20 : 60, 0,
                    LocaleController.isRTL ? 60 : 20, 0
            ));

            updateColors();
        }

        void bind(int itemId, CharSequence title, boolean checked, boolean divider) {
            boolean sameId = boundItemId == itemId;
            boundItemId = itemId;
            needDivider = divider;
            if (!TextUtils.equals(titleView.getText(), title)) {
                titleView.setText(title);
            }
            radioButton.setChecked(checked, sameId && isAttachedToWindow());
            invalidate();
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            radioButton.setColor(
                    Theme.getColor(Theme.key_radioBackground, resourcesProvider),
                    Theme.getColor(Theme.key_radioBackgroundChecked, resourcesProvider)
            );
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_dialogTextBlack);
            colors.add(Theme.key_radioBackground);
            colors.add(Theme.key_radioBackgroundChecked);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : null;
                if (paint == null) {
                    paint = Theme.dividerPaint;
                }
                int left = LocaleController.isRTL ? 0 : dp(60);
                int right = getMeasuredWidth() - (LocaleController.isRTL ? dp(60) : 0);
                canvas.drawLine(left, getMeasuredHeight() - 1, right, getMeasuredHeight() - 1, paint);
            }
        }

        static class Factory extends UItem.UItemFactory<SheetModeCell> {
            static {
                setup(new Factory());
            }

            @Override
            public SheetModeCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new SheetModeCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((SheetModeCell) view).bind(item.id, item.text, item.checked, divider);
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null
                        && a.id == b.id
                        && a.viewType == b.viewType
                        && a.checked == b.checked
                        && TextUtils.equals(a.text, b.text);
            }

            static UItem of(int id, CharSequence title, boolean checked) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.text = title;
                item.checked = checked;
                return item;
            }
        }
    }

    private static class QuickActionCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final ImageView iconView;
        private final LinearLayout textColumn;
        private final TextView titleView;
        private final TextView subtitleView;
        private final ImageView checkView;
        private boolean selected;
        private boolean needDivider;

        QuickActionCell(@NonNull Context context, @Nullable Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView(iconView, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 20, 0, 20, 0));

            textColumn = new LinearLayout(context);
            textColumn.setOrientation(LinearLayout.VERTICAL);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textColumn.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            subtitleView.setVisibility(GONE);
            textColumn.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            addView(textColumn, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL,
                    LocaleController.isRTL ? 52 : 60, 0,
                    LocaleController.isRTL ? 60 : 52, 0
            ));

            checkView = new ImageView(context);
            checkView.setImageResource(R.drawable.msg_text_check);
            checkView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            checkView.setVisibility(GONE);
            addView(checkView, LayoutHelper.createFrame(20, 20, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 20, 0, 20, 0));

            updateColors();
        }

        void bind(@DrawableRes int iconRes, CharSequence title, @Nullable CharSequence subtitle, boolean selected, boolean divider) {
            this.selected = selected;
            this.needDivider = divider;
            iconView.setImageResource(iconRes);
            titleView.setText(title);
            if (!TextUtils.isEmpty(subtitle)) {
                subtitleView.setText(subtitle);
                subtitleView.setVisibility(VISIBLE);
            } else {
                subtitleView.setVisibility(GONE);
            }
            checkView.setVisibility(selected ? VISIBLE : GONE);
            updateColors();
            invalidate();
        }

        @Override
        public void updateColors() {
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            int iconColor = selected ? accent : Theme.getColor(Theme.key_dialogIcon, resourcesProvider);
            int titleColor = selected ? accent : Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
            iconView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
            titleView.setTextColor(titleColor);
            subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            checkView.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            invalidate();
        }

        @Override
        public void addColorKeys(HashSet<Integer> colors) {
            colors.add(Theme.key_windowBackgroundWhiteBlueText4);
            colors.add(Theme.key_dialogIcon);
            colors.add(Theme.key_dialogTextBlack);
            colors.add(Theme.key_dialogTextGray3);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int minH = subtitleView.getVisibility() == VISIBLE ? dp(58) : dp(48);
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(minH + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : null;
                if (paint == null) {
                    paint = Theme.dividerPaint;
                }
                int left = LocaleController.isRTL ? 0 : dp(60);
                int right = getMeasuredWidth() - (LocaleController.isRTL ? dp(60) : 0);
                canvas.drawLine(left, getMeasuredHeight() - 1, right, getMeasuredHeight() - 1, paint);
            }
        }

        static class Factory extends UItem.UItemFactory<QuickActionCell> {
            static {
                setup(new Factory());
            }

            @Override
            public QuickActionCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new QuickActionCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((QuickActionCell) view).bind(item.iconResId, item.text, item.subtext, item.checked, divider);
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return a != null && b != null
                        && a.id == b.id
                        && a.viewType == b.viewType
                        && a.iconResId == b.iconResId
                        && a.checked == b.checked
                        && Objects.equals(a.object, b.object)
                        && TextUtils.equals(a.text, b.text)
                        && TextUtils.equals(a.subtext, b.subtext);
            }

            static UItem of(int id, String actionKey, @DrawableRes int iconRes, CharSequence title, @Nullable CharSequence subtitle, boolean selected) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = actionKey;
                item.iconResId = iconRes;
                item.text = title;
                item.subtext = subtitle;
                item.checked = selected;
                return item;
            }
        }
    }
}
