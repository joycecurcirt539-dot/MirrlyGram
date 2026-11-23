package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.Premium.LimitReachedBottomSheet.TYPE_ACCOUNTS;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyInterfaceConfig;
import android.animation.Animator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.graphics.drawable.shapes.Shape;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.math.MathUtils;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EdgeToEdgeSupportMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FolderDrawable;
import org.telegram.ui.Components.HintsController;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.RenderNodeWithHash;
import org.telegram.ui.Components.blur3.capture.IBlur3Hash;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.GlassContrastHelper;
import org.telegram.utils.glass.GlassEngine;
import org.telegram.utils.glass.GlassSettings;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Wallet.WalletActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class MainTabsActivity extends ViewPagerActivity implements NotificationCenter.NotificationCenterDelegate, FactorAnimator.Target {

    public static final int TABS_COUNT = 4;
    private static final int POSITION_CHATS = 0;
    private static final int POSITION_CONTACTS = 1;
    private static final int POSITION_CALLS_OR_SETTINGS = 2;
    private static final int POSITION_PROFILE = 3;

    private static final int INDEX_CHATS = 0;
    private static final int INDEX_CONTACTS = 1;
    private static final int INDEX_SETTINGS = 2;
    private static final int INDEX_CALLS = 3;
    private static final int INDEX_PROFILE = 4;

    private static int indexToPosition(int index) {
        return index > 2 ? index - 1 : index;
    }

    private static final int ANIMATOR_ID_TABS_VISIBLE = 0;
    private final BoolAnimator animatorTabsVisible = new BoolAnimator(ANIMATOR_ID_TABS_VISIBLE,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 380, true);


    private IUpdateLayout updateLayout;
    private boolean dropCallsFragmentAfterPageScroll;

    private UpdateLayoutWrapper updateLayoutWrapper;
    private FrameLayout tabsViewWrapper;
    private MainTabsLayout tabsView;
    private GlassTabView collapsedCircleTab;
    private boolean isCircleExpanded;
    private boolean pendingOpenSearch;
    private final Rect pillSampleRect = new Rect();
    private BlurredBackgroundDrawable tabsViewBackground;
    private BlurredBackgroundDrawableViewFactory iBlur3FactoryGlass;
    private View fadeView;

    public MainTabsActivity() {
        super();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            iBlur3SourceTabGlass = createTabSource(false);
            iBlur3SourceTabFrosted = createTabSource(true);
        } else {
            iBlur3SourceTabGlass = null;
            iBlur3SourceTabFrosted = null;
        }

        iBlur3SourceColor = new GlassSourceColor();

        Bulletin.Delegate delegate = new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                if (MirrlyInterfaceConfig.isBottomBarCompletelyHidden()) {
                    return navigationBarHeight;
                }
                return navigationBarHeight + dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN);
            }
        };

        Bulletin.addDelegate(this, delegate);
        Bulletin.addDelegate(contentView, delegate);
    }

    /** The pages under the tabs: an intermediate node over one or two of them, their glass or their frosted glass. */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private GlassSourceRenderNode createTabSource(boolean frosted) {
        final GlassSourceRenderNode source = new GlassSourceRenderNode(null);
        source.setupRenderer(new RenderNodeWithHash.Renderer() {
            @Override
            public void renderNodeCalculateHash(IBlur3Hash hash) {
                hash.add(getEstBackgroundColor());
                hash.add(SharedConfig.chatBlurEnabled());
                hash.add(GlassEngine.getGlassSettingsGenerationId());

                for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
                    final FragmentState state = fragmentsArr.valueAt(a);
                    final BaseFragment fragment = state.fragment;
                    if (fragment.fragmentView == null) {
                        continue;
                    }
                    if (!ViewPositionWatcher.computeRectInParent(fragment.fragmentView, contentView, fragmentPosition)) {
                        continue;
                    }
                    if (fragmentPosition.right <= 0 || fragmentPosition.left >= fragmentView.getMeasuredWidth()) {
                        continue;
                    }

                    if (fragment instanceof TabFragmentDelegate) {
                        TabFragmentDelegate delegate = (TabFragmentDelegate) fragment;
                        final GlassSourceRenderNode pageSource = frosted ? delegate.getFrostedGlassSource() : delegate.getGlassSource();
                        if (pageSource != null) {
                            hash.addF(fragmentPosition.left);
                            hash.addF(fragmentPosition.top);
                            hash.add(fragment.getClassGuid());
                        }
                    }
                }
            }

            @Override
            public void renderNodeUpdateDisplayList(Canvas canvas) {
                final int width = fragmentView.getMeasuredWidth();
                final int height = fragmentView.getMeasuredHeight();

                canvas.drawColor(getEstBackgroundColor());

                for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
                    final FragmentState state = fragmentsArr.valueAt(a);
                    final BaseFragment fragment = state.fragment;
                    if (fragment.fragmentView == null) {
                        continue;
                    }
                    if (!ViewPositionWatcher.computeRectInParent(fragment.fragmentView, contentView, fragmentPosition)) {
                        continue;
                    }
                    if (fragmentPosition.right <= 0 || fragmentPosition.left >= fragmentView.getMeasuredWidth()) {
                        continue;
                    }

                    if (fragment instanceof TabFragmentDelegate) {
                        TabFragmentDelegate delegate = (TabFragmentDelegate) fragment;
                        final GlassSourceRenderNode pageSource = frosted ? delegate.getFrostedGlassSource() : delegate.getGlassSource();
                        if (pageSource != null) {
                            canvas.save();
                            canvas.translate(fragmentPosition.left, fragmentPosition.top);
                            pageSource.draw(canvas, 0, 0, width, height);
                            canvas.restore();
                        }
                    }
                }
            }
        });
        return source;
    }

    @Override
    protected FrameLayout createContentView(Context context) {
        return new FrameLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (isCircleExpanded && ev.getAction() == MotionEvent.ACTION_DOWN
                        && tabsView != null && tabsViewWrapper != null
                        && tabsViewWrapper.getVisibility() == View.VISIBLE) {
                    final float localX = ev.getX() - tabsViewWrapper.getX() - tabsView.getX();
                    final float localY = ev.getY() - tabsViewWrapper.getY() - tabsView.getY();
                    if (!tabsView.isPointInsidePill(localX, localY)) {
                        setCircleExpanded(false, true);
                    }
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                checkUi_tabsPosition();
                checkUi_fadeView();
                blur3_updateFragmentsGlass();
                if (changed) {
                    scheduleMainTabsAdaptiveContrastUpdate(true);
                }
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                final int color = getEstBackgroundColor();
                if (insetLeft != 0) {
                    canvas.drawRect(0, 0, insetLeft, getHeight(), Theme.fillingPaint(color));
                }
                if (insetRight != 0) {
                    canvas.drawRect(getWidth() - insetRight, 0, getWidth(), getHeight(), Theme.fillingPaint(color));
                }

                super.dispatchDraw(canvas);
                blur3_invalidateBlur();
                blur3_updateFadeColors();
            }
        };
    }

    private int getEstBackgroundColor() {
        return ColorUtils.blendARGB(
                getThemedColor(Theme.key_windowBackgroundGray),
                getThemedColor(Theme.key_windowBackgroundWhite),
                viewPager != null ? viewPager.getPositionVisibility(0) : 1);
    }

    private boolean tabletLayout;
    public void updateLayout() {
//        if (tabletLayout == AndroidUtilities.isTablet()) return;
//        tabletLayout = AndroidUtilities.isTablet();
//
//        final boolean isUpdateLayoutVisible = updateLayoutWrapper.isUpdateLayoutVisible();
//        final int updateLayoutHeight = isUpdateLayoutVisible ? dp(UpdateLayoutWrapper.HEIGHT) : 0;
//        int bottomMargin = isUpdateLayoutVisible ? (navigationBarHeight + updateLayoutHeight) : 0;
//        if (tabletLayout) {
//            bottomMargin = Math.max(bottomMargin, navigationBarHeight + dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS));
//        }
//        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL);
//        if (tabletLayout) {
//            lp.leftMargin = dp(6);
//            lp.rightMargin = dp(6);
//            lp.topMargin = dp(6);
//        }
//        lp.bottomMargin = bottomMargin;
//
//        viewPager.setLayoutParams(lp);
//        viewPager.setTabletLayout(tabletLayout);
//        checkUi_fadeView();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateLayout();
    }

    @Override
    public void onResume() {
        super.onResume();
        blur3_updateColors();
        checkContactsTabBadge();
        checkUnreadCount(true);
        refreshCustomChatSlotsIfNeeded();

        showAccountChangeHint();
    }

    private void checkContactsTabBadge() {
        if (tabsView != null && tabs != null && tabs[INDEX_CONTACTS] != null) {
            final boolean hasPermission = Build.VERSION.SDK_INT >= 23 && ContactsController.hasContactsPermission();
            if (hasPermission) {
                MessagesController.getGlobalNotificationsSettings().edit().putBoolean("askAboutContacts2", true).apply();
            }
            final boolean contactsIsDefault = MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_CONTACTS) == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT;
            if (contactsIsDefault && Build.VERSION.SDK_INT >= 23 && UserConfig.getInstance(currentAccount).syncContacts && !hasPermission && MessagesController.getGlobalNotificationsSettings().getBoolean("askAboutContacts2", true)) {
                tabs[INDEX_CONTACTS].setCounter("!", true, true);
            } else {
                tabs[INDEX_CONTACTS].setCounter(null, true, true);
            }
        }
        if (collapsedCircleTab != null && MirrlyInterfaceConfig.shouldCollapseToCircle() && !isCircleExpanded) {
            updateCollapsedCircleBadge(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (accountSwitchHint != null) {
            accountSwitchHint.hide();
        }
        if (isCircleExpanded) {
            setCircleExpanded(false, false);
        }
    }

    @Override
    public View createView(Context context) {
        super.createView(context);
        tabletLayout = false;

        tabsView = new MainTabsLayout(context, resourceProvider);
        tabsView.setClipChildren(false);
        tabsView.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4));
        tabsView.setMaxWidth(dp(328 + DialogsActivity.MAIN_TABS_MARGIN * 2));
        tabsView.setOnPillLayoutChangedListener(this::updateAccountSwitchHintJoint);

        tabs = new GlassTabView[5];
        tabs[INDEX_CHATS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CHATS, R.string.MainTabsChats);
        tabs[INDEX_CONTACTS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CONTACTS, R.string.MainTabsContacts);
        tabs[INDEX_SETTINGS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.SETTINGS, R.string.Settings);
        tabs[INDEX_CALLS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CALLS, R.string.MainTabsCalls);
        tabs[INDEX_PROFILE] = GlassTabView.createAvatar(context, resourceProvider, currentAccount, R.string.MainTabsProfile);

        for (int index = 0; index < tabs.length; index++) {
            tabsView.addView(tabs[index]);
            tabsView.setViewVisible(tabs[index], false, false);
        }

        collapsedCircleTab = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CHATS, R.string.MainTabsChats);
        collapsedCircleTab.setCircleCompactMode(true);
        collapsedCircleTab.setContentDescription(getString(R.string.MirrlyInterfaceBarAccessibilityCircle));
        ScaleStateListAnimator.apply(collapsedCircleTab, 0.1f, 1.5f);
        collapsedCircleTab.setOnClickListener(v -> {
            AndroidUtilities.vibrateCursor(v);
            if (tabsView != null) {
                tabsView.pulseBounce();
            }
            setCircleExpanded(true, true);
        });
        collapsedCircleTab.setOnLongClickListener(this::openQuickCustomizeShortcut);
        tabsView.addView(collapsedCircleTab);
        tabsView.addTabToIgnoreClick(collapsedCircleTab);
        tabsView.setViewVisible(collapsedCircleTab, false, false);

        iBlur3SourceColor.setColor(getEstBackgroundColor());

        final ViewPositionWatcher viewPositionWatcher = new ViewPositionWatcher(contentView);

        iBlur3FactoryGlass = new BlurredBackgroundDrawableViewFactory(
            iBlur3SourceTabGlass != null ? iBlur3SourceTabGlass : iBlur3SourceColor,
            iBlur3SourceTabFrosted != null ? iBlur3SourceTabFrosted : iBlur3SourceColor,
            iBlur3SourceColor);
        iBlur3FactoryGlass.setSourceRootView(viewPositionWatcher, contentView);
        iBlur3FactoryGlass.setLiquidGlassEffectAllowed(true);

        tabsViewBackground = iBlur3FactoryGlass.create(tabsView, BlurredBackgroundProviderImpl.mainTabs(resourceProvider));
        tabsViewBackground.setRadius(dp(DialogsActivity.MAIN_TABS_HEIGHT / 2f));
        tabsViewBackground.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN - 0.334f));
        tabsView.setBlurredBackgroundDrawable(tabsViewBackground);

        BlurredBackgroundDrawableViewFactory iBlur3FactoryFade = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
        iBlur3FactoryFade.setSourceRootView(viewPositionWatcher, contentView);

        fadeView = new View(context);
        BlurredBackgroundWithFadeDrawable fadeDrawable = new BlurredBackgroundWithFadeDrawable(iBlur3FactoryFade.create(fadeView, null));
        fadeDrawable.setFadeHeight(dp(60), true);
        fadeView.setBackground(fadeDrawable);

        contentView.addView(fadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM));

        tabsViewWrapper = new FrameLayout(context);
        tabsViewWrapper.setClipChildren(false);
        tabsViewWrapper.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        tabsViewWrapper.setClipToPadding(false);
        contentView.addView(tabsViewWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        updateLayoutWrapper = new UpdateLayoutWrapper(context);
        contentView.addView(updateLayoutWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        updateLayout = ApplicationLoader.applicationLoaderInstance.takeUpdateLayout(getParentActivity(), updateLayoutWrapper);
        if (updateLayout != null) {
            updateLayout.updateAppUpdateViews(currentAccount, false);
        }

        syncBottomBarConfig(false);
        updateLayout();
        checkUnreadCount(false);
        return contentView;
    }

    private void checkUnreadCount(boolean animated) {
        if (tabsView == null || tabs == null || tabs[INDEX_CHATS] == null) {
            return;
        }

        final int unreadCount = MessagesStorage.getInstance(currentAccount).getMainUnreadCount();
        final boolean chatsIsDefault = MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_CHATS) == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT;
        if (chatsIsDefault && unreadCount > 0) {
            final String unreadCountFmt = LocaleController.formatNumber(unreadCount, ',');
            tabs[INDEX_CHATS].setCounter(unreadCountFmt, false, animated);
        } else {
            tabs[INDEX_CHATS].setCounter(null, false, animated);
        }
        if (collapsedCircleTab != null && MirrlyInterfaceConfig.shouldCollapseToCircle() && !isCircleExpanded) {
            updateCollapsedCircleBadge(animated);
        }
    }

    private Drawable createTabScrimBackground() {
        return createTabScrimBackground(tabsView);
    }

    private Drawable createTabScrimBackground(View view) {
        final Theme.ResourcesProvider rp = getResourceProvider();
        final boolean isDark = rp != null ? rp.isDark() : Theme.isCurrentThemeDark();
        final boolean blurEnabled = BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, rp);
        if (blurEnabled && iBlur3FactoryGlass != null) {
            return iBlur3FactoryGlass.create(view != null ? view : tabsView, BlurredBackgroundProviderImpl.scrimMenuBackground(rp))
                    .setRadius(dp(28))
                    .setPadding(dp(8));
        }
        final int baseColor = getThemedColor(Theme.key_windowBackgroundWhite);
        final int fillColor = blurEnabled
                ? Theme.multAlpha(baseColor, MirrlyConfig.getEffectiveGlassAlpha(isDark ? 0.80f : 0.78f))
                : baseColor;
        final float rad = dp(28);
        final float[] radii = new float[]{rad, rad, rad, rad, rad, rad, rad, rad};
        final int rimColor = isDark ? 0x22FFFFFF : 0x18000000;
        final float rimWidth = dpf2(0.55f) * MirrlyConfig.getGlassRimScale();
        final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(rimWidth);
        rimPaint.setColor(rimColor);

        final ShapeDrawable bg = new ShapeDrawable(new RoundRectShape(radii, null, null)) {
            private int currentAlpha = 255;

            @Override
            public void setAlpha(int alpha) {
                currentAlpha = alpha;
                super.setAlpha(alpha);
            }

            @Override
            protected void onDraw(Shape shape, Canvas canvas, Paint paint) {
                super.onDraw(shape, canvas, paint);
                if (blurEnabled && rimWidth > 0 && currentAlpha > 0) {
                    rimPaint.setColor(Theme.multAlpha(rimColor, currentAlpha / 255f));
                    final Rect b = getBounds();
                    final float inset = rimWidth * 0.5f;
                    AndroidUtilities.rectTmp.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, rad - inset, rad - inset, rimPaint);
                }
            }
        };
        bg.getPaint().setColor(fillColor);
        bg.getPaint().setShadowLayer(dp(6), 0, dp(1), Theme.multAlpha(0xFF000000, isDark ? 0.28f : 0.14f));
        return bg;
    }

    private void applyTabScrimBackground(ItemOptions o, View anchor) {
        final Drawable bg = createTabScrimBackground(anchor);
        if (bg instanceof BlurredBackgroundDrawable) {
            o.setViewAdditionalOffsets(dp(8), dp(8), dp(8), dp(8));
        }
        o.setScrimViewBackground(bg);
    }

    public boolean openContactsSelector(View anchor) {
        if (getContext() == null || getParentActivity() == null) return false;
        final ItemOptions o = ItemOptions.makeOptions(this, anchor);
        o.add(R.drawable.msg_contact_add, getString(R.string.NewContact), () -> {
            new NewContactBottomSheet(this, getContext()).show();
        });
        o.add(R.drawable.msg_calls, getString(R.string.VoipChatRecentCalls), () -> {
            Bundle args = new Bundle();
            args.putBoolean("needFinishFragment", false);
            presentFragment(new CallLogActivity(args));
        });
        o.setBlur(true);
        o.translate(0, -dp(4));
        o.setGravity(Gravity.LEFT);
        applyTabScrimBackground(o, anchor);
        o.show();
        return true;
    }

    public boolean openCallsSelector(View anchor) {
        if (getContext() == null || getParentActivity() == null) return false;
        final ItemOptions o = ItemOptions.makeOptions(this, anchor);
        o.add(R.drawable.menu_call_create, getString(R.string.GroupCallCreate2), () -> CallLogActivity.openCreateCall(this));
        if (getUserConfig().showCallsTab) {
            o.add(R.drawable.msg_archive_hide, getString(R.string.HideCallTab), () -> {
                getUserConfig().setShowCallsTab(false);
                checkUi_callTabVisible(false, true);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.callTabsVisibleToggled);
            });
        } else {
            o.add(R.drawable.menu_add_tab_24, getString(R.string.GroupCallShowInMainTabs), () -> {
                getUserConfig().setShowCallsTab(true);
                checkUi_callTabVisible(true, true);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.callTabsVisibleToggled);
            });
        }
        o.setBlur(true);
        o.translate(0, -dp(4));
        applyTabScrimBackground(o, anchor);
        o.show();
        return true;
    }

    private Integer pendingFolderId;

    private boolean openFoldersSelector(View anchor) {
        if (getContext() == null || getParentActivity() == null) return false;
        final ArrayList<MessagesController.DialogFilter> filters = getMessagesController().getDialogFilters();
        if (filters == null || filters.size() <= 1) return false;

        final ItemOptions o = ItemOptions.makeOptions(this, anchor);
        for (int i = 0; i < filters.size(); i++) {
            final MessagesController.DialogFilter folder = filters.get(i);
            final ActionBarMenuSubItem folderItem = new ActionBarMenuSubItem(getParentActivity(), 2, false, false, getResourceProvider());
            folderItem.setPadding(dp(18), 0, dp(18), 0);
            CharSequence title = folder.isDefault() ? getString(R.string.FilterAllChats) : folder.name;
            title = Emoji.replaceEmoji(title, folderItem.getTextView().getPaint().getFontMetricsInt(), false);
            if (!folder.isDefault()) {
                title = MessageObject.replaceAnimatedEmoji(title, folder.entities, folderItem.getTextView().getPaint().getFontMetricsInt());
            }
            final int unreadCount = folder.isDefault()
                    ? MessagesStorage.getInstance(currentAccount).getMainUnreadCount()
                    : folder.unreadCount;
            if (unreadCount > 0) {
                final SpannableStringBuilder titleWithCounter = new SpannableStringBuilder(title);
                final int counterStart = titleWithCounter.length();
                titleWithCounter.append(String.valueOf(unreadCount));
                titleWithCounter.setSpan(
                        new FolderCounterSpan(unreadCount, hasUnmutedUnreadDialogs(folder)),
                        counterStart,
                        titleWithCounter.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                title = titleWithCounter;
                folderItem.setContentDescription(
                        TextUtils.concat(
                                folder.isDefault() ? getString(R.string.FilterAllChats) : folder.name,
                                "\n",
                                LocaleController.formatPluralString("AccDescrUnreadCount", unreadCount)
                        )
                );
            }
            folderItem.setEmojiCacheType(folder.title_noanimate ? AnimatedEmojiDrawable.CACHE_TYPE_NOANIMATE_FOLDER : AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES);
            final int color = getMessagesController().folderTags ? folder.color : -1;
            folderItem.setTextAndIcon(title, 0, new FolderDrawable(getContext(), R.drawable.msg_folders, color));
            folderItem.getTextView().setEmojiColor(getThemedColor(Theme.key_featuredStickers_addButton));
            folderItem.setMinimumWidth(160);
            folderItem.setOnClickListener(e -> {
                o.dismiss();
                openFolder(folder.id);
            });
            o.addView(folderItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        o.setBlur(true);
        o.translate(-dp(8), -dp(4));
        o.setMaxHeight(dp(400));
        applyTabScrimBackground(o, anchor);
        o.setGravity(Gravity.LEFT);
        o.show();

        return true;
    }

    private boolean hasUnmutedUnreadDialogs(MessagesController.DialogFilter folder) {
        final MessagesController messagesController = getMessagesController();
        final ArrayList<TLRPC.Dialog> dialogs = folder.isDefault()
                ? messagesController.getDialogs(0)
                : messagesController.getAllDialogs();
        for (int i = 0; i < dialogs.size(); i++) {
            final TLRPC.Dialog dialog = dialogs.get(i);
            if (!folder.isDefault()) {
                long dialogId = dialog.id;
                if (DialogObject.isEncryptedDialog(dialogId)) {
                    final TLRPC.EncryptedChat encryptedChat = messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
                    if (encryptedChat != null) {
                        dialogId = encryptedChat.user_id;
                    }
                }
                if (!folder.includesDialog(getAccountInstance(), dialogId, dialog)) {
                    continue;
                }
            }
            if ((messagesController.getDialogUnreadCount(dialog) > 0 || dialog.unread_mark)
                    && !messagesController.isDialogMuted(dialog.id, 0)) {
                return true;
            }
        }
        return false;
    }

    private class FolderCounterSpan extends ReplacementSpan {

        private static final float HEIGHT_DP = 17.333f;
        private final String count;
        private final boolean hasUnmutedUnreadDialogs;
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float counterWidth;

        FolderCounterSpan(int count, boolean hasUnmutedUnreadDialogs) {
            this.count = String.valueOf(count);
            this.hasUnmutedUnreadDialogs = hasUnmutedUnreadDialogs;
            textPaint.setTextSize(AndroidUtilities.dpf2(11));
            textPaint.setTypeface(AndroidUtilities.bold());
            counterWidth = Math.max(dp(HEIGHT_DP - 10), textPaint.measureText(this.count)) + dp(10);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            return (int) Math.ceil(dp(5) + counterWidth);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            final float left = x + dp(5);
            final float centerY = (top + bottom) / 2f + dp(1);
            final float halfHeight = dp(HEIGHT_DP) / 2f;
            backgroundPaint.setColor(getThemedColor(
                hasUnmutedUnreadDialogs ?
                    Theme.key_featuredStickers_addButton :
                    Theme.key_chats_tabUnreadUnactiveBackground
            ));
            textPaint.setColor(getThemedColor(Theme.key_actionBarDefault));
            AndroidUtilities.rectTmp.set(left, centerY - halfHeight, left + counterWidth, centerY + halfHeight);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, halfHeight, halfHeight, backgroundPaint);
            final Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            final float baseline = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            canvas.drawText(count, left + (counterWidth - textPaint.measureText(count)) / 2f, baseline, textPaint);
        }
    }

    private void openFolder(int folderId) {
        if (viewPager.getCurrentPosition() == POSITION_CHATS && dialogsActivity != null) {
            dialogsActivity.scrollToFolder(folderId);
        } else {
            if (dialogsActivity == null) {
                prepareDialogsActivity(null);
            }
            pendingFolderId = folderId;
            selectTab(POSITION_CHATS, true);
            viewPager.scrollToPosition(POSITION_CHATS);
        }
    }

    public boolean openAccountSelector(View button) {
        final ArrayList<Integer> accountNumbers = new ArrayList<>();

        accountNumbers.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accountNumbers.add(a);
            }
        }
        Collections.sort(accountNumbers, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });

        ItemOptions o = ItemOptions.makeOptions(this, button);
        if (UserConfig.getActivatedAccountsCount() < UserConfig.MAX_ACCOUNT_COUNT) {
            o.add(R.drawable.msg_addbot, getString(R.string.AddAccount), () -> {
                int freeAccounts = 0;
                Integer availableAccount = null;
                for (int a = UserConfig.MAX_ACCOUNT_COUNT - 1; a >= 0; a--) {
                    if (!UserConfig.getInstance(a).isClientActivated()) {
                        freeAccounts++;
                        if (availableAccount == null) {
                            availableAccount = a;
                        }
                    }
                }
                if (!UserConfig.hasPremiumOnAccounts()) {
                    freeAccounts -= (UserConfig.MAX_ACCOUNT_COUNT - UserConfig.MAX_ACCOUNT_DEFAULT_COUNT);
                }
                if (freeAccounts > 0 && availableAccount != null) {
                    presentFragment(new LoginActivity(availableAccount));
                } else if (!UserConfig.hasPremiumOnAccounts()) {
                    showDialog(new LimitReachedBottomSheet(this, getContext(), TYPE_ACCOUNTS, currentAccount, null));
                }
            });
        }

        if (BuildConfig.DEBUG_PRIVATE_VERSION) {
            o.add(R.drawable.menu_download_round, "Dump Canvas", () -> AndroidUtilities.runOnUIThread(this::dumpCanvas, 1000));
        }

        if (accountNumbers.size() > 0) {
            if (o.getItemsCount() > 0) o.addGap();
            for (int acc : accountNumbers) {
                final int account = acc;
                final View btn = accountView(acc, currentAccount == acc);
                btn.setOnClickListener(v -> {
                    if (currentAccount == account) return;
                    o.dismiss();
                    if (LaunchActivity.instance != null) {
                        LaunchActivity.instance.switchToAccount(account, true);
                    }
                });
                o.addView(btn, LayoutHelper.createLinear(230, 48));
            }
        }

        o.setBlur(true);
        o.translate(0, -dp(4));
        applyTabScrimBackground(o, button);
        o.show();

        HintsController.Hint.AccountSwitchHint.doNotShowAgain();

        return true;
    }

    public LinearLayout accountView(int account, boolean selected) {
        final LinearLayout btn = new LinearLayout(getContext());
        btn.setOrientation(LinearLayout.HORIZONTAL);
        ThemeBind.selectorBackground(btn, Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 0, 0), null, ThemeBind.of(Theme.key_listSelector), getResourceProvider());

        final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();

        final AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(user);

        final FrameLayout avatarContainer = new FrameLayout(getContext()) {
            private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                if (selected) {
                    selectedPaint.setStyle(Paint.Style.STROKE);
                    selectedPaint.setStrokeWidth(dp(1.33f));
                    selectedPaint.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
                    canvas.drawCircle(getWidth() / 2.0f, getHeight() / 2.0f, dp(16), selectedPaint);
                }
                super.dispatchDraw(canvas);
            }
        };
        btn.addView(avatarContainer, LayoutHelper.createLinear(34, 34, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        final BackupImageView avatarView = new BackupImageView(getContext());
        if (selected) {
            avatarView.setScaleX(0.833f);
            avatarView.setScaleY(0.833f);
        }
        avatarView.setRoundRadius(dp(16));
        avatarView.getImageReceiver().setCurrentAccount(account);
        avatarView.setForUserOrChat(user, avatarDrawable);
        avatarContainer.addView(avatarView, LayoutHelper.createLinear(32, 32, Gravity.CENTER, 1, 1, 1, 1));

        final TextView textView = new TextView(getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        ThemeBind.textColor(textView, Theme.key_dialogTextBlack, getResourceProvider());
        textView.setText(UserObject.getUserName(user));
        textView.setMaxLines(2);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        btn.addView(textView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 13, 0, 14, 0));

        return btn;
    }

    @Override
    protected void onViewPagerScrollEnd() {
        if (tabsView != null) {
            selectTab(viewPager.getCurrentPosition(), true);
            setGestureSelectedOverride(0, false);
        }
        blur3_invalidateBlur();
        scheduleMainTabsAdaptiveContrastUpdate(true);

        if (viewPager != null) {
            final int currentPosition = viewPager.getCurrentPosition();
            if (currentPosition != POSITION_CALLS_OR_SETTINGS && dropCallsFragmentAfterPageScroll) {
                dropFragmentAtPosition(POSITION_CALLS_OR_SETTINGS);
                dropCallsFragmentAfterPageScroll = false;
            }
            if (currentPosition != POSITION_PROFILE) {
                dropFragmentAtPosition(POSITION_PROFILE);
            }
            if (pendingFolderId != null && currentPosition == POSITION_CHATS && dialogsActivity != null) {
                dialogsActivity.scrollToFolder(pendingFolderId);
                pendingFolderId = null;
            }
            if (pendingOpenSearch && currentPosition == POSITION_CHATS && dialogsActivity != null) {
                pendingOpenSearch = false;
                dialogsActivity.openSearchFromTabs();
            }
        }

    }

    @Override
    protected void onViewPagerTabAnimationUpdate(boolean manual) {
        final boolean isDragByGesture = !manual;

        if (tabsView != null) {
            final float position = viewPager.getPositionAnimated();
            setGestureSelectedOverride(position, isDragByGesture);
            if (isDragByGesture) {
                selectTab(Math.round(position), true);
            }
        }

        checkUi_fadeView();
        blur3_invalidateBlur();
        contentView.invalidate();
        scheduleMainTabsAdaptiveContrastUpdate(false);
    }


    @Override
    protected int getFragmentsCount() {
        return TABS_COUNT;
    }

    @Override
    protected int getStartPosition() {
        for (int pos = 0; pos < MirrlyInterfaceConfig.SLOT_COUNT; pos++) {
            if (MirrlyInterfaceConfig.getSlotMode(pos) == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT) {
                return pos;
            }
        }
        return POSITION_CHATS;
    }

    private static boolean isNativeTabSlot(int position) {
        if (position < 0 || position >= MirrlyInterfaceConfig.SLOT_COUNT) {
            return false;
        }
        return MirrlyInterfaceConfig.getSlotMode(position) == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT;
    }

    private DialogsActivity dialogsActivity;

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (isCircleExpanded) {
            if (invoked) {
                setCircleExpanded(false, true);
            }
            return false;
        }
        final boolean result = super.onBackPressed(invoked);
        if (result) {
            final int startPosition = getStartPosition();
            if (viewPager.getCurrentPosition() != startPosition) {
                if (invoked) {
                    viewPager.scrollToPosition(startPosition);
                }
                return false;
            }
        }
        return result;
    }

    public DialogsActivity prepareDialogsActivity(Bundle bundle) {
        if (bundle == null) {
            bundle = new Bundle();
        }

        bundle.putBoolean("hasMainTabs", true);
        dialogsActivity = new DialogsActivity(bundle);
        dialogsActivity.setMainTabsActivityController(new MainTabsActivityControllerImpl());
        putFragmentAtPosition(POSITION_CHATS, dialogsActivity);
        return dialogsActivity;
    }

    @Override
    protected BaseFragment createBaseFragmentAt(int position) {
        if (position == POSITION_CONTACTS) {
            Bundle args = new Bundle();
            args.putBoolean("needPhonebook", true);
            args.putBoolean("needFinishFragment", false);
            args.putBoolean("hasMainTabs", true);
            return new ContactsActivity(args);
        } else if (position == POSITION_CALLS_OR_SETTINGS) {
            if (getUserConfig().showCallsTab) {
                Bundle args = new Bundle();
                args.putBoolean("needFinishFragment", false);
                args.putBoolean("hasMainTabs", true);
                return new CallLogActivity(args);
            }
            Bundle args = new Bundle();
            args.putBoolean("hasMainTabs", true);
            return new SettingsActivity(args);
        } else if (position == POSITION_CHATS) {
            Bundle args = new Bundle();
            args.putBoolean("hasMainTabs", true);
            dialogsActivity = new DialogsActivity(args);
            dialogsActivity.setMainTabsActivityController(new MainTabsActivityControllerImpl());
            return dialogsActivity;
        } else if (position == POSITION_PROFILE) {
            Bundle args = new Bundle();
            args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
            args.putBoolean("my_profile", true);
            // args.putBoolean("expandPhoto", true);
            args.putBoolean("hasMainTabs", true);
            return new ProfileActivity(args);
        }
        return null;
    }

    public DialogsActivity getDialogsActivity() {
        return dialogsActivity;
    }

    /* */

    public GlassTabView[] tabs;

    public void selectTab(int position, boolean animated) {
        for (int a = 0; a < tabs.length; a++) {
            GlassTabView tab = tabs[a];
            final int tabPosition = indexToPosition(a);
            tab.setSelected(isNativeTabSlot(tabPosition) && tabPosition == position, animated);
        }
        if (collapsedCircleTab != null && MirrlyInterfaceConfig.shouldCollapseToCircle() && !isCircleExpanded) {
            updateCollapsedCircleContent(getUserConfig().showCallsTab, animated);
        }
    }

    public void setGestureSelectedOverride(float animatedPosition, boolean allow) {
        for (int index = 0; index < tabs.length; index++) {
            final int position = indexToPosition(index);
            if (isNativeTabSlot(position)) {
                final float visibility = Math.max(0, 1f - Math.abs(position - animatedPosition));
                tabs[index].setGestureSelectedOverride(visibility, allow);
            } else {
                tabs[index].setGestureSelectedOverride(0f, false);
            }
        }
        tabsView.invalidate();
    }


    /* * */

    public interface TabFragmentDelegate {
        default boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
            return false;
        }

        default void onParentScrollToTop() {

        }

        default GlassSourceRenderNode getGlassSource() {
            return null;
        }

        default GlassSourceRenderNode getFrostedGlassSource() {
            return null;
        }
    }

    @Override
    protected boolean canScrollForward(MotionEvent ev) {
        return canScrollInternal(ev, true);
    }

    @Override
    protected boolean canScrollBackward(MotionEvent ev) {
        return canScrollInternal(ev, false);
    }

    private boolean canScrollInternal(MotionEvent ev, boolean forward) {
        if (viewPager != null) {
            final int targetPosition = viewPager.getCurrentPosition() + (forward ? 1 : -1);
            if (targetPosition < 0 || targetPosition >= TABS_COUNT || !isNativeTabSlot(targetPosition)) {
                return false;
            }
        }
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment instanceof TabFragmentDelegate) {
            final TabFragmentDelegate delegate = (TabFragmentDelegate) fragment;
            return delegate.canParentTabsSlide(ev, forward);

        }

        return false;
    }


    /* * */

    private int navigationBarHeight;
    private int insetLeft;
    private int insetRight;

    @NonNull
    @Override
    protected WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);

        insetLeft = systemInsets.left;
        insetRight = systemInsets.right;

        navigationBarHeight = systemInsets.bottom;
        final boolean isUpdateLayoutVisible = updateLayoutWrapper.isUpdateLayoutVisible();
        final int updateLayoutHeight = isUpdateLayoutVisible ? dp(UpdateLayoutWrapper.HEIGHT) : 0;
        updateLayoutWrapper.setPadding(0, 0, 0, navigationBarHeight);

        ViewGroup.MarginLayoutParams lp;
        {
            final int height = navigationBarHeight + updateLayoutHeight + dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS);
            lp = (ViewGroup.MarginLayoutParams) fadeView.getLayoutParams();
            if (lp.height != height) {
                lp.height = height;
                fadeView.setLayoutParams(lp);
            }
        }
        {
            int bottomMargin = isUpdateLayoutVisible ? (navigationBarHeight + updateLayoutHeight) : 0;
            if (tabletLayout && !MirrlyInterfaceConfig.isBottomBarCompletelyHidden()) {
                bottomMargin = Math.max(bottomMargin, navigationBarHeight + dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS));
            }
            lp = (ViewGroup.MarginLayoutParams) viewPager.getLayoutParams();
            if (lp.bottomMargin != bottomMargin || lp.leftMargin != systemInsets.left || lp.rightMargin != systemInsets.right) {
                lp.leftMargin = systemInsets.left;
                lp.rightMargin = systemInsets.right;
                lp.bottomMargin = bottomMargin;
                viewPager.setLayoutParams(lp);
            }
        }

        tabsViewWrapper.setPadding(systemInsets.left, 0, systemInsets.right, navigationBarHeight);

        final WindowInsetsCompat consumed = isUpdateLayoutVisible ?
            insets.inset(0, 0, 0, navigationBarHeight) : insets;

        checkUi_tabsPosition();
        checkUi_fadeView();

        return super.onApplyWindowInsets(v, consumed);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.notificationsCountUpdated || id == NotificationCenter.updateInterfaces) {
            checkUnreadCount(fragmentView != null && fragmentView.isAttachedToWindow());
            if (id == NotificationCenter.updateInterfaces && args != null && args.length > 0 && args[0] instanceof Integer) {
                final int mask = (Integer) args[0];
                if ((mask & (MessagesController.UPDATE_MASK_AVATAR
                        | MessagesController.UPDATE_MASK_NAME
                        | MessagesController.UPDATE_MASK_CHAT_AVATAR
                        | MessagesController.UPDATE_MASK_CHAT_NAME)) != 0) {
                    refreshCustomChatSlotsIfNeeded();
                }
            }
        } else if (id == NotificationCenter.appUpdateLoading) {
            if (updateLayout != null) {
                updateLayout.updateFileProgress(null);
                updateLayout.updateAppUpdateViews(currentAccount, true);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            String path = (String) args[0];
            if (SharedConfig.isAppUpdateAvailable()) {
                String name = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
                if (name.equals(path) && updateLayout != null) {
                    updateLayout.updateAppUpdateViews(currentAccount, true);
                }
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            String path = (String) args[0];
            if (SharedConfig.isAppUpdateAvailable()) {
                String name = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
                if (name.equals(path) && updateLayout != null) {
                    updateLayout.updateAppUpdateViews(currentAccount, true);
                }
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            if (updateLayout != null) {
                updateLayout.updateFileProgress(args);
            }
        } else if (id == NotificationCenter.appUpdateAvailable) {
            if (updateLayout != null && LaunchActivity.instance != null) {
                updateLayout.updateAppUpdateViews(currentAccount, LaunchActivity.instance.getMainFragmentsStackSize() == 1);
            }
        } else if (id == NotificationCenter.needSetDayNightTheme) {
            clearAllHiddenFragments();
        } else if (id == NotificationCenter.didSetNewTheme) {
            blur3_updateColors();
            refreshThemeQuickActionsIfNeeded();
        } else if (id == NotificationCenter.dialogsNeedReload) {
            refreshCustomChatSlotsIfNeeded();
        } else if (id == NotificationCenter.callTabsVisibleToggled) {
            final boolean callTabsVisible = getUserConfig().showCallsTab;
            final boolean slot2IsDefault = MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_SETTINGS_OR_CALLS) == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT;
            checkUi_callTabVisible(callTabsVisible, true);
            if (slot2IsDefault && viewPager != null && viewPager.getCurrentPosition() == POSITION_CALLS_OR_SETTINGS) {
                final int target = getStartPosition();
                viewPager.scrollToPosition(target);
                selectTab(target, true);
                dropCallsFragmentAfterPageScroll = true;
            } else {
                dropFragmentAtPosition(POSITION_CALLS_OR_SETTINGS);
            }
        } else if (id == NotificationCenter.mainUserInfoChanged) {
            if (tabs != null && tabs[INDEX_PROFILE] != null
                    && MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_PROFILE) == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT) {
                tabs[INDEX_PROFILE].updateUserAvatar(currentAccount);
            }
            refreshCustomChatSlotsIfNeeded();
            if (collapsedCircleTab != null && MirrlyInterfaceConfig.shouldCollapseToCircle() && !isCircleExpanded) {
                updateCollapsedCircleContent(getUserConfig().showCallsTab, false);
            }
        } else if (id == NotificationCenter.contactsPermissionBadgeCheck) {
            checkContactsTabBadge();
        } else if (id == NotificationCenter.mirrlyGlassSettingsDidChange) {
            blur3_onFragmentGlassCaptured();
            blur3_updateColors();
            if (tabsViewWrapper != null) {
                tabsViewWrapper.invalidate();
            }
        } else if (id == NotificationCenter.mirrlyBottomBarConfigChanged) {
            syncBottomBarConfig(fragmentView != null && fragmentView.isAttachedToWindow());
            blur3_updateFragmentsGlass();
        }
    }

    private NotificationCenter.ObserversGroup observersGroup;

    @Override
    public boolean onFragmentCreate() {
        observersGroup = NotificationCenter.getInstance(currentAccount)
            .createObserversGroup(this)
            .add(NotificationCenter.fileLoaded)
            .add(NotificationCenter.fileLoadProgressChanged)
            .add(NotificationCenter.fileLoadFailed)
            .add(NotificationCenter.notificationsCountUpdated)
            .add(NotificationCenter.updateInterfaces)
            .add(NotificationCenter.callTabsVisibleToggled)
            .add(NotificationCenter.mainUserInfoChanged)
            .add(NotificationCenter.contactsPermissionBadgeCheck)
            .add(NotificationCenter.dialogsNeedReload)
            .addGlobal(NotificationCenter.appUpdateAvailable)
            .addGlobal(NotificationCenter.appUpdateLoading)
            .addGlobal(NotificationCenter.needSetDayNightTheme)
            .addGlobal(NotificationCenter.didSetNewTheme)
            .addGlobal(NotificationCenter.mirrlyGlassSettingsDidChange)
            .addGlobal(NotificationCenter.mirrlyBottomBarConfigChanged);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        cancelMainTabsAdaptiveTick();
        Bulletin.removeDelegate(this);
        Bulletin.removeDelegate(contentView);

        if (observersGroup != null) {
            observersGroup.removeAllObservers();
            observersGroup = null;
        }

        super.onFragmentDestroy();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_TABS_VISIBLE) {
            checkUi_tabsPosition();
            checkUi_fadeView();
        }
    }

    private void checkUi_fadeView() {
        if (viewPager == null || fadeView == null) {
            return;
        }
        if (MirrlyInterfaceConfig.isBottomBarCompletelyHidden()) {
            fadeView.setAlpha(0f);
            fadeView.setVisibility(View.GONE);
            return;
        }

        final float animatedPosition = viewPager.getPositionAnimated();
        final float isProfile = 1f - MathUtils.clamp(Math.abs(POSITION_PROFILE - animatedPosition), 0, 1);
        final float hide = 1f - AndroidUtilities.getNavigationBarThirdButtonsFactor(0, 1f, navigationBarHeight);
        float alpha = (1f - isProfile * hide) * animatorTabsVisible.getFloatValue();
        if (tabletLayout) {
            alpha = 0.0f;
        }

        fadeView.setAlpha(alpha);
        fadeView.setTranslationY(isProfile * dp(48));
        fadeView.setVisibility(alpha > 0 ? View.VISIBLE : View.GONE);
    }

    private void checkUi_tabsPosition() {
        if (tabsViewWrapper == null || tabsView == null || updateLayoutWrapper == null) {
            return;
        }
        if (MirrlyInterfaceConfig.isBottomBarCompletelyHidden()) {
            tabsViewWrapper.setVisibility(View.GONE);
            tabsView.setVisibility(View.GONE);
            return;
        }

        final boolean isUpdateLayoutVisible = updateLayoutWrapper.isUpdateLayoutVisible();
        final int updateLayoutHeight = isUpdateLayoutVisible ? dp(UpdateLayoutWrapper.HEIGHT) : 0;
        final int normalY = -(updateLayoutHeight);
        final int hiddenY = normalY + dp(40);

        final float factor = animatorTabsVisible.getFloatValue();
        final float scale = lerp(0.85f, 1f, factor);

        tabsViewWrapper.setTranslationY(lerp(hiddenY, normalY, factor));
        tabsViewWrapper.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);
        tabsView.setClickable(factor > 1);
        tabsView.setEnabled(factor > 1);
        tabsView.setAlpha(factor);
        tabsView.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);
    }

    private void checkUi_callTabVisible(boolean callTabsVisible, boolean animated) {
        syncBottomBarConfig(animated);
    }

    private void setCircleExpanded(boolean expanded, boolean animated) {
        final boolean shouldCollapse = MirrlyInterfaceConfig.shouldCollapseToCircle();
        final boolean target = shouldCollapse && expanded;
        if (isCircleExpanded == target && animated) {
            return;
        }
        isCircleExpanded = target;
        syncBottomBarConfig(animated);
    }

    private void syncBottomBarConfig(boolean animated) {
        if (tabsView == null || tabs == null) {
            return;
        }
        final boolean allHidden = MirrlyInterfaceConfig.isBottomBarCompletelyHidden();
        final boolean shouldCollapse = MirrlyInterfaceConfig.shouldCollapseToCircle();
        if (!shouldCollapse || allHidden) {
            isCircleExpanded = false;
        }
        final boolean showCalls = getUserConfig().showCallsTab;

        // Slot 0: Chats
        final int slot0Mode = MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_CHATS);
        if (slot0Mode == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT) {
            tabs[INDEX_CHATS].restoreMainTab(GlassTabView.TabAnimation.CHATS, getString(R.string.MainTabsChats));
            tabs[INDEX_CHATS].setContentDescription(getString(R.string.MainTabsChats));
            tabs[INDEX_CHATS].setOnClickListener(createNativeTabClickListener(POSITION_CHATS));
            tabs[INDEX_CHATS].setOnLongClickListener(this::openFoldersSelector);
            tabsView.addTabToIgnoreClick(tabs[INDEX_CHATS]);
        } else if (slot0Mode == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM) {
            bindCustomSlot(tabs[INDEX_CHATS], MirrlyInterfaceConfig.SLOT_CHATS);
        } else {
            tabs[INDEX_CHATS].setCounter(null, false, false);
        }

        // Slot 1: Contacts
        final int slot1Mode = MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_CONTACTS);
        if (slot1Mode == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT) {
            tabs[INDEX_CONTACTS].restoreMainTab(GlassTabView.TabAnimation.CONTACTS, getString(R.string.MainTabsContacts));
            tabs[INDEX_CONTACTS].setContentDescription(getString(R.string.MainTabsContacts));
            tabs[INDEX_CONTACTS].setOnClickListener(createNativeTabClickListener(POSITION_CONTACTS));
            tabs[INDEX_CONTACTS].setOnLongClickListener(this::openContactsSelector);
            tabsView.addTabToIgnoreClick(tabs[INDEX_CONTACTS]);
        } else if (slot1Mode == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM) {
            bindCustomSlot(tabs[INDEX_CONTACTS], MirrlyInterfaceConfig.SLOT_CONTACTS);
        } else {
            tabs[INDEX_CONTACTS].setCounter(null, false, false);
        }

        // Slot 2: Settings or Calls
        final int slot2Mode = MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_SETTINGS_OR_CALLS);
        final GlassTabView activeSlot2 = showCalls ? tabs[INDEX_CALLS] : tabs[INDEX_SETTINGS];
        final GlassTabView inactiveSlot2 = showCalls ? tabs[INDEX_SETTINGS] : tabs[INDEX_CALLS];
        inactiveSlot2.setCounter(null, false, false);
        if (slot2Mode == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT) {
            if (showCalls) {
                tabs[INDEX_CALLS].restoreMainTab(GlassTabView.TabAnimation.CALLS, getString(R.string.MainTabsCalls));
                tabs[INDEX_CALLS].setContentDescription(getString(R.string.MainTabsCalls));
                tabs[INDEX_CALLS].setOnClickListener(createNativeTabClickListener(POSITION_CALLS_OR_SETTINGS));
                tabs[INDEX_CALLS].setOnLongClickListener(this::openCallsSelector);
                tabsView.addTabToIgnoreClick(tabs[INDEX_CALLS]);
            } else {
                tabs[INDEX_SETTINGS].restoreMainTab(GlassTabView.TabAnimation.SETTINGS, getString(R.string.Settings));
                tabs[INDEX_SETTINGS].setContentDescription(getString(R.string.Settings));
                tabs[INDEX_SETTINGS].setOnClickListener(createNativeTabClickListener(POSITION_CALLS_OR_SETTINGS));
                tabs[INDEX_SETTINGS].setOnLongClickListener(null);
                tabsView.removeTabToIgnoreClick(tabs[INDEX_SETTINGS]);
            }
        } else if (slot2Mode == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM) {
            bindCustomSlot(activeSlot2, MirrlyInterfaceConfig.SLOT_SETTINGS_OR_CALLS);
        } else {
            activeSlot2.setCounter(null, false, false);
        }

        // Slot 3: Profile
        final int slot3Mode = MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_PROFILE);
        if (slot3Mode == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT) {
            tabs[INDEX_PROFILE].restoreUserAvatarTab(currentAccount, getString(R.string.MainTabsProfile));
            tabs[INDEX_PROFILE].setContentDescription(getString(R.string.MainTabsProfile));
            tabs[INDEX_PROFILE].setOnClickListener(createNativeTabClickListener(POSITION_PROFILE));
            tabs[INDEX_PROFILE].setOnLongClickListener(this::openAccountSelector);
            tabsView.addTabToIgnoreClick(tabs[INDEX_PROFILE]);
        } else if (slot3Mode == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM) {
            bindCustomSlot(tabs[INDEX_PROFILE], MirrlyInterfaceConfig.SLOT_PROFILE);
        } else {
            tabs[INDEX_PROFILE].setCounter(null, false, false);
        }

        final boolean showSingleCircle = !allHidden && shouldCollapse && !isCircleExpanded;
        if (accountSwitchHint != null && (allHidden || showSingleCircle || slot3Mode != MirrlyInterfaceConfig.SLOT_MODE_DEFAULT)) {
            accountSwitchHint.hide();
        }
        tabsView.setCollapsedCircleMode(showSingleCircle);
        tabsView.setHorizontalAlignment(MirrlyInterfaceConfig.getBottomBarAlignment(), animated);

        if (allHidden) {
            for (GlassTabView tab : tabs) {
                tabsView.setViewVisible(tab, false, animated);
            }
            if (collapsedCircleTab != null) {
                tabsView.setViewVisible(collapsedCircleTab, false, animated);
            }
        } else if (showSingleCircle) {
            for (GlassTabView tab : tabs) {
                tabsView.setViewVisible(tab, false, animated);
            }
            updateCollapsedCircleContent(showCalls, animated);
            if (collapsedCircleTab != null) {
                tabsView.setViewVisible(collapsedCircleTab, true, animated);
            }
        } else {
            if (collapsedCircleTab != null) {
                tabsView.setViewVisible(collapsedCircleTab, false, animated);
            }
            tabsView.setViewVisible(tabs[INDEX_CHATS], slot0Mode != MirrlyInterfaceConfig.SLOT_MODE_HIDDEN, animated);
            tabsView.setViewVisible(tabs[INDEX_CONTACTS], slot1Mode != MirrlyInterfaceConfig.SLOT_MODE_HIDDEN, animated);
            final boolean slot2Visible = slot2Mode != MirrlyInterfaceConfig.SLOT_MODE_HIDDEN;
            tabsView.setViewVisible(tabs[INDEX_SETTINGS], slot2Visible && !showCalls, animated);
            tabsView.setViewVisible(tabs[INDEX_CALLS], slot2Visible && showCalls, animated);
            tabsView.setViewVisible(tabs[INDEX_PROFILE], slot3Mode != MirrlyInterfaceConfig.SLOT_MODE_HIDDEN, animated);
        }

        checkUnreadCount(animated);
        checkContactsTabBadge();

        if (viewPager != null && !viewPager.isManualScrolling() && !viewPager.isTouch()) {
            final int currentPos = viewPager.getCurrentPosition();
            final int startPos = getStartPosition();
            if (!isNativeTabSlot(currentPos) && (currentPos != POSITION_CHATS || startPos != POSITION_CHATS)) {
                viewPager.setPosition(startPos);
            }
            selectTab(viewPager.getCurrentPosition(), false);
        }

        checkUi_tabsPosition();
        checkUi_fadeView();
        updateAccountSwitchHintJoint();
        blur3_updateFragmentsGlass();
        blur3_invalidateBlur();
        scheduleMainTabsAdaptiveContrastUpdate(true);
        if (tabletLayout && fragmentView != null) {
            androidx.core.view.ViewCompat.requestApplyInsets(fragmentView);
        }
        tabsView.requestLayout();
    }

    private View.OnClickListener createNativeTabClickListener(int position) {
        return v -> {
            if (viewPager == null || viewPager.isManualScrolling() || viewPager.isTouch()) {
                return;
            }

            if (viewPager.getCurrentPosition() == position) {
                final BaseFragment fragment = getCurrentVisibleFragment();
                if (fragment instanceof MainTabsActivity.TabFragmentDelegate) {
                    ((MainTabsActivity.TabFragmentDelegate) fragment).onParentScrollToTop();
                }
            } else {
                selectTab(position, true);
                viewPager.scrollToPosition(position);
            }
            if (isCircleExpanded) {
                setCircleExpanded(false, true);
            }
        };
    }

    private void bindCustomSlot(GlassTabView tab, int slot) {
        final String actionKey = MirrlyInterfaceConfig.getSlotCustomAction(slot);
        final long customDialogId = MirrlyInterfaceConfig.getSlotCustomChatId(slot);
        final String storedTitle = MirrlyInterfaceConfig.getSlotCustomChatTitle(slot);
        MirrlyQuickActionRouter.bindTabView(tab, currentAccount, actionKey, customDialogId, storedTitle);
        tab.setCounter(null, false, false);
        tab.setSelected(false, false);
        tab.setGestureSelectedOverride(0f, false);
        tab.setOnClickListener(v -> {
            AndroidUtilities.vibrateCursor(v);
            MirrlyQuickActionRouter.executeAction(
                    MainTabsActivity.this,
                    v,
                    actionKey,
                    customDialogId,
                    this::openSearchFromCustomTab
            );
            if (isCircleExpanded) {
                setCircleExpanded(false, true);
            }
        });
        tab.setOnLongClickListener(this::openQuickCustomizeShortcut);
        tabsView.addTabToIgnoreClick(tab);
    }

    private boolean openQuickCustomizeShortcut(View anchor) {
        if (getContext() == null || getParentActivity() == null) {
            return false;
        }
        AndroidUtilities.vibrate(anchor);
        final ItemOptions o = ItemOptions.makeOptions(this, anchor);
        o.add(R.drawable.mirrly_settings_interface, getString(R.string.MirrlyInterfaceSlotEditShortcut), () -> {
            if (isCircleExpanded) {
                setCircleExpanded(false, false);
            }
            presentFragment(new MirrlyInterfaceSettingsActivity(MirrlyInterfaceSettingsActivity.TAB_BOTTOM_BAR));
        });
        o.setBlur(true);
        o.translate(0, -dp(4));
        applyTabScrimBackground(o, anchor);
        o.show();
        return true;
    }

    private void openSearchFromCustomTab() {
        if (viewPager == null) {
            return;
        }
        if (dialogsActivity == null) {
            prepareDialogsActivity(null);
        }
        if (viewPager.getCurrentPosition() != POSITION_CHATS) {
            pendingOpenSearch = true;
            selectTab(POSITION_CHATS, true);
            viewPager.scrollToPosition(POSITION_CHATS);
        } else if (dialogsActivity != null) {
            dialogsActivity.openSearchFromTabs();
        }
    }

    private void updateCollapsedCircleContent(boolean showCalls, boolean animated) {
        if (collapsedCircleTab == null) {
            return;
        }
        int representativeSlot = -1;
        final int currentPos = viewPager != null ? viewPager.getCurrentPosition() : POSITION_CHATS;
        if (currentPos >= 0 && currentPos < MirrlyInterfaceConfig.SLOT_COUNT
                && MirrlyInterfaceConfig.getSlotMode(currentPos) == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT) {
            representativeSlot = currentPos;
        } else {
            for (int slot = 0; slot < MirrlyInterfaceConfig.SLOT_COUNT; slot++) {
                if (MirrlyInterfaceConfig.getSlotMode(slot) == MirrlyInterfaceConfig.SLOT_MODE_DEFAULT) {
                    representativeSlot = slot;
                    break;
                }
            }
            if (representativeSlot == -1) {
                for (int slot = 0; slot < MirrlyInterfaceConfig.SLOT_COUNT; slot++) {
                    if (MirrlyInterfaceConfig.getSlotMode(slot) != MirrlyInterfaceConfig.SLOT_MODE_HIDDEN) {
                        representativeSlot = slot;
                        break;
                    }
                }
            }
        }
        if (representativeSlot == -1) {
            representativeSlot = MirrlyInterfaceConfig.SLOT_CHATS;
        }

        final int mode = MirrlyInterfaceConfig.getSlotMode(representativeSlot);
        if (mode == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM) {
            MirrlyQuickActionRouter.bindTabView(
                    collapsedCircleTab,
                    currentAccount,
                    MirrlyInterfaceConfig.getSlotCustomAction(representativeSlot),
                    MirrlyInterfaceConfig.getSlotCustomChatId(representativeSlot),
                    MirrlyInterfaceConfig.getSlotCustomChatTitle(representativeSlot)
            );
        } else {
            switch (representativeSlot) {
                case MirrlyInterfaceConfig.SLOT_CONTACTS:
                    collapsedCircleTab.restoreMainTab(GlassTabView.TabAnimation.CONTACTS, "");
                    break;
                case MirrlyInterfaceConfig.SLOT_SETTINGS_OR_CALLS:
                    collapsedCircleTab.restoreMainTab(showCalls ? GlassTabView.TabAnimation.CALLS : GlassTabView.TabAnimation.SETTINGS, "");
                    break;
                case MirrlyInterfaceConfig.SLOT_PROFILE:
                    collapsedCircleTab.restoreUserAvatarTab(currentAccount, "");
                    break;
                case MirrlyInterfaceConfig.SLOT_CHATS:
                default:
                    collapsedCircleTab.restoreMainTab(GlassTabView.TabAnimation.CHATS, "");
                    break;
            }
        }
        collapsedCircleTab.setContentDescription(getString(R.string.MirrlyInterfaceBarAccessibilityCircle));
        updateCollapsedCircleBadge(animated);
    }

    private void updateCollapsedCircleBadge(boolean animated) {
        if (collapsedCircleTab == null) {
            return;
        }
        final int unreadCount = MessagesStorage.getInstance(currentAccount).getMainUnreadCount();
        if (unreadCount > 0) {
            collapsedCircleTab.setCounter(LocaleController.formatNumber(unreadCount, ','), false, animated);
        } else {
            final boolean hasPermission = Build.VERSION.SDK_INT < 23 || ContactsController.hasContactsPermission();
            final boolean contactsVisible = MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_CONTACTS) != MirrlyInterfaceConfig.SLOT_MODE_HIDDEN;
            if (contactsVisible && Build.VERSION.SDK_INT >= 23 && UserConfig.getInstance(currentAccount).syncContacts
                    && !hasPermission && MessagesController.getGlobalNotificationsSettings().getBoolean("askAboutContacts2", true)) {
                collapsedCircleTab.setCounter("!", true, animated);
            } else {
                collapsedCircleTab.setCounter(null, false, animated);
            }
        }
    }

    private void refreshCustomChatSlotsIfNeeded() {
        if (tabs == null) {
            return;
        }
        final boolean showCalls = getUserConfig().showCallsTab;
        boolean changed = false;
        for (int slot = 0; slot < MirrlyInterfaceConfig.SLOT_COUNT; slot++) {
            if (MirrlyInterfaceConfig.getSlotMode(slot) == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM
                    && MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT.equals(MirrlyInterfaceConfig.getSlotCustomAction(slot))) {
                final GlassTabView tab = getSlotView(slot, showCalls);
                if (tab != null) {
                    MirrlyQuickActionRouter.bindTabView(
                            tab,
                            currentAccount,
                            MirrlyInterfaceConfig.QUICK_CUSTOM_CHAT,
                            MirrlyInterfaceConfig.getSlotCustomChatId(slot),
                            MirrlyInterfaceConfig.getSlotCustomChatTitle(slot)
                    );
                    changed = true;
                }
            }
        }
        if (changed && collapsedCircleTab != null && MirrlyInterfaceConfig.shouldCollapseToCircle() && !isCircleExpanded) {
            updateCollapsedCircleContent(showCalls, false);
        }
        if (changed && tabsView != null) {
            tabsView.requestLayout();
        }
    }

    private void refreshThemeQuickActionsIfNeeded() {
        if (tabs == null) {
            return;
        }
        final boolean showCalls = getUserConfig().showCallsTab;
        boolean changed = false;
        for (int slot = 0; slot < MirrlyInterfaceConfig.SLOT_COUNT; slot++) {
            if (MirrlyInterfaceConfig.getSlotMode(slot) == MirrlyInterfaceConfig.SLOT_MODE_CUSTOM
                    && MirrlyInterfaceConfig.QUICK_THEME_TOGGLE.equals(MirrlyInterfaceConfig.getSlotCustomAction(slot))) {
                final GlassTabView tab = getSlotView(slot, showCalls);
                if (tab != null) {
                    MirrlyQuickActionRouter.bindTabView(
                            tab,
                            currentAccount,
                            MirrlyInterfaceConfig.QUICK_THEME_TOGGLE,
                            0L,
                            null
                    );
                    changed = true;
                }
            }
        }
        if (changed && collapsedCircleTab != null && MirrlyInterfaceConfig.shouldCollapseToCircle() && !isCircleExpanded) {
            updateCollapsedCircleContent(showCalls, false);
        }
    }

    @Nullable
    private GlassTabView getSlotView(int slot, boolean showCallsTab) {
        if (tabs == null) {
            return null;
        }
        switch (slot) {
            case MirrlyInterfaceConfig.SLOT_CHATS:
                return tabs[INDEX_CHATS];
            case MirrlyInterfaceConfig.SLOT_CONTACTS:
                return tabs[INDEX_CONTACTS];
            case MirrlyInterfaceConfig.SLOT_SETTINGS_OR_CALLS:
                return showCallsTab ? tabs[INDEX_CALLS] : tabs[INDEX_SETTINGS];
            case MirrlyInterfaceConfig.SLOT_PROFILE:
                return tabs[INDEX_PROFILE];
            default:
                return null;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = super.getThemeDescriptions();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = this::blur3_updateColors;

        return themeDescriptions;
    }

    /* * */

    private class MainTabsActivityControllerImpl implements MainTabsActivityController {
        @Override
        public void setTabsVisible(boolean visible) {
            animatorTabsVisible.setValue(visible, true);
            if (!visible && isCircleExpanded) {
                setCircleExpanded(false, false);
            }
        }
    }


    /* Slide */

    @Override
    public boolean canBeginSlide() {
        final BaseFragment fragment = getCurrentVisibleFragment();
        return fragment != null && fragment.canBeginSlide();
    }

    @Override
    public void onBeginSlide() {
        super.onBeginSlide();
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onBeginSlide();
        }
    }

    @Override
    public void onSlideProgress(boolean isOpen, float progress) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onSlideProgress(isOpen, progress);
        }
    }

    @Override
    public Animator getCustomSlideTransition(boolean topFragment, boolean backAnimation, float distanceToMove) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        return fragment != null ? fragment.getCustomSlideTransition(topFragment, backAnimation, distanceToMove) : null;
    }

    @Override
    public void prepareFragmentToSlide(boolean topFragment, boolean beginSlide) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.prepareFragmentToSlide(topFragment, beginSlide);
        }
    }


    private HintView2 accountSwitchHint;
    private boolean accountSwitchHintShown;

    private void showAccountChangeHint() {
        if (accountSwitchHintShown) return;
        if (MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_PROFILE) != MirrlyInterfaceConfig.SLOT_MODE_DEFAULT
                || MirrlyInterfaceConfig.shouldCollapseToCircle()) {
            return;
        }

        if (accountSwitchHint == null && HintsController.Hint.AccountSwitchHint.show()) {
            AndroidUtilities.runOnUIThread(() -> {
                if (getContext() == null || tabs == null || tabs[INDEX_PROFILE] == null
                        || tabsView == null || !tabsView.isViewVisible(tabs[INDEX_PROFILE])
                        || tabs[INDEX_PROFILE].getVisibility() != View.VISIBLE || tabs[INDEX_PROFILE].getWidth() <= 0
                        || tabsViewWrapper == null || tabsViewWrapper.getVisibility() != View.VISIBLE
                        || MirrlyInterfaceConfig.getSlotMode(MirrlyInterfaceConfig.SLOT_PROFILE) != MirrlyInterfaceConfig.SLOT_MODE_DEFAULT
                        || MirrlyInterfaceConfig.shouldCollapseToCircle()) {
                    return;
                }

                accountSwitchHint = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM);
                if (iBlur3FactoryGlass != null) {
                    accountSwitchHint.setGlass(iBlur3FactoryGlass, BlurredBackgroundProviderImpl.hint(resourceProvider));
                }
                accountSwitchHint.setTranslationY(-navigationBarHeight + dp(4));
                accountSwitchHint.setPadding(dp(7.33f), 0, dp(7.33f), 0);
                accountSwitchHint.setMultilineText(false);
                accountSwitchHint.setCloseButton(true);
                accountSwitchHint.setText(getString(R.string.SwitchAccountHint));
                updateAccountSwitchHintJoint();
                contentView.addView(accountSwitchHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS));
                accountSwitchHint.setOnHiddenListener(() -> {
                    AndroidUtilities.removeFromParent(accountSwitchHint);
                    accountSwitchHint = null;
                    blur3_updateFragmentsGlass();
                    blur3_invalidateBlur();
                });
                accountSwitchHint.setDuration(8000);
                accountSwitchHint.show();
                blur3_updateFragmentsGlass();
                blur3_invalidateBlur();

                HintsController.Hint.AccountSwitchHint.increment();
            }, 1500);
        }

        accountSwitchHintShown = true;
    }

    private void updateAccountSwitchHintJoint() {
        if (accountSwitchHint == null || contentView == null || tabsView == null || tabs == null || tabs[INDEX_PROFILE] == null) {
            return;
        }
        final View v = tabs[INDEX_PROFILE];
        if (v.getWidth() <= 0 || v.getVisibility() != View.VISIBLE || !tabsView.isViewVisible(v)) {
            return;
        }
        final float wrapperX = tabsViewWrapper != null ? tabsViewWrapper.getX() : 0f;
        final float centerX = wrapperX + tabsView.getX() + v.getX() + v.getWidth() * 0.5f;
        accountSwitchHint.setJointPx(0f, centerX - dp(7.33f));
    }


    /* * */

    private final @NonNull GlassSourceColor iBlur3SourceColor;
    private final @Nullable GlassSourceRenderNode iBlur3SourceTabGlass;
    private final @Nullable GlassSourceRenderNode iBlur3SourceTabFrosted;

    /** The one the tabs draw now; null with the blur off, they are on the color then. */
    @Nullable
    private GlassSourceRenderNode getActiveTabSource() {
        final GlassSettings settings = GlassEngine.getCurrentGlassSettings();
        if (!settings.isBlurEnabled) {
            return null;
        }
        return settings.isLiquidGlassEnabled && Build.VERSION.SDK_INT >= 33 ? iBlur3SourceTabGlass : iBlur3SourceTabFrosted;
    }

    private final RectF fragmentPosition = new RectF();
    private final RectF tabsCapturePosition = new RectF();
    private final Runnable onFragmentGlassCaptured = this::blur3_onFragmentGlassCaptured;

    private void blur3_onFragmentGlassCaptured() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && iBlur3SourceTabGlass != null) {
            iBlur3SourceTabGlass.invalidateRenderer();
            iBlur3SourceTabFrosted.invalidateRenderer();
            blur3_invalidateBlur();
        }
        scheduleMainTabsAdaptiveContrastUpdate(false);
    }

    /** The tabs go to the capture of the page under them: the full width, the place of the shown tabs whatever they do. */
    private void blur3_updateFragmentsGlass() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || iBlur3SourceTabGlass == null || contentView == null) {
            return;
        }

        if (MirrlyInterfaceConfig.isBottomBarCompletelyHidden()) {
            for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
                final BaseFragment fragment = fragmentsArr.valueAt(a).fragment;
                if (fragment instanceof TabFragmentDelegate) {
                    fragment.setParentGlass(null, null);
                }
            }
            return;
        }

        final int updateLayoutHeight = updateLayoutWrapper != null && updateLayoutWrapper.isUpdateLayoutVisible() ? dp(UpdateLayoutWrapper.HEIGHT) : 0;
        final int bottom = contentView.getHeight() - navigationBarHeight - updateLayoutHeight - dp(DialogsActivity.MAIN_TABS_MARGIN);
        final int top = bottom - dp(DialogsActivity.MAIN_TABS_HEIGHT);
        int captureTop = top - dp(1);
        if (accountSwitchHint != null && accountSwitchHint.getParent() != null && accountSwitchHint.getVisibility() == View.VISIBLE) {
            captureTop = Math.max(0, top - dp(64));
        }

        for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
            final BaseFragment fragment = fragmentsArr.valueAt(a).fragment;
            final View view = fragment.fragmentView;
            if (view == null || view.getWidth() <= 0 || !(fragment instanceof TabFragmentDelegate)) {
                continue;
            }
            if (top >= view.getHeight()) {
                fragment.setParentGlass(null, null);
            } else {
                tabsCapturePosition.set(0, captureTop, view.getWidth(), bottom + dp(1));
                fragment.setParentGlass(tabsCapturePosition, onFragmentGlassCaptured);
            }
        }
    }

    private void blur3_invalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || iBlur3SourceTabGlass == null || fragmentView == null) {
            return;
        }

        final int width = fragmentView.getMeasuredWidth();
        final int height = fragmentView.getMeasuredHeight();

        blur3_updateFragmentsGlass();
        final GlassSourceRenderNode source = getActiveTabSource();
        if (source != null) {
            source.setSize(width, height);
            source.updateDisplayListIfNeeded();
        }
    }

    private int lastFadeEstBackgroundColor;
    private boolean hasLastFadeEstBackgroundColor;

    private void blur3_updateFadeColors() {
        final int color = getEstBackgroundColor();
        iBlur3SourceColor.setColor(color);
        if (!hasLastFadeEstBackgroundColor || lastFadeEstBackgroundColor != color) {
            hasLastFadeEstBackgroundColor = true;
            lastFadeEstBackgroundColor = color;
            if (fadeView != null) {
                fadeView.invalidate();
            }
        }
    }

    private BlurredBackgroundProvider mainTabsAdaptiveColorProvider;
    private long lastMainTabsSampleMs;
    private float mainTabsAdaptiveTarget;
    private float mainTabsAdaptiveFactor;
    private boolean mainTabsAdaptiveTickPosted;
    private final Runnable mainTabsAdaptiveTickRunnable = () -> {
        mainTabsAdaptiveTickPosted = false;
        updateMainTabsAdaptiveContrast(false);
    };

    private void cancelMainTabsAdaptiveTick() {
        if (mainTabsAdaptiveTickPosted && contentView != null) {
            contentView.removeCallbacks(mainTabsAdaptiveTickRunnable);
        }
        mainTabsAdaptiveTickPosted = false;
    }

    private void scheduleMainTabsAdaptiveContrastUpdate(boolean forceSample) {
        if (forceSample) {
            cancelMainTabsAdaptiveTick();
            updateMainTabsAdaptiveContrast(true);
            return;
        }
        final long now = android.os.SystemClock.uptimeMillis();
        final long elapsed = now - lastMainTabsSampleMs;
        if (elapsed >= 80L) {
            cancelMainTabsAdaptiveTick();
            updateMainTabsAdaptiveContrast(false);
        } else if (!mainTabsAdaptiveTickPosted && contentView != null) {
            mainTabsAdaptiveTickPosted = true;
            contentView.postDelayed(mainTabsAdaptiveTickRunnable, Math.max(16L, 84L - elapsed));
        }
    }

    @Nullable
    private ViewGroup findMainTabScrollTarget(@Nullable View root, int depth) {
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        final ViewGroup group = (ViewGroup) root;
        if (group instanceof androidx.recyclerview.widget.RecyclerView && group.getVisibility() == View.VISIBLE && group.getChildCount() > 0) {
            return group;
        }
        if (depth >= 2) {
            return null;
        }
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            final View child = group.getChildAt(i);
            if (child != null && child.getVisibility() == View.VISIBLE && child.getWidth() > 0 && child.getHeight() > 0) {
                final ViewGroup found = findMainTabScrollTarget(child, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return depth == 0 ? group : null;
    }

    private void updateMainTabsAdaptiveContrast(boolean forceSample) {
        if (tabsView == null || tabs == null || contentView == null) {
            return;
        }
        if (MirrlyInterfaceConfig.isBottomBarCompletelyHidden()
                || tabsView.getVisibility() != View.VISIBLE
                || (tabsViewWrapper != null && tabsViewWrapper.getVisibility() != View.VISIBLE)
                || !BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourceProvider)) {
            if (mainTabsAdaptiveFactor > 0.001f) {
                mainTabsAdaptiveTarget = mainTabsAdaptiveFactor = 0f;
                for (GlassTabView tabView : tabs) {
                    if (tabView != null) {
                        tabView.applyAdaptiveGlassContrast(0f);
                    }
                }
                if (collapsedCircleTab != null) {
                    collapsedCircleTab.applyAdaptiveGlassContrast(0f);
                }
            }
            return;
        }

        final long now = android.os.SystemClock.uptimeMillis();
        if (forceSample || now - lastMainTabsSampleMs >= 80L) {
            lastMainTabsSampleMs = now;
            if (mainTabsAdaptiveColorProvider == null) {
                mainTabsAdaptiveColorProvider = BlurredBackgroundProviderImpl.mainTabs(resourceProvider);
            }
            final BaseFragment currentFrag = getCurrentVisibleFragment();
            final View fragView = currentFrag != null ? currentFrag.fragmentView : null;
            final ViewGroup sampleTarget = findMainTabScrollTarget(fragView, 0);
            final int estBg = getEstBackgroundColor();
            final float backdropLuma = GlassContrastHelper.computeLuminance(estBg);
            final int glassBg = mainTabsAdaptiveColorProvider.getBackgroundColor();
            final int vw = contentView.getWidth();
            final int vh = contentView.getHeight();
            for (GlassTabView tabView : tabs) {
                if (tabView != null) {
                    tabView.updateSmartAdaptiveGlassContrast(sampleTarget, glassBg, vw, vh, backdropLuma, false);
                }
            }
            if (collapsedCircleTab != null) {
                collapsedCircleTab.updateSmartAdaptiveGlassContrast(sampleTarget, glassBg, vw, vh, backdropLuma, false);
            }
        }
    }

    private void blur3_updateColors() {
        mainTabsAdaptiveColorProvider = null;
        lastMainTabsSampleMs = 0L;
        hasLastFadeEstBackgroundColor = false;
        blur3_updateFadeColors();
        if (tabsViewBackground != null) {
            tabsViewBackground.updateColors();
        }
        blur3_invalidateBlur();
        scheduleMainTabsAdaptiveContrastUpdate(true);
        if (fadeView != null) {
            fadeView.invalidate();
        }
        if (tabsView != null) {
            tabsView.invalidate();
        }
        if (tabs != null) {
            for (GlassTabView tabView : tabs) {
                if (tabView != null) {
                    tabView.updateColorsLottie();
                }
            }
        }
        if (collapsedCircleTab != null) {
            collapsedCircleTab.updateColorsLottie();
        }
        if (accountSwitchHint != null) {
            accountSwitchHint.invalidate();
        }
    }

    @Override
    public EdgeToEdgeSupportMode getEdgeToEdgeSupportMode() {
        return EdgeToEdgeSupportMode.FULL;
    }

    @Override
    public void onThemeColorsChanged() {
        try {
            super.onThemeColorsChanged();
            blur3_updateColors();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** theme editor: colors this page shows (former ThemeDescription keys) */
    @Override
    public void addThemeColorKeys(HashSet<Integer> keys) {
        super.addThemeColorKeys(keys);
        keys.add(Theme.key_dialogBackground);
        keys.add(Theme.key_windowBackgroundWhite);
    }
}
