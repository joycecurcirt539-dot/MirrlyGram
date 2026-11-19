package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.WallpaperParallaxEffect;

import java.util.ArrayList;

/**
 * Shared frosted-glass, edge-to-edge base fragment for Mirrlygram settings hub and sub-screens.
 * Uses Telegram's native SimpleActionBar with progressive glass blur fade.
 */
public abstract class MirrlyBaseSettingsFragment extends BaseFragment {

    protected FrameLayout contentView;
    protected MirrlySettingsListView listView;
    protected RecyclerItemsEnterAnimator enterAnimator;
    private WallpaperParallaxEffect parallaxEffect;
    private boolean enterAnimated = false;
    private boolean hasResumedOnce = false;
    private boolean actionBarTitleVisible = false;
    private float actionBarTitleFactor = 0f;
    private ValueAnimator actionBarTitleAnimator;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean hasOwnBackground() {
        return true;
    }

    @Nullable
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(getScreenTitle());
        actionBarTitleVisible = shouldForceActionBarTitle();
        actionBarTitleFactor = actionBarTitleVisible ? 1f : 0f;
        simpleActionBar.setTitlesAlpha(actionBarTitleFactor);
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else {
                    onMenuItemClick(id);
                }
            }
        });
        setupMenu(simpleActionBar.createMenu());

        contentView = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                glassEngine.invalidate();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
                );
            }
        };
        ThemeBind.backgroundColor(contentView, Theme.key_windowBackgroundGray, getResourceProvider());

        parallaxEffect = new WallpaperParallaxEffect(context);
        parallaxEffect.setCallback((offsetX, offsetY, angle) -> applyHeroParallax(offsetX * 0.45f, offsetY * 0.45f));

        listView = new MirrlySettingsListView(context);
        enterAnimator = new RecyclerItemsEnterAnimator(listView, true);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        return fragmentView = contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateParallaxState(true);
        if (!hasResumedOnce) {
            hasResumedOnce = true;
            return;
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        updateParallaxState(false);
    }

    @Override
    public void onFragmentDestroy() {
        if (actionBarTitleAnimator != null) {
            actionBarTitleAnimator.cancel();
            actionBarTitleAnimator = null;
        }
        if (parallaxEffect != null) {
            parallaxEffect.setEnabled(false);
            parallaxEffect.setCallback(null);
        }
        super.onFragmentDestroy();
    }

    protected boolean shouldForceActionBarTitle() {
        return false;
    }

    protected void updateActionBarVisible() {
        updateActionBarVisible(true);
    }

    protected void updateActionBarVisible(boolean animated) {
        if (simpleActionBar == null) {
            return;
        }
        boolean visible = shouldForceActionBarTitle();
        if (!visible && listView != null && listView.getChildCount() > 0) {
            RecyclerView.ViewHolder firstHolder = listView.findViewHolderForAdapterPosition(0);
            if (firstHolder == null) {
                visible = listView.computeVerticalScrollOffset() > dp(120);
            } else if (firstHolder.itemView instanceof MirrlyUiKit.HeroCardView) {
                int topBarBottom = simpleActionBar.getBottom();
                if (topBarBottom <= 0) {
                    topBarBottom = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight();
                }
                visible = firstHolder.itemView.getBottom() <= topBarBottom + dp(56);
            } else {
                visible = true;
            }
        }
        if (actionBarTitleVisible == visible && animated) {
            return;
        }
        actionBarTitleVisible = visible;
        float target = visible ? 1f : 0f;
        if (actionBarTitleAnimator != null) {
            actionBarTitleAnimator.cancel();
            actionBarTitleAnimator = null;
        }
        if (!animated || !SharedConfig.animationsEnabled()) {
            actionBarTitleFactor = target;
            simpleActionBar.setTitlesAlpha(actionBarTitleFactor);
            return;
        }
        actionBarTitleAnimator = ValueAnimator.ofFloat(actionBarTitleFactor, target);
        actionBarTitleAnimator.addUpdateListener(anim -> {
            actionBarTitleFactor = (float) anim.getAnimatedValue();
            if (simpleActionBar != null) {
                simpleActionBar.setTitlesAlpha(actionBarTitleFactor);
            }
        });
        actionBarTitleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        actionBarTitleAnimator.setDuration(320);
        actionBarTitleAnimator.start();
    }

    protected void setupMenu(SimpleActionBarMenu menu) {
    }

    protected void onMenuItemClick(int id) {
    }

    protected abstract CharSequence getScreenTitle();

    protected abstract void fillItems(ArrayList<UItem> items, UniversalAdapter adapter);

    protected abstract void onClick(UItem item, View view, int position, float x, float y);

    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    protected void refreshHeroSubtitles(CharSequence[] subtitles, boolean animated) {
        if (listView == null) {
            return;
        }
        for (int i = 0, count = listView.getChildCount(); i < count; i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MirrlyUiKit.HeroCardView) {
                ((MirrlyUiKit.HeroCardView) child).refreshDynamicSubtitles(subtitles, animated);
            }
        }
    }

    private void updateParallaxState(boolean active) {
        if (parallaxEffect == null) {
            return;
        }
        boolean enable = active
                && !finishing
                && SharedConfig.animationsEnabled()
                && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES);
        parallaxEffect.setEnabled(enable);
    }

    private void applyHeroParallax(float offsetX, float offsetY) {
        if (listView == null) {
            return;
        }
        for (int i = 0, count = listView.getChildCount(); i < count; i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MirrlyUiKit.HeroCardView) {
                ((MirrlyUiKit.HeroCardView) child).setParallaxOffset(offsetX, offsetY);
            }
        }
    }

    protected class MirrlySettingsListView extends UniversalRecyclerView implements MirrlyUiKit.HeroCardView.HostProvider {

        public MirrlySettingsListView(Context context) {
            super(MirrlyBaseSettingsFragment.this, MirrlyBaseSettingsFragment.this::fillItems, MirrlyBaseSettingsFragment.this::onClick, MirrlyBaseSettingsFragment.this::onLongClick);
            setCaptureSectionsDecoratorAllowed(true);
            adapter.setApplyBackground(false);
            setSections();
            setSectionsDrawBackground(true);
            setClipToPadding(false);
            glassEngine.addScrolledView(this);

            addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateActionBarVisible(true);
                    if (Math.abs(dy) >= dp(5)) {
                        for (int i = 0, count = getChildCount(); i < count; i++) {
                            View child = getChildAt(i);
                            if (child instanceof MirrlyUiKit.HeroCardView) {
                                ((MirrlyUiKit.HeroCardView) child).onListScrolled(dy);
                            }
                        }
                    }
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!enterAnimated && SharedConfig.animationsEnabled() && enterAnimator != null) {
                enterAnimated = true;
                enterAnimator.showItemsAnimated(0);
            }
        }

        @Nullable
        @Override
        public BaseFragment getMirrlyHeroHost() {
            return MirrlyBaseSettingsFragment.this;
        }
    }
}
