package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.mirrly.MirrlyConfig;
import org.telegram.messenger.mirrly.MirrlyMonetThemeHelper;
import org.telegram.messenger.mirrly.MirrlyPowerPolicy;
import org.telegram.messenger.mirrly.MirrlyPowerTelemetry;
import org.telegram.messenger.mirrly.MirrlyPowerTelemetry.EcoRecommendation;
import org.telegram.messenger.mirrly.MirrlyPowerTelemetry.ModuleReport;
import org.telegram.messenger.mirrly.MirrlyPowerTelemetry.PowerReport;
import org.telegram.messenger.mirrly.MirrlyPowerTelemetry.SettingItem;
import org.telegram.messenger.mirrly.MirrlyPowerTelemetry.SystemPowerSnapshot;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Charts.BaseChartView;
import org.telegram.ui.Charts.data.ChartData;
import org.telegram.ui.Charts.data.DoubleLinearChartData;
import org.telegram.ui.Charts.data.StackBarChartData;
import org.telegram.ui.Charts.data.StackLinearChartData;
import org.telegram.ui.Charts.view_data.ChartHeaderView;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BatteryDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.IntSeekBarAccessibilityDelegate;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SeekBarAccessibilityDelegate;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;
import org.telegram.utils.glass.GlassEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

import me.vkryl.core.reference.ReferenceList;

/**
 * Native Telegram-styled Power Saving & Energy Analytics screen for MirrlyGram's own subsystems:
 * MirrlyEngine, background notification keep-alive service, Glass/Monet rendering, Vault/Deleted
 * interceptors, Translator/STT workers, and Interface/Haptics settings.
 */
public class MirrlyPowerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final int TAB_OVERVIEW = 0;
    public static final int TAB_ANALYTICS = 1;
    public static final int TAB_CONTROL = 2;

    private static final int MENU_MORE = 1;

    // Tab 1 (Overview) item IDs
    private static final int ID_PERIOD_OVERVIEW = 10;
    private static final int ID_RING_CHART = 11;
    private static final int ID_OVERVIEW_KPI = 12;
    private static final int ID_MODULE_BASE = 100;         // 100..105
    private static final int ID_MODULE_SETTING_BASE = 200; // 200 + moduleId * 20 + idx
    private static final int ID_MODULE_ACTION_BASE = 400;  // 400 + moduleId

    // Tab 2 (Analytics) item IDs
    private static final int ID_PERIOD_ANALYTICS = 20;
    private static final int ID_CHART_STACK_BAR = 21;
    private static final int ID_CHART_STACK_SHARE = 22;
    private static final int ID_CHART_BATT_POWER = 23;
    private static final int ID_CHART_CLOCK_24H = 24;
    private static final int ID_SYSTEM_POWER_CARD = 25;
    private static final int ID_SYS_UNRESTRICTED_ROW = 26;
    private static final int ID_COPY_REPORT_BTN = 27;
    private static final int ID_RESET_STATS_BTN = 28;

    // Tab 3 (Control) item IDs
    private static final int ID_ECO_TIP_BANNER = 30;
    private static final int ID_ECO_SLIDER = 31;
    private static final int ID_ECO_FOLLOW_SYSTEM = 32;
    private static final int ID_ECO_FLAG_PUSH = 33;
    private static final int ID_ECO_FLAG_LIQUID = 34;
    private static final int ID_ECO_FLAG_BLUR = 35;
    private static final int ID_ECO_FLAG_VIBRATION = 36;

    private static final int ID_PUSH_INTERVAL_SEG = 40;
    private static final int ID_PUSH_SERVICE_TOGGLE = 41;
    private static final int ID_PUSH_CONN_TOGGLE = 42;
    private static final int ID_PUSH_UNRESTRICTED_ROW = 43;

    private static final int ID_SW_LIQUID_EVERYWHERE = 50;
    private static final int ID_SW_LIQUID_LENS = 51;
    private static final int ID_SW_GLASS_BLUR = 52;
    private static final int ID_SW_DIALOGS_HEADER = 53;
    private static final int ID_SW_MONET_AMOLED = 54;
    private static final int ID_SW_FLOWSEAL_PER_DC = 55;
    private static final int ID_SW_SAVE_DELETED = 56;
    private static final int ID_SW_SAVE_DELETED_CHATS = 57;
    private static final int ID_SW_SAVE_EDIT_HISTORY = 58;
    private static final int ID_SW_DISABLE_VIBRATION = 59;

    private static final int ID_OPEN_TG_LITEMODE = 60;

    private final int initialTab;
    private ViewPagerFixed pager;
    private ViewPagerFixed.TabsView tabsView;
    private FrameLayout tabsContainer;
    private View listTopOverlay;
    private ActionBarMenuItem moreMenuItem;
    private final ReferenceList<TabListView> listViews = new ReferenceList<>();
    private BaseChartView.SharedUiComponents sharedChartUi;

    private int insetTop;
    private int insetBottom;

    private int selectedPeriod = MirrlyPowerTelemetry.PERIOD_24H;
    private int selectedRingModuleId = -1;
    private boolean ringShowMah = false;
    private final boolean[] expandedModules = new boolean[MirrlyPowerTelemetry.MODULE_COUNT];

    @Nullable
    private PowerReport currentReport;
    @NonNull
    private SystemPowerSnapshot liveSystemSnapshot = MirrlyPowerTelemetry.getLatestSystemSnapshot();
    private int reportGeneration = 0;

    // Cached StatisticActivity.ChartViewData instances rebuilt only when report changes
    private StatisticActivity.ChartViewData chartModulesData;
    private StatisticActivity.ChartViewData chartShareData;
    private StatisticActivity.ChartViewData chartBattPowerData;
    private StatisticActivity.ChartViewData chartClock24hData;

    private boolean livePollingActive = false;
    private final Runnable liveSensorPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!livePollingActive || finishing) {
                return;
            }
            MirrlyPowerTelemetry.refreshLiveSensorsAsync(snap -> {
                if (!livePollingActive || finishing) {
                    return;
                }
                liveSystemSnapshot = snap;
                pushLiveSensorsToVisibleViews(snap);
                AndroidUtilities.runOnUIThread(liveSensorPollRunnable, 2000);
            });
        }
    };

    public MirrlyPowerActivity() {
        this(TAB_OVERVIEW);
    }

    public MirrlyPowerActivity(int initialTab) {
        super();
        if (initialTab >= TAB_OVERVIEW && initialTab <= TAB_CONTROL) {
            this.initialTab = initialTab;
        } else {
            this.initialTab = TAB_OVERVIEW;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        MirrlyConfig.load();
        MirrlyPowerTelemetry.init(ApplicationLoader.applicationContext);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyPowerStatsUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyPowerPolicyChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyGlassSettingsDidChange);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mirrlyVibrationSettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        livePollingActive = false;
        AndroidUtilities.cancelRunOnUIThread(liveSensorPollRunnable);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyPowerStatsUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyPowerPolicyChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyGlassSettingsDidChange);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mirrlyVibrationSettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        livePollingActive = true;
        reloadReportAsync(false);
        AndroidUtilities.cancelRunOnUIThread(liveSensorPollRunnable);
        AndroidUtilities.runOnUIThread(liveSensorPollRunnable, 1500);
    }

    @Override
    public void onPause() {
        super.onPause();
        livePollingActive = false;
        AndroidUtilities.cancelRunOnUIThread(liveSensorPollRunnable);
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
    public View createView(Context context) {
        setHasOwnBackground(true);
        sharedChartUi = new BaseChartView.SharedUiComponents(resourceProvider);

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.MirrlyPowerTitle));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_MORE) {
                    showOverflowMenu();
                }
            }
        });
        SimpleActionBarMenu menu = simpleActionBar.createMenu();
        moreMenuItem = menu.addItem(MENU_MORE, R.drawable.ic_ab_other, getResourceProvider());

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
                );
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if ((pager != null && (pager.checkTabsAnimationInProgress() || isPagerTranslating()))
                        || (tabsView != null && tabsView.isAnimatingIndicator())) {
                    glassEngine.invalidate();
                }
            }
        };
        ThemeBind.backgroundColor(frameLayout, Theme.key_windowBackgroundGray, getResourceProvider());

        pager = new ViewPagerFixed(context) {
            @Override
            protected boolean canScroll(MotionEvent e) {
                if (e != null && isTouchInsideInteractiveControl(this, e)) {
                    return false;
                }
                return super.canScroll(e);
            }
        };
        pager.setAdapter(new PageAdapter());
        if (initialTab != TAB_OVERVIEW) {
            pager.setPosition(initialTab);
        }

        tabsContainer = new FrameLayout(context);
        tabsView = pager.createTabsView(true, ViewPagerFixed.SELECTOR_TYPE_BUBBLE_STYLE);
        frameLayout.addView(pager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        listTopOverlay = new View(context);
        frameLayout.addView(listTopOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP));
        ViewGroup.LayoutParams overlayLp = listTopOverlay.getLayoutParams();
        overlayLp.height = insetTop + ActionBar.getCurrentActionBarHeight() + dp(44);

        navigationBarFadeView = new View(context);
        navigationBarFadeView.setVisibility(insetBottom >= dp(40) ? View.VISIBLE : View.GONE);
        frameLayout.addView(navigationBarFadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, Math.max(0, insetBottom), Gravity.BOTTOM));

        tabsContainer.setTranslationY(BaseFragmentSimpleGlass.getPillGlassTop(insetTop) - dp(9.66f));
        tabsContainer.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36 + 6 + 6));
        tabsContainer.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabsContainer.setBackground(getBaseSimpleGlass().factory
                .create(tabsContainer)
                .setColorProvider(BlurredBackgroundProviderImpl.simpleActionBar(resourceProvider))
                .setPadding(dp(9.66f))
                .setRadius(dp(18))
        );

        getBaseSimpleGlass().setViews(frameLayout, pager, simpleActionBar, resourceProvider);
        getBaseSimpleGlass().setCapture((canvas, position) -> {
            for (int a = 0, n = pager.getChildCount(); a < n; a++) {
                final View child = pager.getChildAt(a);
                if (child instanceof RecyclerListView) {
                    final RecyclerListView rv = (RecyclerListView) child;
                    Blur3Utils.captureRelativeParent(rv, canvas, position, rv, frameLayout);
                }
            }
        });
        getBaseSimpleGlass().setClipView(null);
        frameLayout.addView(tabsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, -4, 0, -4, 0));
        Drawable topFadeDrawable = getBaseSimpleGlass().createActionBarFadeDrawable(listTopOverlay);
        if (topFadeDrawable instanceof org.telegram.utils.glass.fade.GlassFadeBlurredDrawable) {
            ((org.telegram.utils.glass.fade.GlassFadeBlurredDrawable) topFadeDrawable).setOverlayAlpha(105);
        }
        listTopOverlay.setBackground(topFadeDrawable);
        navigationBarFadeView.setBackground(getBaseSimpleGlass().createNavigationBarFadeDrawable(navigationBarFadeView));
        simpleActionBar.setBackground(null);

        reloadReportAsync(false);
        return fragmentView = frameLayout;
    }

    private boolean isTouchInsideInteractiveControl(@NonNull View root, @NonNull MotionEvent e) {
        final float rawX = e.getRawX();
        final float rawY = e.getRawY();
        return findInteractiveChildAt(root, rawX, rawY);
    }

    private boolean findInteractiveChildAt(@NonNull View view, float rawX, float rawY) {
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        if (rawX < loc[0] || rawX > loc[0] + view.getWidth() || rawY < loc[1] || rawY > loc[1] + view.getHeight()) {
            return false;
        }
        if (view instanceof BaseChartView || view instanceof SeekBarView) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                if (findInteractiveChildAt(vg.getChildAt(i), rawX, rawY)) {
                    return true;
                }
            }
        }
        return false;
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

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (event != null && event.getY() <= ActionBar.getCurrentActionBarHeight() + dp(48)) {
            return true;
        }
        if (event != null && pager != null && isTouchInsideInteractiveControl(pager, event)) {
            return false;
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
        if (id == NotificationCenter.didSetNewTheme) {
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
            return;
        }
        if (id == NotificationCenter.mirrlyPowerStatsUpdated
                || id == NotificationCenter.mirrlyPowerPolicyChanged
                || id == NotificationCenter.proxySettingsChanged
                || id == NotificationCenter.mirrlyGlassSettingsDidChange
                || id == NotificationCenter.mirrlyVibrationSettingsChanged) {
            reloadReportAsync(true);
        }
    }

    // -------------------------------------------------------------------------
    // Data Loading & Live Updates
    // -------------------------------------------------------------------------

    private void reloadReportAsync(boolean animate) {
        final int requestedPeriod = selectedPeriod;
        MirrlyPowerTelemetry.sampleNowAndBuildReportAsync(requestedPeriod, report -> {
            if (finishing || report.period != selectedPeriod) {
                return;
            }
            currentReport = report;
            liveSystemSnapshot = report.system;
            reportGeneration++;
            rebuildChartData(report);
            updateAllTabAdapters(animate);
        });
    }

    private void rebuildChartData(@NonNull PowerReport report) {
        chartModulesData = buildChartViewData(
                LocaleController.getString(R.string.MirrlyPowerChartModulesTitle),
                2,
                report.stackBarChartJson,
                false
        );
        chartShareData = buildChartViewData(
                LocaleController.getString(R.string.MirrlyPowerChartShareTitle),
                4,
                report.stackShareChartJson,
                false
        );
        chartBattPowerData = buildChartViewData(
                LocaleController.getString(R.string.MirrlyPowerChartBattPowerTitle),
                1,
                report.batteryVsPowerChartJson,
                false
        );
        chartClock24hData = buildChartViewData(
                LocaleController.getString(R.string.MirrlyPowerChartClock24hTitle),
                0,
                report.clock24hChartJson,
                true
        );
    }

    @NonNull
    private StatisticActivity.ChartViewData buildChartViewData(
            String title,
            int graphType,
            @Nullable JSONObject json,
            boolean hourFormat
    ) {
        StatisticActivity.ChartViewData vd = new StatisticActivity.ChartViewData(title, graphType);
        vd.showAll = true;
        vd.useHourFormat = hourFormat;
        if (json == null) {
            vd.isEmpty = true;
            vd.errorMessage = LocaleController.getString(R.string.MirrlyPowerChartCollecting);
            return vd;
        }
        try {
            ChartData cd;
            switch (graphType) {
                case 1:
                    cd = new DoubleLinearChartData(json);
                    break;
                case 2:
                    cd = new StackBarChartData(json);
                    break;
                case 4:
                    cd = new StackLinearChartData(json, false);
                    break;
                case 0:
                default:
                    cd = new ChartData(json);
                    break;
            }
            if (cd.x == null || cd.x.length < 2 || cd.lines == null || cd.lines.isEmpty()) {
                vd.isEmpty = true;
                vd.errorMessage = LocaleController.getString(R.string.MirrlyPowerChartCollecting);
            } else {
                vd.chartData = cd;
                vd.isEmpty = false;
            }
        } catch (Throwable t) {
            FileLog.e(t);
            vd.isEmpty = true;
            vd.errorMessage = LocaleController.getString(R.string.MirrlyPowerChartCollecting);
        }
        return vd;
    }

    private void updateAllTabAdapters(boolean animate) {
        for (TabListView lv : listViews) {
            if (lv.adapter != null) {
                lv.adapter.update(animate);
            }
        }
    }

    private void updateTabAdapter(int tabType, boolean animate) {
        for (TabListView lv : listViews) {
            if (lv.tabType == tabType && lv.adapter != null) {
                lv.adapter.update(animate);
            }
        }
    }

    private void syncAllAdaptersWithoutNotify() {
        for (TabListView lv : listViews) {
            if (lv.adapter != null) {
                lv.adapter.updateWithoutNotify();
            }
        }
    }

    private void pushLiveSensorsToVisibleViews(@NonNull SystemPowerSnapshot snap) {
        for (TabListView lv : listViews) {
            for (int i = 0, count = lv.getChildCount(); i < count; i++) {
                View child = lv.getChildAt(i);
                if (child instanceof MirrlyPowerOverviewCell) {
                    ((MirrlyPowerOverviewCell) child).updateLiveBattery(snap, true);
                } else if (child instanceof SystemPowerCardCell) {
                    ((SystemPowerCardCell) child).updateSnapshot(snap, true);
                }
            }
        }
    }

    private void onPeriodChanged(int periodIndex) {
        int nextPeriod = periodIndex == 0
                ? MirrlyPowerTelemetry.PERIOD_SESSION
                : (periodIndex == 2 ? MirrlyPowerTelemetry.PERIOD_7D : MirrlyPowerTelemetry.PERIOD_24H);
        if (selectedPeriod == nextPeriod) {
            return;
        }
        selectedPeriod = nextPeriod;
        selectedRingModuleId = -1;
        reloadReportAsync(true);
    }

    // -------------------------------------------------------------------------
    // Overflow Menu & Shared Actions
    // -------------------------------------------------------------------------

    private void showOverflowMenu() {
        View anchor = moreMenuItem != null ? moreMenuItem : simpleActionBar;
        ItemOptions options = ItemOptions.makeOptions(this, anchor);
        options.add(R.drawable.msg_retry, LocaleController.getString(R.string.MirrlyPowerMenuRefresh), () -> {
            reloadReportAsync(true);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, LocaleController.getString(R.string.MirrlyPowerRefreshedToast)).show();
        });
        options.add(R.drawable.msg_copy, LocaleController.getString(R.string.MirrlyPowerMenuCopyReport), this::copyFullReport);
        options.add(R.drawable.msg_delete, LocaleController.getString(R.string.MirrlyPowerMenuReset), true, this::confirmResetStats);
        options.setBlur(true);
        options.show();
    }

    private void copyFullReport() {
        PowerReport r = currentReport;
        if (r == null) {
            return;
        }
        MirrlyUiKit.copyTextWithBulletin(
                this,
                r.buildClipboardReport(),
                LocaleController.getString(R.string.MirrlyPowerReportCopied),
                null,
                getResourceProvider()
        );
    }

    private void confirmResetStats() {
        MirrlyUiKit.confirmDangerAction(
                this,
                getResourceProvider(),
                LocaleController.getString(R.string.MirrlyPowerResetConfirmTitle),
                LocaleController.getString(R.string.MirrlyPowerResetConfirmMessage),
                LocaleController.getString(R.string.Reset),
                () -> MirrlyPowerTelemetry.resetAllStatsAsync(() -> {
                    selectedRingModuleId = -1;
                    reloadReportAsync(true);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, LocaleController.getString(R.string.MirrlyPowerResetDoneToast)).show();
                })
        );
    }

    private void openBatteryOptimizationSettings() {
        Activity activity = getParentActivity();
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        boolean ignoring = liveSystemSnapshot.ignoringBatteryOptimizations;
        if (!ignoring) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
                return;
            } catch (Throwable ignore) {
            }
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void openModuleSettings(int moduleId) {
        switch (moduleId) {
            case MirrlyPowerTelemetry.MODULE_ENGINE:
                presentFragment(new MirrlyNetworkSettingsActivity());
                break;
            case MirrlyPowerTelemetry.MODULE_PUSH:
                if (tabsView != null) {
                    tabsView.scrollToTab(TAB_CONTROL, TAB_CONTROL);
                } else if (pager != null) {
                    pager.setPosition(TAB_CONTROL);
                }
                highlightControlItem(ID_PUSH_INTERVAL_SEG);
                break;
            case MirrlyPowerTelemetry.MODULE_GLASS:
                presentFragment(new MirrlyCustomizationActivity());
                break;
            case MirrlyPowerTelemetry.MODULE_VAULT:
                presentFragment(new MirrlyVaultSettingsActivity());
                break;
            case MirrlyPowerTelemetry.MODULE_TRANSLATOR:
                presentFragment(new MirrlyTranslatorSettingsActivity());
                break;
            case MirrlyPowerTelemetry.MODULE_INTERFACE:
            default:
                presentFragment(new MirrlyInterfaceSettingsActivity());
                break;
        }
    }

    private void highlightOverviewModuleRow(int moduleId) {
        for (TabListView lv : listViews) {
            if (lv.tabType == TAB_OVERVIEW && lv.adapter != null) {
                final int targetId = ID_MODULE_BASE + moduleId;
                lv.highlightRow(() -> {
                    for (int i = 0; i < lv.adapter.getItemCount(); i++) {
                        UItem item = lv.adapter.getItem(i);
                        if (item != null && item.id == targetId) {
                            return i;
                        }
                    }
                    return -1;
                });
            }
        }
    }

    private void highlightControlItem(int targetId) {
        for (TabListView lv : listViews) {
            if (lv.tabType == TAB_CONTROL && lv.adapter != null) {
                lv.postDelayed(() -> lv.highlightRow(() -> {
                    for (int i = 0; i < lv.adapter.getItemCount(); i++) {
                        UItem item = lv.adapter.getItem(i);
                        if (item != null && item.id == targetId) {
                            return i;
                        }
                    }
                    return -1;
                }), 220);
            }
        }
    }

    private void showModuleDetailSheet(@NonNull ModuleReport mod) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(18));

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(activity);
        int modColor = Theme.getColor(mod.colorKey, getResourceProvider());
        icon.setBackground(Theme.createRoundRectDrawable(dp(10), modColor));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImageResource(getModuleIconRes(mod.moduleId));
        icon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        titleRow.addView(icon, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, LocaleController.isRTL ? 0 : 12, 0));

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView nameTv = new TextView(activity);
        nameTv.setText(MirrlyPowerTelemetry.getModuleTitle(mod.moduleId));
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        nameTv.setTypeface(AndroidUtilities.bold());
        nameTv.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        nameTv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        titleCol.addView(nameTv);

        TextView subTv = new TextView(activity);
        subTv.setText(MirrlyPowerTelemetry.formatPercentCompact(mod.batteryPercent)
                + " • " + MirrlyPowerTelemetry.formatMah(mod.uah)
                + " • " + LocaleController.formatString(R.string.MirrlyPowerRatePerHourShort, String.format(Locale.US, "%.2f", mod.ratePctPerHour)));
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subTv.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        subTv.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, getResourceProvider()));
        titleCol.addView(subTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        if (LocaleController.isRTL) {
            titleRow.removeAllViews();
            titleRow.addView(titleCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
            titleRow.addView(icon, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL));
        } else {
            titleRow.addView(titleCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
        }
        root.addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Telemetry box
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(MirrlyUiKit.createTintedSurfaceDrawable(14, getResourceProvider(), false));

        TextView telTv = new TextView(activity);
        telTv.setText(mod.telemetryLine);
        telTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        telTv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        card.addView(telTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        for (SettingItem si : mod.settings) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView k = new TextView(activity);
            k.setText(si.title);
            k.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            k.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, getResourceProvider()));
            k.setSingleLine(true);
            k.setEllipsize(TextUtils.TruncateAt.END);

            TextView v = new TextView(activity);
            v.setText(si.status);
            v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            v.setTypeface(AndroidUtilities.bold());
            int vColor = si.isSaving
                    ? Theme.getColor(Theme.key_statisticChartLine_green, getResourceProvider())
                    : (si.active ? Theme.getColor(Theme.key_dialogTextBlue2, getResourceProvider()) : Theme.getColor(Theme.key_dialogTextGray3, getResourceProvider()));
            v.setTextColor(vColor);
            v.setSingleLine(true);

            row.addView(k, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));
            row.addView(v, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 3));
        }
        root.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        final BottomSheet sheet = MirrlyUiKit.createFrostedBottomSheet(activity, false, getResourceProvider(), root);

        ButtonWithCounterView openBtn = MirrlyUiKit.createFlickerButton(activity, getResourceProvider(), false);
        openBtn.setText(LocaleController.getString(R.string.MirrlyPowerOpenModuleSettings), false);
        openBtn.setOnClickListener(v -> {
            sheet.dismiss();
            openModuleSettings(mod.moduleId);
        });
        root.addView(openBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 0, 0, 8));

        TextView copyBtn = new TextView(activity);
        copyBtn.setText(LocaleController.getString(R.string.MirrlyPowerMenuCopyReport));
        copyBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        copyBtn.setTypeface(AndroidUtilities.bold());
        copyBtn.setGravity(Gravity.CENTER);
        copyBtn.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, getResourceProvider()));
        copyBtn.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, getResourceProvider()), 12, 12));
        copyBtn.setOnClickListener(v -> {
            PowerReport r = currentReport;
            if (r != null) {
                MirrlyUiKit.copyTextWithBulletin(
                        this,
                        r.buildClipboardReport(),
                        LocaleController.getString(R.string.MirrlyPowerReportCopied),
                        sheet,
                        getResourceProvider()
                );
            }
        });
        root.addView(copyBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));

        showDialog(sheet);
    }

    private void applyRecommendationWithUndo(@NonNull EcoRecommendation rec) {
        switch (rec.id) {
            case MirrlyPowerTelemetry.REC_PUSH_INTERVAL: {
                final int prevMin = MirrlyConfig.getPowerPushIntervalMinutes();
                MirrlyConfig.setPowerPushIntervalMinutes(MirrlyConfig.POWER_PUSH_INTERVAL_10_MIN);
                BulletinFactory.of(this).createUndoBulletin(
                        LocaleController.getString(R.string.MirrlyPowerRecAppliedPush),
                        () -> {
                            MirrlyConfig.setPowerPushIntervalMinutes(prevMin);
                            reloadReportAsync(true);
                        },
                        null
                ).show();
                break;
            }
            case MirrlyPowerTelemetry.REC_LIQUID_EVERYWHERE: {
                MirrlyConfig.setLiquidGlassEverywhere(false);
                BulletinFactory.of(this).createUndoBulletin(
                        LocaleController.getString(R.string.MirrlyPowerRecAppliedGlass),
                        () -> {
                            MirrlyConfig.setLiquidGlassEverywhere(true);
                            reloadReportAsync(true);
                        },
                        null
                ).show();
                break;
            }
            case MirrlyPowerTelemetry.REC_FLOWSEAL_PER_DC: {
                MirrlyConfig.setFlowsealPerDcEnabled(false);
                BulletinFactory.of(this).createUndoBulletin(
                        LocaleController.getString(R.string.MirrlyPowerRecAppliedFlowseal),
                        () -> {
                            MirrlyConfig.setFlowsealPerDcEnabled(true);
                            reloadReportAsync(true);
                        },
                        null
                ).show();
                break;
            }
            case MirrlyPowerTelemetry.REC_MONET_AMOLED: {
                MirrlyConfig.setMonetAmoled(true);
                MirrlyMonetThemeHelper.applyMonetMode(getParentActivity(), MirrlyConfig.getMonetMode(), true, null);
                BulletinFactory.of(this).createUndoBulletin(
                        LocaleController.getString(R.string.MirrlyPowerRecAppliedAmoled),
                        () -> {
                            MirrlyConfig.setMonetAmoled(false);
                            MirrlyMonetThemeHelper.applyMonetMode(getParentActivity(), MirrlyConfig.getMonetMode(), true, null);
                            reloadReportAsync(true);
                        },
                        null
                ).show();
                break;
            }
            case MirrlyPowerTelemetry.REC_SYSTEM_UNRESTRICTED:
                openBatteryOptimizationSettings();
                break;
        }
        reloadReportAsync(true);
    }

    @DrawableRes
    static int getModuleIconRes(int moduleId) {
        switch (moduleId) {
            case MirrlyPowerTelemetry.MODULE_ENGINE:
                return R.drawable.mirrly_settings_engine;
            case MirrlyPowerTelemetry.MODULE_PUSH:
                return R.drawable.msg_notifications;
            case MirrlyPowerTelemetry.MODULE_GLASS:
                return R.drawable.mirrly_settings_customization;
            case MirrlyPowerTelemetry.MODULE_VAULT:
                return R.drawable.mirrly_settings_vault;
            case MirrlyPowerTelemetry.MODULE_TRANSLATOR:
                return R.drawable.msg_translate;
            case MirrlyPowerTelemetry.MODULE_INTERFACE:
            default:
                return R.drawable.mirrly_settings_interface;
        }
    }

    // -------------------------------------------------------------------------
    // ViewPager & TabListView
    // -------------------------------------------------------------------------

    private class PageAdapter extends ViewPagerFixed.Adapter {
        @Override
        public int getItemCount() {
            return 3;
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
            if (position == TAB_ANALYTICS) {
                return LocaleController.getString(R.string.MirrlyPowerTabAnalytics);
            }
            if (position == TAB_CONTROL) {
                return LocaleController.getString(R.string.MirrlyPowerTabControl);
            }
            return LocaleController.getString(R.string.MirrlyPowerTabOverview);
        }
    }

    private class TabListView extends UniversalRecyclerView {

        final int tabType;
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
            this.tabType = tabType;
            setCaptureSectionsDecoratorAllowed(true);
            adapter.setApplyBackground(false);
            setSections();
            setSectionsDrawBackground(true);
            setClipToPadding(false);
            glassEngine.addScrolledView(this);

            DefaultItemAnimator animator = new DefaultItemAnimator();
            animator.setSupportsChangeAnimations(false);
            animator.setDelayAnimations(false);
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.setDurations(260);
            setItemAnimator(animator);

            enterAnimator = new RecyclerItemsEnterAnimator(this, true);

            setOnItemClickListener((view, position, x, y) -> {
                UItem item = adapter.getItem(position);
                if (item != null) {
                    onTabItemClick(this, item, view);
                }
            });
            setOnItemLongClickListener((view, position, x, y) -> {
                UItem item = adapter.getItem(position);
                return item != null && onTabItemLongClick(this, item, view);
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
    // Tab Content Builders
    // -------------------------------------------------------------------------

    private void fillItemsForTab(int tabType, ArrayList<UItem> items) {
        if (tabType == TAB_ANALYTICS) {
            fillAnalyticsTab(items);
        } else if (tabType == TAB_CONTROL) {
            fillControlTab(items);
        } else {
            fillOverviewTab(items);
        }
    }

    private ThreeSegmentSpec buildPeriodSegmentSpec() {
        int selIdx = selectedPeriod == MirrlyPowerTelemetry.PERIOD_SESSION ? 0
                : (selectedPeriod == MirrlyPowerTelemetry.PERIOD_7D ? 2 : 1);
        return new ThreeSegmentSpec(
                LocaleController.getString(R.string.MirrlyPowerPeriodSession),
                LocaleController.getString(R.string.MirrlyPowerPeriod24h),
                LocaleController.getString(R.string.MirrlyPowerPeriod7d),
                selIdx,
                this::onPeriodChanged
        );
    }

    private void fillOverviewTab(ArrayList<UItem> items) {
        items.add(ThreeSegmentCardCell.Factory.of(ID_PERIOD_OVERVIEW, buildPeriodSegmentSpec()));
        items.add(MirrlyPowerRingView.Factory.of(
                ID_RING_CHART,
                new RingSpec(
                        currentReport,
                        selectedRingModuleId,
                        ringShowMah,
                        reportGeneration,
                        moduleId -> {
                            selectedRingModuleId = (selectedRingModuleId == moduleId) ? -1 : moduleId;
                            if (selectedRingModuleId >= 0) {
                                highlightOverviewModuleRow(selectedRingModuleId);
                            }
                            syncAllAdaptersWithoutNotify();
                        },
                        () -> {
                            ringShowMah = !ringShowMah;
                            syncAllAdaptersWithoutNotify();
                        }
                )
        ));

        items.add(MirrlyPowerOverviewCell.Factory.of(
                ID_OVERVIEW_KPI,
                new OverviewSpec(currentReport, liveSystemSnapshot, reportGeneration)
        ));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyPowerModulesHeader)));
        PowerReport r = currentReport;
        if (r != null && r.rankedModules != null) {
            for (ModuleReport mod : r.rankedModules) {
                boolean expanded = expandedModules[mod.moduleId];
                items.add(ModuleImpactCell.Factory.of(
                        ID_MODULE_BASE + mod.moduleId,
                        new ModuleRowSpec(mod, expanded, reportGeneration)
                ));
                if (expanded) {
                    for (int i = 0; i < mod.settings.size(); i++) {
                        SettingItem si = mod.settings.get(i);
                        items.add(ModuleSettingRowCell.Factory.of(
                                ID_MODULE_SETTING_BASE + mod.moduleId * 20 + i,
                                new SettingRowSpec(si.title, si.status, si.active, si.isSaving)
                        ));
                    }
                    UItem openAction = UItem.asButton(
                            ID_MODULE_ACTION_BASE + mod.moduleId,
                            R.drawable.msg_settings,
                            LocaleController.getString(R.string.MirrlyPowerOpenModuleSettings)
                    );
                    openAction.accent();
                    items.add(openAction);
                }
            }
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyPowerModulesFootnote)));
    }

    private void fillAnalyticsTab(ArrayList<UItem> items) {
        items.add(ThreeSegmentCardCell.Factory.of(ID_PERIOD_ANALYTICS, buildPeriodSegmentSpec()));
        items.add(UItem.asShadow(null));

        items.add(StackBarChartFactory.of(ID_CHART_STACK_BAR, new ChartSpec(chartModulesData, sharedChartUi, reportGeneration)));
        items.add(UItem.asShadow(null));

        items.add(StackShareChartFactory.of(ID_CHART_STACK_SHARE, new ChartSpec(chartShareData, sharedChartUi, reportGeneration)));
        items.add(UItem.asShadow(null));

        items.add(DoubleLinearChartFactory.of(ID_CHART_BATT_POWER, new ChartSpec(chartBattPowerData, sharedChartUi, reportGeneration)));
        items.add(UItem.asShadow(null));

        items.add(Clock24hChartFactory.of(ID_CHART_CLOCK_24H, new ChartSpec(chartClock24hData, sharedChartUi, reportGeneration)));
        items.add(UItem.asShadow(null));

        items.add(SystemPowerCardCell.Factory.of(ID_SYSTEM_POWER_CARD, new SystemCardSpec(liveSystemSnapshot, reportGeneration)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            items.add(UItem.asButton(
                    ID_SYS_UNRESTRICTED_ROW,
                    R.drawable.msg_notifications,
                    LocaleController.getString(R.string.MirrlyBatteryUnrestricted),
                    liveSystemSnapshot.ignoringBatteryOptimizations
                            ? LocaleController.getString(R.string.MirrlyPowerStateUnrestricted)
                            : LocaleController.getString(R.string.MirrlyPowerStateOptimized)
            ));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyPowerSystemFootnote)));

        UItem copyBtn = UItem.asButton(
                ID_COPY_REPORT_BTN,
                R.drawable.msg_copy,
                LocaleController.getString(R.string.MirrlyPowerMenuCopyReport)
        );
        copyBtn.accent();
        items.add(copyBtn);

        UItem resetBtn = UItem.asButton(
                ID_RESET_STATS_BTN,
                R.drawable.msg_delete,
                LocaleController.getString(R.string.MirrlyPowerMenuReset)
        );
        resetBtn.red();
        items.add(resetBtn);
        items.add(UItem.asShadow(null));
    }

    private void fillControlTab(ArrayList<UItem> items) {
        PowerReport r = currentReport;
        if (r != null && r.recommendations != null && !r.recommendations.isEmpty()) {
            EcoRecommendation topRec = r.recommendations.get(0);
            items.add(EcoTipBannerCell.Factory.of(
                    ID_ECO_TIP_BANNER,
                    new EcoTipSpec(topRec, () -> applyRecommendationWithUndo(topRec))
            ));
            items.add(UItem.asShadow(null));
        }

        // Auto-Eco section
        items.add(PowerEcoSliderCell.Factory.of(
                ID_ECO_SLIDER,
                new EcoSliderSpec(
                        MirrlyConfig.getPowerAutoEcoLevel(),
                        MirrlyPowerPolicy.isAutoEcoActive(),
                        newLevel -> {
                            MirrlyConfig.setPowerAutoEcoLevel(newLevel);
                            syncAllAdaptersWithoutNotify();
                        }
                )
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_ECO_FOLLOW_SYSTEM,
                R.drawable.settings_power,
                LocaleController.getString(R.string.MirrlyPowerFollowSystemSaver),
                MirrlyConfig.isPowerFollowSystemSaver()
        ));
        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyPowerAutoEcoActionsHeader)));
        items.add(UItem.asCheck(ID_ECO_FLAG_PUSH, LocaleController.getString(R.string.MirrlyPowerEcoRulePush))
                .setChecked(MirrlyConfig.isPowerEcoFlagEnabled(MirrlyConfig.POWER_ECO_FLAG_PUSH_INTERVAL)));
        items.add(UItem.asCheck(ID_ECO_FLAG_LIQUID, LocaleController.getString(R.string.MirrlyPowerEcoRuleLiquid))
                .setChecked(MirrlyConfig.isPowerEcoFlagEnabled(MirrlyConfig.POWER_ECO_FLAG_LIQUID_EVERYWHERE)));
        items.add(UItem.asCheck(ID_ECO_FLAG_BLUR, LocaleController.getString(R.string.MirrlyPowerEcoRuleBlur))
                .setChecked(MirrlyConfig.isPowerEcoFlagEnabled(MirrlyConfig.POWER_ECO_FLAG_GLASS_BLUR)));
        items.add(UItem.asCheck(ID_ECO_FLAG_VIBRATION, LocaleController.getString(R.string.MirrlyPowerEcoRuleVibration))
                .setChecked(MirrlyConfig.isPowerEcoFlagEnabled(MirrlyConfig.POWER_ECO_FLAG_VIBRATIONS)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyPowerAutoEcoHint)));

        // Background push keep-alive section
        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyPowerPushSectionHeader)));
        int userMin = MirrlyConfig.getPowerPushIntervalMinutes();
        int intervalIdx = userMin >= MirrlyConfig.POWER_PUSH_INTERVAL_20_MIN ? 2
                : (userMin >= MirrlyConfig.POWER_PUSH_INTERVAL_10_MIN ? 1 : 0);
        items.add(ThreeSegmentCardCell.Factory.of(
                ID_PUSH_INTERVAL_SEG,
                new ThreeSegmentSpec(
                        LocaleController.formatString(R.string.MirrlyPowerIntervalMinutesShort, MirrlyConfig.POWER_PUSH_INTERVAL_3_MIN),
                        LocaleController.formatString(R.string.MirrlyPowerIntervalMinutesShort, MirrlyConfig.POWER_PUSH_INTERVAL_10_MIN),
                        LocaleController.formatString(R.string.MirrlyPowerIntervalMinutesShort, MirrlyConfig.POWER_PUSH_INTERVAL_20_MIN),
                        intervalIdx,
                        idx -> {
                            int target = idx == 2 ? MirrlyConfig.POWER_PUSH_INTERVAL_20_MIN
                                    : (idx == 1 ? MirrlyConfig.POWER_PUSH_INTERVAL_10_MIN : MirrlyConfig.POWER_PUSH_INTERVAL_3_MIN);
                            MirrlyConfig.setPowerPushIntervalMinutes(target);
                            syncAllAdaptersWithoutNotify();
                        }
                )
        ));
        SharedPreferences notifPrefs = MessagesController.getGlobalNotificationsSettings();
        boolean pushServiceOn = notifPrefs.getBoolean("pushService", true);
        boolean pushConnOn = notifPrefs.getBoolean("pushConnection", true);
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_PUSH_SERVICE_TOGGLE,
                R.drawable.msg_notifications,
                LocaleController.getString(R.string.NotificationsService),
                pushServiceOn
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_PUSH_CONN_TOGGLE,
                R.drawable.msg_speed,
                LocaleController.getString(R.string.NotificationsServiceConnection),
                pushConnOn
        ));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            items.add(UItem.asButton(
                    ID_PUSH_UNRESTRICTED_ROW,
                    R.drawable.msg_info,
                    LocaleController.getString(R.string.MirrlyBatteryUnrestricted),
                    liveSystemSnapshot.ignoringBatteryOptimizations
                            ? LocaleController.getString(R.string.MirrlyPowerStateUnrestricted)
                            : LocaleController.getString(R.string.MirrlyPowerStateOptimized)
            ));
        }
        int effMin = MirrlyPowerPolicy.getEffectivePushKeepAliveMinutes();
        String pushHint = MirrlyPowerPolicy.isAutoEcoActive() && effMin > userMin
                ? LocaleController.formatString(R.string.MirrlyPowerPushHintEcoOverride, effMin)
                : LocaleController.getString(R.string.MirrlyPowerPushHint);
        items.add(UItem.asShadow(pushHint));

        // Quick Mirrly Subsystem Switches
        items.add(UItem.asHeader(LocaleController.getString(R.string.MirrlyPowerQuickSwitchesHeader)));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SW_LIQUID_EVERYWHERE,
                R.drawable.mirrly_settings_customization,
                LocaleController.getString(R.string.MirrlyGlassEverywhereToggle),
                MirrlyConfig.isLiquidGlassEverywhere()
        ));
        if (MirrlyConfig.isGlassLiquidSupported()) {
            items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                    ID_SW_LIQUID_LENS,
                    R.drawable.msg_palette,
                    LocaleController.getString(R.string.MirrlyGlassLiquidToggle),
                    MirrlyConfig.isGlassLiquidEnabled()
            ));
        }
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SW_GLASS_BLUR,
                R.drawable.msg_theme,
                LocaleController.getString(R.string.MirrlyGlassBlurToggle),
                MirrlyConfig.isGlassBlurEnabled()
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SW_DIALOGS_HEADER,
                R.drawable.mirrly_settings_interface,
                LocaleController.getString(R.string.MirrlyGlassDialogsHeader),
                MirrlyConfig.isDialogsGlassHeader()
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SW_MONET_AMOLED,
                R.drawable.msg_theme,
                LocaleController.getString(R.string.MirrlyPowerSettingMonetAmoled),
                MirrlyConfig.isMonetAmoled()
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SW_FLOWSEAL_PER_DC,
                R.drawable.mirrly_settings_engine,
                LocaleController.getString(R.string.MirrlyNetworkFlowsealPerDcEnable),
                MirrlyConfig.flowsealPerDcEnabled
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SW_SAVE_DELETED,
                R.drawable.msg_delete,
                LocaleController.getString(R.string.MirrlyVaultSaveDeleted),
                MirrlyConfig.isSaveDeletedMessages()
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SW_SAVE_DELETED_CHATS,
                R.drawable.msg_list,
                LocaleController.getString(R.string.MirrlyVaultSaveDeletedChats),
                MirrlyConfig.isSaveDeletedChats()
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SW_SAVE_EDIT_HISTORY,
                R.drawable.mirrly_settings_vault,
                LocaleController.getString(R.string.MirrlyVaultSaveHistory),
                MirrlyConfig.saveEditHistory
        ));
        items.add(MirrlyUiKit.IconSwitchCell.Factory.of(
                ID_SW_DISABLE_VIBRATION,
                R.drawable.mirrly_settings_chats,
                LocaleController.getString(R.string.MirrlyVibrationDisableAll),
                MirrlyConfig.isDisableAllVibrations()
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MirrlyPowerQuickSwitchesHint)));

        // Optional shortcut to stock Telegram LiteMode
        items.add(SettingsActivity.SettingCell.Factory.of(
                ID_OPEN_TG_LITEMODE,
                IconBackgroundColors.ORANGE_DEEP.top,
                IconBackgroundColors.ORANGE_DEEP.bottom,
                R.drawable.settings_power,
                LocaleController.getString(R.string.MirrlyPowerOpenTelegramLiteMode),
                LocaleController.getString(R.string.MirrlyPowerOpenTelegramLiteModeSub)
        ));
        items.add(UItem.asShadow(null));
    }

    // -------------------------------------------------------------------------
    // Click & Long-Click Handlers
    // -------------------------------------------------------------------------

    private void onTabItemClick(@NonNull TabListView listView, @NonNull UItem item, @NonNull View view) {
        final int id = item.id;

        if (id >= ID_MODULE_BASE && id < ID_MODULE_BASE + MirrlyPowerTelemetry.MODULE_COUNT) {
            int moduleId = id - ID_MODULE_BASE;
            expandedModules[moduleId] = !expandedModules[moduleId];
            AndroidUtilities.vibrateCursor(view);
            updateTabAdapter(TAB_OVERVIEW, true);
            return;
        }
        if (id >= ID_MODULE_ACTION_BASE && id < ID_MODULE_ACTION_BASE + MirrlyPowerTelemetry.MODULE_COUNT) {
            openModuleSettings(id - ID_MODULE_ACTION_BASE);
            return;
        }

        switch (id) {
            case ID_SYS_UNRESTRICTED_ROW:
            case ID_PUSH_UNRESTRICTED_ROW:
                openBatteryOptimizationSettings();
                break;

            case ID_COPY_REPORT_BTN:
                copyFullReport();
                break;

            case ID_RESET_STATS_BTN:
                confirmResetStats();
                break;

            case ID_ECO_FOLLOW_SYSTEM: {
                boolean next = !MirrlyConfig.isPowerFollowSystemSaver();
                MirrlyConfig.setPowerFollowSystemSaver(next);
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_ECO_FLAG_PUSH: {
                boolean next = !MirrlyConfig.isPowerEcoFlagEnabled(MirrlyConfig.POWER_ECO_FLAG_PUSH_INTERVAL);
                MirrlyConfig.setPowerEcoFlag(MirrlyConfig.POWER_ECO_FLAG_PUSH_INTERVAL, next);
                toggleCheckInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_ECO_FLAG_LIQUID: {
                boolean next = !MirrlyConfig.isPowerEcoFlagEnabled(MirrlyConfig.POWER_ECO_FLAG_LIQUID_EVERYWHERE);
                MirrlyConfig.setPowerEcoFlag(MirrlyConfig.POWER_ECO_FLAG_LIQUID_EVERYWHERE, next);
                toggleCheckInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_ECO_FLAG_BLUR: {
                boolean next = !MirrlyConfig.isPowerEcoFlagEnabled(MirrlyConfig.POWER_ECO_FLAG_GLASS_BLUR);
                MirrlyConfig.setPowerEcoFlag(MirrlyConfig.POWER_ECO_FLAG_GLASS_BLUR, next);
                toggleCheckInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_ECO_FLAG_VIBRATION: {
                boolean next = !MirrlyConfig.isPowerEcoFlagEnabled(MirrlyConfig.POWER_ECO_FLAG_VIBRATIONS);
                MirrlyConfig.setPowerEcoFlag(MirrlyConfig.POWER_ECO_FLAG_VIBRATIONS, next);
                toggleCheckInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_PUSH_SERVICE_TOGGLE: {
                SharedPreferences prefs = MessagesController.getGlobalNotificationsSettings();
                boolean next = !prefs.getBoolean("pushService", true);
                prefs.edit().putBoolean("pushService", next).apply();
                ApplicationLoader.startPushService();
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_PUSH_CONN_TOGGLE: {
                SharedPreferences prefs = MessagesController.getGlobalNotificationsSettings();
                boolean next = !prefs.getBoolean("pushConnection", true);
                prefs.edit().putBoolean("pushConnection", next).apply();
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        ConnectionsManager.getInstance(a).setPushConnectionEnabled(next);
                    }
                }
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_SW_LIQUID_EVERYWHERE: {
                boolean next = !MirrlyConfig.isLiquidGlassEverywhere();
                MirrlyConfig.setLiquidGlassEverywhere(next);
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_SW_LIQUID_LENS: {
                boolean next = !MirrlyConfig.isGlassLiquidEnabled();
                MirrlyConfig.setGlassLiquidEnabled(next);
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_SW_GLASS_BLUR: {
                boolean next = !MirrlyConfig.isGlassBlurEnabled();
                MirrlyConfig.setGlassBlurEnabled(next);
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_SW_DIALOGS_HEADER: {
                boolean next = !MirrlyConfig.isDialogsGlassHeader();
                MirrlyConfig.setDialogsGlassHeader(next);
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_SW_MONET_AMOLED: {
                boolean next = !MirrlyConfig.isMonetAmoled();
                MirrlyConfig.setMonetAmoled(next);
                if (MirrlyConfig.getMonetMode() != MirrlyConfig.MONET_MODE_OFF) {
                    MirrlyMonetThemeHelper.applyMonetMode(getParentActivity(), MirrlyConfig.getMonetMode(), true, view);
                }
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_SW_FLOWSEAL_PER_DC: {
                boolean next = !MirrlyConfig.flowsealPerDcEnabled;
                MirrlyConfig.setFlowsealPerDcEnabled(next);
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_SW_SAVE_DELETED: {
                boolean next = !MirrlyConfig.isSaveDeletedMessages();
                MirrlyConfig.setSaveDeletedMessages(next);
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_SW_SAVE_DELETED_CHATS: {
                boolean next = !MirrlyConfig.isSaveDeletedChats();
                MirrlyConfig.setSaveDeletedChats(next);
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_SW_SAVE_EDIT_HISTORY: {
                boolean next = !MirrlyConfig.saveEditHistory;
                MirrlyConfig.setSaveEditHistory(next);
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_SW_DISABLE_VIBRATION: {
                boolean next = !MirrlyConfig.isDisableAllVibrations();
                MirrlyConfig.setDisableAllVibrations(next);
                toggleSwitchInPlace(view, next);
                syncAllAdaptersWithoutNotify();
                break;
            }
            case ID_OPEN_TG_LITEMODE:
                presentFragment(new LiteModeSettingsActivity());
                break;
        }
    }

    private void toggleSwitchInPlace(@NonNull View view, boolean checked) {
        if (view instanceof MirrlyUiKit.IconSwitchCell) {
            ((MirrlyUiKit.IconSwitchCell) view).setChecked(checked);
        }
    }

    private void toggleCheckInPlace(@NonNull View view, boolean checked) {
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(checked);
        }
    }

    private boolean onTabItemLongClick(@NonNull TabListView listView, @NonNull UItem item, @NonNull View view) {
        if (item.id >= ID_MODULE_BASE && item.id < ID_MODULE_BASE + MirrlyPowerTelemetry.MODULE_COUNT) {
            int moduleId = item.id - ID_MODULE_BASE;
            PowerReport r = currentReport;
            if (r == null || r.modulesById == null || moduleId < 0 || moduleId >= r.modulesById.length) {
                return false;
            }
            ModuleReport mod = r.modulesById[moduleId];
            ItemOptions options = ItemOptions.makeOptions(this, view);
            options.add(R.drawable.msg_info, LocaleController.getString(R.string.MirrlyPowerActionDetails), () -> showModuleDetailSheet(mod));
            options.add(R.drawable.msg_settings, LocaleController.getString(R.string.MirrlyPowerOpenModuleSettings), () -> openModuleSettings(moduleId));
            options.add(R.drawable.msg_copy, LocaleController.getString(R.string.MirrlyPowerMenuCopyReport), this::copyFullReport);
            Drawable scrim = listView.getClipBackground(view);
            if (scrim != null) {
                options.setScrimViewBackground(scrim);
            }
            options.setBlur(true);
            options.show();
            return true;
        }
        return false;
    }

    // =========================================================================
    // Custom Native View 1: 3-Segment Glass Pill (Period & Push Interval)
    // =========================================================================

    static final class ThreeSegmentSpec {
        final CharSequence label0;
        final CharSequence label1;
        final CharSequence label2;
        final int selectedIndex;
        final Utilities.Callback<Integer> onSelected;

        ThreeSegmentSpec(CharSequence l0, CharSequence l1, CharSequence l2, int selectedIndex, Utilities.Callback<Integer> onSelected) {
            this.label0 = l0;
            this.label1 = l1;
            this.label2 = l2;
            this.selectedIndex = Math.max(0, Math.min(2, selectedIndex));
            this.onSelected = onSelected;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ThreeSegmentSpec)) return false;
            ThreeSegmentSpec that = (ThreeSegmentSpec) o;
            return selectedIndex == that.selectedIndex
                    && TextUtils.equals(label0, that.label0)
                    && TextUtils.equals(label1, that.label1)
                    && TextUtils.equals(label2, that.label2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label0, label1, label2, selectedIndex);
        }
    }

    static class ThreeSegmentCardCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trackBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbSheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF trackRect = new RectF();
        private final RectF thumbRect = new RectF();
        private final Path clipThumb = new Path();
        private final CellFlickerDrawable thumbFlicker = new CellFlickerDrawable(28, 60, 130);
        private final AnimatedFloat thumbProgress = new AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);

        private final TextView[] labels = new TextView[3];
        private int selectedIndex = 0;
        private Utilities.Callback<Integer> onSelected;
        private int lastTrackTop = Integer.MIN_VALUE;
        private int lastThumbTop = Integer.MIN_VALUE;
        private int lastGlassGen = -1;
        private boolean cachedIsDark;
        private int cachedAccent;
        private int cachedDivider;

        ThreeSegmentCardCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            setPadding(dp(12), dp(8), dp(12), dp(8));

            trackBorderPaint.setStyle(Paint.Style.STROKE);
            thumbBorderPaint.setStyle(Paint.Style.STROKE);
            thumbFlicker.drawFrame = false;
            thumbFlicker.repeatProgress = 4.6f;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(3), dp(3), dp(3), dp(3));

            for (int i = 0; i < 3; i++) {
                final int logicalIndex = LocaleController.isRTL ? (2 - i) : i;
                FrameLayout btn = new FrameLayout(context);
                ScaleStateListAnimator.apply(btn, 0.03f, 1.0f);
                MirrlyUiKit.attachDisallowInterceptTouch(btn);

                TextView tv = new TextView(context);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
                tv.setTypeface(AndroidUtilities.bold());
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setGravity(Gravity.CENTER);
                btn.addView(tv, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 4, 0, 4, 0));

                labels[logicalIndex] = tv;
                btn.setOnClickListener(v -> {
                    if (selectedIndex == logicalIndex) {
                        return;
                    }
                    selectedIndex = logicalIndex;
                    thumbProgress.set(toVisualIndex(selectedIndex), true);
                    AndroidUtilities.vibrateCursor(btn);
                    invalidate();
                    if (onSelected != null) {
                        onSelected.run(logicalIndex);
                    }
                });
                row.addView(btn, LayoutHelper.createLinear(0, 36, 1f));
            }
            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42));
            updateColors();
        }

        private int toVisualIndex(int logical) {
            return LocaleController.isRTL ? (2 - logical) : logical;
        }

        void bind(@NonNull ThreeSegmentSpec spec) {
            this.onSelected = spec.onSelected;
            labels[0].setText(spec.label0);
            labels[1].setText(spec.label1);
            labels[2].setText(spec.label2);
            if (this.selectedIndex != spec.selectedIndex) {
                this.selectedIndex = spec.selectedIndex;
                thumbProgress.set(toVisualIndex(this.selectedIndex), isAttachedToWindow());
            } else {
                thumbProgress.set(toVisualIndex(this.selectedIndex), false);
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
            thumbFlicker.setColors(isDark ? Color.WHITE : accent, isDark ? 16 : 12, isDark ? 36 : 26);
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            thumbFlicker.setParentWidth(Math.max(1, w / 3));
        }

        private void ensureGradients(int trackTop, int trackBottom, int thumbTop, int thumbBottom) {
            int gen = GlassEngine.getGlassSettingsGenerationId();
            float rimScale = Utilities.clamp(MirrlyConfig.getGlassRimScale(), 1.6f, 0.25f);
            if (trackTop != lastTrackTop || gen != lastGlassGen) {
                lastTrackTop = trackTop;
                int alpha = cachedIsDark ? 0x7A : 0x55;
                int topRim = ColorUtils.blendARGB(cachedDivider, Color.WHITE, cachedIsDark ? 0.28f : 0.45f);
                trackBorderPaint.setStrokeWidth(dpf2(0.75f) * Utilities.clamp(rimScale, 1.35f, 0.5f));
                trackBorderPaint.setShader(new LinearGradient(
                        0f, trackTop, 0f, trackBottom,
                        ColorUtils.setAlphaComponent(topRim, Math.min(255, Math.round(alpha * 1.15f * rimScale))),
                        ColorUtils.setAlphaComponent(cachedDivider, Math.min(255, Math.round(alpha * 0.75f * rimScale))),
                        Shader.TileMode.CLAMP
                ));
            }
            if (thumbTop != lastThumbTop || gen != lastGlassGen) {
                lastThumbTop = thumbTop;
                lastGlassGen = gen;
                int topSpec = ColorUtils.blendARGB(cachedAccent, Color.WHITE, cachedIsDark ? 0.55f : 0.38f);
                thumbBorderPaint.setStrokeWidth(dpf2(0.85f) * Utilities.clamp(rimScale, 1.45f, 0.45f));
                thumbBorderPaint.setShader(new LinearGradient(
                        0f, thumbTop, 0f, thumbBottom,
                        ColorUtils.setAlphaComponent(topSpec, Math.min(255, Math.round((cachedIsDark ? 0xA8 : 0x7A) * rimScale))),
                        ColorUtils.setAlphaComponent(cachedAccent, Math.min(255, Math.round((cachedIsDark ? 0x74 : 0x52) * rimScale))),
                        Shader.TileMode.CLAMP
                ));
                int sheenA = Math.min(255, Math.round((cachedIsDark ? 0x22 : 0x38) * Utilities.clamp(rimScale, 1.35f, 0f)));
                thumbSheenPaint.setShader(new LinearGradient(
                        0f, thumbTop, 0f, thumbTop + Math.max(1f, (thumbBottom - thumbTop) * 0.65f),
                        ColorUtils.setAlphaComponent(Color.WHITE, sheenA),
                        0x00FFFFFF,
                        Shader.TileMode.CLAMP
                ));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float l = getPaddingLeft();
            float t = getPaddingTop();
            float r = getWidth() - getPaddingRight();
            float b = getHeight() - getPaddingBottom();

            trackRect.set(l, t, r, b);
            float trackRadius = dp(13);

            float visualT = thumbProgress.set(toVisualIndex(selectedIndex));
            float inset = dp(3);
            float segW = (trackRect.width() - inset * 2f) / 3f;
            float thumbLeft = trackRect.left + inset + segW * visualT;
            thumbRect.set(thumbLeft, trackRect.top + inset, thumbLeft + segW, trackRect.bottom - inset);

            ensureGradients(Math.round(trackRect.top), Math.round(trackRect.bottom), Math.round(thumbRect.top), Math.round(thumbRect.bottom));

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
            for (int logical = 0; logical < 3; logical++) {
                float dist = Math.abs(visualT - toVisualIndex(logical));
                float frac = Utilities.clamp(1f - dist, 1f, 0f);
                labels[logical].setTextColor(ColorUtils.blendARGB(inactiveText, activeText, frac));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    heightMeasureSpec
            );
        }

        public static class Factory extends UItem.UItemFactory<ThreeSegmentCardCell> {
            static {
                setup(new Factory());
            }

            @Override
            public ThreeSegmentCardCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new ThreeSegmentCardCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof ThreeSegmentSpec) {
                    ((ThreeSegmentCardCell) view).bind((ThreeSegmentSpec) item.object);
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
                return equals(a, b) && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, ThreeSegmentSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    // =========================================================================
    // Custom Native View 2: Interactive Ring Chart (CacheChart-inspired)
    // =========================================================================

    static final class RingSpec {
        @Nullable final PowerReport report;
        final int selectedModuleId;
        final boolean showMah;
        final int generation;
        final Utilities.Callback<Integer> onSectorSelected;
        final Runnable onCenterToggle;

        RingSpec(
                @Nullable PowerReport report,
                int selectedModuleId,
                boolean showMah,
                int generation,
                Utilities.Callback<Integer> onSectorSelected,
                Runnable onCenterToggle
        ) {
            this.report = report;
            this.selectedModuleId = selectedModuleId;
            this.showMah = showMah;
            this.generation = generation;
            this.onSectorSelected = onSectorSelected;
            this.onCenterToggle = onCenterToggle;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RingSpec)) return false;
            RingSpec that = (RingSpec) o;
            return selectedModuleId == that.selectedModuleId
                    && showMah == that.showMah
                    && generation == that.generation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(selectedModuleId, showMah, generation);
        }
    }

    static class MirrlyPowerRingView extends View implements Theme.Colorable {

        private static final float GAP_DEG = 5.5f;
        private static final float MIN_ARC_DEG = 10f;

        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();

        private final AnimatedTextView.AnimatedTextDrawable topText = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        private final AnimatedTextView.AnimatedTextDrawable bottomText = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        private final AnimatedTextView.AnimatedTextDrawable hintText = new AnimatedTextView.AnimatedTextDrawable(false, true, true);

        private final AnimatedFloat[] sectorSpring = new AnimatedFloat[MirrlyPowerTelemetry.MODULE_COUNT];
        private final float[] startAngles = new float[MirrlyPowerTelemetry.MODULE_COUNT];
        private final float[] sweepAngles = new float[MirrlyPowerTelemetry.MODULE_COUNT];
        private final int[] sectorModuleIds = new int[MirrlyPowerTelemetry.MODULE_COUNT];
        private final int[] sectorColorKeys = new int[MirrlyPowerTelemetry.MODULE_COUNT];
        private final SweepGradient[] cachedGradients = new SweepGradient[MirrlyPowerTelemetry.MODULE_COUNT];
        private int lastGradCx = -1;
        private int lastGradCy = -1;
        private int activeSectorCount = 0;

        @Nullable
        private PowerReport report;
        private int selectedModuleId = -1;
        private boolean showMah = false;
        private Utilities.Callback<Integer> onSectorSelected;
        private Runnable onCenterToggle;

        MirrlyPowerRingView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setLayerType(LAYER_TYPE_HARDWARE, null);

            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);

            for (int i = 0; i < MirrlyPowerTelemetry.MODULE_COUNT; i++) {
                sectorSpring[i] = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            }

            topText.setCallback(this);
            topText.setGravity(Gravity.CENTER);
            topText.setTypeface(AndroidUtilities.bold());
            topText.setTextSize(dp(30));
            topText.setAnimationProperties(0.35f, 0, 340, CubicBezierInterpolator.EASE_OUT_QUINT);

            bottomText.setCallback(this);
            bottomText.setGravity(Gravity.CENTER);
            bottomText.setTextSize(dp(13));
            bottomText.setAnimationProperties(0.35f, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);

            hintText.setCallback(this);
            hintText.setGravity(Gravity.CENTER);
            hintText.setTextSize(dp(11.5f));
            hintText.setAnimationProperties(0.35f, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);

            updateColors();
        }

        void bind(@NonNull RingSpec spec) {
            this.report = spec.report;
            this.selectedModuleId = spec.selectedModuleId;
            this.showMah = spec.showMah;
            this.onSectorSelected = spec.onSectorSelected;
            this.onCenterToggle = spec.onCenterToggle;
            recomputeSectors();
            updateCenterLabels(isAttachedToWindow());
            invalidate();
        }

        private void recomputeSectors() {
            activeSectorCount = 0;
            if (report == null || report.rankedModules == null || report.totalMirrlyUah <= 0) {
                return;
            }
            long total = 0L;
            int[] ids = new int[MirrlyPowerTelemetry.MODULE_COUNT];
            int[] colors = new int[MirrlyPowerTelemetry.MODULE_COUNT];
            long[] values = new long[MirrlyPowerTelemetry.MODULE_COUNT];
            for (ModuleReport m : report.rankedModules) {
                if (m.uah > 0) {
                    ids[activeSectorCount] = m.moduleId;
                    colors[activeSectorCount] = m.colorKey;
                    values[activeSectorCount] = m.uah;
                    total += m.uah;
                    activeSectorCount++;
                }
            }
            if (activeSectorCount == 0 || total <= 0) {
                activeSectorCount = 0;
                return;
            }
            if (activeSectorCount == 1) {
                sectorModuleIds[0] = ids[0];
                sectorColorKeys[0] = colors[0];
                startAngles[0] = -90f;
                sweepAngles[0] = 359.5f;
                return;
            }

            float totalGap = activeSectorCount * GAP_DEG;
            float avail = Math.max(60f, 360f - totalGap);
            float[] raw = new float[activeSectorCount];
            float sum = 0f;
            for (int i = 0; i < activeSectorCount; i++) {
                raw[i] = Math.max(MIN_ARC_DEG, avail * (values[i] / (float) total));
                sum += raw[i];
            }
            float scale = avail / Math.max(1f, sum);
            float cursor = -90f + GAP_DEG * 0.5f;
            for (int i = 0; i < activeSectorCount; i++) {
                sectorModuleIds[i] = ids[i];
                sectorColorKeys[i] = colors[i];
                startAngles[i] = cursor;
                sweepAngles[i] = raw[i] * scale;
                cursor += sweepAngles[i] + GAP_DEG;
            }
        }

        private void updateCenterLabels(boolean animated) {
            if (report == null) {
                topText.setText("0%", animated);
                bottomText.setText(LocaleController.getString(R.string.MirrlyPowerRingTitle), animated);
                hintText.setText(LocaleController.getString(R.string.MirrlyPowerCollectingShort), animated);
                return;
            }
            if (selectedModuleId >= 0 && selectedModuleId < report.modulesById.length) {
                ModuleReport m = report.modulesById[selectedModuleId];
                topText.setText(showMah ? MirrlyPowerTelemetry.formatMah(m.uah) : MirrlyPowerTelemetry.formatPercentCompact(m.batteryPercent), animated);
                bottomText.setText(MirrlyPowerTelemetry.getModuleShortTitle(m.moduleId), animated);
                hintText.setText(LocaleController.formatString(R.string.MirrlyPowerRingShareOfMirrly, MirrlyPowerTelemetry.formatPercentCompact(m.sharePercent)), animated);
                return;
            }
            topText.setText(showMah ? MirrlyPowerTelemetry.formatMah(report.totalMirrlyUah) : MirrlyPowerTelemetry.formatPercentCompact(report.totalBatteryPercent), animated);
            bottomText.setText(LocaleController.getString(R.string.MirrlyPowerRingTitle), animated);
            if (report.totalMirrlyUah <= 0) {
                hintText.setText(LocaleController.getString(R.string.MirrlyPowerCollectingShort), animated);
            } else {
                hintText.setText(showMah ? MirrlyPowerTelemetry.formatPercentCompact(report.totalBatteryPercent) : MirrlyPowerTelemetry.formatMah(report.totalMirrlyUah), animated);
            }
        }

        @Override
        public void updateColors() {
            int track = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), 0x26);
            trackPaint.setColor(track);
            topText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            bottomText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            hintText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider));
            lastGradCx = -1;
            invalidate();
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == topText || who == bottomText || who == hintText || super.verifyDrawable(who);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float cx = getWidth() * 0.5f;
                float cy = getHeight() * 0.5f;
                float dx = event.getX() - cx;
                float dy = event.getY() - cy;
                float dist = (float) Math.hypot(dx, dy);
                float baseR = Math.min(getWidth(), getHeight()) * 0.5f - dp(28);
                float innerHit = baseR - dp(24);
                float outerHit = baseR + dp(26);

                if (dist < innerHit) {
                    showMah = !showMah;
                    AndroidUtilities.vibrateCursor(this);
                    updateCenterLabels(true);
                    if (onCenterToggle != null) {
                        onCenterToggle.run();
                    }
                    return true;
                }
                if (dist <= outerHit && activeSectorCount > 0) {
                    float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                    // Normalize angle into [-90, 270)
                    if (angle < -90f) {
                        angle += 360f;
                    }
                    for (int i = 0; i < activeSectorCount; i++) {
                        float s = startAngles[i] - GAP_DEG * 0.5f;
                        float e = startAngles[i] + sweepAngles[i] + GAP_DEG * 0.5f;
                        if (angle >= s && angle <= e) {
                            int hitMod = sectorModuleIds[i];
                            selectedModuleId = (selectedModuleId == hitMod) ? -1 : hitMod;
                            AndroidUtilities.vibrateCursor(this);
                            updateCenterLabels(true);
                            invalidate();
                            if (onSectorSelected != null) {
                                onSectorSelected.run(hitMod);
                            }
                            return true;
                        }
                    }
                }
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float stroke = dp(18);
            float baseR = Math.min(getWidth(), getHeight()) * 0.5f - dp(28);
            if (baseR <= dp(32)) {
                return;
            }

            trackPaint.setStrokeWidth(stroke);
            canvas.drawCircle(cx, cy, baseR, trackPaint);

            int cxInt = Math.round(cx);
            int cyInt = Math.round(cy);
            if (cxInt != lastGradCx || cyInt != lastGradCy) {
                lastGradCx = cxInt;
                lastGradCy = cyInt;
                for (int i = 0; i < MirrlyPowerTelemetry.MODULE_COUNT; i++) {
                    cachedGradients[i] = null;
                }
            }

            for (int i = 0; i < activeSectorCount; i++) {
                int modId = sectorModuleIds[i];
                float sel = sectorSpring[modId].set(selectedModuleId == modId ? 1f : 0f);
                float expand = dp(8) * sel;
                float r = baseR + expand;
                arcRect.set(cx - r, cy - r, cx + r, cy + r);

                if (cachedGradients[modId] == null) {
                    int baseColor = Theme.getColor(sectorColorKeys[i], resourcesProvider);
                    int brightColor = ColorUtils.blendARGB(baseColor, Color.WHITE, 0.22f);
                    cachedGradients[modId] = new SweepGradient(cx, cy, new int[]{brightColor, baseColor, brightColor}, new float[]{0f, 0.5f, 1f});
                }
                float dimAlpha = (selectedModuleId >= 0 && selectedModuleId != modId) ? 0.35f : 1f;
                arcPaint.setStrokeWidth(stroke + dp(2.5f) * sel);
                arcPaint.setShader(cachedGradients[modId]);
                arcPaint.setAlpha(Math.round(255 * dimAlpha));
                canvas.drawArc(arcRect, startAngles[i], sweepAngles[i], false, arcPaint);
            }
            arcPaint.setShader(null);
            arcPaint.setAlpha(255);

            int textW = Math.round(baseR * 1.48f);
            int halfW = textW / 2;
            int topY = Math.round(cy - dp(24));
            topText.setBounds(Math.round(cx) - halfW, topY - dp(18), Math.round(cx) + halfW, topY + dp(18));
            topText.draw(canvas);

            int midY = Math.round(cy + dp(6));
            bottomText.setBounds(Math.round(cx) - halfW, midY - dp(10), Math.round(cx) + halfW, midY + dp(10));
            bottomText.draw(canvas);

            int botY = Math.round(cy + dp(26));
            hintText.setBounds(Math.round(cx) - halfW, botY - dp(10), Math.round(cx) + halfW, botY + dp(10));
            hintText.draw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(236), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<MirrlyPowerRingView> {
            static {
                setup(new Factory());
            }

            @Override
            public MirrlyPowerRingView createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new MirrlyPowerRingView(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof RingSpec) {
                    ((MirrlyPowerRingView) view).bind((RingSpec) item.object);
                }
            }

            @Override
            public boolean isShadow() {
                return true;
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
                return equals(a, b) && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, RingSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    // =========================================================================
    // Custom Native View 3: Overview KPI Card (StatisticActivity.OverviewCell)
    // =========================================================================

    static final class OverviewSpec {
        @Nullable final PowerReport report;
        @NonNull final SystemPowerSnapshot liveSnap;
        final int generation;

        OverviewSpec(@Nullable PowerReport report, @NonNull SystemPowerSnapshot liveSnap, int generation) {
            this.report = report;
            this.liveSnap = liveSnap;
            this.generation = generation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OverviewSpec)) return false;
            OverviewSpec that = (OverviewSpec) o;
            return generation == that.generation && liveSnap.timestampMs == that.liveSnap.timestampMs;
        }

        @Override
        public int hashCode() {
            return Objects.hash(generation, liveSnap.timestampMs);
        }
    }

    static class MirrlyPowerOverviewCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final ChartHeaderView headerView;
        private final AnimatedTextView[] primary = new AnimatedTextView[4];
        private final AnimatedTextView[] badge = new AnimatedTextView[4];
        private final TextView[] caption = new TextView[4];
        private final TextView savingsBanner;

        MirrlyPowerOverviewCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(VERTICAL);
            setPadding(0, dp(4), 0, dp(14));

            headerView = new ChartHeaderView(context, resourcesProvider);
            headerView.setTitle(LocaleController.getString(R.string.MirrlyPowerOverviewHeader));
            addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46));

            int idx = 0;
            for (int row = 0; row < 2; row++) {
                LinearLayout rowLayout = new LinearLayout(context);
                rowLayout.setOrientation(HORIZONTAL);
                rowLayout.setPadding(dp(16), dp(row == 0 ? 4 : 14), dp(16), 0);
                for (int col = 0; col < 2; col++) {
                    LinearLayout cell = new LinearLayout(context);
                    cell.setOrientation(VERTICAL);
                    cell.setPadding(col == 1 ? dp(8) : 0, 0, col == 0 ? dp(8) : 0, 0);

                    LinearLayout topLine = new LinearLayout(context);
                    topLine.setOrientation(HORIZONTAL);
                    topLine.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);

                    AnimatedTextView pTv = new AnimatedTextView(context, true, true, false);
                    pTv.setTypeface(AndroidUtilities.bold());
                    pTv.setTextSize(dp(17));
                    pTv.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    pTv.setAnimationProperties(0.4f, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
                    primary[idx] = pTv;

                    AnimatedTextView bTv = new AnimatedTextView(context, true, true, false);
                    bTv.setTextSize(dp(12.5f));
                    bTv.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    bTv.setEllipsizeByGradient(true);
                    bTv.setAnimationProperties(0.4f, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
                    badge[idx] = bTv;

                    if (LocaleController.isRTL) {
                        topLine.addView(bTv, LayoutHelper.createLinear(0, 20, 1f, Gravity.CENTER_VERTICAL, 0, 1, 6, 0));
                        topLine.addView(pTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 24, Gravity.CENTER_VERTICAL));
                    } else {
                        topLine.addView(pTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 24, Gravity.CENTER_VERTICAL));
                        topLine.addView(bTv, LayoutHelper.createLinear(0, 20, 1f, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));
                    }
                    cell.addView(topLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    TextView cTv = new TextView(context);
                    cTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                    cTv.setSingleLine(true);
                    cTv.setEllipsize(TextUtils.TruncateAt.END);
                    cTv.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    caption[idx] = cTv;
                    cell.addView(cTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

                    rowLayout.addView(cell, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                    idx++;
                }
                addView(rowLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            caption[0].setText(LocaleController.getString(R.string.MirrlyPowerKpiImpact));
            caption[1].setText(LocaleController.getString(R.string.MirrlyPowerKpiRate));
            caption[2].setText(LocaleController.getString(R.string.MirrlyPowerKpiBatteryNow));
            caption[3].setText(LocaleController.getString(R.string.MirrlyPowerKpiPushWakeups));

            savingsBanner = new TextView(context);
            savingsBanner.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            savingsBanner.setTypeface(AndroidUtilities.bold());
            savingsBanner.setPadding(dp(12), dp(7), dp(12), dp(7));
            savingsBanner.setVisibility(GONE);
            addView(savingsBanner, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 14, 16, 0));

            updateColors();
        }

        void bind(@NonNull OverviewSpec spec) {
            PowerReport r = spec.report;
            boolean anim = isAttachedToWindow();
            if (r != null) {
                if (r.startWallMs > 0 && r.endWallMs >= r.startWallMs) {
                    headerView.showDate(true);
                    headerView.setDates(r.startWallMs, r.endWallMs);
                } else {
                    headerView.showDate(false);
                }
                primary[0].setText(MirrlyPowerTelemetry.formatPercentCompact(r.totalBatteryPercent), anim);
                if (r.hasPrevComparison && Math.abs(r.deltaBatteryPercent) >= 0.05) {
                    boolean better = r.deltaBatteryPercent < 0;
                    badge[0].setText((better ? "" : "+") + String.format(Locale.US, "%.1f%%", r.deltaBatteryPercent), anim);
                    badge[0].setTextColor(Theme.getColor(better ? Theme.key_statisticChartLine_green : Theme.key_statisticChartLine_red, resourcesProvider));
                } else {
                    badge[0].setText(MirrlyPowerTelemetry.formatMah(r.totalMirrlyUah), anim);
                    badge[0].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                }

                primary[1].setText(LocaleController.formatString(
                        R.string.MirrlyPowerRatePerHourShort,
                        String.format(Locale.US, "%.2f", r.drainRatePctPerHour)
                ), anim);
                badge[1].setText(Math.round(r.avgMirrlyMa) + " " + LocaleController.getString(R.string.MirrlyPowerUnitMa), anim);
                badge[1].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));

                primary[3].setText(String.valueOf(r.pushWakeups), anim);
                badge[3].setText(MirrlyPowerTelemetry.formatShortMs(r.pushWakeLockMs), anim);
                badge[3].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));

                if (r.totalSavedUah >= 100L) {
                    savingsBanner.setVisibility(VISIBLE);
                    savingsBanner.setText(LocaleController.formatString(
                            R.string.MirrlyPowerSavingsNote,
                            MirrlyPowerTelemetry.formatMah(r.totalSavedUah)
                    ));
                } else {
                    savingsBanner.setVisibility(GONE);
                }
            } else {
                headerView.showDate(false);
                primary[0].setText("0%", false);
                badge[0].setText("", false);
                primary[1].setText("0%/h", false);
                badge[1].setText("", false);
                primary[3].setText("0", false);
                badge[3].setText("", false);
                savingsBanner.setVisibility(GONE);
            }
            updateLiveBattery(spec.liveSnap, anim);
        }

        void updateLiveBattery(@NonNull SystemPowerSnapshot snap, boolean anim) {
            String levelStr = snap.batteryLevel >= 0 ? (snap.batteryLevel + "%") : "—";
            primary[2].setText(levelStr, anim);
            if (snap.isCharging) {
                badge[2].setText(LocaleController.getString(R.string.MirrlyPowerStatusChargingShort), anim);
                badge[2].setTextColor(Theme.getColor(Theme.key_statisticChartLine_green, resourcesProvider));
            } else {
                String tempStr = snap.tempTenthsC > 0 ? String.format(Locale.US, "%.1f°C", snap.getTempCelsius()) : "";
                String maStr = Math.abs(snap.currentNowMa) >= 1f
                        ? (Math.round(snap.currentNowMa) + " " + LocaleController.getString(R.string.MirrlyPowerUnitMa))
                        : tempStr;
                badge[2].setText(maStr, anim);
                int cKey = snap.tempTenthsC >= 410 ? Theme.key_statisticChartLine_red : Theme.key_windowBackgroundWhiteGrayText;
                badge[2].setTextColor(Theme.getColor(cKey, resourcesProvider));
            }
        }

        @Override
        public void updateColors() {
            headerView.recolor();
            int black = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            int green = Theme.getColor(Theme.key_statisticChartLine_green, resourcesProvider);
            for (int i = 0; i < 4; i++) {
                primary[i].setTextColor(black);
                caption[i].setTextColor(gray);
            }
            savingsBanner.setTextColor(green);
            savingsBanner.setBackground(Theme.createRoundRectDrawable(dp(10), ColorUtils.setAlphaComponent(green, 0x1E)));
        }

        public static class Factory extends UItem.UItemFactory<MirrlyPowerOverviewCell> {
            static {
                setup(new Factory());
            }

            @Override
            public MirrlyPowerOverviewCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new MirrlyPowerOverviewCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof OverviewSpec) {
                    ((MirrlyPowerOverviewCell) view).bind((OverviewSpec) item.object);
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
                return equals(a, b) && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, OverviewSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    // =========================================================================
    // Custom Native View 4: Expandable Ranked Module Impact Row + Setting Row
    // =========================================================================

    static final class ModuleRowSpec {
        @NonNull final ModuleReport report;
        final boolean expanded;
        final int generation;

        ModuleRowSpec(@NonNull ModuleReport report, boolean expanded, int generation) {
            this.report = report;
            this.expanded = expanded;
            this.generation = generation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ModuleRowSpec)) return false;
            ModuleRowSpec that = (ModuleRowSpec) o;
            return report.moduleId == that.report.moduleId
                    && expanded == that.expanded
                    && generation == that.generation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(report.moduleId, expanded, generation);
        }
    }

    static class ModuleImpactCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final AnimatedTextView pctView;
        private final TextView mahView;
        private final ImageView chevronView;
        private final View barTrack;
        private final View barFill;
        private final Paint dividerPaint = new Paint();

        private int colorKey = Theme.key_statisticChartLine_blue;
        private float shareFraction = 0f;
        private boolean expanded = false;
        private boolean needDivider = true;

        ModuleImpactCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            setPadding(dp(16), dp(11), dp(16), dp(11));

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            addView(iconView, LayoutHelper.createFrame(32, 32, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));

            LinearLayout centerCol = new LinearLayout(context);
            centerCol.setOrientation(LinearLayout.VERTICAL);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            centerCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            centerCol.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            FrameLayout barContainer = new FrameLayout(context);
            barTrack = new View(context);
            barFill = new View(context);
            barContainer.addView(barTrack, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3.5f, Gravity.CENTER_VERTICAL));
            barContainer.addView(barFill, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3.5f, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
            centerCol.addView(barContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 6, 0, 5, 0, 0));

            addView(centerCol, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL,
                    LocaleController.isRTL ? 84 : 44,
                    0,
                    LocaleController.isRTL ? 44 : 84,
                    0
            ));

            LinearLayout rightCol = new LinearLayout(context);
            rightCol.setOrientation(LinearLayout.VERTICAL);
            rightCol.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);

            LinearLayout pctRow = new LinearLayout(context);
            pctRow.setOrientation(LinearLayout.HORIZONTAL);
            pctRow.setGravity(Gravity.CENTER_VERTICAL);

            pctView = new AnimatedTextView(context, true, true, false);
            pctView.setTextSize(dp(15));
            pctView.setTypeface(AndroidUtilities.bold());
            pctView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
            pctView.setAnimationProperties(0.4f, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);

            chevronView = new ImageView(context);
            chevronView.setImageResource(R.drawable.arrow_more);
            chevronView.setScaleType(ImageView.ScaleType.CENTER);

            if (LocaleController.isRTL) {
                pctRow.addView(chevronView, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
                pctRow.addView(pctView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20, Gravity.CENTER_VERTICAL));
            } else {
                pctRow.addView(pctView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 20, Gravity.CENTER_VERTICAL));
                pctRow.addView(chevronView, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
            }
            rightCol.addView(pctRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            mahView = new TextView(context);
            mahView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            mahView.setSingleLine(true);
            rightCol.addView(mahView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

            addView(rightCol, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT,
                    (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL
            ));

            updateColors();
        }

        void bind(@NonNull ModuleRowSpec spec, boolean divider) {
            ModuleReport m = spec.report;
            this.colorKey = m.colorKey;
            this.needDivider = divider;
            this.shareFraction = Utilities.clamp((float) (m.sharePercent / 100.0), 1f, 0f);

            iconView.setImageResource(getModuleIconRes(m.moduleId));
            titleView.setText(MirrlyPowerTelemetry.getModuleTitle(m.moduleId));
            subtitleView.setText(m.telemetryLine);
            pctView.setText(MirrlyPowerTelemetry.formatPercentCompact(m.batteryPercent), isAttachedToWindow());
            mahView.setText(MirrlyPowerTelemetry.formatMah(m.uah));

            boolean targetExp = spec.expanded;
            if (expanded != targetExp) {
                expanded = targetExp;
                chevronView.animate()
                        .rotation(expanded ? 180f : 0f)
                        .setDuration(220)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .start();
            } else {
                chevronView.setRotation(expanded ? 180f : 0f);
            }

            updateColors();
            updateBarFillScale();
            invalidate();
        }

        private void updateBarFillScale() {
            barFill.setPivotX(LocaleController.isRTL ? barFill.getWidth() : 0f);
            barFill.setScaleX(Math.max(0.02f, shareFraction));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            updateBarFillScale();
        }

        @Override
        public void updateColors() {
            int modColor = Theme.getColor(colorKey, resourcesProvider);
            int black = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            int div = Theme.getColor(Theme.key_divider, resourcesProvider);

            iconView.setBackground(Theme.createRoundRectDrawable(dp(9), modColor));
            iconView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            titleView.setTextColor(black);
            subtitleView.setTextColor(gray);
            pctView.setTextColor(black);
            mahView.setTextColor(gray);
            chevronView.setColorFilter(new PorterDuffColorFilter(gray, PorterDuff.Mode.SRC_IN));
            barTrack.setBackground(Theme.createRoundRectDrawable(dp(2), ColorUtils.setAlphaComponent(gray, 0x22)));
            barFill.setBackground(Theme.createRoundRectDrawable(dp(2), modColor));
            dividerPaint.setColor(div);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                float startX = LocaleController.isRTL ? 0 : dp(60);
                float endX = LocaleController.isRTL ? getWidth() - dp(60) : getWidth();
                canvas.drawLine(startX, getHeight() - 1, endX, getHeight() - 1, dividerPaint);
            }
        }

        public static class Factory extends UItem.UItemFactory<ModuleImpactCell> {
            static {
                setup(new Factory());
            }

            @Override
            public ModuleImpactCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new ModuleImpactCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof ModuleRowSpec) {
                    ((ModuleImpactCell) view).bind((ModuleRowSpec) item.object, divider);
                }
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a != null && b != null && a.id == b.id && a.viewType == b.viewType;
            }

            @Override
            public boolean contentsEquals(UItem a, UItem b) {
                return equals(a, b) && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, ModuleRowSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    static final class SettingRowSpec {
        final String title;
        final String status;
        final boolean active;
        final boolean isSaving;

        SettingRowSpec(String title, String status, boolean active, boolean isSaving) {
            this.title = title;
            this.status = status;
            this.active = active;
            this.isSaving = isSaving;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SettingRowSpec)) return false;
            SettingRowSpec that = (SettingRowSpec) o;
            return active == that.active && isSaving == that.isSaving
                    && TextUtils.equals(title, that.title)
                    && TextUtils.equals(status, that.status);
        }

        @Override
        public int hashCode() {
            return Objects.hash(title, status, active, isSaving);
        }
    }

    static class ModuleSettingRowCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final TextView titleView;
        private final TextView statusView;
        private boolean isActive;
        private boolean isSaving;

        ModuleSettingRowCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(LocaleController.isRTL ? dp(16) : dp(60), dp(7), LocaleController.isRTL ? dp(60) : dp(16), dp(7));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            statusView = new TextView(context);
            statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            statusView.setTypeface(AndroidUtilities.bold());
            statusView.setSingleLine(true);
            statusView.setPadding(dp(8), dp(2), dp(8), dp(2));

            if (LocaleController.isRTL) {
                addView(statusView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0));
                addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            } else {
                addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 10, 0));
                addView(statusView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }
            updateColors();
        }

        void bind(@NonNull SettingRowSpec spec) {
            this.isActive = spec.active;
            this.isSaving = spec.isSaving;
            titleView.setText(spec.title);
            statusView.setText(spec.status);
            updateColors();
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            int badgeColor;
            if (isSaving) {
                badgeColor = Theme.getColor(Theme.key_statisticChartLine_green, resourcesProvider);
            } else if (isActive) {
                badgeColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            } else {
                badgeColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            }
            statusView.setTextColor(badgeColor);
            statusView.setBackground(Theme.createRoundRectDrawable(dp(6), ColorUtils.setAlphaComponent(badgeColor, 0x1C)));
        }

        public static class Factory extends UItem.UItemFactory<ModuleSettingRowCell> {
            static {
                setup(new Factory());
            }

            @Override
            public ModuleSettingRowCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new ModuleSettingRowCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof SettingRowSpec) {
                    ((ModuleSettingRowCell) view).bind((SettingRowSpec) item.object);
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
                return equals(a, b) && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, SettingRowSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    // =========================================================================
    // Custom Native View 5: Native Telegram Chart Cell (StatisticActivity.BaseChartCell)
    // =========================================================================

    static final class ChartSpec {
        @Nullable final StatisticActivity.ChartViewData viewData;
        final BaseChartView.SharedUiComponents sharedUi;
        final int generation;

        ChartSpec(@Nullable StatisticActivity.ChartViewData viewData, BaseChartView.SharedUiComponents sharedUi, int generation) {
            this.viewData = viewData;
            this.sharedUi = sharedUi;
            this.generation = generation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChartSpec)) return false;
            ChartSpec that = (ChartSpec) o;
            return generation == that.generation && viewData == that.viewData;
        }

        @Override
        public int hashCode() {
            return Objects.hash(generation);
        }
    }

    static class MirrlyPowerChartCell extends StatisticActivity.BaseChartCell {

        private int boundGeneration = -1;

        MirrlyPowerChartCell(@NonNull Context context, int type, BaseChartView.SharedUiComponents sharedUi, Theme.ResourcesProvider resourcesProvider) {
            super(context, type, sharedUi, resourcesProvider);
            setPadding(0, dp(2), 0, dp(6));
        }

        void bind(@NonNull ChartSpec spec) {
            if (spec.viewData == null) {
                return;
            }
            if (boundGeneration == spec.generation && data == spec.viewData) {
                return;
            }
            boundGeneration = spec.generation;
            updateData(spec.viewData, false);
        }

        @Override
        public void onZoomed() {
            if (data == null || data.chartData == null || data.activeZoom > 0) {
                return;
            }
            if (chartType == 4 && chartView != null && data.chartData.x != null && data.chartData.x.length > 0) {
                long d = chartView.getSelectedDate();
                data.childChartData = new StackLinearChartData(data.chartData, d);
                zoomChart(false);
            }
        }

        @Override
        public void zoomCanceled() {
            if (chartView != null && chartView.legendSignatureView != null) {
                chartView.legendSignatureView.showProgress(false, true);
            }
        }

        @Override
        protected void loadData(StatisticActivity.ChartViewData viewData) {
            // Local telemetry data is already preloaded synchronously into ChartViewData.
        }

        @Override
        public boolean canScrollHorizontally(int direction) {
            return data != null && !data.isEmpty && data.chartData != null;
        }
    }

    abstract static class BasePowerChartFactory extends UItem.UItemFactory<MirrlyPowerChartCell> {
        private final int chartType;

        BasePowerChartFactory(int chartType) {
            this.chartType = chartType;
        }

        @Override
        public MirrlyPowerChartCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new MirrlyPowerChartCell(context, chartType, new BaseChartView.SharedUiComponents(resourcesProvider), resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (item.object instanceof ChartSpec) {
                ChartSpec spec = (ChartSpec) item.object;
                MirrlyPowerChartCell cell = (MirrlyPowerChartCell) view;
                if (spec.sharedUi != null) {
                    cell.chartView.sharedUiComponents = spec.sharedUi;
                    cell.zoomedChartView.sharedUiComponents = spec.sharedUi;
                }
                cell.bind(spec);
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
            return equals(a, b) && Objects.equals(a.object, b.object);
        }
    }

    public static class StackBarChartFactory extends BasePowerChartFactory {
        static {
            setup(new StackBarChartFactory());
        }

        public StackBarChartFactory() {
            super(2);
        }

        public static UItem of(int id, ChartSpec spec) {
            UItem item = UItem.ofFactory(StackBarChartFactory.class);
            item.id = id;
            item.object = spec;
            return item;
        }
    }

    public static class StackShareChartFactory extends BasePowerChartFactory {
        static {
            setup(new StackShareChartFactory());
        }

        public StackShareChartFactory() {
            super(4);
        }

        public static UItem of(int id, ChartSpec spec) {
            UItem item = UItem.ofFactory(StackShareChartFactory.class);
            item.id = id;
            item.object = spec;
            return item;
        }
    }

    public static class DoubleLinearChartFactory extends BasePowerChartFactory {
        static {
            setup(new DoubleLinearChartFactory());
        }

        public DoubleLinearChartFactory() {
            super(1);
        }

        public static UItem of(int id, ChartSpec spec) {
            UItem item = UItem.ofFactory(DoubleLinearChartFactory.class);
            item.id = id;
            item.object = spec;
            return item;
        }
    }

    public static class Clock24hChartFactory extends BasePowerChartFactory {
        static {
            setup(new Clock24hChartFactory());
        }

        public Clock24hChartFactory() {
            super(0);
        }

        public static UItem of(int id, ChartSpec spec) {
            UItem item = UItem.ofFactory(Clock24hChartFactory.class);
            item.id = id;
            item.object = spec;
            return item;
        }
    }

    // =========================================================================
    // Custom Native View 6: Live Android System Power Parameters Card
    // =========================================================================

    static final class SystemCardSpec {
        @NonNull final SystemPowerSnapshot snapshot;
        final int generation;

        SystemCardSpec(@NonNull SystemPowerSnapshot snapshot, int generation) {
            this.snapshot = snapshot;
            this.generation = generation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SystemCardSpec)) return false;
            SystemCardSpec that = (SystemCardSpec) o;
            return generation == that.generation && snapshot.timestampMs == that.snapshot.timestampMs;
        }

        @Override
        public int hashCode() {
            return Objects.hash(generation, snapshot.timestampMs);
        }
    }

    static class SystemPowerCardCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final ChartHeaderView headerView;
        private final TextView[] keys = new TextView[6];
        private final AnimatedTextView[] vals = new AnimatedTextView[6];

        SystemPowerCardCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(VERTICAL);
            setPadding(0, dp(4), 0, dp(12));

            headerView = new ChartHeaderView(context, resourcesProvider);
            headerView.setTitle(LocaleController.getString(R.string.MirrlyPowerSystemHeader));
            headerView.showDate(false);
            addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46));

            int[] keyRes = new int[]{
                    R.string.MirrlyPowerSysLevelStatus,
                    R.string.MirrlyPowerSysCurrentPower,
                    R.string.MirrlyPowerSysVoltageTemp,
                    R.string.MirrlyPowerSysThermal,
                    R.string.MirrlyPowerSysSaverDoze,
                    R.string.MirrlyPowerSysCapacity
            };

            for (int i = 0; i < 6; i++) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(5), dp(16), dp(5));

                TextView k = new TextView(context);
                k.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                k.setText(LocaleController.getString(keyRes[i]));
                k.setSingleLine(true);
                k.setEllipsize(TextUtils.TruncateAt.END);
                k.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                keys[i] = k;

                AnimatedTextView v = new AnimatedTextView(context, true, true, false);
                v.setTextSize(dp(14));
                v.setTypeface(AndroidUtilities.bold());
                v.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
                v.setEllipsizeByGradient(true);
                v.setAnimationProperties(0.4f, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
                vals[i] = v;

                if (LocaleController.isRTL) {
                    row.addView(v, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 22, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
                    row.addView(k, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
                } else {
                    row.addView(k, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
                    row.addView(v, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 22, Gravity.CENTER_VERTICAL));
                }
                addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            updateColors();
        }

        void updateSnapshot(@NonNull SystemPowerSnapshot s, boolean anim) {
            String lvl = s.batteryLevel >= 0 ? (s.batteryLevel + "%") : "—";
            String stateStr;
            if (s.isCharging) {
                String src = s.pluggedType == BatteryManager.BATTERY_PLUGGED_AC ? "AC"
                        : (s.pluggedType == BatteryManager.BATTERY_PLUGGED_USB ? "USB"
                        : (s.pluggedType == BatteryManager.BATTERY_PLUGGED_WIRELESS ? "Qi" : ""));
                stateStr = TextUtils.isEmpty(src)
                        ? LocaleController.getString(R.string.MirrlyPowerStatusChargingShort)
                        : (LocaleController.getString(R.string.MirrlyPowerStatusChargingShort) + " (" + src + ")");
            } else {
                stateStr = LocaleController.getString(R.string.MirrlyPowerStatusDischarging);
            }
            vals[0].setText(lvl + " • " + stateStr, anim);

            String maUnit = LocaleController.getString(R.string.MirrlyPowerUnitMa);
            String mwUnit = LocaleController.getString(R.string.MirrlyPowerUnitMw);
            int curMa = Math.round(s.currentNowMa);
            String curPrefix = (curMa > 0 && !s.isCharging) ? "-" : (s.isCharging && curMa > 0 ? "+" : "");
            vals[1].setText(curPrefix + Math.abs(curMa) + " " + maUnit + " • " + s.systemPowerMw + " " + mwUnit, anim);

            String voltStr = s.voltageMv > 0 ? String.format(Locale.US, "%.2f %s", s.voltageMv / 1000.0, LocaleController.getString(R.string.MirrlyPowerUnitV)) : "—";
            String tempStr = s.tempTenthsC > 0 ? String.format(Locale.US, "%.1f °C", s.getTempCelsius()) : "—";
            vals[2].setText(voltStr + " • " + tempStr, anim);

            vals[3].setText(MirrlyPowerTelemetry.formatThermalLabel(s.thermalStatus), anim);
            int thermalColor = s.thermalStatus >= 3
                    ? Theme.getColor(Theme.key_statisticChartLine_red, resourcesProvider)
                    : (s.thermalStatus >= 2 ? Theme.getColor(Theme.key_statisticChartLine_orange, resourcesProvider)
                    : Theme.getColor(Theme.key_statisticChartLine_green, resourcesProvider));
            vals[3].setTextColor(thermalColor);

            String saverStr = s.systemPowerSaveMode
                    ? LocaleController.getString(R.string.MirrlyPowerStateOn)
                    : LocaleController.getString(R.string.MirrlyPowerStateOff);
            String dozeStr = s.deviceIdleMode ? "Doze" : LocaleController.getString(R.string.MirrlyPowerStateActive);
            vals[4].setText(saverStr + " • " + dozeStr, anim);

            vals[5].setText(Math.round(s.effectiveCapacityMah) + " " + LocaleController.getString(R.string.MirrlyPowerUnitMah), anim);
        }

        @Override
        public void updateColors() {
            headerView.recolor();
            int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            int black = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            for (int i = 0; i < 6; i++) {
                keys[i].setTextColor(gray);
                if (i != 3) {
                    vals[i].setTextColor(black);
                }
            }
        }

        public static class Factory extends UItem.UItemFactory<SystemPowerCardCell> {
            static {
                setup(new Factory());
            }

            @Override
            public SystemPowerCardCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new SystemPowerCardCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof SystemCardSpec) {
                    ((SystemPowerCardCell) view).updateSnapshot(((SystemCardSpec) item.object).snapshot, false);
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
                return equals(a, b) && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, SystemCardSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    // =========================================================================
    // Custom Native View 7: Dynamic Smart Eco Tip Banner
    // =========================================================================

    static final class EcoTipSpec {
        @NonNull final EcoRecommendation rec;
        @NonNull final Runnable onApply;

        EcoTipSpec(@NonNull EcoRecommendation rec, @NonNull Runnable onApply) {
            this.rec = rec;
            this.onApply = onApply;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EcoTipSpec)) return false;
            EcoTipSpec that = (EcoTipSpec) o;
            return rec.id == that.rec.id
                    && TextUtils.equals(rec.title, that.rec.title)
                    && TextUtils.equals(rec.description, that.rec.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rec.id, rec.title, rec.description);
        }
    }

    static class EcoTipBannerCell extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView descView;
        private final ButtonWithCounterView actionBtn;
        private Runnable onApply;

        EcoTipBannerCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setOrientation(VERTICAL);
            setPadding(dp(16), dp(14), dp(16), dp(14));

            LinearLayout head = new LinearLayout(context);
            head.setOrientation(HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            iconView = new ImageView(context);
            iconView.setImageResource(R.drawable.menu_lightbulb);
            iconView.setScaleType(ImageView.ScaleType.CENTER);

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(VERTICAL);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textCol.addView(titleView);

            descView = new TextView(context);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            descView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textCol.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            if (LocaleController.isRTL) {
                head.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0));
                head.addView(iconView, LayoutHelper.createLinear(34, 34, Gravity.CENTER_VERTICAL));
            } else {
                head.addView(iconView, LayoutHelper.createLinear(34, 34, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
                head.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            }
            addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

            actionBtn = MirrlyUiKit.createFlickerButton(context, resourcesProvider, false);
            actionBtn.setOnClickListener(v -> {
                if (onApply != null) {
                    onApply.run();
                }
            });
            addView(actionBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42));

            updateColors();
        }

        void bind(@NonNull EcoTipSpec spec) {
            this.onApply = spec.onApply;
            titleView.setText(spec.rec.title);
            descView.setText(spec.rec.description);
            actionBtn.setText(spec.rec.actionLabel, false);
        }

        @Override
        public void updateColors() {
            int orange = Theme.getColor(Theme.key_statisticChartLine_orange, resourcesProvider);
            iconView.setBackground(Theme.createRoundRectDrawable(dp(10), ColorUtils.setAlphaComponent(orange, 0x22)));
            iconView.setColorFilter(new PorterDuffColorFilter(orange, PorterDuff.Mode.SRC_IN));
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        }

        public static class Factory extends UItem.UItemFactory<EcoTipBannerCell> {
            static {
                setup(new Factory());
            }

            @Override
            public EcoTipBannerCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new EcoTipBannerCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof EcoTipSpec) {
                    ((EcoTipBannerCell) view).bind((EcoTipSpec) item.object);
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
                return equals(a, b) && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, EcoTipSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }

    // =========================================================================
    // Custom Native View 8: Auto-Eco Battery Slider (LiteModeSettingsActivity style)
    // =========================================================================

    static final class EcoSliderSpec {
        final int level;
        final boolean ecoActive;
        final Utilities.Callback<Integer> onLevelChanged;

        EcoSliderSpec(int level, boolean ecoActive, Utilities.Callback<Integer> onLevelChanged) {
            this.level = level;
            this.ecoActive = ecoActive;
            this.onLevelChanged = onLevelChanged;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EcoSliderSpec)) return false;
            EcoSliderSpec that = (EcoSliderSpec) o;
            return level == that.level && ecoActive == that.ecoActive;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, ecoActive);
        }
    }

    static class PowerEcoSliderCell extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final BatteryDrawable batteryIcon;
        private final SpannableStringBuilder batteryText;
        private final TextView headerTextView;
        private final AnimatedTextView headerOnView;
        private final TextView leftTextView;
        private final AnimatedTextView middleTextView;
        private final TextView rightTextView;
        private final SeekBarView seekBarView;
        private final SeekBarAccessibilityDelegate accessibilityDelegate;

        private Utilities.Callback<Integer> onLevelChanged;
        private boolean draggingSlider;
        private boolean headerOnVisible;
        private float onActiveT;
        private float offActiveT;
        private ValueAnimator onActiveAnimator;
        private ValueAnimator offActiveAnimator;

        PowerEcoSliderCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            LinearLayout headerLayout = new LinearLayout(context);
            headerLayout.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            headerLayout.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

            headerTextView = new TextView(context);
            headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            headerTextView.setTypeface(AndroidUtilities.bold());
            headerTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            headerTextView.setText(LocaleController.getString(R.string.MirrlyPowerAutoEcoTitle));
            headerLayout.addView(headerTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            headerOnView = new AnimatedTextView(context, true, false, false) {
                final Drawable bg = Theme.createRoundRectDrawable(dp(4), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider), 0.15f));

                @Override
                protected void onDraw(Canvas canvas) {
                    if (bg instanceof ShapeDrawable) {
                        int c = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider), 0.15f);
                        if (((ShapeDrawable) bg).getPaint().getColor() != c) {
                            ((ShapeDrawable) bg).getPaint().setColor(c);
                        }
                    }
                    bg.setBounds(0, 0, (int) (getPaddingLeft() + getDrawable().getCurrentWidth() + getPaddingRight()), getMeasuredHeight());
                    bg.draw(canvas);
                    super.onDraw(canvas);
                }
            };
            headerOnView.setTypeface(AndroidUtilities.bold());
            headerOnView.setPadding(dp(5.33f), dp(2), dp(5.33f), dp(2));
            headerOnView.setTextSize(dp(11.5f));
            headerOnView.setGravity(Gravity.LEFT);
            if (LocaleController.isRTL) {
                headerLayout.removeView(headerTextView);
                headerLayout.addView(headerOnView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 18, Gravity.CENTER_VERTICAL, 0, 1, 6, 0));
                headerLayout.addView(headerTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            } else {
                headerLayout.addView(headerOnView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 18, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));
            }

            addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 16, 21, 0));

            seekBarView = new SeekBarView(context, true, resourcesProvider);
            seekBarView.setReportChanges(true);
            seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    int newValue = Math.round(progress * 100f);
                    if (newValue != MirrlyConfig.getPowerAutoEcoLevel()) {
                        MirrlyConfig.setPowerAutoEcoLevel(newValue);
                        refreshState(true);
                        if (newValue <= 0 || newValue >= 100) {
                            try {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            } catch (Exception ignore) {
                            }
                        }
                        if (onLevelChanged != null) {
                            onLevelChanged.run(newValue);
                        }
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                    draggingSlider = pressed;
                }

                @Override
                public CharSequence getContentDescription() {
                    return " ";
                }
            });
            seekBarView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP, 6, 68, 6, 0));

            FrameLayout valuesView = new FrameLayout(context);
            valuesView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

            leftTextView = new TextView(context);
            leftTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            leftTextView.setGravity(Gravity.LEFT);
            leftTextView.setText(LocaleController.getString(R.string.LiteBatteryDisabled));
            valuesView.addView(leftTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            middleTextView = new AnimatedTextView(context, false, true, true) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int fullWidth = MeasureSpec.getSize(widthMeasureSpec);
                    if (fullWidth <= 0) {
                        fullWidth = AndroidUtilities.displaySize.x - dp(20);
                    }
                    float lw = leftTextView.getPaint().measureText(leftTextView.getText().toString());
                    float rw = rightTextView.getPaint().measureText(rightTextView.getText().toString());
                    super.onMeasure(MeasureSpec.makeMeasureSpec(Math.max(dp(40), (int) (fullWidth - lw - rw)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY));
                }
            };
            middleTextView.setAnimationProperties(0.45f, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
            middleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            middleTextView.setTextSize(dp(13));
            valuesView.addView(middleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            batteryText = new SpannableStringBuilder("b");
            batteryIcon = new BatteryDrawable();
            batteryIcon.colorFromPaint(middleTextView.getPaint());
            batteryIcon.setTranslationY(dp(1.5f));
            batteryIcon.setBounds(dp(3), dp(-20), dp(23), 0);
            batteryText.setSpan(new ImageSpan(batteryIcon, DynamicDrawableSpan.ALIGN_BOTTOM), 0, batteryText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            rightTextView = new TextView(context);
            rightTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            rightTextView.setGravity(Gravity.RIGHT);
            rightTextView.setText(LocaleController.getString(R.string.LiteBatteryEnabled));
            valuesView.addView(rightTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

            addView(valuesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 50, 21, 0));

            accessibilityDelegate = new IntSeekBarAccessibilityDelegate() {
                @Override
                protected int getProgress() {
                    return MirrlyConfig.getPowerAutoEcoLevel();
                }

                @Override
                protected void setProgress(int progress) {
                    seekBarView.delegate.onSeekBarDrag(true, progress / 100f);
                    seekBarView.setProgress(progress / 100f);
                }

                @Override
                protected int getMaxValue() {
                    return 100;
                }

                @Override
                protected int getDelta() {
                    return 5;
                }

                @Override
                public void onInitializeAccessibilityNodeInfoInternal(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfoInternal(host, info);
                    info.setEnabled(true);
                }

                @Override
                public void onPopulateAccessibilityEvent(@NonNull View host, @NonNull AccessibilityEvent event) {
                    super.onPopulateAccessibilityEvent(host, event);
                    StringBuilder sb = new StringBuilder(LocaleController.getString(R.string.MirrlyPowerAutoEcoTitle)).append(", ");
                    int percent = MirrlyConfig.getPowerAutoEcoLevel();
                    if (percent <= 0) {
                        sb.append(LocaleController.getString(R.string.LiteBatteryAlwaysDisabled));
                    } else if (percent >= 100) {
                        sb.append(LocaleController.getString(R.string.LiteBatteryAlwaysEnabled));
                    } else {
                        sb.append(LocaleController.formatString(R.string.AccDescrLiteBatteryWhenBelow, percent));
                    }
                    event.setContentDescription(sb);
                    setContentDescription(sb);
                }
            };

            updateColors();
            refreshState(false);
        }

        void bind(@NonNull EcoSliderSpec spec) {
            this.onLevelChanged = spec.onLevelChanged;
            // Always read live MirrlyConfig so RecyclerView rebinds never revert slider thumb!
            if (!draggingSlider) {
                int liveLevel = MirrlyConfig.getPowerAutoEcoLevel();
                seekBarView.setProgress(liveLevel / 100f);
                refreshState(false);
            }
        }

        private void refreshState(boolean animate) {
            final int percent = MirrlyConfig.getPowerAutoEcoLevel();
            middleTextView.cancelAnimation();
            if (percent <= 0) {
                middleTextView.setText(LocaleController.getString(R.string.LiteBatteryAlwaysDisabled), animate && !LocaleController.isRTL);
            } else if (percent >= 100) {
                middleTextView.setText(LocaleController.getString(R.string.LiteBatteryAlwaysEnabled), animate && !LocaleController.isRTL);
            } else {
                batteryIcon.setFillValue(percent / 100f, animate);
                middleTextView.setText(
                        AndroidUtilities.replaceCharSequence(
                                "%s",
                                LocaleController.getString(R.string.LiteBatteryWhenBelow),
                                TextUtils.concat(String.format(Locale.US, "%d%% ", percent), batteryText)
                        ),
                        animate && !LocaleController.isRTL
                );
            }

            boolean ecoActive = MirrlyPowerPolicy.isAutoEcoActive();
            headerOnView.setText(
                    (ecoActive ? LocaleController.getString(R.string.MirrlyPowerStateActive) : LocaleController.getString(R.string.LiteBatteryDisabled)).toUpperCase(Locale.ROOT),
                    animate
            );
            boolean showBadge = ecoActive || (percent > 0 && percent < 100);
            if (showBadge != headerOnVisible) {
                headerOnVisible = showBadge;
                headerOnView.animate().alpha(showBadge ? 1f : 0f).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            } else {
                headerOnView.setAlpha(showBadge ? 1f : 0f);
            }
            animateEdgeLabel(true, percent >= 100, animate);
            animateEdgeLabel(false, percent <= 0, animate);
        }

        private void animateEdgeLabel(boolean isRight, boolean active, boolean animated) {
            final float target = active ? 1f : 0f;
            float current = isRight ? onActiveT : offActiveT;
            if (!animated) {
                if (isRight) {
                    onActiveT = target;
                    rightTextView.setTextColor(ColorUtils.blendARGB(
                            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider),
                            Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider),
                            target
                    ));
                } else {
                    offActiveT = target;
                    leftTextView.setTextColor(ColorUtils.blendARGB(
                            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider),
                            Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider),
                            target
                    ));
                }
                return;
            }
            if (Math.abs(current - target) < 0.001f) {
                return;
            }
            ValueAnimator prev = isRight ? onActiveAnimator : offActiveAnimator;
            if (prev != null) {
                prev.cancel();
            }
            ValueAnimator anim = ValueAnimator.ofFloat(current, target);
            anim.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                int c = ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider),
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider),
                        v
                );
                if (isRight) {
                    onActiveT = v;
                    rightTextView.setTextColor(c);
                } else {
                    offActiveT = v;
                    leftTextView.setTextColor(c);
                }
            });
            anim.setDuration(280);
            anim.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            if (isRight) {
                onActiveAnimator = anim;
            } else {
                offActiveAnimator = anim;
            }
            anim.start();
        }

        @Override
        public void updateColors() {
            int blueHeader = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider);
            int blueText = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider);
            int grayText = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
            headerTextView.setTextColor(blueHeader);
            headerOnView.setTextColor(blueHeader);
            middleTextView.setTextColor(blueText);
            rightTextView.setTextColor(ColorUtils.blendARGB(grayText, blueText, onActiveT));
            leftTextView.setTextColor(ColorUtils.blendARGB(grayText, blueText, offActiveT));
            invalidate();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            accessibilityDelegate.onInitializeAccessibilityNodeInfo(this, info);
        }

        @Override
        public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
            super.onPopulateAccessibilityEvent(event);
            accessibilityDelegate.onPopulateAccessibilityEvent(this, event);
        }

        @Override
        public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
            return accessibilityDelegate.performAccessibilityAction(this, action, arguments);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(116), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<PowerEcoSliderCell> {
            static {
                setup(new Factory());
            }

            @Override
            public PowerEcoSliderCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new PowerEcoSliderCell(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                if (item.object instanceof EcoSliderSpec) {
                    ((PowerEcoSliderCell) view).bind((EcoSliderSpec) item.object);
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
                return equals(a, b) && Objects.equals(a.object, b.object);
            }

            public static UItem of(int id, EcoSliderSpec spec) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.object = spec;
                return item;
            }
        }
    }
}
